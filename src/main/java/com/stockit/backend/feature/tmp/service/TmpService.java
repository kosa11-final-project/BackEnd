package com.stockit.backend.feature.tmp.service;

import com.stockit.backend.feature.tmp.vo.TmpMessageVO;

public interface TmpService {

    TmpMessageVO getWelcomeMessage();

    TmpMessageVO echo(String message);
}
