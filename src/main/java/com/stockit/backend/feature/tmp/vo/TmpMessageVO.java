package com.stockit.backend.feature.tmp.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TmpMessageVO {

    private Long id;
    private String messageValue;

    public TmpMessageVO() {
    }

    public TmpMessageVO(Long id, String messageValue) {
        this.id = id;
        this.messageValue = messageValue;
    }
}
