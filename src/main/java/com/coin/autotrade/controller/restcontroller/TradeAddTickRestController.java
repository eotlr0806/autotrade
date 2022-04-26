package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.model.AddTick;
import com.coin.autotrade.service.AddTickService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class TradeAddTickRestController {

    @Autowired
    AddTickService addTickService;

    @PostMapping(value = "/v1/trade/add-tick")
    public String postAutoTrade(@RequestBody AddTick body) {
        log.info("[TRADE ADD TICK CONTROLLER] request : {} ", body.toString());
        return addTickService.addTick(body).toString();
    }


}
