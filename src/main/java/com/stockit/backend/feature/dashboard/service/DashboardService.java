package com.stockit.backend.feature.dashboard.service;

import java.time.LocalDate;

import com.stockit.backend.feature.dashboard.dto.response.DashboardResponse;

public interface DashboardService {

    DashboardResponse getDashboard();

    DashboardResponse getLiveDashboard();

    DashboardResponse getLiveDashboard(LocalDate asOfDate);
}
