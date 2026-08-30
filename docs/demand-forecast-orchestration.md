# 일일 수요예측 Backend 오케스트레이션

## 처리 흐름

```text
매일 01:00 KST
  -> 전날 SALES_DAILY 검증·CSV 생성·Azure Blob 업로드
  -> FastAPI Azure ML Job 제출
  -> FastAPI를 통한 Azure Job 상태 조회
  -> 완료 시 FastAPI 결과 전송 요청
  -> 최대 1,000건 단위 staging 저장
  -> 전체 배치·전체 건수 검증
  -> demand_forecast 최종 MERGE
  -> 실행 성공 상태 및 관리자 알림 저장
```

`demand_forecast_staging`에 전체 결과를 격리하므로 기존 Forecast 조회, 위험도,
통계 및 대시보드 쿼리는 변경하지 않습니다. 마지막 배치 트랜잭션의 최종 MERGE가
커밋된 이후에만 새로운 기준일의 결과가 조회됩니다.

## FastAPI 계약

Backend는 당일을 예측 시작일로 전달합니다. CSV에는 그 전날까지 확정된 판매 이력이
포함됩니다.

```json
{
  "forecastBaseDate": "2026-08-22",
  "triggerType": "SCHEDULED"
}
```

FastAPI 제출 응답의 `azureJobId`로 Backend가 실행 상태를 연결합니다. 모델명과 모델
버전은 결과 callback의 Azure Job tag 기반 metadata에서 확정합니다.
Azure Job 완료 후 Backend가 Import를 요청하면 FastAPI는 기존 적재 API로 다음
manifest 값을 모든 배치에서 동일하게 전달합니다. 현재 FastAPI처럼 `totalItems`를
생략하면 Backend가 마지막 고유 배치 수신 시 실제 수신 건수로 확정합니다.

```json
{
  "azureJobId": "azure-job-125",
  "modelName": "stockit-demand-lightgbm",
  "modelVersion": "1",
  "forecastBaseDate": "2026-08-22",
  "batchNumber": 1,
  "totalBatches": 10,
  "totalItems": 9842,
  "forecasts": []
}
```

동일 `azureJobId + batchNumber`가 재전송되면 payload hash가 같은 경우 멱등 성공으로
응답하고, 내용이 다르면 `DEMAND_FORECAST-007` 충돌로 거절합니다.

## 운영 환경변수

```env
DEMAND_FORECAST_ORCHESTRATION_ENABLED=true
DEMAND_FORECAST_ORCHESTRATION_CRON=0 0 1 * * *
DEMAND_FORECAST_ORCHESTRATION_ZONE=Asia/Seoul
DEMAND_FORECAST_POLL_INTERVAL=30s
DEMAND_FORECAST_JOB_TIMEOUT=2h
DEMAND_FORECAST_FASTAPI_BASE_URL=https://<fastapi-host>
DEMAND_FORECAST_FASTAPI_KEY=<secret>
DEMAND_FORECAST_TEAMS_ALERT_ENABLED=true
DEMAND_FORECAST_TEAMS_WEBHOOK_URL=https://<teams-workflow-webhook>
DEMAND_FORECAST_TEAMS_CONNECT_TIMEOUT=3s
DEMAND_FORECAST_TEAMS_READ_TIMEOUT=10s
DEMAND_FORECAST_TEAMS_SCHEDULER_COOLDOWN=10m
APP_ENVIRONMENT=production
DEMAND_FORECAST_DASHBOARD_URL=https://<admin-dashboard>
```

오케스트레이션을 활성화할 때 판매 CSV destination은 `AZURE_BLOB`이어야 합니다.
로컬 destination은 Blob URL을 만들지 않으므로 실행이 명확한 실패 상태와 알림으로
종료됩니다.

## 알림 API

```text
GET   /api/v1/notifications
GET   /api/v1/notifications/unread-count
PATCH /api/v1/notifications/{notificationId}/read
```

전체 staging 데이터가 최종 테이블에 반영된 이후 성공 알림이 `notification`에
생성됩니다. 실패 알림은 `notification`에 저장하지 않고 Teams IT 운영 채널 Workflow로
전송합니다.

## Teams IT 운영 채널 알림

Teams 채널의 Workflows 앱에서 `Send webhook alerts to a channel`을 생성하고 발급된
HTTPS URL을 `DEMAND_FORECAST_TEAMS_WEBHOOK_URL`로 주입합니다. Workflow에는 운영
연속성을 위해 공동 소유자를 지정합니다.

다음 실패가 Adaptive Card 한 건으로 채널에 게시됩니다.

- CSV Export 또는 FastAPI 제출 실패
- 오케스트레이터 큐 포화
- Azure ML Job 실패·취소
- 전체 파이프라인 제한 시간 초과
- 일일 Trigger 등록 실패
- Azure 상태 Poller 조회 실패

카드 본문은 제목과 실행 메타데이터를 먼저 표시하고, 오류 상세는 작은 글씨로 가장 안쪽
원인만 요약합니다. Teams Incoming Webhook은 임의 CSS나 `font-size`를 허용하지 않으므로
Adaptive Card의 `size: Small` 범위 안에서 표시 크기를 제어합니다.

Run 실패는 상태 전이가 한 번만 성공하므로 한 번 전송됩니다. Run이 만들어지기 전의
스케줄러 실패는 동일 장애가 반복될 수 있어 인스턴스별 cooldown 동안 중복 전송을
억제합니다. Teams 전송 실패는 원래 수요예측 상태 트랜잭션을 롤백하지 않고 서버 로그에
남깁니다.
