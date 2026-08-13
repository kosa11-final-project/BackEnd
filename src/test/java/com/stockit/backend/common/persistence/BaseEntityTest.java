package com.stockit.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.junit.jupiter.api.Test;

class BaseEntityTest {

    @Test
    void exposesCommonAuditFieldsToSubclasses() {
        TestEntity entity = new TestEntity();
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 13, 10, 0);
        LocalDateTime updatedAt = createdAt.plusHours(1);

        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(2L);
        entity.setIsDeleted(true);

        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(entity.getCreatedBy()).isEqualTo(1L);
        assertThat(entity.getUpdatedBy()).isEqualTo(2L);
        assertThat(entity.getIsDeleted()).isTrue();
    }

    @Test
    void exposesInheritedSettersToMyBatis() {
        MetaClass metaClass = MetaClass.forClass(TestEntity.class, new DefaultReflectorFactory());

        assertThat(metaClass.hasSetter("createdAt")).isTrue();
        assertThat(metaClass.hasSetter("updatedAt")).isTrue();
        assertThat(metaClass.hasSetter("createdBy")).isTrue();
        assertThat(metaClass.hasSetter("updatedBy")).isTrue();
        assertThat(metaClass.hasSetter("isDeleted")).isTrue();
        assertThat(metaClass.findProperty("is_deleted", true)).isEqualTo("isDeleted");
    }

    @Test
    void defaultsDeletedToFalse() {
        assertThat(new TestEntity().getIsDeleted()).isFalse();
    }

    private static final class TestEntity extends BaseEntity {
    }
}
