package com.stockit.backend.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class AuthProvisioningContractTest {

    @Test
    void seedsOnlyGreenfoodAdminRoleWithSystemAuditFields() throws IOException {
        String migration = readClasspathResource("db/migration/V7__seed_greenfood_admin_role.sql");

        assertThat(migration).contains(
                "MERGE INTO app_role",
                "'GREENFOOD_ADMIN' AS role_code",
                "system_user.login_id = '__system__'",
                "created_by",
                "updated_by",
                "is_deleted"
        );
        assertThat(migration).doesNotContain("user_access_scope");
    }

    private static String readClasspathResource(String path) throws IOException {
        try (var input = AuthProvisioningContractTest.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing test resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
