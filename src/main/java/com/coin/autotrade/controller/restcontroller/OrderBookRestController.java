package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.service.parser.OrderBookParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;

@RestController
@Slf4j
public class OrderBookRestController {

    @Autowired
    OrderBookParser orderBook;

    @GetMapping(value = "/v1/orderbook")
    public String getCoinoneOrderbook(HttpServletRequest request, @RequestParam HashMap<String, String> params) throws Exception{

        String returnVal = "";

        if(params.get("exchange") != null && params.get("currency") != null && params.get("userId") != null){
            String exchange = params.get("exchange");
            String coin     = params.get("currency");
            String userId   = params.get("userId");

            returnVal = orderBook.getOrderBook(exchange, coin, userId);
        }
        return returnVal;
    }


}
