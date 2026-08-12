package com.stockit.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.stockit.backend.feature.tmp.vo.TmpMessageVO;

@SpringBootTest
@ActiveProfiles("test")
class MyBatisConfigurationTest {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void appliesCommonMyBatisSettings() {
        org.apache.ibatis.session.Configuration configuration = sqlSessionFactory.getConfiguration();

        assertThat(configuration.isMapUnderscoreToCamelCase()).isTrue();
        assertThat(configuration.getDefaultFetchSize()).isEqualTo(100);
        assertThat(configuration.getTypeAliasRegistry().resolveAlias("TmpMessageVO"))
                .isEqualTo(TmpMessageVO.class);
    }
}
