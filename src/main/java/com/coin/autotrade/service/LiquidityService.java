package com.coin.autotrade.service;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.AutoTrade;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.model.Liquidity;
import com.coin.autotrade.model.User;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.repository.LiquidityRepository;
import com.coin.autotrade.repository.UserRepository;
import com.coin.autotrade.service.thread.LiquidityTradeThread;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;

@Service
@Slf4j
public class LiquidityService {

    @Autowired
    LiquidityRepository liquidityRepository;

    @Autowired
    CoinService coinService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ExchangeRepository exchangeRepository;

    /**
     * liquidity List 를 반환하는 메서드
     * @return
     */
    public String getLiquidity(){
        String returnVal = "";
        try{
            List<Liquidity> liquidityList = liquidityRepository.findAll();
            if(liquidityList.size() < 1){
                String msg = "msg:There is no active liquidity";
                returnVal =  ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);
            }else{
                Gson gson = new Gson();
                returnVal = gson.toJson(liquidityList);
            }

        }catch(Exception e){
            log.error("[ERROR][Get Liquidity Trade] {}",e.getMessage());
        }

        return returnVal;
    }


    // Start schedule
    @Transactional
    public String postLiquidity(Liquidity liquidity, String userId){

        String returnValue = "";
        String msg         = "";
        Thread thread      = null;
        Long id            = liquidity.getId();

        try{
            log.info("[LiquidityTradeService - Start] ThreadId : {} ", id);

            Gson gson = new Gson();

            Liquidity savedLiquidity = liquidityRepository.getById(id);
            savedLiquidity.setStatus(DataCommon.STATUS_RUN);
            User user         = userRepository.findByUserId(userId);
            Exchange exchange = exchangeRepository.findByexchangeCode(savedLiquidity.getExchange());

            // Thread 생성
            LiquidityTradeThread liquidityTrade = new LiquidityTradeThread();
            liquidityTrade.initClass(savedLiquidity, user, exchange);
            thread = new Thread(liquidityTrade);

            if(ServiceCommon.setLiquidityThread(id,liquidityTrade)){   // Thread pool에 넣기 성공
                liquidityRepository.save(savedLiquidity);              // DB에 해당 liquidity 저장
                msg = "msg:start thread, id:" + id;
                returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_SUCCESS, msg);
            }else{                                              // 실패
                liquidityTrade.setStop();
                msg = "msg:can not push thread object in list";
                returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);
            }
            ServiceCommon.startThread(thread);  // thread start by async mode
        }catch(Exception e){
            if(thread.isAlive()){
                thread.interrupt();
            }
            if(!msg.equals("")){
                ServiceCommon.getLiquidityThread(id);
            }
            log.error("[ERROR][Post Liquidity Trade] {}",e.getMessage());
        }
        return returnValue;
    }

    // Delete schedule
    public String deleteLiquidity(Liquidity liquidity){
        String msg         = "";
        String returnValue = "";

        try{
            log.info("[LiquidityTradeService - Delete] Thread Id : {} ", liquidity.getId());

            // Thread pool 에서 thread를 가져와 멈춘다.
            LiquidityTradeThread thread = ServiceCommon.getLiquidityThread(liquidity.getId());
            if(thread != null){
                thread.setStop();
                log.info("[SUCCESS][LiquidityTradeService - Delete Thread] Thread Id : {} ", liquidity.getId());
            }else{
                log.error("[ERROR][LiquidityTradeService - Delete] No Thread Id : {} ", liquidity.getId());
            }
            // DB의 스케줄을 삭제한다.
            if(liquidityRepository.existsById(liquidity.getId())){
                liquidityRepository.deleteById(liquidity.getId());
                log.info("[SUCCESS][LiquidityTradeService - Delete Data] Thread Id : {} ", liquidity.getId());
            }
        }catch(Exception e){
            msg = "msg:fail delete thread, id:" + liquidity.getId();
            returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);

            log.error("[ERROR][Delete Liquidity Trade] {}",e.getMessage());
        }
        msg = "msg:success delete thread, id:" + liquidity.getId();
        returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_SUCCESS, msg);

        return returnValue;
    }


    // Delete schedule
    @Transactional
    public String stopLiquidity(Liquidity liquidity){
        String msg         = "";
        String returnValue = "";

        try{
            log.info("[LiquidityTradeService - Stop] Thread Id : {} ", liquidity.getId());

            // Thread pool 에서 thread를 가져와 멈춘다.
            LiquidityTradeThread thread = ServiceCommon.getLiquidityThread(liquidity.getId());
            if(thread != null){
                thread.setStop();
                log.info("[SUCCESS][LiquidityTradeService - Stop Thread] Thread Id : {} ", liquidity.getId());
            }else{
                log.error("[ERROR][AutoTradeService - Stop] No Thread Id : {} ", liquidity.getId());
            }

            // 현재 값을 STOP 으로 변경
            Liquidity savedLiquidity = liquidityRepository.getById(liquidity.getId());
            savedLiquidity.setStatus(DataCommon.STATUS_STOP);
            liquidityRepository.save(savedLiquidity);
        }catch(Exception e){
            msg = "msg:fail stop thread, id:" + liquidity.getId();
            returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);

            log.error("[ERROR][Stop Liquidity Trade] {}",e.getMessage());
        }
        msg = "msg:success stop thread, id:" + liquidity.getId();
        returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_SUCCESS, msg);

        return returnValue;
    }
}
