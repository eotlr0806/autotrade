package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.model.ExchangeCoin;
import com.coin.autotrade.service.ExchangeCoinService;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class ExchangeCoinRestController {

    @Autowired
    ExchangeCoinService coinService;

    /**
     * 거래 화면에서 코인 추가시 사용하는 API
     * @param body
     * @return
     */
    @PostMapping(value = "/v1/exchanges/coin")
    public String addExchangeCoin(@RequestBody String body){

        Response response = new Response(ReturnCode.FAIL);
        Gson gson         = Utils.getGson();
        try {

            ExchangeCoin coin     = gson.fromJson(body, ExchangeCoin.class);
            ReturnCode returnVal  = coinService.insertUpdateCoin(coin);
            response.setResponse(returnVal);
        }catch(Exception e){
            response.setResponse(ReturnCode.FAIL);
            log.error("[ADD EXCHANGE COIN] Occur error : {}", e.getMessage());
            e.printStackTrace();
        }
        return response.toString();
    }

    @DeleteMapping(value = "/v1/exchanges/coin")
    public String deleteExchangeCoin(@RequestBody String body){

        Response response = new Response(ReturnCode.FAIL);
        Gson gson         = Utils.getGson();
        try {
            ExchangeCoin coin = gson.fromJson(body, ExchangeCoin.class);
            ReturnCode returnVal  = coinService.deleteCoin(coin.getId());
            response.setResponse(returnVal);
        }catch(Exception e){
            response.setResponse(ReturnCode.FAIL);
            log.error("[DELETE EXCHANGE COIN] Occur error : {}", e.getMessage());
            e.printStackTrace();
        }
        return response.toString();
    }

}
