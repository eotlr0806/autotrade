package com.coin.autotrade.controller.restcontroller;

import ch.qos.logback.core.joran.conditional.ElseAction;
import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.common.code.SessionKey;
import com.coin.autotrade.model.AutoTrade;
import com.coin.autotrade.service.AutoTradeService;
import com.google.gson.Gson;
import com.sun.xml.bind.v2.TODO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@Slf4j
public class AutoTradeRestController {

    @Autowired
    AutoTradeService service;

    /**
     * Service Get
     * @return
     */
    @GetMapping(value = "/v1/trade/auto")
    public String getAutoTrade() {
        Response response = new Response();
        try{
            String autoList = service.getAutoTrade();
            if(autoList.equals(ReturnCode.NO_DATA.getValue())){
                response.setResponseWhenFail(ReturnCode.NO_DATA.getCode(), ReturnCode.NO_DATA.getMsg());
            }else if(autoList.equals(ReturnCode.FAIL.getValue())){
                response.setResponseWhenFail(ReturnCode.FAIL.getCode(), ReturnCode.FAIL.getMsg());
            }else{
                response.setResponseWhenSuccess(ReturnCode.SUCCESS.getCode(), autoList);
            }
            log.info("[GET AUTOTRADE] Get autotrade list : {}", autoList);
        }catch(Exception e){
            log.error("[GET AUTOTRADE] {}", e.getMessage());
            response.setResponseWhenFail(ReturnCode.FAIL.getCode(), ReturnCode.FAIL.getMsg());
            e.printStackTrace();
        }
        return response.toString();
    }


    /**
     * schedule의 action type에 따라 분기
     * @param body
     * @param user
     * @return
     */
    @PostMapping(value = "/v1/trade/auto")
    public String postAutoTrade(HttpServletRequest request, @RequestBody String body) {

        String returnVal  = ReturnCode.FAIL.getValue();
        Gson gson         = new Gson();
        Response response = new Response();

        try{
            AutoTrade autoTrade = gson.fromJson(body, AutoTrade.class);

            switch(autoTrade.getStatus()) {
                case "RUN":
                    returnVal = service.postAutoTrade(autoTrade, request.getSession().getAttribute(SessionKey.USER_ID.toString()).toString());
                    break;
                case "STOP" :
                    returnVal = service.stopAutoTrade(autoTrade);
                    break;
                case "DELETE":
                    returnVal = service.deleteAutoTrade(autoTrade);
                    break;
                default:
                    break;
            }

            if(returnVal.equals(ReturnCode.SUCCESS.getValue())){
                response.setResponseWhenSuccess(ReturnCode.SUCCESS.getCode(), null);
            }else if(returnVal.equals(ReturnCode.NO_DATA.getValue())){
                response.setResponseWhenFail(ReturnCode.NO_DATA.getCode(), ReturnCode.NO_DATA.getMsg());
            }else if(returnVal.equals(ReturnCode.DUPLICATION_DATA.getValue())){
                response.setResponseWhenFail(ReturnCode.DUPLICATION_DATA.getCode(), ReturnCode.DUPLICATION_DATA.getMsg());
            }else{
                response.setResponseWhenFail(ReturnCode.FAIL.getCode(), ReturnCode.FAIL.getMsg());
            }

        }catch(Exception e){
            response.setResponseWhenFail(ReturnCode.FAIL.getCode(), e.getMessage());
            log.error("[POST AUTOTRADE] error : {}", e.getMessage());
            e.printStackTrace();
        }

        return response.toString();
    }


}
