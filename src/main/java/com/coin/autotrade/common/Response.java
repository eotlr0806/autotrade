package com.coin.autotrade.common;

import com.coin.autotrade.common.code.ReturnCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class Response {

    private ReturnCode status;
    private Object data = null;
    JsonMapper mapper = new JsonMapper();

    public Response(ReturnCode status){
        this.status = status;
    }

    public void setResponseWithObject(ReturnCode status, Object data){
        this.status = status;
        this.data   = data;
    }

    public void setResponse(ReturnCode status){
        this.status = status;
    }

    @Override
    public String toString() {
        String  returnVal = "{" +
                            "\"status\": " + ReturnCode.FAIL +
                            ", \"msg\" : \"" + ReturnCode.FAIL.getMsg() + "\"" +
                            "}";
        try {
            returnVal = "{" +
                        "\"status\": " + status.getCode() +
                        ", \"msg\" : \"" + status.getMsg() + "\"" +
                        ", \"data\": " + mapper.writeValueAsString(data) +
                        "}";
        } catch (JsonProcessingException e) {
            log.error("[TO STRING] Response toString() error");
            e.printStackTrace();
            return returnVal;
        }

        return returnVal;
    }
}
