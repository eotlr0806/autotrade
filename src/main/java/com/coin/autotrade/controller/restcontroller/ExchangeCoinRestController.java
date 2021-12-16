package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.model.ExchangeCoin;
import com.coin.autotrade.service.ExchangeCoinService;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class ExchangeCoinRestController {

    @Autowired
    ExchangeCoinService coinService;

    Gson gson = new Gson();

    /**
     * 거래 화면에서 코인 추가시 사용하는 API
     * @param body
     * @return
     */
    @PostMapping(value = "/v1/exchanges/coin")
    public String addExchangeCoin(@RequestBody String body){

        Response response = new Response();
        try {
            ExchangeCoin coin = gson.fromJson(body, ExchangeCoin.class);
            String returnVal  = coinService.insertUpdateCoin(coin);
            if(returnVal.equals(ReturnCode.SUCCESS.getValue())){
                response.setResponseWhenSuccess(ReturnCode.SUCCESS.getCode(), null);
            }else{
                response.setResponseWhenFail(ReturnCode.FAIL.getCode(), ReturnCode.FAIL.getMsg());
            }
        }catch(Exception e){
            log.error("[ADD EXCHANGE COIN] Occur error : {}", e.getMessage());
            response.setResponseWhenFail(ReturnCode.FAIL.getCode(), ReturnCode.FAIL.getMsg());
            e.printStackTrace();
        }
        return response.toString();
    }

    @DeleteMapping(value = "/v1/exchanges/coin")
    public String deleteExchangeCoin(@RequestBody String body){

        Response response = new Response();
        try {
            ExchangeCoin coin = gson.fromJson(body, ExchangeCoin.class);
            String returnVal  = coinService.deleteCoin(coin.getId());
            if(returnVal.equals(ReturnCode.SUCCESS.getValue())){
                response.setResponseWhenSuccess(ReturnCode.SUCCESS.getCode(), null);
            }else{
                response.setResponseWhenFail(ReturnCode.FAIL.getCode(), ReturnCode.FAIL.getMsg());
            }
        }catch(Exception e){
            log.error("[DELETE EXCHANGE COIN] Occur error : {}", e.getMessage());
            response.setResponseWhenFail(ReturnCode.FAIL.getCode(), ReturnCode.FAIL.getMsg());
            e.printStackTrace();
        }
        return response.toString();
    }

}
