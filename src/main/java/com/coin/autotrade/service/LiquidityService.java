package com.coin.autotrade.service;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.common.code.ReturnCode;
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
import java.util.Optional;

@Service
@Slf4j
public class LiquidityService {

    @Autowired
    LiquidityRepository liquidityRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ExchangeRepository exchangeRepository;

    Gson gson = new Gson();

    /* liquidity List 를 반환하는 메서드 */
    public String getLiquidity(){
        String returnVal = ReturnCode.NO_DATA.getValue();
        try{
            List<Liquidity> liquidityList = liquidityRepository.findAll();
            if(!liquidityList.isEmpty()){
                returnVal = gson.toJson(liquidityList);
            }
        }catch(Exception e){
            returnVal = ReturnCode.FAIL.getValue();
            log.error("[GET LIQUIDITY] Occur error : {}",e.getMessage());
            e.printStackTrace();
        }
        return returnVal;
    }


    // Start schedule
    @Transactional
    public String postLiquidity(Liquidity liquidity, String userId){

        String returnValue = ReturnCode.FAIL.getValue();
        Thread thread      = null;

        try{
            if(ServiceCommon.isLiquidityTradeThread(liquidity.getId())){
                log.error("[POST LIQUIDITY] Thread is already existed id: {}", liquidity.getId());
                returnValue = ReturnCode.DUPLICATION_DATA.getValue();
            }else{
                Liquidity savedLiquidity = liquidityRepository.getById(liquidity.getId());
                savedLiquidity.setStatus(DataCommon.STATUS_RUN);
                User user         = userRepository.findByUserId(userId);
                Exchange exchange = exchangeRepository.findByexchangeCode(savedLiquidity.getExchange());

                LiquidityTradeThread liquidityTrade = new LiquidityTradeThread();
                liquidityTrade.initClass(savedLiquidity, user, exchange);

                if(ServiceCommon.setLiquidityThread(liquidity.getId(),liquidityTrade)){   // Thread pool에 넣기 성공
                    liquidityRepository.save(savedLiquidity);                             // DB에 해당 liquidity 저장
                    thread = new Thread(liquidityTrade);
                    ServiceCommon.startThread(thread);                                   // thread start by async mode
                    returnValue = ReturnCode.SUCCESS.getValue();
                    log.info("[POST LIQUIDITY] Start liquidity thread id : {} ", liquidity.getId());
                }else{
                    log.error("[POST LIQUIDITY] Save liquidity is failed thread id : {} ", liquidity.getId());
                }
            }
        }catch(Exception e){
            if(thread.isAlive()){
                thread.interrupt();
            }
            ServiceCommon.popLiquidityThread(liquidity.getId());

            log.error("[POST LIQUIDITY] Occur error : {}",e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }

    // Delete schedule
    @Transactional
    public String deleteLiquidity(Liquidity liquidity){

        String returnValue = ReturnCode.FAIL.getValue();
        try{
            // Thread pool 에서 thread를 가져와 멈춘다.
            LiquidityTradeThread thread = ServiceCommon.popLiquidityThread(liquidity.getId());
            if(thread != null){
                thread.setStop();
            }

            if(liquidityRepository.existsById(liquidity.getId())){
                liquidityRepository.deleteById(liquidity.getId());
            }
            returnValue = ReturnCode.SUCCESS.getValue();
            log.info("[DELETE LIQUIDITY] Delete liquidity thread id : {} ", liquidity.getId());
        }catch(Exception e){
            log.error("[DELETE LIQUIDITY] Occur error : {}",e.getMessage());
            e.printStackTrace();
        }

        return returnValue;
    }


    // Delete schedule
    @Transactional
    public String stopLiquidity(Liquidity liquidity){
        String returnValue = ReturnCode.FAIL.getValue();

        try{

            // Thread pool 에서 thread를 가져와 멈춘다.
            LiquidityTradeThread thread = ServiceCommon.popLiquidityThread(liquidity.getId());
            if(thread != null) {
                thread.setStop();
            }

            Optional<Liquidity> optionalLiquidity = liquidityRepository.findById(liquidity.getId());
            if(optionalLiquidity.isPresent()){
                Liquidity savedLiquidity = optionalLiquidity.get();
                savedLiquidity.setStatus(DataCommon.STATUS_STOP);
                liquidityRepository.save(savedLiquidity);
            }else{
                log.info("[STOP LIQUIDITY] There is no thread in DB thread id : {}", liquidity.getId());
            }
            returnValue = ReturnCode.SUCCESS.getValue();
            log.info("[STOP LIQUIDITY] Stop thread id : {}", liquidity.getId());

        }catch(Exception e){
            log.error("[STOP LIQUIDITY] Occur error : {}",e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }
}
