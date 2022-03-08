package com.coin.autotrade.service;

import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.model.AutoTrade;
import com.coin.autotrade.repository.AutoTradeRepository;
import com.coin.autotrade.service.thread.AutoTradeThread;
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

    /* autoTrade List 를 반환하는 메서드 */
    public List<AutoTrade> getAutoTrade() throws Exception{
        return autotradeRepository.findAll();
    }

    /**
     * @return ReturnCode.FAIL , ReturnCode.DUPLICATION_DATA , ReturnCode.SUCCESS
     */
    @Transactional
    public ReturnCode postAutoTrade(AutoTrade autoTrade, String userId) throws Exception{
        ReturnCode returnCode = ReturnCode.FAIL;
        Thread thread         = null;

        try{
            // 중복 체크
            if(Utils.isAutoTradeThread(autoTrade.getId())){
                log.error("[POST AUTOTRADE] Thread is already existed id: {}", autoTrade.getId());
                returnCode = ReturnCode.DUPLICATION_DATA;
            }else{
                // 기존의 Autotrade 값을 가져온다.
                Optional<AutoTrade> optionalAutoTrade = autotradeRepository.findById(autoTrade.getId());
                if(optionalAutoTrade.isPresent()){
                    AutoTrade savedAutotrade = optionalAutoTrade.get();
                    AutoTradeThread autoTradeThread = new AutoTradeThread();
                    autoTradeThread.setTrade(savedAutotrade);
                    savedAutotrade.setStatus(UtilsData.STATUS_RUN);

                    // Input Thread to pool
                    if(Utils.setAutoTradeThread(autoTrade.getId(), autoTradeThread)){
                        autotradeRepository.save(savedAutotrade);

                        thread = new Thread(autoTradeThread);
                        Utils.startThread(thread);      // Thread Start
                        log.info("[POST AUTOTRADE] Start autotrade thread id : {} ", autoTrade.getId());
                        returnCode = ReturnCode.SUCCESS;
                    }else{
                        log.error("[POST AUTOTRADE] Save autotrade is failed thread id : {} ", autoTrade.getId());
                    }
                }
            }
        }catch(Exception e){
            log.error("[POST AUTOTRADE] Occur error : {}",e.getMessage());
            e.printStackTrace();
            if(thread != null && thread.isAlive()){
                thread.interrupt();
            }
            Utils.popAutoTradeThread(autoTrade.getId());

        }
        return returnCode;
    }

    /**
     * @return ReturnCode.FAIL , ReturnCode.DUPLICATION_DATA , ReturnCode.SUCCESS
     */
    @Transactional
    public ReturnCode deleteAutoTrade(AutoTrade autoTrade){
        ReturnCode returnCode = ReturnCode.FAIL;

        try{
            // Thread pool 에서 thread를 가져와 멈춘다.
            AutoTradeThread thread = Utils.popAutoTradeThread(autoTrade.getId());
            if(thread != null){
                thread.setStop();
            }
            // DB의 스케줄을 삭제한다.
            if(autotradeRepository.existsById(autoTrade.getId())){
                autotradeRepository.deleteById(autoTrade.getId());
            }
            log.info("[DELETE AUTOTRADE] Delete thread Id : {} ", autoTrade.getId());
            returnCode = ReturnCode.SUCCESS;
        }catch(Exception e){
            log.error("[DELETE AUTOTRADE] Occur error : {}", e.getMessage());
            e.printStackTrace();
        }
        return returnCode;
    }

    /**
     * @return ReturnCode.FAIL , ReturnCode.DUPLICATION_DATA , ReturnCode.SUCCESS
     */
    @Transactional
    public ReturnCode stopAutoTrade(AutoTrade autoTrade){
        ReturnCode returnCode = ReturnCode.FAIL;

        try{
            // Thread pool 에서 thread를 가져와 멈춘다.
            AutoTradeThread thread = Utils.popAutoTradeThread(autoTrade.getId());
            if(thread != null){
                thread.setStop();
            }
            // 현재 값을 STOP 으로 변경
            Optional<AutoTrade> optionalAutoTrade = autotradeRepository.findById(autoTrade.getId());
            if(optionalAutoTrade.isPresent()){
                AutoTrade savedAutoTrade = optionalAutoTrade.get();
                savedAutoTrade.setStatus(UtilsData.STATUS_STOP);
                autotradeRepository.save(savedAutoTrade);
            }else{
                log.info("[STOP AUTOTRADE] There is no thread in DB thread id : {} ", autoTrade.getId());
            }
            returnCode = ReturnCode.SUCCESS;
            log.info("[STOP AUTOTRADE] Stop thread id : {} ", autoTrade.getId());
        }catch(Exception e){
            log.error("[STOP AUTOTRADE] error:  {}",e.getMessage());
            e.printStackTrace();
        }

        return returnCode;
    }
}
