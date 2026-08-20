# AI 전략 생성 RabbitMQ 개발 가이드

## 처리 흐름

```text
strategy_case 저장
  -> DB COMMIT
  -> AFTER_COMMIT 메시지 발행
  -> Publisher Confirm
  -> Main Queue
  -> Consumer가 Case와 request_payload_json 재조회
  -> generation_stage=FORECASTING 조건부 갱신
  -> ACK
```

이번 구현에서 Consumer의 성공 책임은 `FORECASTING` 단계 진입까지다. ML 수요예측,
전략 후보 계산, Redis 결과 저장과 SSE 알림은 후속 Workflow에서 연결한다.

## 로컬 RabbitMQ 실행

```bash
docker compose -f compose.rabbitmq.yml up -d
```

- AMQP: `localhost:5672`
- Management UI: `http://localhost:15672`
- 로컬 기본 계정: `stockit_local`
- 로컬 기본 비밀번호: `stockit_local`

운영에서는 `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`를 반드시 별도 비밀값으로
재정의한다. 로컬 RabbitMQ를 중지할 때는 다음 명령을 사용한다.

```bash
docker compose -f compose.rabbitmq.yml down
```

## Queue topology

| 구분 | 이름 | 역할 |
| --- | --- | --- |
| Main Queue | `stockit.ai-strategy.generate.v1` | Consumer가 처리할 생성 작업 |
| Retry Queue | `stockit.ai-strategy.generate.v1.retry` | 일시 실패 메시지를 기본 30초 보관 |
| DLQ | `stockit.ai-strategy.generate.dlq` | 영구 오류 또는 총 3회 실패 메시지 격리 |

최초 처리도 시도 횟수에 포함한다. 일시 오류는 최대 두 번 재시도하며, Retry Queue의
TTL이 끝나면 Main Queue로 돌아간다. DLQ에는 자동 Consumer를 연결하지 않는다.

Management UI에서 다음 값을 우선 확인한다.

- `Ready`: 아직 Consumer에게 전달되지 않은 메시지
- `Unacked`: Consumer가 수신했지만 ACK하지 않은 메시지
- `Consumers`: Queue를 구독 중인 Consumer 수
- Retry Queue와 DLQ의 적재량

## 주요 환경변수

```env
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=stockit_local
RABBITMQ_PASSWORD=stockit_local
AI_STRATEGY_MAX_ATTEMPTS=3
AI_STRATEGY_RETRY_DELAY=30s
AI_STRATEGY_CONFIRM_TIMEOUT=5s
```

일반 런타임에서는 메시징이 활성화된다. RabbitMQ와 무관한 기존 테스트는
`application-test.yml`에서 비활성화하고, Testcontainers 통합 테스트만 동적으로 다시
활성화한다.

## 장애 처리

- Publisher NACK, Confirm timeout, 미라우팅 return은 `MQ_PUBLISH_FAILED`로 기록한다.
- 손상된 메시지, 지원하지 않는 schema version과 잘못된 저장 payload는 재시도 없이
  `GENERATION_FAILED` 및 DLQ로 보낸다.
- 일시 오류가 총 3회 실패하면 `MQ_RETRY_EXHAUSTED`로 기록하고 DLQ로 보낸다.
- Retry 메시지의 Publisher Confirm을 받기 전에는 원본 메시지를 ACK하지 않는다.
- 동일 Case의 중복 메시지는 조건부 DB 갱신 결과를 이용해 no-op ACK한다.

## 테스트

```bash
./gradlew test --tests \
  "com.stockit.backend.feature.strategy.messaging.StrategyGenerationRabbitIntegrationTest"
```

Docker가 실행 중이면 Testcontainers가 실제 RabbitMQ로 정상 소비, Retry TTL과 DLQ
라우팅을 검증한다. Docker를 사용할 수 없는 환경에서는 해당 통합 테스트만 skip된다.

## 현재 한계와 후속 안정화

- DB COMMIT 직후 프로세스가 종료되면 메시지가 발행되지 않을 수 있다. Transactional
  Outbox와 미발행 Case 복구 배치는 후속 안정화 범위다.
- 현재 멱등성은 단일 Consumer, 종료 상태 확인과 `generation_stage IS NULL` 조건부 갱신을
  사용한다. 장시간 실행되는 ML·AI Workflow를 연결하기 전 Redis Case lock 또는 실행 lease가
  필요하다.
- DLQ 메시지의 조회와 수동 재발행 API는 아직 제공하지 않는다.
- Queue TTL은 Queue 선언 인자이므로 운영 중 retry delay를 변경할 때는 Queue 재생성 또는
  versioning 절차가 필요하다.
