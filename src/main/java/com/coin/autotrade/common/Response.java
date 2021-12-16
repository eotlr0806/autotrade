package com.coin.autotrade.common;

public class Response {
    private int status;
    private String msg;
    private String data;

    public void setResponseWhenSuccess(int status, String data){
        this.status = status;
        this.data   = data;
    }

    public void setResponseWhenFail(int status, String msg){
        this.status = status;
        this.msg    = msg;
    }

    @Override
    public String toString() {
        return "{" +
                "\"status\": " + status +
                ", \"msg\" : \"" + msg + "\"" +
                ", \"data\": " + data +
                "}";
    }
}
