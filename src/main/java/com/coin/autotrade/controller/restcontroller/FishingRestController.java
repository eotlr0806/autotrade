package com.coin.autotrade.controller.restcontroller;

import ch.qos.logback.core.joran.conditional.ElseAction;
import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.common.code.SessionKey;
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

    /* Liquidity Get */
    @GetMapping(value = "/v1/trade/fishing")
    public String getFishingTrade() {
        Response response = new Response();
        try{
            String fishingList = service.getFishing();
            if(fishingList.equals(ReturnCode.NO_DATA.getValue())){
                response.setResponseWhenFail(ReturnCode.NO_DATA.getCode(), ReturnCode.NO_DATA.getMsg());
            }else if(fishingList.equals(ReturnCode.FAIL.getValue())){
                response.setResponseWhenFail(ReturnCode.FAIL.getCode(), ReturnCode.FAIL.getMsg());
            }else{
                response.setResponseWhenSuccess(ReturnCode.SUCCESS.getCode(), fishingList);
            }
            log.info("[GET FISHING TRADE] Get fishingtrade list : {}", fishingList);
        }catch(Exception e){
            response.setResponseWhenFail(ReturnCode.FAIL.getCode(), ReturnCode.FAIL.getMsg());
            log.error("[GET FISHING TRADE] Occur error : {} ", e.getMessage());
            e.printStackTrace();
        }

        return response.toString();
    }


    /* schedule의 action type에 따라 분기 */
    @PostMapping(value = "/v1/trade/fishing")
    public String postFishingTrade(HttpServletRequest request, @RequestBody String body) {

        String returnVal  = ReturnCode.FAIL.getValue();
        Response response = new Response();
        Gson gson         = new Gson();

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
            log.error("[POST FISHING TRADE] {} ", e.getMessage());
            e.printStackTrace();
        }

        return response.toString();
    }


}
