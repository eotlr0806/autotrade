package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.common.code.ReturnCode;
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
    public String getExchanges(){
        Gson gson                   = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        Response response           = new Response();
        try{
            // stack over flow 를 방지하기 위해, expose 어노테이션을 준 필드는 가져오지 않게 설정.
            List<Exchange> exchangeList = service.getExchanges();

            if(!exchangeList.isEmpty()){
                response.setResponseWhenSuccess(ReturnCode.SUCCESS.getCode(), gson.toJson(exchangeList));
            }else{
                log.info("[GET EXCHANGE] There is no exchages");
                response.setResponseWhenFail(ReturnCode.NO_DATA.getCode(), ReturnCode.NO_DATA.getMsg());
            }
        }catch (Exception e){
            log.error("[GET EXCHANGE] {}",e.getMessage());
            response.setResponseWhenFail(ReturnCode.FAIL.getCode(), e.getMessage());
        }
        return response.toString();
    }

}
