package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.service.thread.BalanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;

@RestController
@Slf4j
public class BalanceRestController {

    @Autowired
    BalanceService balanceService;

    @GetMapping(value = "/v1/balance")
    public String getBalance(@RequestParam HashMap<String, String> params) {
        Response response = new Response(ReturnCode.FAIL);
        try{

            if(isData(params)){
                response = balanceService.getBalance(Long.parseLong(params.get("exchange")), params.get("currency"));
            }else{
                response.setResponse(ReturnCode.NO_SUFFICIENT_PARAMS);
            }
        }catch (Exception e){
            log.error("[GET BALANCE] Error is occured");
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
