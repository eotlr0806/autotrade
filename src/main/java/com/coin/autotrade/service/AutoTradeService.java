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

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

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
                Gson gson = new Gson();
                returnVal = gson.toJson(autoTradeList);
            }

        }catch(Exception e){
            log.error("[ERROR][Get AutoTrade Trade] {}",e.getMessage());
        }

        return returnVal;
    }


    // Start autoTrade
    @Transactional
    public String postAutoTrade(AutoTrade autoTrade, String userId){

        String returnValue = "";
        String msg         = "";
        Thread thread      = null;
        Long id            = autoTrade.getId();

        try{
            log.info("[AutoTradeService - Start] Thread Id : {} ", id);

            Gson gson = new Gson();

            // 중복 체크
            AutoTradeThread threadForCheck = ServiceCommon.getAutoTradeThread(autoTrade.getId());
            if(threadForCheck != null){
                log.error("[ERROR][Post AutoTrade Trade][Thread started already] thread Id: {}", autoTrade.getId());
                msg = "msg:can not push thread object in list";
                returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);
                return returnValue;
            }
            // 기존의 Autotrade 값을 가져온다.
            AutoTrade savedAutotrade = autotradeRepository.getById(id);
            savedAutotrade.setStatus(DataCommon.STATUS_RUN);
            User user         = userRepository.findByUserId(userId);
            Exchange exchange = exchangeRepository.findByexchangeCode(savedAutotrade.getExchange());

            // Thread 생성
            AutoTradeThread autoTradeThread = new AutoTradeThread();
            autoTradeThread.initClass(savedAutotrade,user,exchange);
            thread = new Thread(autoTradeThread);

            if(ServiceCommon.setAutoTradeThread(id, autoTradeThread)){  // Thread pool에 넣기 성공
                autotradeRepository.save(savedAutotrade);              // DB에 해당 autoTrade 저장
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
            log.error("[ERROR][Post AutoTrade Trade] {}",e.getMessage());
        }
        return returnValue;
    }

    // Stop autoTrade
    public String deleteAutoTrade(AutoTrade autoTrade){
        String msg         = "";
        String returnValue = "";

        try{
            log.info("[AutoTradeService - Delete] Thread Id : {} ", autoTrade.getId());

            // Thread pool 에서 thread를 가져와 멈춘다.
            AutoTradeThread thread = ServiceCommon.getAutoTradeThread(autoTrade.getId());
            if(thread != null){
                thread.setStop();
                log.info("[SUCCESS][AutoTradeService - Delete Thread] Thread Id : {} ", autoTrade.getId());
            }else{
                log.error("[ERROR][AutoTradeService - Delete] No Thread Id : {} ", autoTrade.getId());
            }
            // DB의 스케줄을 삭제한다.
            if(autotradeRepository.existsById(autoTrade.getId())){
                autotradeRepository.deleteById(autoTrade.getId());
                log.info("[SUCCESS][AutoTradeService - Delete Data] Thread Id : {} ", autoTrade.getId());
            }
        }catch(Exception e){
            msg = "msg:fail delete thread, id:" + autoTrade.getId();
            returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);

            log.error("[ERROR][Delete AutoTrade Trade] {}",e.getMessage());
        }
        msg = "msg:success delete thread, id:" + autoTrade.getId();
        returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_SUCCESS, msg);

        return returnValue;
    }

    @Transactional
    public String stopAutoTrade(AutoTrade autoTrade){
        String msg         = "";
        String returnValue = "";

        try{
            log.info("[AutoTradeService - Stop] Thread Id : {} ", autoTrade.getId());

            // Thread pool 에서 thread를 가져와 멈춘다.
            AutoTradeThread thread = ServiceCommon.getAutoTradeThread(autoTrade.getId());
            if(thread != null){
                thread.setStop();
                log.info("[SUCCESS][AutoTradeService - Stop Thread] Thread Id : {} ", autoTrade.getId());
            }else{
                log.error("[ERROR][AutoTradeService - Stop] No Thread Id : {} ", autoTrade.getId());
            }

            // 현재 값을 STOP 으로 변경
            AutoTrade savedAutoTrade = autotradeRepository.getById(autoTrade.getId());
            savedAutoTrade.setStatus(DataCommon.STATUS_STOP);
            autotradeRepository.save(savedAutoTrade);

        }catch(Exception e){
            msg = "msg:fail stop thread, id:" + autoTrade.getId();
            returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);

            log.error("[ERROR][Stop AutoTrade Trade] {}",e.getMessage());
        }
        msg = "msg:success stop thread, id:" + autoTrade.getId();
        returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_SUCCESS, msg);

        return returnValue;
    }
}
