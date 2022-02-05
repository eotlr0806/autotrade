package com.coin.autotrade.service;

import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.model.Fishing;
import com.coin.autotrade.repository.FishingRepository;
import com.coin.autotrade.service.thread.FishingTradeThread;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Service
@Slf4j
public class FishingService {

    @Autowired
    FishingRepository fishingRepository;

    /* fishing List 를 반환하는 메서드 */
    public List<Fishing> getFishing() throws Exception{
        return fishingRepository.findAll();
    }


    /**
     * @return ReturnCode.Fail , ReturnCode.SUCCESS
     */
    @Transactional
    public ReturnCode postFishing(Fishing fishing, String userId){

        ReturnCode returnCode = ReturnCode.FAIL;
        Thread thread         = null;

        try{

            if(Utils.isFishingTradeThread(fishing.getId())){
                log.error("[POST FISHING] Thread is already existed id: {}", fishing.getId());
                returnCode = ReturnCode.DUPLICATION_DATA;
            }else{
                Optional<Fishing> optionalFishing = fishingRepository.findById(fishing.getId());
                if(optionalFishing.isPresent()){
                    Fishing savedFishing = optionalFishing.get();
                    FishingTradeThread fishingTrade = new FishingTradeThread();
                    fishingTrade.setTrade(savedFishing);
                    savedFishing.setStatus(UtilsData.STATUS_RUN);

                    if(Utils.setFishingThread(fishing.getId(), fishingTrade)){   // Thread pool에 넣기
                        fishingRepository.save(savedFishing);
                        thread = new Thread(fishingTrade);
                        Utils.startThread(thread);  // thread start by async mode
                        returnCode = ReturnCode.SUCCESS;
                        log.info("[POST FISHING] Start fishing trade thread id : {} ", fishing.getId());
                    }else{
                        log.error("[POST FISHING] Save fishing trade is failed thread id : {} ", fishing.getId());
                    }
                }
            }
        }catch(Exception e){
            log.error("[POST FISHING] Occur error: {}",e.getMessage());
            e.printStackTrace();

            if(thread != null && thread.isAlive()){
                thread.interrupt();
            }
            Utils.popFishingThread(fishing.getId());
        }
        return returnCode;
    }

    /**
     * @return ReturnCode.FAIL, ReturnCode.SUCCESS
     */
    @Transactional
    public ReturnCode deleteFishing(Fishing fishing){

        ReturnCode returnValue = ReturnCode.FAIL;

        try{
            // Thread pool 에서 thread를 가져와 멈춘다.
            FishingTradeThread thread = Utils.popFishingThread(fishing.getId());
            if(thread != null){
                thread.setStop();
            }

            // DB의 스케줄을 삭제한다.
            if(fishingRepository.existsById(fishing.getId())){
                fishingRepository.deleteById(fishing.getId());
            }
            log.info("[DELETE FISHING] Delete thread thread id : {} ", fishing.getId());
            returnValue = ReturnCode.SUCCESS;
        }catch(Exception e){
            log.error("[DELETE FISHING] Occur error : {}",e.getMessage());
            e.printStackTrace();
        }

        return returnValue;
    }


    /**
     * @return ReturnCode.FAIL, ReturnCode.SUCCESS
     */
    @Transactional
    public ReturnCode stopFishing(Fishing fishing){
        ReturnCode returnCode = ReturnCode.FAIL;

        try{

            // Thread pool 에서 thread를 가져와 멈춘다.
            FishingTradeThread thread = Utils.popFishingThread(fishing.getId());
            if(thread != null) {
                thread.setStop();
            }

            // 현재 값을 STOP 으로 변경
            Optional<Fishing> optionalFishing = fishingRepository.findById(fishing.getId());
            if(optionalFishing.isPresent()){
                Fishing savedFishing = optionalFishing.get();
                savedFishing.setStatus(UtilsData.STATUS_STOP);
                fishingRepository.save(savedFishing);
            }else{
                log.info("[STOP FISHING] There is no thread in DB thread id : {}", fishing.getId());
            }
            returnCode = ReturnCode.SUCCESS;
            log.info("[STOP FISHING] Stop thread id : {} ", fishing.getId());

        }catch(Exception e){
            log.error("[STOP FISHING] Occur error : {}",e.getMessage());
            e.printStackTrace();
        }
        return returnCode;
    }
}
