package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.model.ExceptionLog;
import com.coin.autotrade.service.ExceptionLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
public class TradeExceptionRestController {

    @Autowired
    ExceptionLogService exceptionLogService;

    @GetMapping(value = "/v1/trade/exception")
    public String getTradeException() {
        Response response = new Response(ReturnCode.FAIL);
        try{
            List<ExceptionLog> exceptionList = exceptionLogService.findAll();
            if(exceptionList.isEmpty()){
                response.setResponse(ReturnCode.NO_DATA);
            }else{
                response.setBody(ReturnCode.SUCCESS, exceptionList);
            }
            log.info("[GET TRADE EXCEPTION] Success get exception.");
        }catch(Exception e){
            log.error("[GET TRADE EXCEPTION] {}", e.getMessage());
            e.printStackTrace();
        }
        return response.toString();
    }
}
