package com.coin.autotrade.service;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.*;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.service.function.*;
import com.coin.autotrade.service.parser.OrderBookParser;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Coin 이용에 필요한 서비스
 */
@Service
@Slf4j
public class CoinService {

    @Autowired
    ExchangeRepository exchangeRepository;

    @Autowired
    OrderBookParser orderBookParser;

    private CoinOneFunction       coinOneFunction;
    private DcoinFunction         dCoinFunction;
    private FlataFunction         flataFunction;
    private FoblGateFunction      foblGateFunction;
    private BithumbGlobalFunction bithumbGlobalFunction;

    private String BUY_CODE  = "BUY";
    private String SELL_CODE = "SELL";

    // Coinone function 은 single tone으로
    public void checkFunction(){
        if(coinOneFunction == null){
            coinOneFunction = new CoinOneFunction();
        }
        if(dCoinFunction == null){
            dCoinFunction = new DcoinFunction();
        }
        if(flataFunction == null){
            flataFunction = new FlataFunction();
        }
        if(foblGateFunction == null){
            foblGateFunction = new FoblGateFunction();
        }
        if(bithumbGlobalFunction == null){
            bithumbGlobalFunction = new BithumbGlobalFunction();
        }
    }

    /**
     * best offer 구간 여부를 구하는 메서드
     * @param autoTrade
     * @return
     */
    public String getBestOffer(AutoTrade autoTrade){
        checkFunction();

        String returnVal  = "false";
        String list       = "";
        Gson gson         = new Gson();

        try{
            String[] coinData = ServiceCommon.setCoinData(autoTrade.getCoin());

            Exchange findedEx = exchangeRepository.findByexchangeCode(autoTrade.getExchange());
            for(ExchangeCoin coin : findedEx.getExchangeCoin()){
                if(coin.getCoinCode().equals(coinData[0]) && coin.getId() == Long.parseLong(coinData[1])){
                    /** Coin one **/
                    if(DataCommon.COINONE.equals(autoTrade.getExchange())){
                        list = coinOneFunction.getOrderBook(coin.getCoinCode());
                    }
                    /** Dcoin one **/
                    else if(DataCommon.DCOIN.equals(autoTrade.getExchange())){
                        // Exchange가 없을 경우 setting
                        if(dCoinFunction.getExchange() == null) dCoinFunction.setExchange(findedEx);
                        String value = dCoinFunction.getOrderBook(findedEx, coinData[0], coinData[1]);
                        list = orderBookParser.parseData(findedEx.getExchangeCode(), value);
                    }
                    /** Flata **/
                    else if(DataCommon.FLATA.equals(autoTrade.getExchange())){
                        // Exchange가 없을 경우 setting
                        if(flataFunction.getExchange() == null) flataFunction.setExchange(findedEx);
                        String value = flataFunction.getOrderBook(findedEx, coinData[0], coinData[1]);
                        list = orderBookParser.parseData(findedEx.getExchangeCode(), value);
                    }
                    /** Folbgate **/
                    else if(DataCommon.FOBLGATE.equals(autoTrade.getExchange())){
                        // Exchange가 없을 경우 setting
                        if(foblGateFunction.getExchange() == null) foblGateFunction.setExchange(findedEx);
                        String value = foblGateFunction.getOrderBook(findedEx, coinData[0], coinData[1]);
                        list = orderBookParser.parseData(findedEx.getExchangeCode(), value);
                    }
                    /** Bithub Global **/
                    else if(DataCommon.BITHUMB_GLOBAL.equals(autoTrade.getExchange())){
                        // Exchange가 없을 경우 setting
                        if(bithumbGlobalFunction.getExchange() == null) bithumbGlobalFunction.setExchange(findedEx);
                        String value = bithumbGlobalFunction.getOrderBook(findedEx, coinData[0], coinData[1]);
                        list = orderBookParser.parseData(findedEx.getExchangeCode(), value);
                    }

                    JsonObject json = gson.fromJson(list, JsonObject.class);

                    JsonArray bid = json.getAsJsonArray("bid");
                    JsonObject firstBid = bid.get(0).getAsJsonObject();
                    JsonArray ask = json.getAsJsonArray("ask");
                    JsonObject firstAsk = ask.get(0).getAsJsonObject();

                    BigDecimal bidValue  = new BigDecimal( firstBid.get("price").getAsString() );
                    BigDecimal askValue  = new BigDecimal( firstAsk.get("price").getAsString() );
                    BigDecimal coinPrice = new BigDecimal(coin.getCoinPrice() );

                    // 매도 - 매수가 최소 기준보다 커야 함.
                    if((askValue.subtract(bidValue)).compareTo(coinPrice) > 0 ){
                        int dotInx   = coin.getCoinPrice().indexOf(".");
                        int length   = coin.getCoinPrice().length();
                        int loop     = 0;
                        BigDecimal value = getRandomBestOffer(coinPrice, dotInx, length, bidValue, askValue);

                        // 10번은 돌아준다.
                        while(value.compareTo(askValue) >= 0 || value.compareTo(bidValue) <= 0){
                            value = getRandomBestOffer(coinPrice, dotInx, length, bidValue, askValue);
                            if(loop++ > 10) break;
                        }

                        if(value.compareTo(askValue) >= 0 || value.compareTo(bidValue) <= 0){
                            returnVal   = "false";
                        }else{
                            returnVal = value.toPlainString();

                            if(coinPrice.compareTo(new BigDecimal("1.0")) < 0){
                                int dot = returnVal.indexOf(".");
                                returnVal = returnVal.substring(0, (dot + length - dotInx )  );
                            }else{
                                returnVal = returnVal.substring(0, (returnVal.indexOf(".")) );
                            }
                        }
                    }
                    break;
                }
            }
        }catch (Exception e){
            log.error("[ERROR][Get Best Offer] {}",e.getMessage());
        }

        return returnVal;
    }

