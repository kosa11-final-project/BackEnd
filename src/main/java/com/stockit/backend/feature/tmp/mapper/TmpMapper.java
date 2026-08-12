package com.stockit.backend.feature.tmp.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.stockit.backend.feature.tmp.vo.TmpMessageVO;

@Mapper
public interface TmpMapper {

    TmpMessageVO selectWelcomeMessage();
}
