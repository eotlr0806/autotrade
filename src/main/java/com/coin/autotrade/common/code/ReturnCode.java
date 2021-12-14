package com.coin.autotrade.common.code;

public enum ReturnCode {
    SUCCESS(2000,"SUCCESS"),
    FAIL(4000, "FAIL");

    private Integer code;
    private String msg;
    ReturnCode(Integer code, String msg){
        this.code = code;
        this.msg  = msg;
    }

    public int getCode() {
        return code;
    }
    public String getMsg() {
        return msg;
    }
}
