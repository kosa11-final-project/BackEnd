package com.stockit.backend.feature.strategy.alert;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** 운영 채널에 Secret과 과도한 오류 본문이 노출되지 않도록 실패 메시지를 정제한다. */
@Component
public class AiStrategyFailureMessageSanitizer {

    private static final int MAX_LENGTH = 1000;
    private static final String EMPTY_MESSAGE =
            "상세 오류 메시지가 기록되지 않았습니다.";
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*)([^,;]+)"
    );
    private static final Pattern SENSITIVE_QUERY = Pattern.compile(
            "(?i)([?&](?:key|token|sig|api[_-]?key|access[_-]?token)=)[^&\\s]+"
    );
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)((?:api[_-]?key|token|secret|password|cookie|session(?:id)?)"
                    + "\\s*[:=]\\s*)([^\\s,;]+)"
    );

    public String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return EMPTY_MESSAGE;
        }
        String normalized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        normalized = AUTHORIZATION.matcher(normalized).replaceAll("$1***");
        normalized = SENSITIVE_QUERY.matcher(normalized).replaceAll("$1***");
        normalized = SECRET_ASSIGNMENT.matcher(normalized).replaceAll("$1***");
        return normalized.length() <= MAX_LENGTH
                ? normalized
                : normalized.substring(0, MAX_LENGTH) + "...";
    }
}
