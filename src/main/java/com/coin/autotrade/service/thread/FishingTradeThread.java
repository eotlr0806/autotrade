package com.coin.autotrade.service.thread;

import com.coin.autotrade.common.BeanUtils;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.model.Fishing;
import com.coin.autotrade.repository.FishingRepository;
import com.coin.autotrade.service.CoinService;
import com.coin.autotrade.service.exchangeimp.AbstractExchange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fishing Trade Thread
 * DESC : 매매긁기 쓰레드
 */
@Service
@Slf4j
public class FishingTradeThread implements Runnable{


    CoinService           coinService;
    FishingRepository     fishingRepository;
    AbstractExchange abstractExchange;

    boolean run                  = true;
    Fishing fishing              = null;
    private String NO_BEST_OFFER = "NO_BEST_OFFER";
    /**
     * 해당 쓰레드에 필요한 초기값 셋팅
     * @param fishing
     * @param user
     */
    public void setTrade(Fishing inputFishing) throws Exception{
        coinService         = (CoinService) BeanUtils.getBean(CoinService.class);
        fishingRepository   = (FishingRepository) BeanUtils.getBean(FishingRepository.class);

        fishing             = inputFishing;
        abstractExchange    = Utils.getInstance(fishing.getExchange().getExchangeCode());
        abstractExchange.initClass(fishing, coinService);
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
                Map<String, List> list = coinService.getFishingList(fishing);
                if(!list.isEmpty()){    // 아무런 key도 없을 경우.
                    intervalTime = Utils.getRandomInt(fishing.getMinSeconds(), fishing.getMaxSeconds()) * 1000;

                    ArrayList<String> tickList = new ArrayList<>();
                    for(String mode : list.keySet()){   // sell or buy 1개만 들어가 있음.
                        tickList = (ArrayList) list.get(mode);
                    }

                    if(tickList.size() < 1){
                        setTempStopNoBestOffer();
                    }else{
                        if(!fishing.getStatus().equals("RUN")){
                            fishing.setStatus("RUN");
                            log.info("[FISHINGTRADE THREAD] Restart because find best offer");
                            fishingRepository.save(fishing);
                        }

                        /** Start Fishing Thread **/
                        abstractExchange.startFishingTrade(list, intervalTime);
                    }
                }
                log.info("[FISHING THREAD] Run thread , intervalTime : {} seconds", intervalTime/1000);
                Thread.sleep(intervalTime);
            }
        }catch(Exception e){
            log.error("[FISHING THREAD] Run is failed {}", e.getMessage());
            e.printStackTrace();
        }
    }


    // best offer가 없어서 멈춤
    private void setTempStopNoBestOffer () throws Exception{
        // DB의 스케줄을 STOP으로
        if(!fishing.getStatus().equals(NO_BEST_OFFER)){
            fishing.setStatus(NO_BEST_OFFER);
            fishingRepository.save(fishing);
        }
        log.info("[FISHINGTRADE THREAD] Temporarily Stop , There is no best offer");
    }
}
