# 통합재고 SKU × 판매처 상태등급 및 30일 예상 폐기 정책

기준 규칙 버전: `v1.8.0`

## 1. 판정 범위와 기준 데이터

- 판정 단위는 `SKU × 판매처`입니다.
- 실제 보유 판매처(`stock_sales_point_id`)와 할당 판매처(`allocated_sales_point_id`) 중 하나라도 있으면 같은 판매처 재고로 봅니다. 둘 다 없으면 `UNASSIGNED` 물류센터 미할당 재고입니다.
- 수요예측은 판정일 당일 `base_date`를 우선하고 없으면 전일 행까지만 사용합니다. 이틀 이상 지난 예측은 미래 horizon이 어긋날 수 있어 해당 scope에서 unusable로 처리합니다.
- 같은 예측 행의 D+7·D+14·D+30 값이 수정된 경우에도 `updated_at`을 비교해 재판정합니다.
- 활성 모델의 실제 예측만 사용하며 `DUMMY_BASELINE`은 제외합니다.
- 누적 예측값은 `0 <= F7 <= F14 <= F30 <= F60 <= F90`이어야 합니다. 비단조·음수·null horizon은 해당 forecast만 unusable로 처리하고 품질 오류 건수를 동기화 로그에 남깁니다.

## 2. LOT 상태와 판매 가능 재고

```text
effectiveEndDate(lot)
  = min(expiryDate, saleStopDate)
```

동기화 시 원천 테이블의 LOT 상태는 사용하지 않고 날짜와 통합 잔량으로 다시 판정합니다. 소비기한과 판매중지일이 모두 판정일 이전이면 더 빠른 날짜를 적용하고, 같은 날이면 `EXPIRED`를 우선합니다.

```text
expiryDate <= 판정일 또는 saleStopDate <= 판정일
  -> expiryDate가 없거나 saleStopDate보다 빠르면 EXPIRED
  -> saleStopDate가 expiryDate보다 빠르면 SALE_STOPPED
  -> 두 날짜가 같으면 EXPIRED

위 날짜 상태가 아니고 통합 잔량(on_hand_qty + reserved_qty) == 0
  -> DEPLETED

그 외
  -> AVAILABLE
```

판매 가능 재고는 소비기한 경과, 판매중지, 소진 LOT를 제외한 `on_hand_qty` 합계입니다. 판매 불가 LOT의 예약수량은 화면 집계에서 0으로 처리합니다. 경과 LOT는 이미 판매 불가 재고이므로 미래 예상 폐기수량에는 다시 넣지 않습니다.

## 3. 실제 판매 종료일 기준 30일 예상 폐기수량

판정일 다음 날부터 D+30까지 판매가 종료되는 판매 가능 LOT만 대상으로 합니다. D+31 이후 LOT는 이번 지표에서 제외합니다.

각 실제 판매 종료일 `t`에 대해 다음 값을 계산합니다. 이미 `EXPIRED`, `SALE_STOPPED`, `DEPLETED`인 LOT는 대상에서 제외합니다.

```text
S(t) = t일까지 판매가 종료되는 LOT 수량의 누적합
F(t) = t일까지의 누적 예측수요

rawDisposal(t) = max(0, S(t) - F(t))
D(t) = max over u<=t(rawDisposal(u))
E30 = D(30)
```

`F(t)`는 D+7, D+14, D+30 누적 예측 사이를 선형 보간합니다.

```text
1 <= t <= 7
  F(t) = F7 × t / 7

7 < t <= 14
  F(t) = F7 + (F14 - F7) × (t - 7) / 7

14 < t <= 30
  F(t) = F14 + (F30 - F14) × (t - 14) / 16
```

미할당 재고 또는 유효한 수요예측이 없는 범위는 보수적으로 `F(t) = 0`으로 두어 30일 안에 종료되는 수량 전부를 예상 폐기로 봅니다. 이는 없는 예측을 임의로 연결하는 것이 아니라, 확정된 LOT 종료 수량만 계산하는 제한 기준입니다.

