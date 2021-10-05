package com.coin.autotrade.controller.restcontroller;

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

@RestController
@Slf4j
public class FishingRestController {

    @Autowired
    FishingService service;

    /**
     * Liquidity Get
     * @return
     */
    @GetMapping(value = "/v1/trade/fishing")
    public String getFishingTrade() {
        String returnVal = "";
        try{
            returnVal = service.getFishing();
        }catch(Exception e){
            log.error("[API - Get Fishing] {} ", e.getMessage());
        }

        return returnVal;
    }


    /**
     * schedule의 action type에 따라 분기
     * @param body
     * @param user
     * @return
     */
    @PostMapping(value = "/v1/trade/fishing")
    public String postFishingTrade(HttpServletRequest request, @RequestBody String body) {

        String returnVal = "";
        Gson gson = new Gson();

        try{
            Fishing fishing = gson.fromJson(body, Fishing.class);

            switch(fishing.getStatus()) {
                case "RUN":
                    returnVal = service.postFishing(fishing, request.getSession().getAttribute("userId").toString());
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
        }catch(Exception e){
            log.error("[API - Post Fishing] {} ", e.getMessage());
        }

        return returnVal;
    }


}
