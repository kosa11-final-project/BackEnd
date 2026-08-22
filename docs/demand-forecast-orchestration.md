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

전체 staging 데이터가 최종 테이블에 반영된 이후 성공 알림이 생성됩니다. Export,
FastAPI 제출, Azure Job 또는 전체 파이프라인 제한 시간 실패는 오류 알림을 생성합니다.