```text
R30 = E30 / availableQty × 100
```

중기·장기 지표는 30일 폐기분을 다시 세지 않습니다.

```text
E90 = D(90)
M90 = max(0, E90 - E30)                    # D+31~D+90 추가 폐기 예상
remainingSellable(t) = max(0, availableQty - F(t) - D(t))
B = safetyStockQty (없으면 0)
X60 = max(0, remainingSellable(60) - B)
X90 = max(0, remainingSellable(90) - B)
```

`T30/D30`은 E30을 처음 만든 가장 빠른 실제 LOT 종료일, `TM/DM`은 M90을 처음 만든 실제 LOT 종료일입니다. `M90`은 D+31~D+90의 중기 폐기, `X60/X90`은 장기 안전재고 초과 잔량을 의미합니다. D+60·D+90은 유효 forecast가 있을 때만 계산하며 ML 신뢰도는 사용하지 않습니다.

`R30`은 운영 우선순위용 예상 폐기율이며 실제 폐기율이나 ESG 공시 폐기율이 아닙니다.

## 4. SKU × 판매처 최종 위험등급

모든 규칙을 계산한 뒤 가장 높은 심각도를 최종 등급으로 사용합니다.

DB 저장값과 API 반환값은 모두 `GOOD`, `NORMAL`, `WARNING`, `CRITICAL`입니다. 프론트엔드만 각각 `양호`, `보통`, `주의`, `위험`으로 표시합니다.

### 4.1 판매 가능 여부와 부족 위험

| 조건 | 등급 |
|---|---|
| 현재 재고 데이터 없음 | CRITICAL |
| `availableQty == 0` | CRITICAL |
| `availableQty >= safetyStockQty`이고 `projectedD7 < safetyStockQty` | CRITICAL |
| `availableQty < safetyStockQty` | WARNING |
| D+30 예측 부족이 있고 예상 재고일수 `<= 14일` | CRITICAL |
| D+30 예측 부족이 있고 예상 재고일수 `> 14일` 및 `< 30일` | WARNING |
| 판매처 재고에 예측과 안전재고 정책이 모두 없고 다른 위험 신호가 없음 | NORMAL |
| 미할당 재고에 예측과 안전재고 정책이 없지만 6절의 30일 안정 조건을 모두 충족 | GOOD |

### 4.2 예상 폐기 위험

`E30 > 0`인 경우에만 폐기 위험을 올립니다.

| 조건 | 등급 |
|---|---|
| `R30 >= 20%` | CRITICAL |
| `nearestSaleEndDays <= 7` 이고 `R30 >= 5%` | CRITICAL |
| 위 CRITICAL이 아니고 `R30 >= 5%` | WARNING |
| 위 CRITICAL이 아니고 `nearestSaleEndDays <= 7` | WARNING |
| `R30 < 5%` 이고 `nearestSaleEndDays > 7` | NORMAL |
| `E30 == 0` | 폐기 사유로 등급을 올리지 않음 |

### 4.3 중기·장기 전망

| 조건 | 등급 |
|---|---|
| D+31~D+90 추가 폐기율 `RM90 >= 20%` | WARNING |
| 90일 후 안전재고 초과율 `RX90 >= 20%` | WARNING |
| D+31~D+90 추가 폐기율 `0% < RM90 < 20%` | NORMAL |
| 60일 후 초과분이 있으나 90일 이내 해소 | NORMAL |
| 장기 신호만으로 CRITICAL | 사용하지 않음 |

모든 영역을 계산한 뒤 `CRITICAL > WARNING > NORMAL > GOOD`의 최대 심각도를 최종 등급으로 선택합니다. 동급이면 `DATA_MISSING → ZERO_AVAILABLE_STOCK → STOCKOUT_WITHIN_14_DAYS → PROJECTED_UNDER_SAFETY_D7 → EXPECTED_DISPOSAL_DANGER → CURRENT_UNDER_SAFETY → STOCKOUT_WITHIN_30_DAYS → EXPECTED_DISPOSAL_CAUTION → MEDIUM_TERM_DISPOSAL_CAUTION → LONG_TERM_OVERSTOCK_CAUTION → 모니터링 → 안정` 순서로 핵심 사유 한 건을 고릅니다.

