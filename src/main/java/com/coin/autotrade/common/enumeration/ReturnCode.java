package com.coin.autotrade.common.enumeration;

public enum ReturnCode {
    SUCCESS(2000,"SUCCESS","Success"),

    FAIL(4000, "FAIL", "Fail"),
    NO_DATA(4001, "NO_DATA","There is no data"),
    NO_SUFFICIENT_PARAMS(4002, "NO_SUFFICIENT_PARAMS", "parameter values are not enough"),
    DUPLICATION_DATA(4003,"DUPLICATION_DATA","There is existed duplication id"),
    FAIL_CREATE(4004,"FAIL_CREATE","Create request is failed");

    private int code;
    private String value;
    private String msg;

    ReturnCode(int code, String value, String msg){
        this.code    = code;
        this.value   = value;
        this.msg     = msg;
    }

    public int getCode() {
        return code;
    }
    public String getMsg() {
        return msg;
    }
    public String getValue(){ return value; }
}
