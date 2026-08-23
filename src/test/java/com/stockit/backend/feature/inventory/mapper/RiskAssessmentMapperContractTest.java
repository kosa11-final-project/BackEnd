package com.stockit.backend.feature.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class RiskAssessmentMapperContractTest {

    @Test
    void readsThePersistedRiskAssessmentFieldsForDetailResponses() throws IOException {
        String mapperXml;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("mappers/inventory/RiskAssessmentMapper.xml")) {
            mapperXml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(mapperXml)
                .contains("<select id=\"selectLatestPersistedAssessment\"")
                .contains("ra.reason_message")
                .contains("ra.stock_days")
                .contains("ra.holding_days")
                .contains("ra.expiry_days_left")
                .contains("ra.rule_version");
    }

    @Test
    void readsLotStatusSoDetailRiskEvaluationCanApplyLotStateRules() throws IOException {
        String mapperXml;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("mappers/inventory/RiskAssessmentMapper.xml")) {
            mapperXml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(mapperXml)
                .contains("<select id=\"selectLotRiskItems\"")
                .contains("l.lot_status AS lotStatus")
                .contains("l.received_date, l.lot_status");
    }
}
