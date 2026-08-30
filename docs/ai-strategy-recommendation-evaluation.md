# AI 전략 추천 품질·지연 평가

## 목적

AI 전략 추천을 "LLM이 응답했다"는 성공 여부만으로 평가하지 않는다. 동일한 서버
후보 집합에서 추천의 실행 가능성, 경제성, 대안 다양성, 사용자 선호 반영을
정량화하고, 품질을 보존하는 범위에서 지연과 Token 사용량을 줄인다.

서버는 실행 가능한 후보와 정량 계산을 확정하고 LLM은 후보 ID를 선택·정렬해
설명한다. 따라서 평가는 LLM이 새 수치를 만들었는지가 아니라 다음 질문에 답한다.

- 허용된 후보만 구조적으로 올바르게 선택했는가?
- 경제효과가 지나치게 낮은 후보를 1순위로 선택하지 않았는가?
- 서로 다른 실행 대안을 제공했는가?
- 사용자가 명시한 전략·판매처 우선순위와 고정 조건을 존중했는가?
- 이 품질을 얻는 데 걸린 시간과 Token은 얼마인가?
- 공급자 오류가 발생했을 때 어떤 이유로 Fallback했는가?

## 운영 품질 지표

### 구조 유효성과 실행 가능성

LLM 응답의 후보 ID, 중복 후보, 중복 전략군, 순위 범위·연속성을 검사한다.
운영 흐름에서는 기존 `StrategyRecommendationResponseValidator`가 잘못된 응답을
거부하고 결정론적 서버 Fallback으로 전환한다. 평가기는 같은 응답에서 다음을
계산한다.

```text
validSelectionRate
= 구조적으로 유효한 추천 수 / 전체 추천 수
```

목표는 `1.0`, 즉 100%다. LLM 실패 원인은 무제한 오류 코드를 Metric 태그로
사용하지 않고 다음 저카디널리티 범주로 정규화한다.

- `timeout`
- `rate_limited`
- `quota_exhausted`
- `unavailable`
- `incomplete`
- `budget_exceeded`
- `invalid_response`
- `authentication`
- `request_rejected`
- `other`

### 경제성 Regret

```text
Top1 Regret
= max(서버가 전달한 후보의 netEffect) - AI 1순위 netEffect

Top1 Regret Rate
= Top1 Regret / max(|최고 netEffect|, 1)
```

Regret는 AI 1순위가 경제성 최대 후보와 얼마나 차이 나는지 보여준다. AI가 폐기량,
재고 소진, 사용자 우선순위와 대안 다양성을 함께 고려하므로 Regret `0`을 강제하지
않는다. 대신 지나치게 열등한 후보가 1순위가 되는지 탐지하는 품질 Guardrail로
사용한다. 모든 후보가 손실인 경우에도 최고 `netEffect`의 절댓값을 분모로 사용해
비율을 안정적으로 비교한다.

### 대안 다양성

- 추천된 서로 다른 전략군 수와 비율
- 추천된 서로 다른 전략 타입 수
- 추천된 서로 다른 대상 판매처 수

동일 전략군은 수량·할인율·기간만 다른 실행 변형이다. 최종 3~4개 대안에 같은
전략군이 중복되면 비교 가치가 낮으므로 구조 위반으로 집계하고 Fallback 대상이
된다.

### 사용자 조건 반영

- AI 1순위의 전략 타입 우선순위 준수 여부
- AI 1순위의 대상 판매처 우선순위 준수 여부
- 고정 시작일·종료일 위반 수
- 사용자가 제한한 전략 타입·대상 판매처 위반 수

우선순위 준수와 경제성은 분리해 관찰한다. 우선순위 후보의 경제효과가 낮아 AI가
다른 후보를 선택할 수 있으므로, 단순히 경제성 Regret만으로 추천 오류라고 판단하지
않는다. 반면 고정 기간과 허용 범위 위반은 서버 계약 위반이므로 목표가 0건이다.

## Metric

| Metric | Tag | 의미 |
|---|---|---|
| `stockit.ai.strategy.recommendation.quality` | `metric`, `source` | 유효 선택률, 다양성 비율, Regret Rate, 우선순위 준수율 |
| `stockit.ai.strategy.recommendation.quality.count` | `metric`, `source` | 구조·고정 조건 위반 수, 전략군·전략 타입·판매처 수 |
| `stockit.ai.strategy.llm.failure` | `reason` | 정규화한 LLM 실패 원인 |
| `stockit.ai.strategy.llm.tokens` | `direction` | 입력·출력 Token |
| `stockit.ai.strategy.generation.stage.duration` | `stage`, `outcome` | 생성 단계별 지연 |

