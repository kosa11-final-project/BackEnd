# SALES_DAILY CSV Azure Blob Export 운영 가이드

## 처리 흐름

`salesDailyCsvExportJob`은 Oracle의 `SALES_DAILY`를 로컬 임시 CSV로 스트리밍한 뒤,
설정된 destination에 따라 Azure Blob 업로드를 수행합니다.

```text
Oracle SALES_DAILY
  -> 로컬 임시 CSV 생성
  -> LOCAL no-op 또는 Azure Blob 업로드
  -> 성공 시 로컬 최종 CSV 원자적 교체
```

- 조합 집합: `baseDate`까지 유효한 `sku_id + sales_point_id` 전체 조합
- `BOOTSTRAP`: 최초 유효 판매일부터 `baseDate`까지 날짜와 전체 조합의 완전 패널
- `DAILY`: `baseDate` 하루와 전체 조합의 완전 패널
- 실제 판매 행이 없거나 삭제된 행만 있으면 `net_sales_qty=0`
- Export 이후 `날짜 수 × 조합 수`와 실제 CSV 행 수를 검증하고 불일치하면 업로드 전에 실패
- `exportMode` 생략 시 안전한 기본값은 `DAILY`
- CSV 컬럼: `sales_date,sku_id,sales_point_id,net_sales_qty`
- 인코딩: UTF-8
- Blob content type: `text/csv; charset=UTF-8`
- 기본 destination: `LOCAL`

`AZURE_BLOB`에서도 `SALES_DAILY_EXPORT_PATH`는 Blob URL이 아니라 로컬 staging 및
최종 파일 경로입니다. `https://...blob.core.windows.net/...` URL을 이 값에 넣지 않습니다.

## Azure 리소스 준비

1. StorageV2 Storage Account를 생성하고 Blob public access를 비활성화합니다.
2. `demand-forecast-input`과 같은 비공개 container를 생성합니다.
3. Backend를 호스팅하는 Azure 리소스에서 Managed Identity를 활성화합니다.
4. 해당 Identity에 container 범위 또는 Storage Account 범위의
   `Storage Blob Data Contributor` 역할을 할당합니다.
5. Storage 방화벽을 사용하는 경우 Backend 서브넷을 허용합니다. Private Endpoint를
   사용하면 Backend의 VNet 통합, Private DNS Zone 연결 및 DNS 해석도 함께 확인합니다.

역할 할당은 Azure에 전파되는 데 시간이 걸릴 수 있습니다. 설정 직후 403이 발생하면
Identity의 object ID, 역할 범위, 전파 상태를 차례로 확인합니다.

## 환경변수

```env
SALES_DAILY_EXPORT_DESTINATION=AZURE_BLOB
SALES_DAILY_EXPORT_PATH=/tmp/stockit/sales_daily.csv
AZURE_STORAGE_ACCOUNT_URL=https://<storage-account>.blob.core.windows.net
AZURE_STORAGE_CONTAINER=demand-forecast-input
AZURE_STORAGE_BLOB_PREFIX=sales-daily
```

- `SALES_DAILY_EXPORT_DESTINATION`: `LOCAL` 또는 `AZURE_BLOB`. 생략 시 `LOCAL`입니다.
- `SALES_DAILY_EXPORT_PATH`: 항상 Backend가 쓸 수 있는 로컬 경로입니다. 기존 환경변수와
  호환됩니다.
- `AZURE_STORAGE_ACCOUNT_URL`, `AZURE_STORAGE_CONTAINER`: `AZURE_BLOB` Job 실행 시
  필수입니다. 누락되어도 애플리케이션 기동은 가능하지만 Upload Step이 명확한 오류로
  실패합니다.
- `AZURE_STORAGE_BLOB_PREFIX`: 기본값은 `sales-daily`입니다.

Connection String과 Storage Key는 사용하지 않습니다. 운영에서는 Managed Identity를
통해 `DefaultAzureCredential`이 토큰을 취득합니다.

### 로컬 개발 인증

로컬에서는 `az login`으로 로그인한 개발자 계정을 `DefaultAzureCredential`이 사용할 수
있습니다. 해당 개발자 계정에도 대상 container에 대한 데이터 역할이 필요합니다.

Security Defaults가 Device Code Flow를 차단하는 환경에서는 `az login --use-device-code`가
아닌 브라우저 기반 `az login`을 사용합니다. 로컬에서 Azure CLI 인증만 선택하려면 다음
환경변수를 Run Configuration에 설정합니다.

```env
AZURE_TOKEN_CREDENTIALS=AzureCliCredential
```

Service Principal로 검증해야 할 때만 다음 표준 환경변수를 로컬 보안 저장소에 설정합니다.
값을 Git, `application.yml`, 문서에 기록하지 않습니다.

```env
AZURE_CLIENT_ID=<service-principal-client-id>
AZURE_TENANT_ID=<tenant-id>
AZURE_CLIENT_SECRET=<secret>
```

