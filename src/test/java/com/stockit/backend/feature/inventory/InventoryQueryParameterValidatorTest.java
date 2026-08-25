package com.stockit.backend.feature.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.inventory.dto.request.InventoryQueryParameterValidator;

import jakarta.servlet.http.HttpServletRequest;

class InventoryQueryParameterValidatorTest {

    @Test
    void acceptsTheSafetyStockShortageFilterParameter() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameterMap()).thenReturn(Map.of("shortageYn", new String[]{"Y"}));

        assertDoesNotThrow(() -> InventoryQueryParameterValidator.validate(request));
    }
}
