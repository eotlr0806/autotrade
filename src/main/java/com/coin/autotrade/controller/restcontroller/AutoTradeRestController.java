package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.model.AutoTrade;
import com.coin.autotrade.service.AutoTradeService;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
public class AutoTradeRestController {

    @Autowired
    AutoTradeService service;

    /**
     * Service Get
     * @return
     */
    @GetMapping(value = "/v1/trade/auto")
    public String getAutoTrade() {
        String returnVal = "";
        try{
            returnVal = service.getAutoTrade();
        }catch(Exception e){
            System.out.println(e.getMessage());
        }

        return returnVal;
    }


    /**
     * schedule의 action type에 따라 분기
     * @param body
     * @param user
     * @return
     */
    @PostMapping(value = "/v1/trade/auto")
    public String postAutoTrade(HttpServletRequest request, @RequestBody String body) {

        String returnVal = "";
        Gson gson = new Gson();

        try{
            AutoTrade autoTrade = gson.fromJson(body, AutoTrade.class);

            switch(autoTrade.getStatus()) {
                case "RUN":
                    returnVal = service.postAutoTrade(autoTrade, request.getSession().getAttribute("userId").toString());
                    break;
                case "STOP":
                    returnVal = service.deleteAutoTrade(autoTrade);
                    break;
                default:
                    break;
            }
        }catch(Exception e){
            System.out.println(e.getMessage());
        }

        return returnVal;
    }


}
