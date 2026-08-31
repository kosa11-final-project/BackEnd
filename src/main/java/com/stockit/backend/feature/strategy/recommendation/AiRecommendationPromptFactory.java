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
                    netEffect는 공헌이익 증감 + 회피 폐기 처리비 + 회피 보관비
                    - 전체 액션 비용으로 서버가 계산한 값입니다.
                    예상 판매량, 공헌이익, 잔여·폐기 재고, 회피 비용,
                    실행 비용, netEffect와 가정을 함께 비교하세요.
                    같은 strategyFamilyId의 후보는 실행 구조가 같고 수량, 할인율 또는
                    기간만 다른 변형입니다. 같은 strategyFamilyId에서는 하나만 선택하세요.
                    서로 다른 주 전략 유형의 후보가 있으면 각 유형에서 먼저 하나씩 선택해
                    추천 결과의 전략 유형을 최대한 다양하게 구성하세요.
                    서로 다른 전략군에서 가장 적합한 후보를 minimumRecommendationCount 이상
                    maximumRecommendationCount 이하로 선택하고 순위와 설명을 작성하세요.
                    수치가 불리해도 명시된 가정 때문에 불확실한 후보는 단정적으로 배제하지 마세요.
                    추천 사유·장점·주의사항은 입력 수치에서 확인 가능한 사실만 한국어로 작성하세요.
                    옵션명은 100자 이하, 추천 사유·장점·주의사항은 각각 300자 이하의
                    한국어 1~2문장으로 간결하게 작성하세요.
                    옵션명에는 수량, 날짜, 기간 범위를 넣지 마세요. 수량과 기간은 후보의
                    별도 필드로 화면에 표시됩니다.
                    동일한 수치와 설명을 여러 필드에서 반복하지 마세요.
                    선택한 후보를 rank 1부터 중복 없이 빠짐없이 반환하세요.

                    입력 JSON:
                    """ + objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new InvalidAiRecommendationException(
                    "AI recommendation input serialization failed", exception
            );
        }
    }
}