5%, 20%, 7일, 30일은 법정 또는 업계 공통 기준이 아니라 초기 운영 임계값입니다. 8~12주간 예측값과 실제 폐기·할인·기부·전환 결과를 비교한 뒤 상품군과 판매처별로 보정해야 합니다.

### 4.4 저장되는 대표 핵심 사유 전체 목록

아래 조건들은 서로 배타적이지 않습니다. 서버는 모든 조건을 계산한 뒤 최고 등급을 고르고, 같은 등급이 여러 개면 위 우선순위에 따라 한 문장만 `risk_assessment.reason_message`에 저장합니다. 대괄호 안 값은 실제 계산값으로 치환됩니다.

| 코드 | 등급 | 발생 조건 | 저장 문장 형식 |
|---|---|---|---|
| `DATA_MISSING` | CRITICAL | 현재고 데이터 없음 | 현재 재고수량을 확인할 수 없어 판매 가능 재고와 부족 위험을 판정할 수 없습니다. |
| `ZERO_AVAILABLE_STOCK` | CRITICAL | 판매 불가 LOT 제외 후 `availableQty == 0` | 현재 재고 `[현재고]`개 중 판매 불가 LOT의 재고 `[제외수량]`개를 제외한 판매 가능 재고가 0개입니다. |
| `STOCKOUT_WITHIN_14_DAYS` | CRITICAL | D+30 수요 부족, 예상 재고일수 `<= 14` | 현재 판매 가능 재고 `[가용수량]`개와 30일 예측수요 `[F30]`개 기준으로 약 `[재고일수]`일 후 재고가 소진될 것으로 예상됩니다. |
| `PROJECTED_UNDER_SAFETY_D7` | CRITICAL | 현재는 안전재고 이상이지만 D+7 예상재고가 안전재고 미만 | 7일 후 예상 재고 `[D+7 잔량]`개가 안전재고 `[안전재고]`개보다 `[부족수량]`개 부족할 것으로 예상됩니다. |
| `EXPECTED_DISPOSAL_DANGER` | CRITICAL | `R30 >= 20%`, 또는 종료일까지 7일 이하이면서 `R30 >= 5%` | `[종료일]` 판매 종료일까지 `[남은일수]`일 남았으며 30일 예상 폐기수량은 `[E30]`개로, 현재 판매 가능 재고 `[가용수량]`개의 `[R30]%`입니다. |
| `WAREHOUSE_UNSELLABLE_CRITICAL` | CRITICAL | 미할당 재고의 판매 불가 비율 `>= 20%` | 현재 재고 `[현재고]`개 중 판매 불가 재고 `[제외수량]`개를 제외했으며, 판매 불가 비율은 `[비율]%`입니다. |
| `CURRENT_UNDER_SAFETY` | WARNING | 현재 가용수량이 안전재고 미만 | 현재 판매 가능 재고 `[가용수량]`개가 안전재고 `[안전재고]`개보다 `[부족수량]`개 부족합니다. |
| `STOCKOUT_WITHIN_30_DAYS` | WARNING | D+30 수요 부족, 예상 재고일수 `> 14` 및 `< 30` | 30일 예측수요 `[F30]`개가 판매 가능 재고 `[가용수량]`개보다 `[부족수량]`개 많아 약 `[재고일수]`일 후 재고가 소진될 것으로 예상됩니다. |
| `EXPECTED_DISPOSAL_CAUTION` | WARNING | CRITICAL 조건이 아니며 `R30 >= 5%`, 또는 종료일까지 7일 이하 | `[종료일]` 판매 종료일까지 `[남은일수]`일 남았으며 30일 예상 폐기수량은 `[E30]`개로, 현재 판매 가능 재고 `[가용수량]`개의 `[R30]%`입니다. |
| `MEDIUM_TERM_DISPOSAL_CAUTION` | WARNING | D+31~D+90 추가 폐기율 `>= 20%` | 현재 30일 예상 폐기수량은 `[E30]`개입니다. 다만 `[종료일]([남은일수]일 후)` 판매 종료 LOT에서 `[M90]`개(`[RM90]%`)가 남을 것으로 예상됩니다. |
| `LONG_TERM_OVERSTOCK_CAUTION` | WARNING | 90일 후 안전재고 초과율 `>= 20%` | 현재 30일 즉시 위험은 없습니다. 다만 90일 후 안전재고를 제외한 `[X90]`개(`[RX90]%`)가 남을 것으로 예상되어 장기 과잉재고 관리가 필요합니다. |
| `WAREHOUSE_UNSELLABLE_WARNING` | WARNING | 미할당 재고의 판매 불가 비율 `>= 5%` 및 `< 20%` | 현재 재고 `[현재고]`개 중 판매 불가 재고 `[제외수량]`개를 제외했으며, 판매 불가 비율은 `[비율]%`입니다. |
| `EXPECTED_DISPOSAL_MONITORING` | NORMAL | `0% < R30 < 5%`이고 종료일까지 7일 초과 | `[종료일]` 판매 종료일까지 `[남은일수]`일 남았고 30일 예상 폐기수량은 `[E30]`개(`[R30]%`)로 즉시 조치 기준 미만이지만 추이를 관찰해야 합니다. |
| `MEDIUM_TERM_DISPOSAL_MONITORING` | NORMAL | D+31~D+90 추가 폐기율 `> 0%` 및 `< 20%` | 현재 30일 예상 폐기수량은 `[E30]`개입니다. 다만 `[종료일]([남은일수]일 후)` 판매 종료 LOT에서 `[M90]`개(`[RM90]%`)가 남을 것으로 예상되어 중기 재고 추이를 관찰해야 합니다. |
| `LONG_TERM_OVERSTOCK_MONITORING` | NORMAL | 90일 후 안전재고 초과수량이 있고 초과율 `< 20%` | 현재 30일 즉시 위험은 없습니다. 다만 90일 후 안전재고를 제외한 `[X90]`개(`[RX90]%`)가 남을 것으로 예상되어 장기 재고 추이를 관찰해야 합니다. |
| `LONG_TERM_CLEARING_MONITORING` | NORMAL | D+60에는 초과재고가 있지만 D+90 안에 안전재고 수준으로 소진 | 현재 30일 기준 위험은 없습니다. 60일 후 안전재고를 제외한 `[X60]`개가 남지만 90일 이내 안전재고 수준까지 소진될 것으로 예상됩니다. |
| `WAREHOUSE_UNSELLABLE_MONITORING` | NORMAL | 미할당 재고의 판매 불가 비율 `> 0%` 및 `< 5%` | 현재 재고 `[현재고]`개 중 판매 불가 재고 `[제외수량]`개를 제외했으며, 판매 불가 비율은 `[비율]%`입니다. |
| `WAREHOUSE_LOT_DATE_MISSING` | NORMAL | 미할당 양수 LOT의 종료일 누락, 또는 LOT 수량으로 전체 가용수량 설명 불가 | 판매 종료일을 확인할 수 없는 판매 가능 LOT 또는 재고 범위가 `[건수]`건(`[수량]`개) 있어 양호를 확정할 수 없습니다. |
| `LIMITED_BASIS_MONITORING` | NORMAL | 수요예측·안전재고가 없고 미할당 30일 안정 조건도 충족하지 못함 | 현재 판매 가능 재고는 `[가용수량]`개이며, 현재 확인 가능한 기준에서 보통으로 판정했습니다. |
| `WAREHOUSE_30_DAY_CLEAR` | GOOD | 미할당 재고의 6절 안정 조건 모두 충족 | 현재 판매 가능 재고는 `[가용수량]`개이며, 판매 불가 재고와 30일 이내 판매 종료 예정 재고는 0개입니다. |
| `SALE_END_CLEAR` | GOOD | 30일 안에 종료되는 LOT가 있지만 예측수요로 전량 소진 가능하여 `E30 == 0` | `[남은일수]`일 후 판매 종료되는 LOT가 있지만 현재 수요예측 기준으로 기한 내 전량 소진 가능하며, 30일 예상 폐기수량은 0개입니다. |
| `CURRENT_POLICY_CLEAR` | GOOD | 안전재고 정책은 있고 수요예측은 없으며 현재 안전재고 충족 | 현재 판매 가능 재고 `[가용수량]`개가 안전재고 `[안전재고]`개를 충족합니다. |
| `OPTIMAL_STOCK` | GOOD | 유효 수요예측 기준 부족·폐기·장기과잉 신호가 없고 안전재고도 충족 | 현재 판매 가능 재고 `[가용수량]`개가 `[30일 수요 또는 안전재고]`를 충족하고, 30일 예상 폐기수량은 0개이며 90일 이내 장기 과잉재고도 예상되지 않습니다. |

