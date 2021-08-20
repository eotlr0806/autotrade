package com.coin.autotrade.service.thread;

import com.coin.autotrade.common.BeanUtils;
import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.model.Fishing;
import com.coin.autotrade.model.Liquidity;
import com.coin.autotrade.model.User;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.repository.FishingRepository;
import com.coin.autotrade.repository.LiquidityRepository;
import com.coin.autotrade.service.CoinService;
import com.coin.autotrade.service.function.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    boolean run                 = true;
    Fishing fishing             = null;

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
                coinOne.initCoinOne(fishing, user, exchange);
            }
//            /** Dcoin **/
//            else if(DataCommon.DCOIN.equals(liquidity.getExchange())){
//                dCoin = new DcoinFunction();
//                dCoin.initDcoinLiquidity(liquidity, user, exchange);
//            }
//            /** Flata **/
//            else if(DataCommon.FLATA.equals(liquidity.getExchange())){
//                flata = new FlataFunction();
//                flata.initFlataLiquidity(liquidity, user, exchange);
//            }
//            /** FoblGate **/
//            else if(DataCommon.FOBLGATE.equals(liquidity.getExchange())){
//                foblGate = new FoblGateFunction();
//                foblGate.initFoblGateLiquidity(liquidity, user, exchange);
//            }
//            /** BithumGlobal **/
//            else if(DataCommon.BITHUMB_GLOBAL.equals(liquidity.getExchange())){
//                bithumbGlobal = new BithumbGlobalFunction();
//                bithumbGlobal.initBithumbGlobalLiquidity(liquidity, user, exchange);
//            }

        }catch (Exception e){
            log.error("[ERROR][Fishing Start fail] exchange : {} ", exchange.getExchangeCode());
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
//                Map list = coinService.getLiquidityList(liquidity);

                /** Start Liquidity Thread **/
//                startProcess(list, liquidity);

                intervalTime = ServiceCommon.getRandomInt(fishing.getMinSeconds(), fishing.getMaxSeconds()) * 1000;
                log.info("[Fishing-Thread] Start , intervalTime : {} seconds", intervalTime/1000);
                Thread.sleep(intervalTime);
            }
        }catch(Exception e){
            log.error("[ERROR][Fishing-Thread] Start error {}", e.getMessage());
        }
    }


    /**
     * 거래소에 맞게 Thread 시작
     * @param exchange
     */
    public void startProcess(Map list, Fishing fishing) {
        try{
            String[] coinData = ServiceCommon.setCoinData(fishing.getCoin());

            /** Auto Trade start **/
            // Coin one
            if(DataCommon.COINONE.equals(fishing.getExchange())){
//                if(coinOne.startLiquidity(list, liquidity.getMinCnt(), liquidity.getMaxCnt()) == DataCommon.CODE_SUCCESS){
//                    // insert into history table
//                }
            }
        }catch (Exception e){
            log.error("[ERROR][FishingThread - StartProcess] exchange {}, error {}", fishing.getExchange(), e.getMessage());
        }
        return;
    }


}
