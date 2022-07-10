package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.service.OrderBookService;
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
    OrderBookService orderBook;

    @GetMapping(value = "/v1/orderbook")
    public String getOrderbook(@RequestParam HashMap<String, String> params) {
        Response response = new Response(ReturnCode.FAIL);
        try{

            if(isData(params)){
                String orders = orderBook.getOrderBook(Long.parseLong(params.get("exchange")), params.get("currency"));
                if(orders.equals(ReturnCode.FAIL.getValue())){
                    response.setResponse(ReturnCode.FAIL);
                }else if(orders.equals(ReturnCode.NO_DATA.getValue())){
                    response.setResponse(ReturnCode.NO_DATA);
                }else{
                    response.setBody(ReturnCode.SUCCESS, orders);
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
