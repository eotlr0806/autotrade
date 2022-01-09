package com.coin.autotrade.service.thread;

import com.coin.autotrade.common.BeanUtils;
import com.coin.autotrade.common.TradeService;
import com.coin.autotrade.model.Liquidity;
import com.coin.autotrade.model.User;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.repository.LiquidityRepository;
import com.coin.autotrade.service.CoinService;
import com.coin.autotrade.service.exchangeimp.AbstractExchange;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Liquidity Trade Thread
 * DESC : 호가 유동성 쓰레드
 */
@org.springframework.stereotype.Service
@Slf4j
public class LiquidityTradeThread implements Runnable{


    CoinService         coinService;
    LiquidityRepository liquidityRepository;
    AbstractExchange abstractExchange;
    boolean run          = true;
    Liquidity liquidity  = null;

    /**
     * 해당 쓰레드에 필요한 초기값 셋팅
     * @param liquidity
     * @param user
     */
    public void setTrade(Liquidity inputLiquidity) throws Exception{
        coinService         = (CoinService) BeanUtils.getBean(CoinService.class);
        liquidityRepository = (LiquidityRepository) BeanUtils.getBean(LiquidityRepository.class);

        liquidity           = inputLiquidity;
        abstractExchange    = TradeService.getInstance(liquidity.getExchange().getExchangeCode());
        abstractExchange.initClass(liquidity);
    }

    // Stop thread
    public void setStop(){
        run = false;
    }

    @Override
    public void run() {
        try{
            int intervalTime = 100;
            while(run){
                /** Check is there best offer */
                Map list = coinService.getLiquidityList(liquidity);
                /** Start Liquidity Thread **/
                abstractExchange.startLiquidity(list);

                intervalTime = TradeService.getRandomInt(liquidity.getMinSeconds(), liquidity.getMaxSeconds()) * 1000;
                log.info("[LIQUIDITY THREAD] Run thread , intervalTime : {} seconds", intervalTime/1000);
                Thread.sleep(intervalTime);
            }
        }catch(Exception e){
            log.error("[LIQUIDITY THREAD] Run is failed {}", e.getMessage());
            e.printStackTrace();
        }
    }

}
