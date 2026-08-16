package com.stockit.backend.feature.demandforecast.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.demandforecast.vo.DemandForecastVO;

@Mapper
public interface DemandForecastMapper {

    Long selectModelVersionId(
            @Param("modelName") String modelName,
            @Param("modelVersion") String modelVersion
    );

    int countExistingSkus(@Param("skuIds") List<Long> skuIds);

    int countExistingSalesPoints(@Param("salesPointIds") List<Long> salesPointIds);

    int mergeDemandForecasts(@Param("forecasts") List<DemandForecastVO> forecasts);
}