운영에서는 위 secret 방식 대신 Managed Identity를 사용합니다. User-assigned Managed
Identity를 쓰는 경우에는 선택할 Identity의 `AZURE_CLIENT_ID`가 추가로 필요할 수 있습니다.

## Job 실행 예시

애플리케이션 기본 설정은 자동 Job 실행을 비활성화합니다. 운영 스케줄러 또는 내부 실행
컴포넌트가 `salesDailyCsvExportJob`을 선택하고 `baseDate`와 `exportMode`를 Job Parameter로
전달해야 합니다.

최초 1회, 2026-08-17까지의 전체 판매 이력을 적재하는 예시는 다음과 같습니다.

```bash
SPRING_BATCH_JOB_ENABLED=true \
SPRING_BATCH_JOB_NAME=salesDailyCsvExportJob \
./gradlew bootRun \
  --args='baseDate=2026-08-17 exportMode=BOOTSTRAP run.id=2026081701'
```

이후 2026-08-18 하루치만 적재하는 예시는 다음과 같습니다.

```bash
SPRING_BATCH_JOB_ENABLED=true \
SPRING_BATCH_JOB_NAME=salesDailyCsvExportJob \
./gradlew bootRun \
  --args='baseDate=2026-08-18 exportMode=DAILY run.id=2026081801'
```

`BOOTSTRAP`은 초기 기준일까지의 전체 이력을 한 번 만들 때만 사용합니다. 정기 실행은
`DAILY`를 사용하며, 새벽 실행이라면 집계가 끝난 전날을 `baseDate`로 전달합니다. 재실행할
때는 새로운 `run.id`를 전달해 새 JobInstance/JobExecution을 만듭니다. 운영 Oracle에 Spring
Batch 메타데이터 테이블이 준비되어 있어야 합니다.

## Blob 이름과 실행 결과

초기 전체 이력은 다음 경로에 생성합니다.

```text
sales-daily-bootstrap/base-date=2026-08-17/sales_daily.csv
```

이후 일별 증분은 날짜별 고정 경로에 생성합니다.

```text
sales-daily/sales-date=2026-08-18/sales_daily.csv
sales-daily/sales-date=2026-08-19/sales_daily.csv
```

- 경로에서 `job-execution-id`를 제거해 동일 판매일의 Blob이 여러 개 생기지 않게 합니다.
- 동일 날짜를 재실행하면 같은 Blob을 최신 CSV로 덮어씁니다.
- ML은 bootstrap 파일과 bootstrap 기준일 다음 날부터의 daily 파일들을 합쳐 읽습니다.
- bootstrap 기준일과 daily 시작일이 겹치면 같은 판매 이력이 중복되므로 허용하지 않습니다.
- 성공한 Upload Step은 JobExecutionContext에 아래 값을 기록합니다.

```text
salesDailyCsvBlobName
salesDailyCsvBlobUrl
```

후속 FastAPI/Azure ML Job 제출에는 prefix를 다시 추측하지 말고 ExecutionContext의 정확한
`salesDailyCsvBlobUrl`을 전달합니다.

## 업로드 확인

Portal에서는 Storage Account의 **Data storage > Containers**에서 container와 실행별 경로를
확인합니다. Azure CLI에서는 로그인 기반 인증으로 다음처럼 확인할 수 있습니다.

```bash
az storage blob show \
  --account-name <storage-account> \
  --container-name demand-forecast-input \
  --name 'sales-daily/sales-date=2026-08-18/sales_daily.csv' \
  --auth-mode login
```

## 장애, 재실행 및 보존 정책

- Export 또는 Upload Step 실패 시 Job은 `FAILED`가 됩니다.
- 실패 시 불완전한 로컬 임시 파일은 삭제합니다.
- 업로드 실패 전에 존재하던 정상 로컬 최종 파일은 교체하지 않습니다.
- 날짜별 고정 Blob 이름을 사용하므로 동일 날짜 재실행은 새 중복 파일을 만들지 않습니다.
- Azure가 업로드를 수신한 직후 응답 단절이 발생하면 Job은 실패했지만 해당 날짜 Blob은
  이미 갱신되었을 수 있습니다. 원인을 해결한 뒤 새로운 `run.id`로 재실행하면 같은 경로를
  최신 내용으로 다시 덮어씁니다.
- 현재 구현은 성공 Blob을 자동 삭제하지 않습니다. Storage Lifecycle Management 정책을
  별도로 정의합니다.

## 공식 참고 문서

- [Java로 Blob 업로드](https://learn.microsoft.com/en-us/azure/storage/blobs/storage-blob-upload-java)
- [Azure Storage 데이터 액세스 권한 부여](https://learn.microsoft.com/en-us/azure/storage/common/authorize-data-access)
- [Java Blob Storage 빠른 시작과 DefaultAzureCredential](https://learn.microsoft.com/en-us/azure/storage/blobs/storage-quickstart-blobs-java)
