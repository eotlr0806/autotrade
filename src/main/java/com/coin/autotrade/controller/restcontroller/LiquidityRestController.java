package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.common.enumeration.SessionKey;
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
import java.util.List;

@RestController
@Slf4j
public class LiquidityRestController {

    @Autowired
    LiquidityService service;

    /* Liquidity Get */
    @GetMapping(value = "/v1/trade/liquidity")
    public String getLiquidityTrade() {
        Response response = new Response(ReturnCode.FAIL);
        try{
            List<Liquidity> liquidity = service.getLiquidity();
            if(liquidity.isEmpty()){
                response.setResponse(ReturnCode.NO_DATA);
            }else{
                response.setResponseWithObject(ReturnCode.SUCCESS, liquidity);
            }
            log.info("[GET LIQUIDITY] Success get liquidity.");
        }catch(Exception e){
            log.error("[GET LIQUIDITY] Occur error : {} ", e.getMessage());
            response.setResponse(ReturnCode.FAIL);
            e.printStackTrace();
        }
        return response.toString();
    }


    /* schedule의 action type에 따라 분기 */
    @PostMapping(value = "/v1/trade/liquidity")
    public String postLiquidityTrade(HttpServletRequest request, @RequestBody String body) {

        ReturnCode returnVal  = ReturnCode.FAIL;
        Response response     = new Response(ReturnCode.FAIL);
        Gson gson             = Utils.getGson();

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

            response.setResponse(returnVal);
        }catch(Exception e){
            response.setResponse(ReturnCode.FAIL);
            log.error("[POST LIQUIDITY] Occur error : {}", e.getMessage());
            e.printStackTrace();
        }

        return response.toString();
    }


}
