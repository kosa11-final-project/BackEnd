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
                    prioritySource가 USER인 우선순위만 사용자 선호로 고려하세요.
                    AI_DEFAULT는 후보 생성을 위한 내부 순서이며 사용자 선호가 아닙니다.
                    AI_DEFAULT이거나 priority 값이 null이면 설명에 우선순위와 내부 필드명을
                    절대 언급하지 마세요.
                    사용자 우선순위가 없는 경우 기준 대비 경제효과가 음수인 후보보다
                    0 이상인 후보를 우선하고, 모두 음수라면 손실이 가장 작은 후보를 우선하세요.
                    예상 판매량, 공헌이익, 잔여·폐기 재고, 실행 비용, 가정을 함께 비교하세요.
                    입력 후보는 서버가 실행 구조별 대표안으로 선별했습니다.
                    입력 후보를 모두 반환하되 순위와 설명을 작성하세요.
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
