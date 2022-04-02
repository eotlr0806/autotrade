package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.service.ExchangeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
public class ExchangeRestController {

    @Autowired
    ExchangeService service;

    @GetMapping(value = "/v1/exchanges")
    public String getExchanges(){

        Response response           = new Response(ReturnCode.FAIL);
        try{
            // stack over flow 를 방지하기 위해, expose 어노테이션을 준 필드는 가져오지 않게 설정.
            List<Exchange> exchangeList = service.getExchanges();
            if(!exchangeList.isEmpty()){
                response.setBody(ReturnCode.SUCCESS, exchangeList);
            }else{
                log.info("[GET EXCHANGE] There is no exchages");
                response.setResponse(ReturnCode.NO_DATA);
            }
        }catch (Exception e){
            e.printStackTrace();
            log.error("[GET EXCHANGE] {}",e.getMessage());
            response.setResponse(ReturnCode.FAIL);
        }
        return response.toString();
    }

}
