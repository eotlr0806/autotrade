package com.coin.autotrade.service;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
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
            List<Liquidity> LiquidityList = liquidityRepository.findAll();
            if(LiquidityList.size() < 1){
                String msg = "msg:There is no active liquidity";
                returnVal =  ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);
            }else{
                // TODO
                Gson gson = new Gson();
                returnVal = gson.toJson(LiquidityList);
            }

        }catch(Exception e){
            System.out.println("getLiquidity:" + e.getMessage());
        }

        return returnVal;
    }


    // Start schedule
    public String postLiquidity(Liquidity liquidity, String userId){

        String returnValue = "";
        String msg         = "";
        Thread thread      = null;
        Long id            = null;

        try{
            Gson gson = new Gson();
            log.info("[LiquidityTradeService - Start] Data : {} ", gson.toJson(liquidity));

            User user         = userRepository.findByUserId(userId);
            Exchange exchange = exchangeRepository.findByexchangeCode(liquidity.getExchange());
            // JPA 테이블에 max schedule 값 반환
            id = liquidityRepository.selectMaxId();
            liquidity.setId(id);
            liquidity.setDate(ServiceCommon.getNowData());

            // Thread 생성
            LiquidityTradeThread liquidityTrade = new LiquidityTradeThread();
            liquidityTrade.initClass(liquidity, user, exchange);
            thread = new Thread(liquidityTrade);

            if(ServiceCommon.setLiquidityThread(id,liquidityTrade)){   // Thread pool에 넣기 성공
                liquidityRepository.save(liquidity);              // DB에 해당 liquidity 저장
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
            System.out.println(e.getMessage());
        }
        return returnValue;
    }

    // Stop schedule
    public String deleteLiquidity(Liquidity liquidity){
        String msg         = "";
        String returnValue = "";

        try{
            // Thread pool 에서 thread를 가져와 멈춘다.
            LiquidityTradeThread thread = ServiceCommon.getLiquidityThread(liquidity.getId());
            if(thread != null){
                thread.setStop();
            }
            // DB의 스케줄을 삭제한다.
            if(liquidityRepository.existsById(liquidity.getId())){
                liquidityRepository.deleteById(liquidity.getId());
            }
        }catch(Exception e){
            msg = "msg:fail stop thread, id:" + liquidity.getId();
            returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_ERROR, msg);

            System.out.println(e.getMessage());
        }
        msg = "msg:success stop thread, id:" + liquidity.getId();
        returnValue = ServiceCommon.makeReturnValue(DataCommon.CODE_SUCCESS, msg);

        return returnValue;
    }
}
