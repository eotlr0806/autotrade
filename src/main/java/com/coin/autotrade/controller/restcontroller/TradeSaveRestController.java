package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.model.AutoTrade;
import com.coin.autotrade.service.AutoTradeService;
import com.coin.autotrade.service.TradeService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@Slf4j
public class TradeSaveRestController {

    @Autowired
    AutoTradeService service;

    @Autowired
    TradeService tradeService;

    /** Get trade info */
    @GetMapping(value = "/v1/trade/save")
    public String getTrade() {
        String returnVal = "";
        try{
            returnVal = service.getAutoTrade();
        }catch(Exception e){
            log.error("[ERROR][API - GET TRADE INFO] {}", e.getMessage());
        }

        return returnVal;
    }


    /* schedule의 action type에 따라 분기 */
    @PostMapping(value = "/v1/trade/save")
    public String postAutoTrade(HttpServletRequest request, @RequestBody String body) {

        String returnVal = "";
        Gson gson = new Gson();

        try{
            JsonObject requestData = gson.fromJson(body, JsonObject.class);
            returnVal = String.valueOf(tradeService.saveTrade(body));
        }catch(Exception e){
            log.error("[ERROR][API - POST TRADE INFO] {}", e.getMessage());
        }

        return returnVal;
    }


}
