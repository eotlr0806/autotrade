package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.DataCommon;
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

        Gson gson        = new Gson();
        String returnVal = "";

        try {
            ExchangeCoin coin = gson.fromJson(body, ExchangeCoin.class);
            returnVal         = String.valueOf(coinService.insertCoin(coin));
        }catch(Exception e){
            log.error("[ERROR][API - Add Coin] {}", e.getMessage());
            returnVal = String.valueOf(DataCommon.CODE_ERROR);
        }
        return returnVal;
    }

    @DeleteMapping(value = "/v1/exchanges/coin")
    public String deleteExchangeCoin(@RequestBody String body){

        Gson gson        = new Gson();
        String returnVal = "";

        try {
            ExchangeCoin coin = gson.fromJson(body, ExchangeCoin.class);
            returnVal         = String.valueOf(coinService.deleteCoin(coin.getId()));
        }catch(Exception e){
            log.error("[ERROR][API - Delete Coin] {}", e.getMessage());
            returnVal = String.valueOf(DataCommon.CODE_ERROR);
        }
        return returnVal;
    }

}
