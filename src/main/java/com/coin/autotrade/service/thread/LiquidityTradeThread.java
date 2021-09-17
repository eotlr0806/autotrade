package com.coin.autotrade.service.thread;

import com.coin.autotrade.common.BeanUtils;
import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.model.Liquidity;
import com.coin.autotrade.model.User;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.repository.LiquidityRepository;
import com.coin.autotrade.service.CoinService;
import com.coin.autotrade.service.function.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Liquidity Trade Thread
 * DESC : 호가 유동성 쓰레드
 */
@Service
@Slf4j
public class LiquidityTradeThread implements Runnable{


    CoinService         coinService;
    LiquidityRepository liquidityRepository;
    ExchangeRepository  exchangeRepository;
    ExchangeFunction exchangeFunction;
    boolean run                 = true;
    Liquidity liquidity         = null;

    /**
     * 해당 쓰레드에 필요한 초기값 셋팅
     * @param liquidity
     * @param user
     */
    public void initClass(Liquidity inputLiquidity, User inputUser, Exchange exchange){
        liquidity           = inputLiquidity;
        coinService         = (CoinService) BeanUtils.getBean(CoinService.class);
        liquidityRepository = (LiquidityRepository) BeanUtils.getBean(LiquidityRepository.class);
        exchangeRepository  = (ExchangeRepository) BeanUtils.getBean(ExchangeRepository.class);

        initExchangeValue(inputLiquidity, inputUser, exchange);
        return;
    }

    /**
     * 각 거래소 정보들 초기값 셋팅
     * @param user
     */
    public void initExchangeValue(Liquidity liquidity, User user, Exchange exchange){
        try{
            exchangeFunction = ServiceCommon.initExchange(liquidity.getExchange());
            exchangeFunction.initClass(liquidity, user, exchange);

        }catch (Exception e){
            log.error("[ERROR][LIQUIDITY THREAD INIT ERROR] error : {}" , e.getMessage());
        }
        return;
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
                exchangeFunction.startLiquidity(list);

                intervalTime = ServiceCommon.getRandomInt(liquidity.getMinSeconds(), liquidity.getMaxSeconds()) * 1000;
                log.info("[LIQUIDITY THREAD][START] , intervalTime : {} seconds", intervalTime/1000);
                Thread.sleep(intervalTime);
            }
        }catch(Exception e){
            log.error("[ERROR][LIQUIDITY THREAD] Run is failed {}", e.getMessage());
        }
    }

}