    /**
     * random 으로 best offer 값을 잡아준다.
     */
    public BigDecimal getRandomBestOffer(BigDecimal coinPrice, int dotInx, int length, BigDecimal bidValue, BigDecimal askValue ){

        BigDecimal value = new BigDecimal("-1.0");
        // 소수
        if(coinPrice.compareTo(new BigDecimal("1.0")) < 0){
            BigDecimal floorFix      = new BigDecimal( String.valueOf( Math.pow(10, length - (dotInx + 1)) ) );
            BigDecimal standardPrice = coinPrice.multiply(floorFix);
            BigDecimal randomVal     = ServiceCommon.getRandomDecimal(bidValue, askValue).multiply(floorFix);
            BigDecimal parseVal      = randomVal.subtract(randomVal.remainder(standardPrice));
            value                    = parseVal.divide(floorFix);
        }else{
            BigDecimal randomVal     = ServiceCommon.getRandomDecimal(bidValue, askValue);
            BigDecimal parseVal      = randomVal.subtract(randomVal.remainder(coinPrice));
            value                    = parseVal;
        }

        return value;
    }

    public Map getLiquidityList(Liquidity liquidity){
        checkFunction();

        String list     = "";
        Gson gson       = new Gson();

        Map<String, LinkedList<String>> returnMap = new HashMap<>();
        LinkedList<String> sellList = new LinkedList<>();
        LinkedList<String> buyList  = new LinkedList<>();
        returnMap.put("sell" , sellList);
        returnMap.put("buy", buyList);

        try{
            String[] coinData = ServiceCommon.setCoinData(liquidity.getCoin());

            // Code 값으로 거래소 데이터 조회
            Exchange findedEx = exchangeRepository.findByexchangeCode(liquidity.getExchange());
            for(ExchangeCoin coin : findedEx.getExchangeCoin()) {
                if(coin.getCoinCode().equals(coinData[0]) && coin.getId() == Long.parseLong(coinData[1])){
                    list = getOrderBookByExchange(findedEx,coin,coinData);

                    JsonObject json = gson.fromJson(list, JsonObject.class);
                    JsonArray ask       = json.getAsJsonArray("ask");             // 매도
                    JsonObject firstAsk = ask.get(0).getAsJsonObject();
                    JsonArray bid       = json.getAsJsonArray("bid");             // 매수
                    JsonObject firstBid = bid.get(0).getAsJsonObject();

                    double askValue  = Double.parseDouble(firstAsk.get("price").getAsString());
                    double bidValue  = Double.parseDouble(firstBid.get("price").getAsString());
                    // 수동 모드
                    double tick        = Double.parseDouble(liquidity.getRangeTick());
                    BigDecimal tickDecimal     = new BigDecimal(String.valueOf(tick));
                    if(DataCommon.MODE_SELF_L.equals(liquidity.getMode())){
                        String[] selfTicks = liquidity.getSelfTick().split(",");
                        Arrays.sort(selfTicks);
                        for(int i = 0; i < selfTicks.length; i++){
                            int value               = Integer.parseInt(selfTicks[i]);
                            BigDecimal decimalValue = new BigDecimal(String.valueOf(value));
                            BigDecimal sellDecimal  = new BigDecimal(String.valueOf(askValue));
                            BigDecimal buyDecimal   = new BigDecimal(String.valueOf(bidValue));
                            BigDecimal valueDecimal = tickDecimal.multiply(decimalValue);
                            String insertSellVal = sellDecimal.add(valueDecimal).toString();
                            String insertBuyVal  = buyDecimal.subtract(valueDecimal).toString();
                            sellList.add(String.valueOf(insertSellVal));
                            buyList.add(String.valueOf(insertBuyVal));
                        }
                    }
                    // 자동 모드
                    else{
                        double coinTick = Double.parseDouble(coin.getCoinPrice());             // Coin 등록 시 저장한 Tick
                        int randomTick  = Integer.parseInt(liquidity.getRandomTick());      // Liquidity 실행 시, 저장한 랜덤 Tick 값
                        BigDecimal variableTick;
                        BigDecimal randomInx       = new BigDecimal(String.valueOf(ServiceCommon.getRandomInt(0,4))) ;                  // 호가 0~4 인덱스
                        BigDecimal decimalCoinTick = new BigDecimal(String.valueOf(coinTick));
                        BigDecimal inputRandomTick = decimalCoinTick.multiply(randomInx); // 현재 호가창에서 최소매도/최대매수의 랜덤 시작 값을 구하기 위한 수
                        BigDecimal insertSellVal   = new BigDecimal(String.valueOf(askValue));  // 현재 호가창 최소 매도가격
                        BigDecimal insertBuyVal    = new BigDecimal(String.valueOf(bidValue));  // 현재 호가창 최대 매수가격

                        for(int i=0; i < randomTick; i ++){
                            BigDecimal index   = new BigDecimal(String.valueOf(i));
                            variableTick       = tickDecimal.multiply(index);
                            String decimalSell = insertSellVal.add(inputRandomTick).add(variableTick).toPlainString();
                            String decimalBuy  = insertBuyVal.subtract(inputRandomTick).subtract(variableTick).toPlainString();
                            sellList.add(decimalSell);
                            buyList.add(decimalBuy);
                        }
                    }
                }
            }
        }catch(Exception e){
            log.error("[ERROR][GET Liquidity List] {}",e.getMessage());
        }

        return returnMap;
    }


