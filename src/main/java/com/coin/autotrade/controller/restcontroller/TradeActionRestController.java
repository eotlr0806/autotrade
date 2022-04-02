package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.service.TradeActionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;

@RestController
@Slf4j
public class TradeActionRestController {

    @Autowired
    private TradeActionService tradeActionService;;

    /* schedule의 action type에 따라 분기 */
    @PostMapping(value = "/v1/trade/action")
    public String saveTrade(@RequestBody HashMap<String, String> params) {

        Response response = new Response(ReturnCode.FAIL);
        try{
            response = tradeActionService.tradeAction(params);
        }catch(Exception e){
            log.error("[TRADE ACTION] Occur error : {}", e.getMessage());
            e.printStackTrace();
        }

        return response.toString();
    }


}
