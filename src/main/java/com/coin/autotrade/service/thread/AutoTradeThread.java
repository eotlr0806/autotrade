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
    CoinOneFunction     coinOne;        // Coin one
    DcoinFunction       dCoin;          // D coin
    FlataFunction       flata;          // Flata
    FoblGateFunction    foblGate;       // Foblgate
    BithumbGlobalFunction bithumbGlobal;

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
            /** Coin one **/
            if(DataCommon.COINONE.equals(autoTrade.getExchange())){
                coinOne = new CoinOneFunction();
                coinOne.initCoinOne(autoTrade, user, exchange);
            }
            /** FoblGate **/
            else if(DataCommon.FOBLGATE.equals(autoTrade.getExchange())){
                foblGate = new FoblGateFunction();
                foblGate.initFoblGate(autoTrade, user, exchange);
            }
            /** Flata **/
            else if(DataCommon.FLATA.equals(autoTrade.getExchange())){
                flata = new FlataFunction();
                flata.initFlata(autoTrade, user, exchange);
            }
            /** Dcoin **/
            else if(DataCommon.DCOIN.equals(autoTrade.getExchange())){
                dCoin = new DcoinFunction();
                dCoin.initDcoin(autoTrade, user, exchange);
            }
            /** BithumGlobal **/
            else if(DataCommon.BITHUMB_GLOBAL.equals(autoTrade.getExchange())){
                bithumbGlobal = new BithumbGlobalFunction();
                bithumbGlobal.initBithumbGlobalAutoTrade(autoTrade, user, exchange);
            }
        }catch (Exception e){
            log.error("[Auto Trade Start fail][ERROR] exchange {}" , exchange.getExchangeCode());
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
        log.info("[AutoTrade-Thread] temporarily Stop , There is no best offer");
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
                        log.info("[AutoTrade-Thread] Restart , Find best offer");
                        autotradeRepository.save(autoTrade);
                    }
                    /** make cnt random value */
                    String cnt   = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double) autoTrade.getMinCnt(), (double) autoTrade.getMaxCnt()) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);

                    /** Auto Trade start **/
                    startProcess(price, cnt, autoTrade);
                }

                intervalTime = ServiceCommon.getRandomInt(autoTrade.getMinSeconds(), autoTrade.getMaxSeconds()) * 1000;
                log.info("[AutoTrade-Thread] Start , intervalTime : {} seconds", intervalTime/1000);
                Thread.sleep(intervalTime);
            }
        }catch(Exception e){
            log.error("[AutoTrade-Thread][ERROR] Start error {}", e.getMessage());
        }
    }

    // Stop thread
    public void setStop(){  run = false; }





    /**
     * 거래소에 맞게 Thread 시작
     * @param exchange
     */
    public void startProcess(String price, String cnt, AutoTrade autoTrade) {
        try{
            String[] coinData = ServiceCommon.setCoinData(autoTrade.getCoin());

            /** 거래소가 코인원일 경우 **/
            if(DataCommon.COINONE.equals(autoTrade.getExchange())){
                if(coinOne.startAutoTrade(price, cnt) == DataCommon.CODE_SUCCESS){
                    // Insert into history table
                }
            }
            /** 거래소가 포블게이트일 경우 **/
            else if(DataCommon.FOBLGATE.equals(autoTrade.getExchange())){
                if(foblGate.startAutoTrade(price, cnt) == DataCommon.CODE_SUCCESS){
                    // Insert into history table
                }
            }
            /** 거래소가 플랫타일 경우 **/
            else if(DataCommon.FLATA.equals(autoTrade.getExchange())){
                if(flata.startAutoTrade(price, cnt) == DataCommon.CODE_SUCCESS){
                    // Insert into history table
                }
            }
            /** 거래소가 디코인일 경우 **/
            else if(DataCommon.DCOIN.equals(autoTrade.getExchange())){
                if(dCoin.startAutoTrade(price, cnt) == DataCommon.CODE_SUCCESS){
                    // Insert into history table
                }
            }
            /** 거래소가 빗썸글로벌일 경우 **/
            else if(DataCommon.BITHUMB_GLOBAL.equals(autoTrade.getExchange())){
                String symbol = coinData[0] + "-" + bithumbGlobal.getCurrency(bithumbGlobal.getExchange(), coinData[0], coinData[1]);
                if(bithumbGlobal.startAutoTrade(price, cnt, coinData[0], coinData[1], symbol ,autoTrade.getMode()) == DataCommon.CODE_SUCCESS){
                    // Insert into history table
                }
            }

        }catch (Exception e){
            log.error("[AutoTradeThread - StartProcess][ERROR] exchange {}, error {}",
                    autoTrade.getExchange(), e.getMessage());
        }
        return;
    }
}
