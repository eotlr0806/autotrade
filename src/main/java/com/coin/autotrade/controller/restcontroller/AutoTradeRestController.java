package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.common.code.SessionKey;
import com.coin.autotrade.model.AutoTrade;
import com.coin.autotrade.service.AutoTradeService;
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
public class AutoTradeRestController {

    @Autowired
    AutoTradeService service;

    /**
     * Service Get
     * @return
     */
    @GetMapping(value = "/v1/trade/auto")
    public String getAutoTrade() {
        Response response = new Response(ReturnCode.FAIL);
        try{
            List<AutoTrade> autoList = service.getAutoTrade();
            if(autoList.isEmpty()){
                response.setResponse(ReturnCode.NO_DATA);
            }else{
                response.setResponseWithObject(ReturnCode.SUCCESS, autoList);
            }
            log.info("[GET AUTOTRADE] Get autotrade success :{}", Utils.getMapper().writeValueAsString(autoList));
        }catch(Exception e){
            log.error("[GET AUTOTRADE] {}", e.getMessage());
            e.printStackTrace();
        }
        return response.toString();
    }


    /**
     * schedule의 action type에 따라 분기
     * @param body
     * @param user
     * @return
     */
    @PostMapping(value = "/v1/trade/auto")
    public String postAutoTrade(HttpServletRequest request, @RequestBody String body) {

        ReturnCode returnVal  = ReturnCode.FAIL;
        Response response     = new Response(ReturnCode.FAIL);
        Gson gson             = Utils.getGson();

        try{
            AutoTrade autoTrade = gson.fromJson(body, AutoTrade.class);

            switch(autoTrade.getStatus()) {
                case "RUN":
                    returnVal = service.postAutoTrade(autoTrade, request.getSession().getAttribute(SessionKey.USER_ID.toString()).toString());
                    break;
                case "STOP" :
                    returnVal = service.stopAutoTrade(autoTrade);
                    break;
                case "DELETE":
                    returnVal = service.deleteAutoTrade(autoTrade);
                    break;
                default:
                    break;
            }
            response.setResponse(returnVal);

        }catch(Exception e){
            response.setResponse(ReturnCode.FAIL);
            log.error("[POST AUTOTRADE] error : {}", e.getMessage());
            e.printStackTrace();
        }

        return response.toString();
    }


}
