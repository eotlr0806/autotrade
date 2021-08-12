package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.service.ExchangeService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@Slf4j
public class ExchangeRestController {

    @Autowired
    ExchangeService service;

    @GetMapping(value = "/v1/exchanges")
    public String getExchanges(HttpServletRequest request){
        String exchanges = "";
        try{

            // stack over flow 를 방지하기 위해, expose 어노테이션을 준 필드는 가져오지 않게 설정.
            Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
            List<Exchange> exchangeList = service.getExchanges();
            if(exchangeList.size() < 1){
                log.info("[API - Get Exchange] There is no exchage");
                String msg = "msg:There is no exchanges";
                return ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);
            }else{
                log.info("[API - Get Exchange] list : {}", gson.toJson(exchangeList.toString()));
                String msg = "msg:API success";
                return gson.toJson(exchangeList);
            }
        }catch (Exception e){
            log.error("[ERROR][API - Get Exchange] {}",e.getMessage());
            String msg = "msg:"+e.getMessage().toString();
            return ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);
        }
    }


}