`FORECAST_UNAVAILABLE`, `FORECAST_INVALID`, `LOT_EXPIRED_EXCLUDED`, `LOT_SALE_STOPPED_EXCLUDED`, `LOT_DEPLETED_EXCLUDED`는 계산 근거를 설명하는 `INFO` 사유입니다. 단독으로 최종 등급을 올리거나 대표 핵심 사유가 되지 않습니다.

## 5. 계산 예시

### 예시 A: 폐기율 20% 이상

```text
availableQty = 100개
D+20 판매 종료 LOT 누적수량 S(20) = 50개
F7 = 10, F14 = 20, F30 = 25
F(20) = 20 + (25 - 20) × 6 / 16 = 21.875개
E30 = 50 - 21.875 = 28.125개
R30 = 28.125 / 100 × 100 = 28.13%
```

`R30 >= 20%`이므로 폐기 위험은 `CRITICAL`입니다.

### 예시 B: 폐기율 5% 이상 20% 미만

```text
availableQty = 100개
D+20 판매 종료 LOT 누적수량 S(20) = 30개
F(20) = 21.875개
E30 = 8.125개
R30 = 8.13%
```

판매 종료일까지 20일 남았고 `5% <= R30 < 20%`이므로 폐기 위험은 `WARNING`입니다.

