package com.stockit.backend.feature.strategy.recommendation;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 정량 값을 변경하지 않고 후보를 비교·설명하도록 하는 버전 고정 프롬프트. */
@Component
public class AiRecommendationPromptFactory {

    private final ObjectMapper objectMapper;

    public AiRecommendationPromptFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String create(AiRecommendationRequest request) {
        try {
            return """
                    당신은 통합 재고 관리 전략 검토자입니다.
                    입력 후보는 서버가 실행 가능성을 검증하고 시뮬레이션한 결과입니다.
                    후보를 새로 만들거나 수량, 금액, 비율, 기간을 변경하지 마세요.
                    candidateId는 입력에 있는 값만 사용하세요.
                    사용자 strategyPriority와 targetPriority를 우선 고려하되,
                    예상 판매량, 공헌이익, 잔여·폐기 재고, 실행 비용, 가정을 함께 비교하세요.
                    동일한 유형만 반복하기보다 합리적인 대안 다양성을 확보하세요.
                    수치가 불리해도 명시된 가정 때문에 불확실한 후보는 단정적으로 배제하지 마세요.
                    추천 사유·장점·주의사항은 입력 수치에서 확인 가능한 사실만 한국어로 작성하세요.
                    아래 JSON의 minimumRecommendationCount 이상 maximumRecommendationCount 이하를
                    rank 1부터 빠짐없이 반환하세요.

                    입력 JSON:
                    """ + objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new InvalidAiRecommendationException(
                    "AI recommendation input serialization failed", exception
            );
        }
    }
}