    /**
     * fishing 시, 매도/매수에 따른 상위값 제공
     * @param fishing
     * @return
     */
    public Map getFishingList(Fishing fishing){
        checkFunction();

        Map<String, List>  returnMap    = new HashMap();
        String list                     = "";
        Gson gson                       = new Gson();
        List returnList                 = new ArrayList();

        try{
            String[] coinData = ServiceCommon.setCoinData(fishing.getCoin());

            Exchange findedEx = exchangeRepository.findByexchangeCode(fishing.getExchange());
            for(ExchangeCoin coin : findedEx.getExchangeCoin()){
                if(coin.getCoinCode().equals(coinData[0]) && coin.getId() == Long.parseLong(coinData[1])){

                    list = getOrderBookByExchange(findedEx,coin,coinData);

                    JsonObject json     = gson.fromJson(list, JsonObject.class);
                    JsonArray bid       = json.getAsJsonArray("bid");
                    JsonObject firstBid = bid.get(0).getAsJsonObject();
                    JsonArray ask       = json.getAsJsonArray("ask");
                    JsonObject firstAsk = ask.get(0).getAsJsonObject();

                    BigDecimal bidValue  = new BigDecimal( firstBid.get("price").getAsString() );
                    BigDecimal askValue  = new BigDecimal( firstAsk.get("price").getAsString() );
                    BigDecimal coinPrice = new BigDecimal( fishing.getRangeTick() );
                    BigDecimal coinCnt   = new BigDecimal( fishing.getTickCnt() );
                    BigDecimal lastCoinPrice = coinPrice.multiply(coinCnt);

                    // 랜덤일 경우, 1이면 sell / 0이면 buy로 변환
                    String mode = fishing.getMode();
                    if(DataCommon.MODE_RANDOM.equals(mode)){
                        mode = (ServiceCommon.getRandomInt(0,1) == 1) ? DataCommon.MODE_SELL : DataCommon.MODE_BUY;
                    }
                    // 매도 최소값 - 매수 최대값 > 최대 더하거나 빼야하는 코인 값
                    if((askValue.subtract(bidValue)).compareTo(lastCoinPrice) > 0 ){
                        returnList = makeFishingValue(fishing.getTickCnt(), coinPrice, askValue, bidValue, mode);
                    }
                    returnMap.put(mode, returnList);

                    break;
                }
            }
        }catch (Exception e){
            log.error("[ERROR][Get Best Offer] {}",e.getMessage());
        }


        return returnMap;
    }