`source`는 `llm`, `fallback`만 사용한다. Case·SKU·판매처·후보 ID는 Metric 태그에
넣지 않아 시계열 폭증을 방지한다.

## 고정 시나리오 Gemini 평가 Harness

외부 Gemini를 사용하는 평가는 일반 테스트와 분리된 수동 Source Set에서 실행한다.
후보 개수별 실험은 하지 않고 모든 시나리오에 고정된 6개 후보를 사용한다.

1. `ECONOMIC_BALANCE`: 경제효과가 다른 전략군을 비교한다.
2. `USER_PRIORITY_TRADEOFF`: 경제효과와 사용자 우선순위가 충돌한다.
3. `DISPOSAL_TRADEOFF`: 순효과와 폐기 감소 효과가 충돌한다.

```bash
GEMINI_LIVE_TEST=true \
AI_RECOMMENDATION_EVAL_RUNS=3 \
AI_RECOMMENDATION_EVAL_THINKING_LEVEL=low \
./gradlew geminiLiveTest \
  --tests '*GeminiRecommendationQualityEvaluationTest' --info
```

결과는 다음 위치에 JSON으로 생성한다.

```text
build/reports/ai-strategy-evaluation/
gemini-quality-evaluation-{thinkingLevel}.json
```

할당량 등으로 일부 시나리오만 이어서 측정할 때는
`AI_RECOMMENDATION_EVAL_SCENARIO`에 `ECONOMIC_BALANCE`,
`USER_PRIORITY_TRADEOFF`, `DISPOSAL_TRADEOFF` 중 하나를 지정한다.

각 실행의 지연, 입출력 Token, 추천 후보 순위, 품질 지표를 기록하고 전체 p50·p95,
평균 Regret, 다양성, 우선순위 준수율, 시나리오별 Top1 일치율을 요약한다. API
할당량이나 일시 오류가 발생해도 성공 표본을 잃지 않고 실패 코드를 함께 기록한다.

## 2026-08-29 Baseline

`gemini-3.5-flash`, `thinking_level=low`, 시나리오 3개를 각각 3회 실행한 결과다.
표본은 품질 평가 체계와 개선 방향을 검증하기 위한 로컬 Baseline이며 운영 SLA가
아니다.

| 항목 | 결과 |
|---|---:|
| 실행 수 | 9 |
| Gemini 지연 p50 | 11,978ms |
| Gemini 지연 p95 | 20,072ms |
| 평균 입력 Token | 2,711.67 |
| 평균 출력 Token | 671.00 |
| 평균 사고 Token | 1,100.33 |
| 평균 총 Token | 4,483.00 |
| 평균 Top1 Regret Rate | 11.43% |
| 전략군 다양성 비율 | 100% |
| 전략 타입 우선순위 준수율 | 100% |
| 판매처 우선순위 준수율 | 100% |
| 구조 위반 | 0건 |
| 시나리오별 Top1 일치율 | 모두 100% |

동일 후보와 seed에서도 사고 Token이 `0~2,424`로 변했고, 사고 Token이 없는 호출은
약 5.8~8.0초, 2천 개 이상인 호출은 약 14.9~20.1초가 걸렸다. 서버 후보 계산보다
Gemini의 동적 사고량이 전체 지연 편차에 큰 영향을 주는 것으로 관찰됐다.

## `minimal` 사고 수준 비교와 적용 판단

Gemini 3.5 Flash가 지원하는 `minimal` 사고 수준을 동일한 3개 시나리오에서 각각
3회씩 비교했다. API 할당량으로 중단된 시나리오는 필터를 사용해 이어서 측정했고,
두 실행의 성공 표본 9개를 합산했다.

| 항목 | `low` | `minimal` | 변화 |
|---|---:|---:|---:|
| 성공 표본 | 9 | 9 | 동일 |
| 지연 p50 | 11,978ms | 4,404ms | 63.2% 감소 |
| 지연 p95 | 20,072ms | 11,320ms | 43.6% 감소 |
| 평균 입력 Token | 2,711.67 | 2,711.67 | 동일 |
| 평균 출력 Token | 671.00 | 694.56 | 3.5% 증가 |
| 평균 사고 Token | 1,100.33 | 0 | 100% 감소 |
| 평균 총 Token | 4,483.00 | 3,406.22 | 24.0% 감소 |
| 평균 Top1 Regret Rate | 11.43% | 11.43% | 동일 |
| 전략군 다양성 | 100% | 100% | 동일 |
| 전략·판매처 우선순위 준수 | 100% | 100% | 동일 |
| 구조 위반 | 0건 | 0건 | 동일 |
| 시나리오별 Top1 일치율 | 100% | 100% | 동일 |

