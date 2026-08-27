# 통합재고 SKU × 판매처 상태등급 및 30일 예상 폐기 정책

기준 규칙 버전: `v1.6.0`

## 1. 판정 범위와 기준 데이터

- 판정 단위는 `SKU × 판매처`입니다.
- 실제 보유 판매처(`stock_sales_point_id`)와 할당 판매처(`allocated_sales_point_id`) 중 하나라도 있으면 같은 판매처 재고로 봅니다. 둘 다 없으면 `UNASSIGNED` 물류센터 미할당 재고입니다.
- 수요예측은 판정일 이하의 `base_date` 중 가장 최신 행 하나만 사용합니다.
- 같은 예측 행의 D+7·D+14·D+30 값이 수정된 경우에도 `updated_at`을 비교해 재판정합니다.
- 활성 모델의 실제 예측만 사용하며 `DUMMY_BASELINE`은 제외합니다.
- 누적 예측값은 `0 <= F7 <= F14 <= F30`이어야 합니다.

## 2. LOT 상태와 판매 가능 재고

```text
effectiveEndDate(lot)
  = min(expiryDate, saleStopDate)
```

동기화 시 원천 테이블의 LOT 상태는 사용하지 않고 날짜와 통합 잔량으로 다시 판정합니다.

```text
effectiveEndDate <= 판정일
  -> 더 빠른 원인이 소비기한이면 EXPIRED
  -> 더 빠른 원인이 판매중지일이면 SALE_STOPPED

통합 잔량(on_hand_qty + reserved_qty) == 0
  -> DEPLETED

그 외
  -> AVAILABLE
```

판매 가능 재고는 소비기한 경과, 판매중지, 소진 LOT를 제외한 `on_hand_qty` 합계입니다. 판매 불가 LOT의 예약수량은 화면 집계에서 0으로 처리합니다. 경과 LOT는 이미 판매 불가 재고이므로 미래 예상 폐기수량에는 다시 넣지 않습니다.

## 3. 실제 판매 종료일 기준 30일 예상 폐기수량

판정일 다음 날부터 D+30까지 판매가 종료되는 판매 가능 LOT만 대상으로 합니다. D+31 이후 LOT는 이번 지표에서 제외합니다.

각 실제 판매 종료일 `t`에 대해 다음 값을 계산합니다.

```text
S(t) = t일까지 판매가 종료되는 LOT 수량의 누적합
F(t) = t일까지의 누적 예측수요

E30 = max over t(max(0, S(t) - F(t)))
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

미할당 재고 또는 유효한 수요예측이 없는 범위는 보수적으로 `F(t) = 0`으로 두어 30일 안에 종료되는 수량 전부를 예상 폐기로 봅니다.

```text
R30 = E30 / availableQty × 100
```

`R30`은 운영 우선순위용 예상 폐기율이며 실제 폐기율이나 ESG 공시 폐기율이 아닙니다.

## 4. SKU × 판매처 최종 위험등급

모든 규칙을 계산한 뒤 가장 높은 심각도를 최종 등급으로 사용합니다.

```text
CRITICAL -> DANGER
WARNING  -> CAUTION
NORMAL   -> NORMAL
GOOD     -> SAFE
```

### 4.1 판매 가능 여부와 부족 위험

| 조건 | 등급 |
|---|---|
| 현재 재고 데이터 없음 | DANGER |
| `availableQty == 0` | DANGER |
| `projectedD7 < safetyStockQty` | DANGER |
| `availableQty < safetyStockQty` | CAUTION |
| D+30 예측 부족이 있고 예상 재고일수 `< 14일` | CAUTION |
| D+30 예측 부족이 있으나 예상 재고일수 `>= 14일` | NORMAL |
| 예측과 안전재고 정책이 모두 없음 | NORMAL |

### 4.2 예상 폐기 위험

`E30 > 0`인 경우에만 폐기 위험을 올립니다.

| 조건 | 등급 |
|---|---|
| `R30 >= 20%` | DANGER |
| `nearestSaleEndDays <= 7` 이고 `R30 >= 5%` | DANGER |
| 위 DANGER가 아니고 `R30 >= 5%` | CAUTION |
| 위 DANGER가 아니고 `nearestSaleEndDays <= 7` | CAUTION |
| `R30 < 5%` 이고 `nearestSaleEndDays > 7` | NORMAL |
| `E30 == 0` | 폐기 사유로 등급을 올리지 않음 |

5%, 20%, 7일, 30일은 법정 또는 업계 공통 기준이 아니라 초기 운영 임계값입니다. 8~12주간 예측값과 실제 폐기·할인·기부·전환 결과를 비교한 뒤 상품군과 판매처별로 보정해야 합니다.

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

`R30 >= 20%`이므로 폐기 위험은 `DANGER`입니다.

### 예시 B: 폐기율 5% 이상 20% 미만

```text
availableQty = 100개
D+20 판매 종료 LOT 누적수량 S(20) = 30개
F(20) = 21.875개
E30 = 8.125개
R30 = 8.13%
```

판매 종료일까지 20일 남았고 `5% <= R30 < 20%`이므로 폐기 위험은 `CAUTION`입니다.

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

`R30 >= 20%`이므로 `DANGER`입니다.

### 예시 E: 일부 LOT가 이미 판매 불가

```text
전체 on_hand_qty = 100개
이미 소비기한이 지난 LOT = 40개
정상 LOT = 60개
availableQty = 100 - 40 = 60개
```

지난 LOT는 판매 가능 재고에서 제외하지만 미래 `E30`에는 중복 포함하지 않습니다. 정상 LOT와 수요가 충분하고 다른 부족 규칙도 발생하지 않으면 SKU × 판매처 전체는 `SAFE`가 될 수 있습니다.

## 6. 동기화와 화면 계약

- 규칙 버전 변경 후 첫 동기화에서는 기존 SKU × 판매처 범위를 모두 재판정합니다.
- 이후 원천 해시가 같아도 다음 범위는 수동·자동 동기화 때 재판정합니다.
  - 날짜 도달로 LOT 상태가 바뀌는 범위
  - 향후 30일 판매 종료 LOT가 있어 날짜 경과에 따라 E30/R30이 바뀌는 범위
  - 최신 유효 수요예측 행이 바뀐 범위
  - 같은 수요예측 행의 값이 마지막 판정 이후 수정된 범위
- 30일 판매 종료 후보의 날짜 경과 재판정은 서울 날짜마다 최초 한 번만 수행합니다. 같은 날 다시 동기화할 때는 원천·LOT 상태·수요예측이 실제로 바뀐 범위만 다시 판정합니다.
- 한 동기화 작업 안에서는 시작 시 확정한 동일한 서울 기준일을 LOT 상태, E30/R30, 위험등급, 스냅샷에 모두 사용합니다.
- 서버 응답은 `expectedDisposalQty30`, `expectedDisposalRate30`, `nearestSaleEndDays`를 함께 제공합니다.
- 상세 조회 API는 현재 통합재고·LOT·최신 수요예측으로 등급과 E30/R30을 한 번에 재계산해 서로 다른 판정시점의 값을 섞지 않습니다. 통합재고 목록용 저장 등급은 동기화 시 같은 규칙으로 갱신합니다.
- 프론트엔드는 등급이나 폐기율을 다시 계산하지 않고 서버 결과를 표시합니다.
- 계산값은 조회·동기화 시 파생하며 별도 DB 컬럼이나 Flyway 변경을 추가하지 않습니다.

## 7. 산정 근거

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
