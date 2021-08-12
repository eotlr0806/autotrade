package com.coin.autotrade.service;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.AutoTrade;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.model.User;
import com.coin.autotrade.repository.AutoTradeRepository;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.repository.UserRepository;
import com.coin.autotrade.service.thread.AutoTradeThread;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AutoTradeService {

    @Autowired
    AutoTradeRepository autotradeRepository;

    @Autowired
    CoinService coinService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ExchangeRepository exchangeRepository;

    /**
     * autoTrade List 를 반환하는 메서드
     * @return
     */
    public String getAutoTrade(){
        String returnVal = "";
        try{
            List<AutoTrade> autoTradeList = autotradeRepository.findAll();
            if(autoTradeList.size() < 1){
                String msg = "msg:There is no active autoTrade";
                returnVal =  ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);
            }else{
                // TODO
                Gson gson = new Gson();
                returnVal = gson.toJson(autoTradeList);
            }

        }catch(Exception e){
            System.out.println("getshceulde:" + e.getMessage());
        }

        return returnVal;
    }


    // Start autoTrade
    public String postAutoTrade(AutoTrade autoTrade, String userId){

        String returnValue = "";
        String msg         = "";
        Thread thread      = null;
        Long id            = null;

        try{
            Gson gson = new Gson();
            log.info("[AutoTradeService - Start] Data : {} ", gson.toJson(autoTrade));

            User user         = userRepository.findByUserId(userId);
            Exchange exchange = exchangeRepository.findByexchangeCode(autoTrade.getExchange());

            // JPA 테이블에 max autoTrade 값 반환
            id = autotradeRepository.selectMaxId();
            autoTrade.setId(id);
            autoTrade.setDate(ServiceCommon.getNowData());

            // Thread 생성
            AutoTradeThread autoTradeThread = new AutoTradeThread();
            autoTradeThread.initClass(autoTrade,user,exchange);
            thread = new Thread(autoTradeThread);

            if(ServiceCommon.setAutoTradeThread(id,autoTradeThread)){  // Thread pool에 넣기 성공
                autotradeRepository.save(autoTrade);              // DB에 해당 autoTrade 저장
                msg = "msg:start thread, id:" + id;
                returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_SUCCESS, msg);
            }else{                                              // 실패
                autoTradeThread.setStop();
                msg = "msg:can not push thread object in list";
                returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);
            }
            ServiceCommon.startThread(thread);  // thread start by async mode
        }catch(Exception e){
            if(thread.isAlive()){
                thread.interrupt();
            }
            if(!msg.equals("")){
                ServiceCommon.getAutoTradeThread(id);
            }
            System.out.println(e.getMessage());
        }
        return returnValue;
    }

    // Stop autoTrade
    public String deleteAutoTrade(AutoTrade autoTrade){
        String msg         = "";
        String returnValue = "";

        try{
            // Thread pool 에서 thread를 가져와 멈춘다.
            AutoTradeThread thread = ServiceCommon.getAutoTradeThread(autoTrade.getId());
            if(thread != null){
                thread.setStop();
            }
            // DB의 스케줄을 삭제한다.
            if(autotradeRepository.existsById(autoTrade.getId())){
                autotradeRepository.deleteById(autoTrade.getId());
            }
        }catch(Exception e){
            msg = "msg:fail stop thread, id:" + autoTrade.getId();
            returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);

            System.out.println(e.getMessage());
        }
        msg = "msg:success stop thread, id:" + autoTrade.getId();
        returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_SUCCESS, msg);

        return returnValue;
    }
}