### 예시 C: 30일 안에 종료되지만 전량 소진 가능

```text
availableQty = 100개
D+10 판매 종료 LOT 누적수량 S(10) = 20개
F7 = 15, F14 = 30
F(10) = 15 + (30 - 15) × 3 / 7 = 21.429개
E30 = max(0, 20 - 21.429) = 0개
```

날짜가 30일 안에 있더라도 현재 예측수요로 전량 판매 가능하므로 폐기 사유로 등급을 올리지 않습니다.

### 예시 D: 미할당 재고에 수요예측 없음

```text
availableQty = 100개
D+12 판매 종료 LOT 수량 = 40개
유효 수요예측 없음 -> F(12) = 0
E30 = 40개
R30 = 40%
```

`R30 >= 20%`이므로 `CRITICAL`입니다.

### 예시 E: 일부 LOT가 이미 판매 불가

```text
전체 on_hand_qty = 100개
이미 소비기한이 지난 LOT = 40개
정상 LOT = 60개
availableQty = 100 - 40 = 60개
```

지난 LOT는 판매 가능 재고에서 제외하지만 미래 `E30`에는 중복 포함하지 않습니다. 판매처 범위는 나머지 정상 LOT와 수요가 충분하면 다른 규칙에 따라 등급을 정합니다. 미할당 범위는 아래 판매 불가 비율 규칙도 함께 적용합니다.

## 6. 미할당 물류센터 재고 전용 판정

미할당 범위에는 판매처별 수요예측과 안전재고를 연결하지 않습니다. 대신 현재 확정할 수 있는 통합 재고·LOT 사실만으로 판정합니다.

