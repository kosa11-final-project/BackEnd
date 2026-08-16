package com.stockit.backend.feature.dashboard.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.dashboard.vo.DashboardSnapshotVO;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql({
        "classpath:dashboard/dashboard_snapshot_test_prerequisites.sql",
        "classpath:db/migration/V13__create_dashboard_snapshot.sql"
})
class DashboardSnapshotMapperTest {

    @DynamicPropertySource
    static void useIsolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:dashboard-snapshot;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        );
    }

    @Autowired
    private DashboardSnapshotMapper snapshotMapper;

    @Test
    void storesAndReadsLatestJsonSnapshot() {
        String payloadJson = "{\"summary\":{\"totalAvailableStock\":4062}}";
        Long snapshotId = snapshotMapper.selectNextSnapshotId();

        snapshotMapper.insertSnapshot(
                snapshotId,
                101L,
                1,
                payloadJson
        );

        DashboardSnapshotVO latest = snapshotMapper.selectLatestSnapshot();

        assertThat(latest.getDashboardSnapshotId()).isEqualTo(snapshotId);
        assertThat(latest.getPayloadVersion()).isEqualTo(1);
        assertThat(latest.getPayloadJson()).isEqualTo(payloadJson);
        assertThat(latest.getCreatedAt()).isNotNull();
        assertThat(latest.getUpdatedAt()).isNotNull();
        assertThat(latest.getCreatedBy()).isEqualTo(1L);
        assertThat(latest.getUpdatedBy()).isEqualTo(1L);
        assertThat(latest.getIsDeleted()).isFalse();
        assertThat(snapshotMapper.selectSnapshotIdBySyncJobId(101L)).isEqualTo(snapshotId);
    }
}
