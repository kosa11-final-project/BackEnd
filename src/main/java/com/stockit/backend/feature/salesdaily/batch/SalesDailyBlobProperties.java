package com.stockit.backend.feature.salesdaily.batch;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sales-daily-export.azure")
public record SalesDailyBlobProperties(
        String accountUrl,
        String containerName,
        String blobPrefix
) {

    public void validateForUpload() {
        requireText(accountUrl, "AZURE_STORAGE_ACCOUNT_URL");
        requireText(containerName, "AZURE_STORAGE_CONTAINER");
        requireText(blobPrefix, "AZURE_STORAGE_BLOB_PREFIX");
        if (normalizePrefix(blobPrefix).isBlank()) {
            throw new IllegalStateException(
                    "AZURE_STORAGE_BLOB_PREFIX는 슬래시 외 문자를 포함해야 합니다."
            );
        }

        try {
            URI uri = new URI(accountUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalStateException(
                        "AZURE_STORAGE_ACCOUNT_URL은 유효한 HTTPS URL이어야 합니다."
                );
            }
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(
                    "AZURE_STORAGE_ACCOUNT_URL은 유효한 HTTPS URL이어야 합니다.",
                    exception
            );
        }
    }

    public String normalizedBlobPrefix() {
        validateForUpload();
        return normalizePrefix(blobPrefix);
    }

    private static String normalizePrefix(String value) {
        return value.replaceAll("^/+|/+$", "");
    }

    private static void requireText(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    environmentVariable + " 환경변수가 설정되지 않았습니다."
            );
        }
    }
}