반복 측정 도중 `LLM_API_QUOTA_EXHAUSTED`가 1회 발생했다. 이는 `low`와
`minimal` 호출을 연속 수행하며 누적 할당량에 도달한 시점에 발생했고, 잠시 뒤
누락 시나리오만 재실행했을 때 3회 모두 성공했다. 품질 차이로 인한 실패로
분류하지 않되 운영 Metric에서는 `quota_exhausted`로 계속 관찰한다.

사고 수준 지원 범위와 `minimal`의 지연 최소화 목적은 Google의
[Gemini thinking 공식 문서](https://ai.google.dev/gemini-api/docs/thinking)를
기준으로 한다.

평가 결과 구조 유효성, 경제성 Regret, 다양성, 사용자 우선순위를 유지하면서 p95와
총 Token이 감소했으므로 기본 사고 수준을 `minimal`로 변경한다. 설정은
`ai.recommendation.thinking-level`로 분리되어 있어 운영 품질 저하가 관찰되면 환경
변수만 `low`로 되돌릴 수 있다.

다음 조건은 이후 모델·프롬프트 변경에도 동일한 품질 Guardrail로 사용한다.

- 구조 위반과 고정 조건 위반 0건
- 전략군 다양성과 사용자 우선순위 준수율 유지
- 평균 Top1 Regret Rate가 Baseline보다 유의미하게 악화되지 않음
- p95 지연과 총 Token이 감소
- 공급자 오류·Fallback률이 증가하지 않음

## 외부 수요예측 지연과 전체 병목 판단

Gemini만 빠르게 만든 뒤 전체 생성이 빨라졌다고 결론 내리지 않기 위해 실제 ML
수요예측 API도 별도로 측정했다. 2026-08-29 로컬 환경에서 이미 정상 생성된
체크포인트와 같은 SKU·판매처 범위를 사용했으며, 판매처 4개에 대해 90일 일별
예측을 5회 요청했다. 응답은 매회 17,439 bytes였고 계약 필드
`salesPointForecasts` 존재를 함께 검증했다.

| 항목 | 결과 |
|---|---:|
| 판매처 수 | 4개 |
| 예측 기간 | 90일 |
| 성공 요청 | 5/5 |
| 지연 최소 | 154ms |
| 지연 p50 | 175ms |
| 지연 p95 | 638ms |
| 지연 최대 | 638ms |

첫 요청은 638ms, 이후 네 요청은 154~175ms였다. 같은 시점의 `minimal` Gemini
p50 4,404ms·p95 11,320ms 및 대형 서버 후보 시뮬레이션 p50 74.029ms·p95
77.711ms와 비교하면, 서로 다른 고정 Fixture를 단순 합산한 세 구간 중 Gemini가
p50 약 94.5%, p95 약 94.0%를 차지한다. 이는 실제 한 Case를 관통한 E2E SLA가
아니라 병목 위치를 판단하기 위한 분리 측정이다.

같은 방식으로 `low`와 `minimal`의 측정 구간 합을 비교하면 다음과 같다.

| 분리 측정 합 | `low` | `minimal` | 변화 |
|---|---:|---:|---:|
| p50 방향성 지표 | 12,227ms | 4,653ms | 61.9% 감소 |
| p95 방향성 지표 | 20,788ms | 12,036ms | 42.1% 감소 |

따라서 이번 변경의 주된 지연 개선 수단을 후보 계산 멀티스레드화가 아니라 Gemini
사고 수준 조정으로 둔 판단과 일치한다. 운영 환경에서는 이 문서의 합산값을 SLA로
사용하지 않고 `stockit.ai.strategy.generation.stage.duration`의 실제 Case
분포로 다시 검증한다.

## 복구 관찰 결과

`low`와 `minimal`을 연속 평가하는 동안 할당량 초과가 1회 발생했다. 잠시 뒤
누락 시나리오만 재실행했을 때 3/3회 성공해 결과 표본을 복구했다. 표본이 한 건뿐인
만큼 복구율 목표의 근거로 사용하지는 않으며, 다음 항목을 분리해 운영에서
관찰한다.

- 최초 LLM 성공률과 `quota_exhausted` 발생률
- 메시지 재시도 후 성공률
- 마지막 시도에서 결정론적 Fallback으로 완료된 비율
- LLM과 Fallback 각각의 구조 위반·Regret·다양성

현재 회귀 테스트는 잘못된 LLM 응답, 불완전 상호작용, 사고 예산 초과, 인증 실패,
마지막 시도의 일시 장애가 검증된 서버 Fallback 또는 명시적 재시도 정책으로
처리되는지를 검증한다. Fallback 역시 같은 품질 평가기를 통과하며 원인 코드는
저카디널리티 범주로 계측된다.
