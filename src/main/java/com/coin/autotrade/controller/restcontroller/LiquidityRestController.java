package com.coin.autotrade.controller.restcontroller;

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

    /**
     * Liquidity Get
     * @return
     */
    @GetMapping(value = "/v1/trade/liquidity")
    public String getLiquidityTrade() {
        String returnVal = "";
        try{
            returnVal = service.getLiquidity();
        }catch(Exception e){
            log.error("[API - Get Liquidity] {} ", e.getMessage());
        }

        return returnVal;
    }


    /**
     * schedule의 action type에 따라 분기
     * @param body
     * @param user
     * @return
     */
    @PostMapping(value = "/v1/trade/liquidity")
    public String postLiquidityTrade(HttpServletRequest request, @RequestBody String body) {

        String returnVal = "";
        Gson gson = new Gson();

        try{
            Liquidity liquidity = gson.fromJson(body, Liquidity.class);

            switch(liquidity.getStatus()) {
                case "RUN":
                    returnVal = service.postLiquidity(liquidity, request.getSession().getAttribute("userId").toString());
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
        }catch(Exception e){
            log.error("[API - Post Liquidity] {} ", e.getMessage());
        }

        return returnVal;
    }


}
