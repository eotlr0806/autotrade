package com.coin.autotrade.service;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.model.Fishing;
import com.coin.autotrade.model.User;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.repository.FishingRepository;
import com.coin.autotrade.repository.UserRepository;
import com.coin.autotrade.service.thread.FishingTradeThread;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class FishingService {

    @Autowired
    FishingRepository fishingRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ExchangeRepository exchangeRepository;

    Gson gson = new Gson();

    /* fishing List 를 반환하는 메서드 */
    public String getFishing(){
        String returnVal = ReturnCode.NO_DATA.getValue();
        try{
            List<Fishing> fishingList = fishingRepository.findAll();
            if(!fishingList.isEmpty()){
                returnVal = gson.toJson(fishingList);
            }
        }catch(Exception e){
            returnVal = ReturnCode.FAIL.getValue();
            log.error("[GET FISHING] Occur error : {}",e.getMessage());
            e.printStackTrace();
        }
        return returnVal;
    }


    /**
     * @return ReturnCode.Fail , ReturnCode.SUCCESS
     */
    @Transactional
    public String postFishing(Fishing fishing, String userId){

        String returnValue = ReturnCode.FAIL.getValue();
        Thread thread      = null;

        try{

            if(ServiceCommon.isFishingTradeThread(fishing.getId())){
                log.error("[POST FISHING] Thread is already existed id: {}", fishing.getId());
                returnValue = ReturnCode.DUPLICATION_DATA.getValue();
            }else{
                Fishing savedFishing = fishingRepository.getById(fishing.getId());
                savedFishing.setStatus(DataCommon.STATUS_RUN);
                User user         = userRepository.findByUserId(userId);
                Exchange exchange = exchangeRepository.findByexchangeCode(savedFishing.getExchange());

                FishingTradeThread fishingTrade = new FishingTradeThread();
                fishingTrade.initClass(savedFishing, user, exchange);

                if(ServiceCommon.setFishingThread(fishing.getId(), fishingTrade)){   // Thread pool에 넣기
                    fishingRepository.save(savedFishing);
                    thread = new Thread(fishingTrade);
                    ServiceCommon.startThread(thread);  // thread start by async mode
                    returnValue = ReturnCode.SUCCESS.getValue();
                    log.info("[POST FISHING] Start fishing trade thread id : {} ", fishing.getId());
                }else{
                    log.error("[POST FISHING] Save fishing trade is failed thread id : {} ", fishing.getId());
                }
            }
        }catch(Exception e){
            if(thread.isAlive()){
                thread.interrupt();
            }
            ServiceCommon.popFishingThread(fishing.getId());

            log.error("[POST FISHING] Occur error: {}",e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }

    /**
     * @return ReturnCode.FAIL, ReturnCode.SUCCESS
     */
    @Transactional
    public String deleteFishing(Fishing fishing){

        String returnValue = ReturnCode.FAIL.getValue();

        try{
            // Thread pool 에서 thread를 가져와 멈춘다.
            FishingTradeThread thread = ServiceCommon.popFishingThread(fishing.getId());
            if(thread != null){
                thread.setStop();
            }

            // DB의 스케줄을 삭제한다.
            if(fishingRepository.existsById(fishing.getId())){
                fishingRepository.deleteById(fishing.getId());
            }
            log.info("[DELETE FISHING] Delete thread thread id : {} ", fishing.getId());
            returnValue = ReturnCode.SUCCESS.getValue();
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
    public String stopFishing(Fishing fishing){
        String returnValue = ReturnCode.FAIL.getValue();

        try{

            // Thread pool 에서 thread를 가져와 멈춘다.
            FishingTradeThread thread = ServiceCommon.popFishingThread(fishing.getId());
            if(thread != null) {
                thread.setStop();
            }

            // 현재 값을 STOP 으로 변경
            Optional<Fishing> optionalFishing = fishingRepository.findById(fishing.getId());
            if(optionalFishing.isPresent()){
                Fishing savedFishing = optionalFishing.get();
                savedFishing.setStatus(DataCommon.STATUS_STOP);
                fishingRepository.save(savedFishing);
            }else{
                log.info("[STOP FISHING] There is no thread in DB thread id : {}", fishing.getId());
            }
            returnValue = ReturnCode.SUCCESS.getValue();
            log.info("[STOP FISHING] Stop thread id : {} ", fishing.getId());

        }catch(Exception e){
            log.error("[STOP FISHING] Occur error : {}",e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }
}
