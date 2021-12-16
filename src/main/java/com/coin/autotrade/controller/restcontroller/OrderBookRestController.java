package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.service.parser.OrderBookParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;

@RestController
@Slf4j
public class OrderBookRestController {

    @Autowired
    OrderBookParser orderBook;

    @GetMapping(value = "/v1/orderbook")
    public String getCoinoneOrderbook(@RequestParam HashMap<String, String> params) throws Exception{
        Response response = new Response();
        if(isData(params)){
            String orders = orderBook.getOrderBook(params.get("exchange"), params.get("currency"), params.get("userId"));
            if(orders.equals(ReturnCode.NO_DATA.getValue())){
                response.setResponseWhenFail(ReturnCode.NO_DATA.getCode(), ReturnCode.NO_DATA.getMsg());
            }else{
                response.setResponseWhenSuccess(ReturnCode.SUCCESS.getCode(), orders);
            }
        }else{
            response.setResponseWhenFail(ReturnCode.NO_SUFFICIENT_PARAMS.getCode(), ReturnCode.NO_SUFFICIENT_PARAMS.getMsg());
        }
        return response.toString();
    }


    private boolean isData(HashMap<String, String> params){
        boolean flag = false;
        if(params.get("exchange") != null && params.get("currency") != null && params.get("userId") != null){
            flag = true;
        }
        return flag;
    }

}
