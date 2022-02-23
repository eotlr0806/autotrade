package com.coin.autotrade.controller.restcontroller;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.common.code.SessionKey;
import com.coin.autotrade.model.RealtimeSync;
import com.coin.autotrade.service.RealtimeSyncService;
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
public class RealtimeSyncRestController {

    @Autowired
    RealtimeSyncService service;

    /**
     * Service Get
     * @return
     */
    @GetMapping(value = "/v1/trade/realtime_sync")
    public String getRealtimeSync() {
        Response response = new Response(ReturnCode.FAIL);
        try{
            List<RealtimeSync> realtimeSyncList = service.getRealtimeSync();
            if(realtimeSyncList.isEmpty()){
                response.setResponse(ReturnCode.NO_DATA);
            }else{
                response.setResponseWithObject(ReturnCode.SUCCESS, realtimeSyncList);
            }
            log.info("[GET REALTIME SYNC] Success get realtime sync");
        }catch(Exception e){
            log.error("[GET REALTIME SYNC] {}", e.getMessage());
            e.printStackTrace();
        }
        return response.toString();
    }


    /**
     * schedule의 action type에 따라 분기
     */
    @PostMapping(value = "/v1/trade/realtime_sync")
    public String postRealtimeSync(HttpServletRequest request, @RequestBody String body) {

        ReturnCode returnVal  = ReturnCode.FAIL;
        Response response     = new Response(ReturnCode.FAIL);
        Gson gson             = Utils.getGson();

        try{
            RealtimeSync realtimeSync = gson.fromJson(body, RealtimeSync.class);

            switch(realtimeSync.getStatus()) {
                case "RUN":
                    returnVal = service.postRealtimeSync(realtimeSync, request.getSession().getAttribute(SessionKey.USER_ID.toString()).toString());
                    break;
                case "STOP" :
                    returnVal = service.stopRealtimeSync(realtimeSync);
                    break;
                case "DELETE":
                    returnVal = service.deleteRealtimeSync(realtimeSync);
                    break;
                default:
                    break;
            }
            response.setResponse(returnVal);

        }catch(Exception e){
            response.setResponse(ReturnCode.FAIL);
            log.error("[POST REALTIME SYNC] error : {}", e.getMessage());
            e.printStackTrace();
        }

        return response.toString();
    }


}
