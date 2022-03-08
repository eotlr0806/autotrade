package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.common.enumeration.SessionKey;
import com.coin.autotrade.model.Fishing;
import com.coin.autotrade.service.FishingService;
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
public class FishingRestController {

    @Autowired
    FishingService service;

    /* Liquidity Get */
    @GetMapping(value = "/v1/trade/fishing")
    public String getFishingTrade() {
        Response response = new Response(ReturnCode.FAIL);
        try{
            List<Fishing> fishingList = service.getFishing();
            if(fishingList.isEmpty()){
                response.setResponse(ReturnCode.NO_DATA);
            }else{
                response.setResponseWithObject(ReturnCode.SUCCESS, fishingList);
            }
            log.info("[GET FISHING TRADE] Success get fishingtrade.");
        }catch(Exception e){
            response.setResponse(ReturnCode.FAIL);
            log.error("[GET FISHING TRADE] Occur error : {} ", e.getMessage());
            e.printStackTrace();
        }

        return response.toString();
    }


    /* schedule의 action type에 따라 분기 */
    @PostMapping(value = "/v1/trade/fishing")
    public String postFishingTrade(HttpServletRequest request, @RequestBody String body) {

        ReturnCode returnVal  = ReturnCode.FAIL;
        Response response     = new Response(ReturnCode.FAIL);
        Gson gson             = Utils.getGson();

        try{
            Fishing fishing = gson.fromJson(body, Fishing.class);

            switch(fishing.getStatus()) {
                case "RUN":
                    returnVal = service.postFishing(fishing, request.getSession().getAttribute(SessionKey.USER_ID.toString()).toString());
                    break;
                case "STOP" :
                    returnVal = service.stopFishing(fishing);
                    break;
                case "DELETE":
                    returnVal = service.deleteFishing(fishing);
                    break;
                default:
                    break;
            }

            response.setResponse(returnVal);

        }catch(Exception e){
            log.error("[POST FISHING TRADE] {} ", e.getMessage());
            e.printStackTrace();
        }

        return response.toString();
    }


}