```text
excludedQty = EXPIRED 또는 SALE_STOPPED LOT의 on_hand_qty 합계
excludedRate = excludedQty / currentQty × 100
E30 = 30일 이내 종료되는 AVAILABLE LOT 수량 (수요예측이 없으므로 F(t)=0)
```

| 조건 | 등급 | 핵심 사유 방향 |
|---|---|---|
| `availableQty == 0` | CRITICAL | 현재 판매 가능 재고가 0개 |
| `excludedRate >= 20%` | CRITICAL | 판매 불가 수량과 비율 명시 |
| `5% <= excludedRate < 20%` | WARNING | 판매 불가 수량과 비율 명시 |
| `0% < excludedRate < 5%` | NORMAL | 판매 불가 재고 모니터링 |
| `E30 / availableQty >= 20%` | CRITICAL | 30일 예상 폐기 수량·비율 명시 |
| `E30 / availableQty >= 5%` 또는 종료일까지 7일 이내 | WARNING | 수량·비율·남은 일수 명시 |
| 양수 재고를 가진 LOT의 종료 날짜가 없거나 LOT 수량으로 가용수량 전체를 설명하지 못함 | NORMAL | 확인 불가 LOT 건수·수량 명시 |
| `availableQty > 0`, `excludedQty == 0`, `E30 == 0`, 모든 양수 LOT의 종료 날짜 확인 가능, LOT 수량 합계가 가용수량과 일치 | GOOD | 판매 가능 수량과 30일 위험 0개 명시 |

D+31 이후 판매 종료 재고는 현재 30일 위험등급을 올리지 않습니다. 따라서 판매 가능 재고 188개가 모두 유효한 날짜의 AVAILABLE LOT로 설명되고 판매 불가 재고와 D+30 종료 LOT가 0개라면, 수요예측과 안전재고가 없어도 `GOOD`입니다.

## 7. 동기화와 화면 계약

- 규칙 버전 변경 후 첫 동기화에서는 기존 SKU × 판매처 및 미할당 범위를 모두 재판정합니다. `v1.8.0` 배포 중에는 구·신 버전 동기화를 동시에 실행하지 않습니다.
- 이후 원천 해시가 같아도 다음 범위는 수동·자동 동기화 때 재판정합니다.
  - 날짜 도달로 LOT 상태가 바뀌는 범위
  - 향후 30일 판매 종료 LOT가 있어 날짜 경과에 따라 E30/R30이 바뀌는 범위
  - 최신 유효 수요예측 행이 바뀐 범위
  - 같은 수요예측 행의 값이 마지막 판정 이후 수정된 범위
- 30일 판매 종료 후보의 날짜 경과 재판정은 서울 날짜마다 최초 한 번만 수행합니다. 같은 날 다시 동기화할 때는 원천·LOT 상태·수요예측이 실제로 바뀐 범위와 현재 규칙 버전 판정이 없는 범위만 다시 판정합니다. D+90까지의 판매 종료 후보를 일일 재평가합니다.
- 한 동기화 작업 안에서는 시작 시 확정한 동일한 `assessmentInstant`와 서울 기준일을 LOT 상태, E30/R30, 중기·장기 지표, 위험등급, 스냅샷에 모두 사용합니다.
- 통합재고 반영·LOT 상태 갱신·위험판정 저장은 하나의 트랜잭션으로 게시하며 중간 batch 실패 시 신규 run 전체를 rollback하고 이전 성공 스냅샷을 유지합니다.
- 서버 응답은 `expectedDisposalQty30`, `expectedDisposalRate30`, `nearestSaleEndDays`를 함께 제공합니다.
- 상세 조회 API의 등급·핵심 사유·판정시각은 최근 동기화가 저장한 SKU × 판매처 판정을 사용합니다. E30/R30, 안전재고 충족, 예상 보유 가능 일수 등 별도 저장하지 않는 표시용 파생값은 현재 통합재고·LOT·현재 기준일 이하 최신 유효 수요예측으로 계산하고 `현재 조회 기준`으로 표시합니다.
- 최근 동기화가 저장한 명시적 `UNASSESSED`는 미판정으로 표시합니다. 응답 필드가 아직 도착하지 않은 로딩 순간은 `확인 중`으로 표시하고 임의의 미판정 값을 만들지 않습니다.
- 서버는 동기화 시 날짜·수량·비율이 채워진 한국어 핵심 사유 한 건을 `reason_message`에 저장합니다. 목록·상세 API와 프론트엔드는 저장 문장을 파싱·재선정·재작성하지 않고 그대로 표시합니다.
- API의 `riskGrade`는 DB의 `risk_grade`와 동일한 `GOOD/NORMAL/WARNING/CRITICAL`을 반환합니다. 한국어 변환은 프론트엔드 표시 계층에서만 수행합니다.
- 계산값은 조회·동기화 시 파생하며 별도 DB 컬럼이나 Flyway 변경을 추가하지 않습니다.

