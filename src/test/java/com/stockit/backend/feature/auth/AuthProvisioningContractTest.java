package com.stockit.backend.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/auth-test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AuthProvisioningContractTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void executeRoleMigration() throws IOException {
        String migration = readClasspathResource("db/migration/V7__seed_greenfood_admin_role.sql");

        // H2는 system_user를 내장 함수로 해석하므로 테스트 실행 시 테이블 별칭만 치환
        String h2CompatibleMigration = migration.replace("system_user", "audit_user");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ByteArrayResource(h2CompatibleMigration.getBytes(StandardCharsets.UTF_8))
        );
        populator.execute(jdbcTemplate.getDataSource());
        populator.execute(jdbcTemplate.getDataSource());
    }

    @Test
    void seedsOnlyGreenfoodAdminRoleWithSystemAuditFields() throws IOException {
        String migration = readClasspathResource("db/migration/V7__seed_greenfood_admin_role.sql");

        SeededRole role = jdbcTemplate.queryForObject(
                """
                SELECT role_code,
                       role_name,
                       created_at,
                       updated_at,
                       created_by,
                       updated_by,
                       is_deleted
                FROM app_role
                WHERE role_code = 'GREENFOOD_ADMIN'
                """,
                (resultSet, rowNumber) -> new SeededRole(
                        resultSet.getString("role_code"),
                        resultSet.getString("role_name"),
                        resultSet.getTimestamp("created_at"),
                        resultSet.getTimestamp("updated_at"),
                        resultSet.getLong("created_by"),
                        resultSet.getLong("updated_by"),
                        resultSet.getInt("is_deleted")
                )
        );

        assertThat(role).isNotNull();
        assertThat(role.roleCode()).isEqualTo("GREENFOOD_ADMIN");
        assertThat(role.roleName()).isEqualTo("그린푸드 총괄");
        assertThat(role.createdAt()).isNotNull();
        assertThat(role.updatedAt()).isNotNull();
        assertThat(role.createdBy()).isEqualTo(100L);
        assertThat(role.updatedBy()).isEqualTo(100L);
        assertThat(role.isDeleted()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_role WHERE role_code = 'GREENFOOD_ADMIN'",
                Integer.class
        )).isEqualTo(1);
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

    private record SeededRole(
            String roleCode,
            String roleName,
            Timestamp createdAt,
            Timestamp updatedAt,
            Long createdBy,
            Long updatedBy,
            int isDeleted
    ) {
    }
}
