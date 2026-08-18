package com.stockit.backend.feature.demandforecast.controller;

import static com.stockit.backend.feature.auth.security.InternalApiSecurityConstants.INTERNAL_API_KEY_HEADER;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiErrorResponse;
import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.auth.security.AuthPrincipal;
import com.stockit.backend.feature.demandforecast.dto.request.DemandForecastImportRequest;
import com.stockit.backend.feature.demandforecast.dto.response.DemandForecastImportResponse;
import com.stockit.backend.feature.demandforecast.dto.response.DemandForecastResponse;
import com.stockit.backend.feature.demandforecast.service.DemandForecastService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "수요 예측", description = "SKU당 판매처별 수요 예측 API")
@SecurityScheme(
        name = "internalApiKey",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = INTERNAL_API_KEY_HEADER,
        description = "FastAPI 서버 간 통신용 API Key"
)
@RestController
@Validated
public class DemandForecastController {

    private static final String IMPORT_REQUEST_EXAMPLE = """
            {
              "azureJobId": "purple_monkey_gyk4m5yyxr",
              "modelName": "stockit-demand-lightgbm",
              "modelVersion": "1",
              "forecastBaseDate": "2026-07-31",
              "batchNumber": 1,
              "totalBatches": 10,
              "forecasts": [
                {
                  "skuId": 101,
                  "salesPointId": 10,
                  "predictedQtyD7": 12.3,
                  "predictedQtyD14": 24.8,
                  "predictedQtyD30": 51.2,
                  "predictedQtyD60": 103.7,
                  "predictedQtyD90": 157.1,
                  "forecastSource": "LIGHTGBM",
                  "confidenceLevel": "HIGH"
                }
              ]
            }
            """;

    private static final String IMPORT_RESPONSE_EXAMPLE = """
            {
              "data": {
                "azureJobId": "purple_monkey_gyk4m5yyxr",
                "modelName": "stockit-demand-lightgbm",
                "modelVersion": "1",
                "modelVersionId": 7,
                "forecastBaseDate": "2026-07-31",
                "batchNumber": 1,
                "totalBatches": 10,
                "importedCount": 1
              },
              "timestamp": "2026-08-15T13:00:00Z"
            }
            """;

    private final DemandForecastService demandForecastService;

    public DemandForecastController(DemandForecastService demandForecastService) {
        this.demandForecastService = demandForecastService;
    }

    @Operation(
            summary = "수요예측 결과 일괄 적재",
            description = """
                    FastAPI가 Azure ML의 demand_forecast.csv를 파싱한 뒤 전달한 예측 결과를 적재합니다.
                    요청 하나는 최대 1,000건이며, 모델·SKU·판매처와 예측값을 검증한 후 배치 전체를
                    단일 트랜잭션으로 DEMAND_FORECAST에 MERGE합니다.
                    """,
            security = @SecurityRequirement(name = "internalApiKey")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "배치 적재 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = IMPORT_RESPONSE_EXAMPLE)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 형식, 필드 또는 예측값 검증 실패 (COMMON-001, COMMON-002)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "X-API-Key 누락 또는 불일치 (AUTH-001)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "모델 버전, SKU 또는 판매처를 찾을 수 없음 (DEMAND_FORECAST-002~004)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "요청 내 SKU·판매처 조합 중복 (DEMAND_FORECAST-005)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류 (COMMON-006)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping("/api/v1/demand-forecasts/import")
    public ApiResponse<DemandForecastImportResponse> importForecasts(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "최대 1,000건의 수요예측 결과 배치",
                    content = @Content(
                            schema = @Schema(implementation = DemandForecastImportRequest.class),
                            examples = @ExampleObject(
                                    name = "LightGBM 예측 결과",
                                    summary = "FastAPI가 전달하는 유효한 요청 예시",
                                    value = IMPORT_REQUEST_EXAMPLE
                            )
                    )
            )
            @Valid @RequestBody DemandForecastImportRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ApiResponse.of(demandForecastService.importForecasts(request, principal.getUserId()));
    }

    @GetMapping("/api/v1/inventories/{skuCode}/sales-points/{salesPointCode}/forecast")
    @Operation(
            summary = "SKU×판매처 수요예측 조회",
            description = "선택한 SKU와 판매처의 D+7~D+90 누적 수요예측과 현재 가용재고·예상 잔고를 조회합니다. 적용 가능한 안전재고 정책이 있을 때만 기준선이 함께 반환됩니다."
    )
    public ApiResponse<DemandForecastResponse> getForecast(
            @Parameter(description = "상품 SKU 업무 코드", example = "SKU-4D82A9F1")
            @PathVariable String skuCode,
            @Parameter(description = "판매처 업무 코드", example = "GREETING")
            @PathVariable String salesPointCode
    ) {
        return ApiResponse.of(demandForecastService.getForecast(skuCode, salesPointCode));
    }

    @GetMapping("/api/v1/inventories/{skuCode}/forecast")
    @Operation(
            summary = "SKU 전체 합계 수요예측 조회",
            description = "선택한 SKU의 모든 판매처에서 동일한 기준일·모델·원천을 사용하는 예측만 합산하고, 현재 가용재고·예상 잔고를 조회합니다. 적용 가능한 안전재고 정책이 있을 때만 기준선이 함께 반환됩니다."
    )
    public ApiResponse<DemandForecastResponse> getSkuAggregateForecast(
            @Parameter(description = "상품 SKU 업무 코드", example = "SKU-4D82A9F1")
            @PathVariable String skuCode,
            @Parameter(description = "조회 범위 (SKU_AGGREGATE)", example = "SKU_AGGREGATE")
            @RequestParam(name = "scope", defaultValue = "SKU_AGGREGATE") String scope
    ) {
        if (!"SKU_AGGREGATE".equalsIgnoreCase(scope)) {
            throw new AppException(ErrorCode.INVALID_PARAMETER, "scope은 SKU_AGGREGATE만 지원합니다.");
        }
        return ApiResponse.of(demandForecastService.getSkuAggregateForecast(skuCode));
    }

}