    /** Fishing 거래 시 매도/매수에 대한 틱 간격에 맞춰 값 리턴을 하는 함수 */
    public List makeFishingValue(int cnt, BigDecimal price, BigDecimal ask, BigDecimal bid, String type){
        List returnList = new ArrayList();
        if(type.equals(DataCommon.MODE_BUY)){
            BigDecimal tempBid = bid;
            for (int i = 0; i < cnt; i++) {
                tempBid = tempBid.add(price).stripTrailingZeros();
                returnList.add(tempBid.toPlainString());
            }
        }else{
            BigDecimal tempAsk = ask;
            for (int i = 0; i < cnt; i++) {
                tempAsk = tempAsk.subtract(price).stripTrailingZeros();
                returnList.add(tempAsk.toPlainString());
            }
        }

        return returnList;
    }

    /**
     * 현재 매도/매수에 대한 최소/최대 호가 조회
     * @param exchange
     * @param mode
     * @return
     */
    public Map<String, String> getFirstTick(String coinBeforeSplit, String exchange){
        checkFunction();

        String list                   = "";
        Gson gson                     = new Gson();
        Map<String, String> returnMap = new HashMap<>();

        try{
            Thread.sleep(1200); // 매도/매수 후 바로 조회 시, 반영이 안됨. 1.2초 정도 대기해보자..
            String[] coinData = ServiceCommon.setCoinData(coinBeforeSplit);

            Exchange findedEx = exchangeRepository.findByexchangeCode(exchange);
            for(ExchangeCoin coin : findedEx.getExchangeCoin()){
                if(coin.getCoinCode().equals(coinData[0]) && coin.getId() == Long.parseLong(coinData[1])){
                    list = getOrderBookByExchange(findedEx,coin,coinData);

                    JsonObject json     = gson.fromJson(list, JsonObject.class);
                    JsonArray bid       = json.getAsJsonArray("bid");
                    JsonObject firstBid = bid.get(0).getAsJsonObject();
                    JsonArray ask       = json.getAsJsonArray("ask");
                    JsonObject firstAsk = ask.get(0).getAsJsonObject();

                    BigDecimal bidValue  = new BigDecimal( firstBid.get("price").getAsString() ).stripTrailingZeros();
                    BigDecimal askValue  = new BigDecimal( firstAsk.get("price").getAsString() ).stripTrailingZeros();

                    returnMap.put(DataCommon.MODE_BUY, bidValue.toPlainString());
                    returnMap.put(DataCommon.MODE_SELL, askValue.toPlainString());
                    break;
                }
            }
        }catch (Exception e){
            log.error("[ERROR][Get FirstTick] {}",e.getMessage());
        }

        return returnMap;
    }

    /**
     * 거래소별 order book 값을 맞춰 가져오게 변경
     * @param exchange
     * @param findedEx
     * @param coin
     * @param coinData
     * @return
     */
    private String getOrderBookByExchange(Exchange findedEx, ExchangeCoin coin, String[] coinData){
        String list = "";
        try{
            /** Coin one **/
            if(DataCommon.COINONE.equals(findedEx.getExchangeCode())){
                list = coinOneFunction.getOrderBook(coin.getCoinCode());
            }
            /** Dcoin one **/
            else if(DataCommon.DCOIN.equals(findedEx.getExchangeCode())){
                // Exchange가 없을 경우 setting
                if(dCoinFunction.getExchange() == null) dCoinFunction.setExchange(findedEx);
                String value = dCoinFunction.getOrderBook(findedEx, coinData[0], coinData[1]);
                list         = orderBookParser.parseData(findedEx.getExchangeCode(), value);
            }
            /** Flata **/
            else if(DataCommon.FLATA.equals(findedEx.getExchangeCode())){
                // Exchange가 없을 경우 setting
                if(flataFunction.getExchange() == null) flataFunction.setExchange(findedEx);
                String value = flataFunction.getOrderBook(findedEx, coinData[0], coinData[1]);
                list         = orderBookParser.parseData(findedEx.getExchangeCode(), value);
            }
            /** Folbgate **/
            else if(DataCommon.FOBLGATE.equals(findedEx.getExchangeCode())){
                // Exchange가 없을 경우 setting
                if(foblGateFunction.getExchange() == null) foblGateFunction.setExchange(findedEx);
                String value = foblGateFunction.getOrderBook(findedEx, coinData[0], coinData[1]);
                list         = orderBookParser.parseData(findedEx.getExchangeCode(), value);
            }
            /** Bithub Global **/
            else if(DataCommon.BITHUMB_GLOBAL.equals(findedEx.getExchangeCode())){
                // Exchange가 없을 경우 setting
                if(bithumbGlobalFunction.getExchange() == null) bithumbGlobalFunction.setExchange(findedEx);
                String value = bithumbGlobalFunction.getOrderBook(findedEx, coinData[0], coinData[1]);
                list         = orderBookParser.parseData(findedEx.getExchangeCode(), value);
            }
        }catch(Exception e){
            log.error("[ERROR][Get getOrderBookByExchange] {}",e.getMessage());
        }

        return list;
    }
}
