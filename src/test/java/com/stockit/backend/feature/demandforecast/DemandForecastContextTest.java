package com.stockit.backend.feature.demandforecast;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.stockit.backend.feature.demandforecast.controller.DemandForecastController;
import com.stockit.backend.feature.demandforecast.mapper.DemandForecastMapper;
import com.stockit.backend.feature.demandforecast.service.DemandForecastService;
import com.stockit.backend.feature.demandforecast.service.impl.DemandForecastServiceImpl;
import com.stockit.backend.feature.demandforecast.vo.DemandForecastVO;

import io.swagger.v3.oas.annotations.tags.Tag;

@SpringBootTest
@ActiveProfiles("test")
class DemandForecastContextTest {

    private static final String RESULT_MAP_ID =
            "com.stockit.backend.feature.demandforecast.mapper.DemandForecastMapper"
                    + ".demandForecastResultMap";

    @Autowired
    private DemandForecastController demandForecastController;

    @Autowired
    private DemandForecastService demandForecastService;

    @Autowired
    private DemandForecastMapper demandForecastMapper;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void loadsDemandForecastSpringBeans() {
        assertThat(demandForecastController).isNotNull();
        assertThat(demandForecastService).isInstanceOf(DemandForecastServiceImpl.class);
        assertThat(demandForecastMapper).isNotNull();
    }

    @Test
    void describesDemandForecastControllerForOpenApi() {
        Tag tag = DemandForecastController.class.getAnnotation(Tag.class);

        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("수요 예측");
        assertThat(tag.description()).isEqualTo("SKU당 판매처별 수요 예측 API");
    }

    @Test
    void loadsDemandForecastMapperAndXmlResultMap() {
        org.apache.ibatis.session.Configuration configuration =
                sqlSessionFactory.getConfiguration();

        assertThat(configuration.hasMapper(DemandForecastMapper.class)).isTrue();
        assertThat(configuration.getResultMap(RESULT_MAP_ID).getType())
                .isEqualTo(DemandForecastVO.class);
        assertThat(configuration.getTypeAliasRegistry().resolveAlias("DemandForecastVO"))
                .isEqualTo(DemandForecastVO.class);
    }
}
