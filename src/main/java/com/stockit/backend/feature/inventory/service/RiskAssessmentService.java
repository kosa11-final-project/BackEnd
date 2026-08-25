package com.stockit.backend.feature.inventory.service;

import com.stockit.backend.feature.inventory.dto.response.RiskAssessmentDetailResponse;

public interface RiskAssessmentService {

    RiskAssessmentDetailResponse getRiskAssessment(String skuCode, String salesPointCode);

}
