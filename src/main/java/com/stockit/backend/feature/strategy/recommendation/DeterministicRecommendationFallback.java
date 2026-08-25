package com.stockit.backend.feature.strategy.recommendation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;

/** LLM 응답 형식이 잘못됐을 때 후보 선별 순서를 이용해 안전한 대안을 만든다. */
@Component
public class DeterministicRecommendationFallback {

    AiRecommendationProviderResponse create(
            RecommendationCandidateSelection selection,
            AiRecommendationRequest request
    ) {
        List<StrategyCandidateEvaluationResult.EvaluatedCandidate> selected =
                new ArrayList<>();
        Set<RecommendationFamilyKey> families = new HashSet<>();
        for (StrategyCandidateEvaluationResult.EvaluatedCandidate candidate
                : selection.candidates()) {
            if (families.add(RecommendationFamilyKey.from(candidate.candidate()))) {
                selected.add(candidate);
            }
            if (selected.size() >= request.maximumRecommendationCount()) {
                break;
            }
        }
        if (selected.size() < request.minimumRecommendationCount()) {
            throw new IllegalStateException(
                    "deterministic fallback cannot satisfy recommendation count"
            );
        }

        List<AiRecommendationProviderResponse.Recommendation> recommendations =
                new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            StrategyCandidateEvaluationResult.EvaluatedCandidate candidate =
                    selected.get(index);
            int rank = index + 1;
            recommendations.add(new AiRecommendationProviderResponse.Recommendation(
                    candidate.candidate().candidateId(),
                    rank,
                    "재고 전략 대안 " + rank,
                    "서버가 검증한 후보 중 사용자 조건, 정량 지표와 대안 다양성을 "
                            + "기준으로 선정했습니다.",
                    "실행 가능성과 예상 재고·재무 결과가 서버 시뮬레이션으로 "
                            + "검증되었습니다.",
                    "AI 응답 검증 실패로 서버 기준 대안을 제공했으므로 담당자 검토가 "
                            + "필요합니다."
            ));
        }
        return new AiRecommendationProviderResponse(
                "deterministic-fallback",
                "server-rule-fallback",
                0,
                0,
                recommendations
        );
    }
}
