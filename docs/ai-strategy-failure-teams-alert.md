# AI 전략 생성 최종 실패 Teams 알림

## 목적

AI 전략 생성이 재시도와 복구를 모두 마친 뒤 `GENERATION_FAILED`로 확정되면
`IT-AI-전략` 채널에 운영 알림을 보낸다. 중간 재시도와 Gemini 실패 후 서버 후보로
정상 복구된 경우에는 알림을 보내지 않는다.

## 처리 흐름

```text
StrategyGenerationFailureService
  → GENERATION_FAILED 상태 커밋
  → StrategyGenerationStateChangedEvent
  → AFTER_COMMIT Listener
  → Oracle에서 Case·요청자·SKU·판매처 조회
  → 오류 메시지 Secret 제거 및 요청 조건 요약
  → Teams Workflow Webhook에 Adaptive Card 전송
```

알림은 원래 생성 실패 처리와 분리된 best-effort 후처리다. Oracle 조회, 카드 생성,
Teams 호출 중 어느 단계가 실패해도 Case 상태를 변경하거나 사용자 요청을 다시
실행하지 않고 서버 로그에만 남긴다. RabbitMQ 발행 자체가 실패한 경우도 알릴 수 있도록
이 경로는 전략 생성 메시지 큐에 의존하지 않는다.

## 카드 정보

- 환경, 발생 시각, 실패 영역, 생성 단계, 최종 처리 코드·근본 원인 코드와 정제된 오류 메시지
- Case ID·코드·제목과 재시도 원본 Case ID
- 요청자 ID·이름, SKU ID·코드·이름, 현재 재고 보유 판매처
- 선택 LOT 수, 희망 판매처·전략 타입의 사용자 지정 여부와 개수, 희망 판매 기간
- `AI_STRATEGY_GENERATION_FAILED:{strategyCaseId}` 중복 식별 키와 로그 검색어
- 설정된 경우 Case 상세 및 로그 조회 링크

요청 스냅샷 JSON을 해석하지 못하더라도 Case와 실패 원인 중심의 최소 알림은 계속
전송한다. 오류 메시지는 개행을 제거하고 인증·토큰·Secret 값을 마스킹한 뒤 최대
1,000자로 제한한다.

## 환경변수

```env
AI_STRATEGY_FAILURE_TEAMS_ALERT_ENABLED=true
AI_STRATEGY_FAILURE_TEAMS_WEBHOOK_URL=https://<teams-workflow-webhook>
AI_STRATEGY_FAILURE_TEAMS_CONNECT_TIMEOUT=3s
AI_STRATEGY_FAILURE_TEAMS_READ_TIMEOUT=10s
APP_ENVIRONMENT=production

# 선택 사항: {strategyCaseId}가 실제 Case ID로 치환된다.
AI_STRATEGY_FAILURE_CASE_URL_TEMPLATE=https://<frontend>/ai-strategies/{strategyCaseId}
AI_STRATEGY_FAILURE_LOG_URL_TEMPLATE=https://<logging>/search?caseId={strategyCaseId}
```

Webhook은 Teams의 `IT-AI-전략` 채널에서 만든 Workflow URL을 사용한다. 최종 전략
승인 요청에 사용하는 `TEAMS_APPROVAL_WEBHOOK_URL`과는 목적과 채널이 다르므로 공유하지
않는다. 기능을 활성화한 환경에서는 Webhook URL이 절대 HTTPS URL이 아니면 애플리케이션
기동을 거부한다.

## 현재 보장 범위와 후속 안정화

상태 전이가 한 번만 성공할 때 이벤트도 한 번 발생하므로 정상 처리에서는 Case당 한
건이 전송된다. 다만 Teams가 카드를 수신한 직후 응답이 유실되는 극단적 상황까지
영속적으로 보장하는 Outbox는 이번 범위에 포함하지 않는다. 반드시 한 번 이상 전달과
운영 재처리가 필요해지면 알림 Outbox, 고유 중복 키, 재전송 스케줄러를 별도 도입한다.
