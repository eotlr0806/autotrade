package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.service.TradeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class TradeSaveRestController {

    @Autowired
    TradeService tradeService;

    /* schedule의 action type에 따라 분기 */
    @PostMapping(value = "/v1/trade/save")
    public String saveTrade(@RequestBody String body) {

        Response response = new Response(ReturnCode.FAIL);
        try{
            ReturnCode returnVal = tradeService.saveTrade(body);
            response.setResponse(returnVal);
            if(returnVal == ReturnCode.SUCCESS){
                log.info("[SAVE TRADE] Success saving trade : {}", body);
            }else{
                log.error("[SAVE TRADE] Fail saving trade : {}", body);
            }
        }catch(Exception e){
            log.error("[SAVE TRADE] Occur error : {}", e.getMessage());
            e.printStackTrace();
        }

        return response.toString();
    }


}
