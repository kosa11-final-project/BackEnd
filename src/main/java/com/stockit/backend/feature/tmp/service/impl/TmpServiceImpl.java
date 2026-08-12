package com.stockit.backend.feature.tmp.service.impl;

import org.springframework.stereotype.Service;

import com.stockit.backend.feature.tmp.mapper.TmpMapper;
import com.stockit.backend.feature.tmp.service.TmpService;
import com.stockit.backend.feature.tmp.vo.TmpMessageVO;

@Service
public class TmpServiceImpl implements TmpService {

    private final TmpMapper tmpMapper;

    public TmpServiceImpl(TmpMapper tmpMapper) {
        this.tmpMapper = tmpMapper;
    }

    @Override
    public TmpMessageVO getWelcomeMessage() {
        return tmpMapper.selectWelcomeMessage();
    }

    @Override
    public TmpMessageVO echo(String message) {
        return new TmpMessageVO(null, message);
    }
}
