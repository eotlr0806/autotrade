package com.coin.autotrade.service;

import com.coin.autotrade.common.TradeData;
import com.coin.autotrade.common.TradeService;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.model.RealtimeSync;
import com.coin.autotrade.repository.RealtimeSyncRepository;
import com.coin.autotrade.service.thread.RealtimeSyncThread;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Service
@Slf4j
public class RealtimeSyncService {

    @Autowired
    RealtimeSyncRepository realtimeSyncRepository;

    /* realtime List 를 반환하는 메서드 */
    public List<RealtimeSync> getRealtimeSync() throws Exception{
        return realtimeSyncRepository.findAll();
    }

    /**
     * @return ReturnCode.FAIL , ReturnCode.DUPLICATION_DATA , ReturnCode.SUCCESS
     */
    @Transactional
    public ReturnCode postRealtimeSync(RealtimeSync realtimeSync, String userId) throws Exception{
        ReturnCode returnCode = ReturnCode.FAIL;
        Thread thread         = null;

        try{
            // 중복 체크
            if(TradeService.isRealtimeSyncThread(realtimeSync.getId())){
                log.error("[POST REALTIME SYNC] Thread is already existed id: {}", realtimeSync.getId());
                returnCode = ReturnCode.DUPLICATION_DATA;
            }else{
                Optional<RealtimeSync> optionalRealtimeSync = realtimeSyncRepository.findById(realtimeSync.getId());
                if(optionalRealtimeSync.isPresent()){
                    // 기존의 RealtimeSync 값을 가져온다.
                    RealtimeSync savedRealtimeSync = optionalRealtimeSync.get();
                    RealtimeSyncThread realTimeSyncThread = new RealtimeSyncThread();
                    realTimeSyncThread.setTrade(savedRealtimeSync);
                    savedRealtimeSync.setStatus(TradeData.STATUS_RUN);

                    // Input Thread to pool
                    if(TradeService.setRealtimeSyncThread(realtimeSync.getId(), realTimeSyncThread)){
                        realtimeSyncRepository.save(savedRealtimeSync);

                        thread = new Thread(realTimeSyncThread);
                        TradeService.startThread(thread);      // Thread Start
                        log.info("[POST REALTIME SYNC] Start realtime sync thread id : {} ", realtimeSync.getId());
                        returnCode = ReturnCode.SUCCESS;
                    }else{
                        log.error("[POST REALTIME SYNC] Save realtime sync is failed thread id : {} ", realtimeSync.getId());
                    }
                }
            }
        }catch(Exception e){
            log.error("[POST REALTIME SYNC] Occur error : {}",e.getMessage());
            e.printStackTrace();

            if(thread != null && thread.isAlive()){
                thread.interrupt();
            }
            TradeService.popRealtimeSyncThread(realtimeSync.getId());
        }
        return returnCode;
    }

    /**
     * @return ReturnCode.FAIL , ReturnCode.DUPLICATION_DATA , ReturnCode.SUCCESS
     */
    @Transactional
    public ReturnCode deleteRealtimeSync(RealtimeSync realtimeSync){
        ReturnCode returnCode = ReturnCode.FAIL;

        try{
            // Thread pool 에서 thread를 가져와 멈춘다.
            RealtimeSyncThread thread = TradeService.popRealtimeSyncThread(realtimeSync.getId());
            if(thread != null){
                thread.setStop();
            }
            // DB의 스케줄을 삭제한다.
            if(realtimeSyncRepository.existsById(realtimeSync.getId())){
                realtimeSyncRepository.deleteById(realtimeSync.getId());
            }
            log.info("[DELETE REALTIME SYNC] Delete thread Id : {} ", realtimeSync.getId());
            returnCode = ReturnCode.SUCCESS;
        }catch(Exception e){
            log.error("[DELETE REALTIME SYNC] Occur error : {}", e.getMessage());
            e.printStackTrace();
        }
        return returnCode;
    }

    /**
     * @return ReturnCode.FAIL , ReturnCode.DUPLICATION_DATA , ReturnCode.SUCCESS
     */
    @Transactional
    public ReturnCode stopRealtimeSync(RealtimeSync realtimeSync){
        ReturnCode returnCode = ReturnCode.FAIL;

        try{
            // Thread pool 에서 thread를 가져와 멈춘다.
            RealtimeSyncThread thread = TradeService.popRealtimeSyncThread(realtimeSync.getId());
            if(thread != null){
                thread.setStop();
            }
            // 현재 값을 STOP 으로 변경
            Optional<RealtimeSync> optionalRealtimeSync = realtimeSyncRepository.findById(realtimeSync.getId());
            if(optionalRealtimeSync.isPresent()){
                RealtimeSync savedRealtimeSync = optionalRealtimeSync.get();
                savedRealtimeSync.setStatus(TradeData.STATUS_STOP);
                realtimeSyncRepository.save(savedRealtimeSync);
            }else{
                log.info("[STOP REALTIME SYNC] There is no thread in DB thread id : {} ", realtimeSync.getId());
            }
            returnCode = ReturnCode.SUCCESS;
            log.info("[STOP REALTIME SYNC] Stop thread id : {} ", realtimeSync.getId());
        }catch(Exception e){
            log.error("[STOP REALTIME SYNC] error:  {}",e.getMessage());
            e.printStackTrace();
        }

        return returnCode;
    }
}