## 8. 산정 근거

- [식품의약품안전처 소비기한 표시제](https://www.mfds.go.kr/brd/m_580/view.do?seq=81): 소비기한과 보관조건 확인의 필요성
- [식품·축산물·건강기능식품의 소비기한 설정실험 가이드라인](https://www.mfds.go.kr/brd/m_1060/view.do?Data_stts_gubun=C9999&company_cd=&company_nm=&itm_seq_1=0&itm_seq_2=0&multi_itm_seq=0&page=9&seq=15279&srchFr=&srchTo=&srchTp=0&srchWord=%EC%9D%98%EB%A3%8C%EA%B8%B0%EA%B8%B0): 제품·보관조건별 기한 설정 근거
- [GS1 2D Barcode Playbook](https://ref.gs1.org/sme-guidance/2d-retail-systems-playbook/): LOT·소비기한 추적과 FEFO 운영
- [WRI Food Loss & Waste Protocol](https://www.wri.org/initiatives/food-loss-waste-protocol): 예상·실제 폐기 범위와 측정 정의 분리
- [UNEP Food Waste Index Report 2024](https://www.unep.org/resources/publication/food-waste-index-report-2024): 소매 단계 폐기량 기준선과 추세 측정
- [GRI 306: Waste 2020](https://www.globalreporting.org/publications/documents/english/gri-306-waste-2020/): 발생 폐기와 회수·전환·최종처리의 분리
- [현대그린푸드 음식물쓰레기 감축 사례](https://hyundaigreenfood.com/po/pr/ntn/PRNTN02V.hg?bbsSqPk=186272): 사전수요와 잔량 분석을 통한 감축 활동
- [BGF Retail Sustainability Report](https://bgfretail.com/assets/file/bgf-retail/21-22_BGFRetail_SustainabilityReport_Eng.pdf): 판매량·날씨·입지 기반 발주와 임박상품 조치
- [Carrefour 2024 Universal Registration Document](https://www.carrefour.com/sites/default/files/2025-03/CFR_URD_2024_EN_250328_MEL.pdf): 제품별 폐기율 순위와 판매·생산 예측 기반 운영
- [Buisman et al. (2019)](https://research.wur.nl/en/publications/discounting-and-dynamic-shelf-life-to-reduce-fresh-food-waste-at-/): 유통기한·수요·할인·재보충을 결합한 신선식품 폐기 감소
- [Riesenegger & Hübner (2023)](https://doi.org/10.1016/j.samod.2023.100020): 서비스 수준·수요변동·유통기한이 소매 폐기에 미치는 영향
- [Shelf-life-aware forecast accuracy 연구](https://www.sciencedirect.com/science/article/pii/S0959652620356407): 일반 예측 오차와 폐기·품절 결과를 함께 평가할 필요성

외부 문서는 날짜·LOT·수요예측·폐기량을 함께 관리하는 방향을 뒷받침합니다. 다만 현재의 `D+30`, `D+7`, `5%`, `20%` 숫자를 공통 표준으로 직접 규정하지는 않으므로 내부 운영정책으로 명시하고 실데이터로 보정합니다.
