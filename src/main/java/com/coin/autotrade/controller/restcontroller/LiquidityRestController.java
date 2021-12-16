package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.common.code.SessionKey;
import com.coin.autotrade.model.Liquidity;
import com.coin.autotrade.service.LiquidityService;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@Slf4j
public class LiquidityRestController {

    @Autowired
    LiquidityService service;

    /* Liquidity Get */
    @GetMapping(value = "/v1/trade/liquidity")
    public String getLiquidityTrade() {
        Response response = new Response();
        try{
            String liquidity = service.getLiquidity();
            if(liquidity.equals(ReturnCode.NO_DATA.getValue())){
                response.setResponseWhenFail(ReturnCode.NO_DATA.getCode(), ReturnCode.NO_DATA.getMsg());
            }else if(liquidity.equals(ReturnCode.FAIL.getValue())){
                response.setResponseWhenFail(ReturnCode.FAIL.getCode(), ReturnCode.FAIL.getMsg());
            }else{
                response.setResponseWhenSuccess(ReturnCode.SUCCESS.getCode(), liquidity);
            }
            log.info("[GET LIQUIDITY] Get liquidity list : {}", liquidity);
        }catch(Exception e){
            log.error("[GET LIQUIDITY] Occur error : {} ", e.getMessage());
            response.setResponseWhenFail(ReturnCode.FAIL.getCode(), ReturnCode.FAIL.getMsg());
            e.printStackTrace();
        }
        return response.toString();
    }


    /* schedule의 action type에 따라 분기 */
    @PostMapping(value = "/v1/trade/liquidity")
    public String postLiquidityTrade(HttpServletRequest request, @RequestBody String body) {

        String returnVal  = ReturnCode.FAIL.getValue();
        Gson gson         = new Gson();
        Response response = new Response();

        try{
            Liquidity liquidity = gson.fromJson(body, Liquidity.class);

            switch(liquidity.getStatus()) {
                case "RUN":
                    returnVal = service.postLiquidity(liquidity, request.getSession().getAttribute(SessionKey.USER_ID.toString()).toString());
                    break;
                case "STOP" :
                    returnVal = service.stopLiquidity(liquidity);
                    break;
                case "DELETE":
                    returnVal = service.deleteLiquidity(liquidity);
                    break;
                default:
                    break;
            }

            if(returnVal.equals(ReturnCode.SUCCESS.getValue())){
                response.setResponseWhenSuccess(ReturnCode.SUCCESS.getCode(), null);
            }else if(returnVal.equals(ReturnCode.NO_DATA.getValue())){
                response.setResponseWhenFail(ReturnCode.NO_DATA.getCode(), ReturnCode.NO_DATA.getMsg());
            }else if(returnVal.equals(ReturnCode.DUPLICATION_DATA.getValue())){
                response.setResponseWhenFail(ReturnCode.DUPLICATION_DATA.getCode(), ReturnCode.DUPLICATION_DATA.getMsg());
            }else{
                response.setResponseWhenFail(ReturnCode.FAIL.getCode(), ReturnCode.FAIL.getMsg());
            }
        }catch(Exception e){
            response.setResponseWhenFail(ReturnCode.FAIL.getCode(), e.getMessage());
            log.error("[POST LIQUIDITY] Occur error : {}", e.getMessage());
            e.printStackTrace();
        }

        return response.toString();
    }


}
