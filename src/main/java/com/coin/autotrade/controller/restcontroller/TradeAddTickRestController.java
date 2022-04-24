package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.model.AddTick;
import com.coin.autotrade.service.AddTickService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@Slf4j
public class TradeAddTickRestController {

    @Autowired
    AddTickService addTickService;

    @PostMapping(value = "/v1/trade/add-tick")
    public String postAutoTrade(HttpServletRequest request, @RequestBody AddTick body) {

        ReturnCode returnVal  = ReturnCode.FAIL;
        Response response     = new Response(ReturnCode.FAIL);
        log.info("[TRADE ADD TICK CONTROLLER] request : {} ", body.toString());
        try{
            response = addTickService.addTick(body);
        }catch (Exception e){
            e.printStackTrace();
        }

        return response.toString();
    }


}
