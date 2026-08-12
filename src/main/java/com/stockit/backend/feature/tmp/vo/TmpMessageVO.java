package com.stockit.backend.feature.tmp.vo;

public class TmpMessageVO {

    private Long id;
    private String messageValue;

    public TmpMessageVO() {
    }

    public TmpMessageVO(Long id, String messageValue) {
        this.id = id;
        this.messageValue = messageValue;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessageValue() {
        return messageValue;
    }

    public void setMessageValue(String messageValue) {
        this.messageValue = messageValue;
    }
}
