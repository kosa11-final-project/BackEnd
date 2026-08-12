package com.stockit.backend.feature.tmp.dto.response;

import com.stockit.backend.feature.tmp.vo.TmpMessageVO;

public record TmpResponse(String message) {

    public static TmpResponse from(TmpMessageVO tmpMessageVO) {
        return new TmpResponse(tmpMessageVO.getMessageValue());
    }
}
