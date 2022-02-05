package com.coin.autotrade.service.thread;

import com.coin.autotrade.common.BeanUtils;
import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.model.AutoTrade;
import com.coin.autotrade.repository.AutoTradeRepository;
import com.coin.autotrade.service.CoinService;
import com.coin.autotrade.service.exchangeimp.AbstractExchange;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto Trade Thread
 * DESC : 자전거래용 쓰레드
 */
@org.springframework.stereotype.Service
@Slf4j
public class AutoTradeThread implements Runnable{


    CoinService         coinService;
    AutoTradeRepository autotradeRepository;
    AbstractExchange abstractExchange;

    boolean run                  = true;
    AutoTrade autoTrade          = null;
    private String NO_BEST_OFFER = "NO_BEST_OFFER";

    /** 해당 쓰레드에 필요한 초기값 셋팅 */
    public void setTrade(AutoTrade inputAutoTrade) throws Exception{
        /** New 로 쓰레드 생성 시, 인젝션 주입. */
        coinService         = (CoinService)         BeanUtils.getBean(CoinService.class);
        autotradeRepository = (AutoTradeRepository) BeanUtils.getBean(AutoTradeRepository.class);

        autoTrade           = inputAutoTrade;
        abstractExchange    = Utils.getInstance(autoTrade.getExchange().getExchangeCode());
        abstractExchange.initClass(autoTrade);
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
                        log.info("[AUTOTRADE THREAD] Restart because find best offer");
                        autotradeRepository.save(autoTrade);
                    }
                    /** make cnt random value */
                    String cnt   = Utils.getRandomString(autoTrade.getMinCnt(), autoTrade.getMaxCnt());
                    /** Auto Trade start **/
                    abstractExchange.startAutoTrade(price, cnt);
                }

                intervalTime = Utils.getRandomInt(autoTrade.getMinSeconds(), autoTrade.getMaxSeconds()) * 1000;
                log.info("[AUTOTRADE THREAD] Run thread , intervalTime : {} seconds", intervalTime/1000);
                Thread.sleep(intervalTime);
            }
        }catch(Exception e){
            log.error("[AUTOTRADE THREAD] Run is failed {}", e.getMessage());
            e.printStackTrace();
        }
    }

    // Stop thread
    public void setStop(){  run = false; }

    // best offer가 없어서 멈춤
    private void setTempStopNoBestOffer () throws Exception{
        // DB의 스케줄을 STOP으로
        if(!autoTrade.getStatus().equals(NO_BEST_OFFER)){
            autoTrade.setStatus(NO_BEST_OFFER);
            autotradeRepository.save(autoTrade);
        }
        log.info("[AUTOTRADE THREAD] Temporarily Stop , There is no best offer");
    }
}
