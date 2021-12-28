package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.service.parser.OrderBookParser;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@RestController
@Slf4j
public class OrderBookRestController {

    @Autowired
    OrderBookParser orderBook;

    @GetMapping(value = "/v1/orderbook")
    public String getOrderbook(@RequestParam HashMap<String, String> params) {
        Response response = new Response(ReturnCode.FAIL);
        Gson gson         = new Gson();
        try{

            if(isData(params)){
                String orders = orderBook.getOrderBook(params.get("exchange"), params.get("currency"), params.get("userId"));
                List<String> orderList = new ArrayList<>();
                orderList.add(orders);

                if(orders.equals(ReturnCode.NO_DATA.getValue())){
                    response.setResponse(ReturnCode.NO_DATA);
                }else{
                    response.setResponseWithObject(ReturnCode.SUCCESS, orderList);
                }
            }else{
                response.setResponse(ReturnCode.NO_SUFFICIENT_PARAMS);
            }
        }catch (Exception e){
            log.error("[GET ORDER BOOK] Error is occured");
            e.printStackTrace();
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
