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
    CoinOneFunction     coinOne;
    DcoinFunction       dCoin;
    FlataFunction       flata;
    FoblGateFunction    foblGate;
    BithumbGlobalFunction bithumbGlobal;

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

            /** Coin one **/
            if(DataCommon.COINONE.equals(liquidity.getExchange())){
                coinOne = new CoinOneFunction();
                coinOne.initCoinOne(liquidity, user, exchange);
            }
            /** Dcoin **/
            else if(DataCommon.DCOIN.equals(liquidity.getExchange())){
                dCoin = new DcoinFunction();
                dCoin.initDcoinLiquidity(liquidity, user, exchange);
            }
            /** Flata **/
            else if(DataCommon.FLATA.equals(liquidity.getExchange())){
                flata = new FlataFunction();
                flata.initFlataLiquidity(liquidity, user, exchange);
            }
            /** FoblGate **/
            else if(DataCommon.FOBLGATE.equals(liquidity.getExchange())){
                foblGate = new FoblGateFunction();
                foblGate.initFoblGateLiquidity(liquidity, user, exchange);
            }
            /** BithumGlobal **/
            else if(DataCommon.BITHUMB_GLOBAL.equals(liquidity.getExchange())){
                bithumbGlobal = new BithumbGlobalFunction();
                bithumbGlobal.initBithumbGlobalLiquidity(liquidity, user, exchange);
            }

        }catch (Exception e){
            log.error("[ERROR][Liquidity Start fail] exchange : {} ", exchange.getExchangeCode());
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
                startProcess(list, liquidity);

                intervalTime = ServiceCommon.getRandomInt(liquidity.getMinSeconds(), liquidity.getMaxSeconds()) * 1000;
                log.info("[Liquidity-Thread] Start , intervalTime : {} seconds", intervalTime/1000);
                Thread.sleep(intervalTime);
            }
        }catch(Exception e){
            log.error("[ERROR][Liquidity-Thread] Start error {}", e.getMessage());
        }
    }


    /**
     * 거래소에 맞게 Thread 시작
     * @param exchange
     */
    public void startProcess(Map list, Liquidity liquidity) {
        try{
            String[] coinData = ServiceCommon.setCoinData(liquidity.getCoin());

            /** Auto Trade start **/
            // Coin one
            if(DataCommon.COINONE.equals(liquidity.getExchange())){
                if(coinOne.startLiquidity(list, liquidity.getMinCnt(), liquidity.getMaxCnt()) == DataCommon.CODE_SUCCESS){
                    // insert into history table
                }
            }
            // Dcoin
            else if(DataCommon.DCOIN.equals(liquidity.getExchange())){
                String symbol = coinData[0] + "" + dCoin.getCurrency(dCoin.getExchange(),coinData[0], coinData[1]);
                if(dCoin.startLiquidity(list, liquidity.getMinCnt(), liquidity.getMaxCnt(), symbol) == DataCommon.CODE_SUCCESS){
                    // insert into history table
                }
            }
            // Flata
            else if(DataCommon.FLATA.equals(liquidity.getExchange())){
                String symbol = coinData[0] + "/" + flata.getCurrency(flata.getExchange(),coinData[0], coinData[1]);
                if(flata.startLiquidity(list, liquidity.getMinCnt(), liquidity.getMaxCnt(), coinData[0], coinData[1], symbol) == DataCommon.CODE_SUCCESS){
                    // Insert into history table
                }
            }
            // Foblgate
            else if(DataCommon.FOBLGATE.equals(liquidity.getExchange())){
                String symbol = coinData[0] + "/" + foblGate.getCurrency(foblGate.getExchange(),coinData[0], coinData[1]);
                if(foblGate.startLiquidity(list, liquidity.getMinCnt(), liquidity.getMaxCnt(), coinData[0], coinData[1], symbol) == DataCommon.CODE_SUCCESS){
                    // Insert into history table
                }
            }
            // Bithumb Global
            else if(DataCommon.BITHUMB_GLOBAL.equals(liquidity.getExchange())){
                String symbol = coinData[0] + "-" + bithumbGlobal.getCurrency(bithumbGlobal.getExchange(), coinData[0], coinData[1]);
                if(bithumbGlobal.startLiquidity(list, liquidity.getMinCnt(), liquidity.getMaxCnt(), coinData[0], coinData[1], symbol) == DataCommon.CODE_SUCCESS){
                    // Insert into history table
                }
            }
        }catch (Exception e){
            log.error("[ERROR][LiquidityThread - StartProcess] exchange {}, error {}",
                    liquidity.getExchange(), e.getMessage());
        }
        return;
    }


}
