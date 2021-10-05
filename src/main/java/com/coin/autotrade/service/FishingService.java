package com.coin.autotrade.service;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
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

@Service
@Slf4j
public class FishingService {

    @Autowired
    FishingRepository fishingRepository;

    @Autowired
    CoinService coinService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ExchangeRepository exchangeRepository;

    /**
     * fishing List 를 반환하는 메서드
     * @return
     */
    public String getFishing(){
        String returnVal = "";
        try{
            List<Fishing> fishingList = fishingRepository.findAll();
            if(fishingList.size() < 1){
                String msg = "msg:There is no active liquidity";
                returnVal =  ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);
            }else{
                // TODO
                Gson gson = new Gson();
                returnVal = gson.toJson(fishingList);
            }

        }catch(Exception e){
            log.error("[ERROR][Get Fishing Trade] {}",e.getMessage());
        }

        return returnVal;
    }


    // Start schedule
    @Transactional
    public String postFishing(Fishing fishing, String userId){

        String returnValue = "";
        String msg         = "";
        Thread thread      = null;
        Long id            = fishing.getId();

        try{
            Gson gson = new Gson();
            log.info("[FishingTradeService - Start] Thread Id : {} ", id);

            Fishing savedFishing = fishingRepository.getById(id);
            savedFishing.setStatus(DataCommon.STATUS_RUN);
            User user         = userRepository.findByUserId(userId);
            Exchange exchange = exchangeRepository.findByexchangeCode(savedFishing.getExchange());

            // Thread 생성
            FishingTradeThread fishingTrade = new FishingTradeThread();
            fishingTrade.initClass(savedFishing, user, exchange);
            thread = new Thread(fishingTrade);

            if(ServiceCommon.setFishingThread(id,fishingTrade)){   // Thread pool에 넣기 성공
                fishingRepository.save(savedFishing);              // DB에 해당 liquidity 저장
                msg = "msg:start thread, id:" + id;
                returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_SUCCESS, msg);
            }else{                                              // 실패
                fishingTrade.setStop();
                msg = "msg:can not push thread object in list";
                returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);
            }
            ServiceCommon.startThread(thread);  // thread start by async mode
        }catch(Exception e){
            if(thread.isAlive()){
                thread.interrupt();
            }
            if(!msg.equals("")){
                ServiceCommon.getFishingThread(id);
            }
            log.error("[ERROR][Post Fishing Trade] {}",e.getMessage());
        }
        return returnValue;
    }

    // Stop schedule
    public String deleteFishing(Fishing fishing){
        String msg         = "";
        String returnValue = "";

        try{
            log.info("[FishingTradeService - Delete] Thread Id : {} ", fishing.getId());

            // Thread pool 에서 thread를 가져와 멈춘다.
            FishingTradeThread thread = ServiceCommon.getFishingThread(fishing.getId());
            if(thread != null){
                thread.setStop();
                log.info("[SUCCESS][FishingTradeService - Delete Thread] Thread Id : {} ", fishing.getId());
            }else{
                log.error("[ERROR][FishingTradeService - Delete] No Thread Id : {} ", fishing.getId());
            }

            // DB의 스케줄을 삭제한다.
            if(fishingRepository.existsById(fishing.getId())){
                fishingRepository.deleteById(fishing.getId());
                log.info("[SUCCESS][FishingTradeService - Delete Data] Thread Id : {} ", fishing.getId());
            }
        }catch(Exception e){
            msg = "msg:fail delete thread, id:" + fishing.getId();
            returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);

            log.error("[ERROR][Delete Fishing Trade] {}",e.getMessage());
        }
        msg = "msg:success delete thread, id:" + fishing.getId();
        returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_SUCCESS, msg);

        return returnValue;
    }


    // Stop schedule
    @Transactional
    public String stopFishing(Fishing fishing){
        String msg         = "";
        String returnValue = "";

        try{
            log.info("[FishingTradeService - Stop] Thread Id : {} ", fishing.getId());

            // Thread pool 에서 thread를 가져와 멈춘다.
            FishingTradeThread thread = ServiceCommon.getFishingThread(fishing.getId());
            if(thread != null){
                thread.setStop();
                log.info("[SUCCESS][FishingTradeService - Stop Thread] Thread Id : {} ", fishing.getId());
            }else{
                log.error("[ERROR][FishingTradeService - Stop] No Thread Id : {} ", fishing.getId());
            }

            // 현재 값을 STOP 으로 변경
            Fishing savedFishing = fishingRepository.getById(fishing.getId());
            savedFishing.setStatus(DataCommon.STATUS_STOP);
            fishingRepository.save(savedFishing);

        }catch(Exception e){
            msg = "msg:fail stop thread, id:" + fishing.getId();
            returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);

            log.error("[ERROR][Stop Fishing Trade] {}",e.getMessage());
        }
        msg = "msg:success stop thread, id:" + fishing.getId();
        returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_SUCCESS, msg);

        return returnValue;
    }
}
