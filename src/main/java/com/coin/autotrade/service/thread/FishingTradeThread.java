package com.coin.autotrade.service.thread;

import com.coin.autotrade.common.BeanUtils;
import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.model.Fishing;
import com.coin.autotrade.model.User;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.repository.FishingRepository;
import com.coin.autotrade.service.CoinService;
import com.coin.autotrade.service.function.*;
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
    ExchangeRepository    exchangeRepository;
    CoinOneFunction       coinOne;
    DcoinFunction         dCoin;
    FlataFunction         flata;
    FoblGateFunction      foblGate;
    BithumbGlobalFunction bithumbGlobal;

    boolean run                  = true;
    Fishing fishing              = null;
    private String NO_BEST_OFFER = "NO_BEST_OFFER";
    /**
     * 해당 쓰레드에 필요한 초기값 셋팅
     * @param fishing
     * @param user
     */
    public void initClass(Fishing inputFishing, User inputUser, Exchange exchange){
        fishing             = inputFishing;
        coinService         = (CoinService) BeanUtils.getBean(CoinService.class);
        fishingRepository   = (FishingRepository) BeanUtils.getBean(FishingRepository.class);
        exchangeRepository  = (ExchangeRepository) BeanUtils.getBean(ExchangeRepository.class);

        initExchangeValue(inputFishing, inputUser, exchange);
        return;
    }

    /**
     * 각 거래소 정보들 초기값 셋팅
     * @param user
     */
    public void initExchangeValue(Fishing fishing, User user, Exchange exchange){
        try{

            /** Coin one **/
            if(DataCommon.COINONE.equals(fishing.getExchange())){
                coinOne = new CoinOneFunction();
                coinOne.initCoinOne(fishing, user, exchange, coinService);
            }
            /** FoblGate **/
            else if(DataCommon.FOBLGATE.equals(fishing.getExchange())){
                foblGate = new FoblGateFunction();
                foblGate.initFoblGate(fishing, user, exchange, coinService);
            }
            /** Flata **/
            else if(DataCommon.FLATA.equals(fishing.getExchange())){
                flata = new FlataFunction();
                flata.initFlata(fishing, user, exchange, coinService);
            }
            /** Dcoin **/
            else if(DataCommon.DCOIN.equals(fishing.getExchange())){
                dCoin = new DcoinFunction();
                dCoin.initDcoin(fishing, user, exchange, coinService);
            }
            /** BithumGlobal **/
            else if(DataCommon.BITHUMB_GLOBAL.equals(fishing.getExchange())){
                bithumbGlobal = new BithumbGlobalFunction();
                bithumbGlobal.initBithumbGlobal(fishing, user, exchange, coinService);
            }

        }catch (Exception e){
            log.error("[Fishing Start fail][ERROR] exchange : {} ", exchange.getExchangeCode());
        }
        return;
    }


    // best offer가 없어서 멈춤
    public void setTempStopNoBestOffer () {
        // DB의 스케줄을 STOP으로
        if(!fishing.getStatus().equals(NO_BEST_OFFER)){
            fishing.setStatus(NO_BEST_OFFER);
            fishingRepository.save(fishing);
        }
        log.info("[FishingTrade-Thread] temporarily Stop , There is no best offer");
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

                // 매도 매수에 따른 값 설정
                /** Check is there best offer */
                Map<String, List> list     = coinService.getFishingList(fishing);
                intervalTime = ServiceCommon.getRandomInt(fishing.getMinSeconds(), fishing.getMaxSeconds()) * 1000;

                String mode = "";
                for(String temp : list.keySet()){  mode = temp; }
                ArrayList<String> tickList = (ArrayList) list.get(mode);

                if(tickList.size() < 1){
                    setTempStopNoBestOffer();
                }else{
                    if(!fishing.getStatus().equals("RUN")){
                        fishing.setStatus("RUN");
                        log.info("[FishingTrade-Thread] Restart , Find best offer");
                        fishingRepository.save(fishing);
                    }
                    /** Start Fishing Thread **/
                    startProcess(list, intervalTime,  fishing);
                }
                log.info("[Fishing-Thread] Start , intervalTime : {} seconds", intervalTime/1000);
                Thread.sleep(intervalTime);
            }
        }catch(Exception e){
            log.error("[Fishing-Thread][ERROR] Start error {}", e.getMessage());
        }
    }

    /**
     * 거래소에 맞게 Thread 시작
     * @param exchange
     */
    public void startProcess(Map list, int intervalTime, Fishing fishing) {
        try{
            /** Auto Trade start **/
            // Coin one
            if(DataCommon.COINONE.equals(fishing.getExchange())){
                if(coinOne.startFishingTrade(list, intervalTime) == DataCommon.CODE_SUCCESS){
                }
            }
            /** 거래소가 포블게이트일 경우 **/
            else if(DataCommon.FOBLGATE.equals(fishing.getExchange())){
                if(foblGate.startFishingTrade(list, intervalTime) == DataCommon.CODE_SUCCESS){
                }
            }
            /** 거래소가 포블게이트일 경우 **/
            else if(DataCommon.FLATA.equals(fishing.getExchange())){
                if(flata.startFishingTrade(list, intervalTime) == DataCommon.CODE_SUCCESS){
                }
            }
            else if(DataCommon.DCOIN.equals(fishing.getExchange())){
                if(dCoin.startFishingTrade(list, intervalTime) == DataCommon.CODE_SUCCESS){

                }
            }
            else if(DataCommon.BITHUMB_GLOBAL.equals(fishing.getExchange())){
                if(bithumbGlobal.startFishingTrade(list, intervalTime) == DataCommon.CODE_SUCCESS){

                }
            }
        }catch (Exception e){
            log.error("[FishingThread - StartProcess][ERROR] exchange {}, error {}", fishing.getExchange(), e.getMessage());
        }
        return;
    }


}
