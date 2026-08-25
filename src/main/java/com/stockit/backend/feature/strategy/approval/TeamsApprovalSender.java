package com.stockit.backend.feature.strategy.approval;

public interface TeamsApprovalSender {
    void send(TeamsApprovalMessage message);
}
