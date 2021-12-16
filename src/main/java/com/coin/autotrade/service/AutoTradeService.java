package com.coin.autotrade.service;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.common.code.ReturnCode;
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
    UserRepository userRepository;

    @Autowired
    ExchangeRepository exchangeRepository;

    Gson gson = new Gson();
    /* autoTrade List 를 반환하는 메서드 */
    public String getAutoTrade(){
        String returnVal = ReturnCode.NO_DATA.getValue();
        try{
            List<AutoTrade> autoTradeList = autotradeRepository.findAll();
            if(!autoTradeList.isEmpty()){
                returnVal = gson.toJson(autoTradeList);
            }
        }catch(Exception e){
            returnVal = ReturnCode.FAIL.getValue();
            log.error("[GET AUTOTRADE] error: {}",e.getMessage());
            e.printStackTrace();
        }
        return returnVal;
    }


    /**
     * @return ReturnCode.FAIL , ReturnCode.DUPLICATION_DATA , ReturnCode.SUCCESS
     */
    @Transactional
    public String postAutoTrade(AutoTrade autoTrade, String userId){

        String returnValue = ReturnCode.FAIL.getValue();
        Thread thread      = null;

        try{
            // 중복 체크
            if(ServiceCommon.isAutoTradeThread(autoTrade.getId())){
                log.error("[POST AUTOTRADE] Thread is already existed id: {}", autoTrade.getId());
                returnValue = ReturnCode.DUPLICATION_DATA.getValue();
            }else{
                // 기존의 Autotrade 값을 가져온다.
                AutoTrade savedAutotrade = autotradeRepository.getById(autoTrade.getId());
                savedAutotrade.setStatus(DataCommon.STATUS_RUN);
                Exchange exchange = exchangeRepository.findByexchangeCode(savedAutotrade.getExchange());

                User user = userRepository.findByUserId(userId);
                AutoTradeThread autoTradeThread = new AutoTradeThread();
                autoTradeThread.initClass(savedAutotrade,user,exchange);

                // Input Thread to pool
                if(ServiceCommon.setAutoTradeThread(autoTrade.getId(), autoTradeThread)){
                    autotradeRepository.save(savedAutotrade);

                    thread = new Thread(autoTradeThread);
                    ServiceCommon.startThread(thread);      // Thread Start
                    log.info("[POST AUTOTRADE] Start autotrade thread id : {} ", autoTrade.getId());
                    returnValue = ReturnCode.SUCCESS.getValue();
                }else{
                    log.error("[POST AUTOTRADE] Save autotrade is failed thread id : {} ", autoTrade.getId());
                }
            }
        }catch(Exception e){
            if(thread.isAlive()){
                thread.interrupt();
            }
            ServiceCommon.popAutoTradeThread(autoTrade.getId());

            log.error("[POST AUTOTRADE] Occur error : {}",e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }

    /**
     * @return ReturnCode.FAIL , ReturnCode.DUPLICATION_DATA , ReturnCode.SUCCESS
     */
    @Transactional
    public String deleteAutoTrade(AutoTrade autoTrade){
        String returnValue = ReturnCode.FAIL.getValue();

        try{
            // Thread pool 에서 thread를 가져와 멈춘다.
            AutoTradeThread thread = ServiceCommon.popAutoTradeThread(autoTrade.getId());
            if(thread != null){
                thread.setStop();
            }

            // DB의 스케줄을 삭제한다.
            if(autotradeRepository.existsById(autoTrade.getId())){
                autotradeRepository.deleteById(autoTrade.getId());
            }
            log.info("[DELETE AUTOTRADE] Delete thread Id : {} ", autoTrade.getId());
            returnValue = ReturnCode.SUCCESS.getValue();
        }catch(Exception e){
            log.error("[DELETE AUTOTRADE] Occur error : {}", e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }

    /**
     * @return ReturnCode.FAIL , ReturnCode.DUPLICATION_DATA , ReturnCode.SUCCESS
     */
    @Transactional
    public String stopAutoTrade(AutoTrade autoTrade){
        String returnValue = ReturnCode.FAIL.getValue();

        try{
            // Thread pool 에서 thread를 가져와 멈춘다.
            AutoTradeThread thread = ServiceCommon.popAutoTradeThread(autoTrade.getId());
            if(thread != null){
                thread.setStop();
            }
            // 현재 값을 STOP 으로 변경
            Optional<AutoTrade> optionalAutoTrade = autotradeRepository.findById(autoTrade.getId());
            if(optionalAutoTrade.isPresent()){
                AutoTrade savedAutoTrade = optionalAutoTrade.get();
                savedAutoTrade.setStatus(DataCommon.STATUS_STOP);
                autotradeRepository.save(savedAutoTrade);
            }else{
                log.info("[STOP AUTOTRADE] There is no thread in DB thread id : {} ", autoTrade.getId());
            }
            returnValue = ReturnCode.SUCCESS.getValue();
            log.info("[STOP AUTOTRADE] Stop thread id : {} ", autoTrade.getId());
        }catch(Exception e){
            log.error("[STOP AUTOTRADE] error:  {}",e.getMessage());
            e.printStackTrace();
        }

        return returnValue;
    }
}
