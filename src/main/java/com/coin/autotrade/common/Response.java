package com.coin.autotrade.common;

import com.coin.autotrade.common.enumeration.ReturnCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Getter
public class Response <T> {

    private ReturnCode status;
    private String msg        = null;
    private List<T> body      = null;
    JsonMapper mapper         = new JsonMapper();
    private T data            = null;

    public Response(ReturnCode status){
        this.status = status;
    }

    public void setBody(ReturnCode status, List<T> body){
        this.status = status;
        this.body   = body;
    }

    public void setBody(ReturnCode status, T data) {
        this.status = status;
        this.body   = new ArrayList<T>();
        this.body.add(data);
    }

    public void setResponse(ReturnCode status){
        this.status = status;
    }

    public void setResponse(ReturnCode status, String msg){
        this.status = status;
        this.msg    = msg;
    }

    @Override
    public String toString() {
        String  returnVal = "{" +
                            "\"status\": " + ReturnCode.FAIL +
                            ", \"msg\" : \"" + ReturnCode.FAIL.getMsg() + "\"" +
                            "}";
        try {
            String msg = (this.msg == null) ? status.getMsg() : this.msg;
            returnVal = "{" +
                        "\"status\": "   +   status.getCode() +
                        ", \"msg\" : \"" +   msg + "\"" +
                        ", \"data\": "   +   mapper.writeValueAsString(body) +
                        "}";
        } catch (JsonProcessingException e) {
            log.error("[TO STRING] Response toString() error");
            e.printStackTrace();
            return returnVal;
        }

        return returnVal;
    }
}
