package com.coin.autotrade.service.thread;

import com.coin.autotrade.common.BeanUtils;
import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.AutoTrade;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.model.User;
import com.coin.autotrade.repository.AutoTradeRepository;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.service.CoinService;
import com.coin.autotrade.service.function.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Auto Trade Thread
 * DESC : 자전거래용 쓰레드
 */
@Service
@Slf4j
public class AutoTradeThread implements Runnable{


    CoinService         coinService;
    AutoTradeRepository autotradeRepository;
    ExchangeRepository  exchangeRepository;
    ExchangeFunction exchangeFunction;

    boolean run                  = true;
    AutoTrade autoTrade          = null;
    private String NO_BEST_OFFER = "NO_BEST_OFFER";

    /** 해당 쓰레드에 필요한 초기값 셋팅 */
    public void initClass(AutoTrade inputAutoTrade, User inputUser, Exchange exchange){
        /** New 로 쓰레드 생성 시, 인젝션 주입. */
        autoTrade           = inputAutoTrade;
        coinService         = (CoinService)         BeanUtils.getBean(CoinService.class);
        autotradeRepository = (AutoTradeRepository) BeanUtils.getBean(AutoTradeRepository.class);
        exchangeRepository  = (ExchangeRepository)  BeanUtils.getBean(ExchangeRepository.class);

        // 거래소 셋팅
        initExchangeValue(inputAutoTrade, inputUser, exchange);
        return;
    }

    /** 각 거래소 정보들 초기값 셋팅 */
    public void initExchangeValue(AutoTrade autoTrade, User user, Exchange exchange){
        try{
            exchangeFunction = ServiceCommon.initExchange(autoTrade.getExchange());
            exchangeFunction.initClass(autoTrade, user, exchange);
        }catch (Exception e){
            log.error("[ERROR][AUTOTRADE THREAD INIT ERROR] error : {}" , e.getMessage());
        }
        return;
    }

    // best offer가 없어서 멈춤
    public void setTempStopNoBestOffer () {
        // DB의 스케줄을 STOP으로
        if(!autoTrade.getStatus().equals(NO_BEST_OFFER)){
            autoTrade.setStatus(NO_BEST_OFFER);
            autotradeRepository.save(autoTrade);
        }
        log.info("[AUTOTRADE THREAD] Temporarily Stop , There is no best offer");
    }


    @Override
    public void run() {
        try{
            int intervalTime = 100;
            while(run){

                /** Check is there best offer */
                String price = coinService.getBestOffer(autoTrade);
                if("false".equals(price)){
                    setTempStopNoBestOffer();
                }else{
                    if(!autoTrade.getStatus().equals("RUN")){
                        autoTrade.setStatus("RUN");
                        log.info("[AUTOTRADE THREAD][RESTART] Find best offer");
                        autotradeRepository.save(autoTrade);
                    }
                    /** make cnt random value */
                    String cnt   = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double) autoTrade.getMinCnt(), (double) autoTrade.getMaxCnt()) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                    /** Auto Trade start **/
                    exchangeFunction.startAutoTrade(price, cnt);
                }

                intervalTime = ServiceCommon.getRandomInt(autoTrade.getMinSeconds(), autoTrade.getMaxSeconds()) * 1000;
                log.info("[AUTOTRADE THREAD][START] , intervalTime : {} seconds", intervalTime/1000);
                Thread.sleep(intervalTime);
            }
        }catch(Exception e){
            log.error("[ERROR][AUTOTRADE THREAD] Run is failed {}", e.getMessage());
        }
    }

    // Stop thread
    public void setStop(){  run = false; }
}
