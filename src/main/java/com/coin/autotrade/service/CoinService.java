package com.coin.autotrade.service;

import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.model.*;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.service.exchangeimp.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/** Coin 이용에 필요한 서비스 */
@Service
@Slf4j
public class CoinService {

    @Autowired
    ExchangeRepository exchangeRepository;

    @Autowired
    OrderBookService orderBookService;

    private String BUY_CODE  = "BUY";
    private String SELL_CODE = "SELL";
    private Gson gson        = new Gson();

    /** best offer 구간 여부를 구하는 메서드
     * AutoTrade 를 이용하는데 사용됨. */
    public String getBestOffer(AutoTrade autoTrade){

        String returnVal  = "false";
        String list       = "";

        try{
            String[] coinData = Utils.splitCoinWithId(autoTrade.getCoin());
            Exchange findedEx = autoTrade.getExchange();

            for(ExchangeCoin coin : findedEx.getExchangeCoin()){
                if(coin.getCoinCode().equals(coinData[0]) && coin.getId() == Long.parseLong(coinData[1])){
                    list = getOrderBookByExchange(findedEx, coinData);

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
            BigDecimal floorFix      = new BigDecimal( String.valueOf( Math.pow(10, length - (dotInx + 1)) ) ); // 0.001 -> 1000
            BigDecimal standardPrice = coinPrice.multiply(floorFix);                                            // 1.0
            BigDecimal randomVal     = Utils.getRandomDecimal(bidValue, askValue).multiply(floorFix);
            BigDecimal parseVal      = randomVal.subtract(randomVal.remainder(standardPrice));
            value                    = parseVal.divide(floorFix);
        }else{
            BigDecimal randomVal     = Utils.getRandomDecimal(bidValue, askValue);
            BigDecimal parseVal      = randomVal.subtract(randomVal.remainder(coinPrice));
            value                    = parseVal;
        }

        return value;
    }

    public Map<String, LinkedList<String>> getLiquidityList(Liquidity liquidity){

        Map<String, LinkedList<String>> returnMap = new HashMap<>();
        LinkedList<String> sellList               = new LinkedList<>();
        LinkedList<String> buyList                = new LinkedList<>();
        returnMap.put("sell" , sellList);
        returnMap.put("buy", buyList);

        try{
            String[] coinData = Utils.splitCoinWithId(liquidity.getCoin());

            // Code 값으로 거래소 데이터 조회
            Exchange findedEx = liquidity.getExchange();
            for(ExchangeCoin coin : findedEx.getExchangeCoin()) {
                if(coin.getCoinCode().equals(coinData[0]) && coin.getId() == Long.parseLong(coinData[1])){
                    String list = getOrderBookByExchange(findedEx,coinData);

                    JsonObject json     = gson.fromJson(list, JsonObject.class);
                    JsonArray ask       = json.getAsJsonArray("ask");             // 매도
                    JsonObject firstAsk = ask.get(0).getAsJsonObject();
                    JsonArray bid       = json.getAsJsonArray("bid");             // 매수
                    JsonObject firstBid = bid.get(0).getAsJsonObject();

                    BigDecimal firstAskValue = new BigDecimal(firstAsk.get("price").getAsString());
                    BigDecimal firstBidValue = new BigDecimal(firstBid.get("price").getAsString());
                    BigDecimal tickRange     = new BigDecimal(liquidity.getRangeTick()); // 해당 코인 저장 시 등록한 틱 간격

                    // 수동 모드
                    if(UtilsData.MODE_SELF_L.equals(liquidity.getMode())){
                        String[] selfTicks = liquidity.getSelfTick().split(",");
                        Arrays.sort(selfTicks);
                        for(int i = 0; i < selfTicks.length; i++){
                            BigDecimal decimalValue = new BigDecimal(selfTicks[i]);
                            BigDecimal valueDecimal = tickRange.multiply(decimalValue);
                            String insertSellVal    = firstAskValue.add(valueDecimal).toPlainString();
                            String insertBuyVal     = firstBidValue.subtract(valueDecimal).toPlainString();
                            sellList.add(insertSellVal);
                            buyList.add(insertBuyVal);
                        }
                    }
                    // 자동 모드
                    else{
                        BigDecimal randomInx       = new BigDecimal(Utils.getRandomInt(0,4)) ;  // 호가 0~4 까지(5개) 중 한개를 무작위로 뽑음
                        BigDecimal inputRandomTick = tickRange.multiply(randomInx);                  // 현재 호가창에서 최소매도/최대매수의 랜덤 시작 값을 구하기 위한 수
                        int randomTick             = Integer.parseInt(liquidity.getRandomTick());      // Liquidity 실행 시, 저장한 랜덤 Tick 값
                        for(int i=0; i < randomTick; i ++){
                            BigDecimal index        = new BigDecimal(i);
                            BigDecimal variableTick = tickRange.multiply(index);
                            String decimalSell      = firstAskValue.add(inputRandomTick).add(variableTick).toPlainString();
                            String decimalBuy       = firstBidValue.subtract(inputRandomTick).subtract(variableTick).toPlainString();
                            sellList.add(decimalSell);
                            buyList.add(decimalBuy);
                        }
                    }
                }
            }
        }catch(Exception e){
            log.error("[ERROR][GET Liquidity List] {}",e.getMessage());
            e.printStackTrace();
        }
        return returnMap;
    }


    /**
     * fishing 시, 매도/매수에 따른 상위값 제공
     * @param fishing
     * @return
     */
    public Map getFishingList(Fishing fishing){

        Map<String, List>  returnMap    = new HashMap();
        log.info("[COIN SERVICE][GET FISHING LIST] START");
        try{
            String[] coinData = Utils.splitCoinWithId(fishing.getCoin());
            Exchange findedEx = fishing.getExchange();
            for(ExchangeCoin coin : findedEx.getExchangeCoin()){
                if(coin.getCoinCode().equals(coinData[0]) && coin.getId() == Long.parseLong(coinData[1])){
                    String list = getOrderBookByExchange(findedEx,coinData);

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
                    if(UtilsData.MODE_RANDOM.equals(mode)){
                        mode = (Utils.getRandomInt(0,1) == 1) ? UtilsData.MODE_SELL : UtilsData.MODE_BUY;
                    }

                    // 매도 최소값 - 매수 최대값 > 최대 더하거나 빼야하는 코인 값
                    List returnList = new ArrayList();
                    if((askValue.subtract(bidValue)).compareTo(lastCoinPrice) > 0 ){
                        returnList = makeFishingValue(fishing.getTickCnt(), coinPrice, askValue, bidValue, mode);
                    }
                    returnMap.put(mode, returnList);

                    break;
                }
            }
        }catch (Exception e){
            log.error("[COIN SERVICE][GET FISHING LISH] Error {}",e.getMessage());
        }
        log.info("[COIN SERVICE][GET FISHING LIST] END");
        return returnMap;
    }

    /** Fishing 거래 시 매도/매수에 대한 틱 간격에 맞춰 값 리턴을 하는 함수 */
    public List makeFishingValue(int cnt, BigDecimal price, BigDecimal ask, BigDecimal bid, String type){
        List returnList = new ArrayList();
        if(type.equals(UtilsData.MODE_BUY)){
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
    public Map<String, String> getFirstTick(String coinBeforeSplit, Exchange exchange){

        Map<String, String> returnMap = new HashMap<>();

        try{
            Thread.sleep(1200); // 매도/매수 후 바로 조회 시, 반영이 안됨. 1.2초 정도 대기해보자..
            String[] coinData = Utils.splitCoinWithId(coinBeforeSplit);

            for(ExchangeCoin coin : exchange.getExchangeCoin()){
                if(coin.getCoinCode().equals(coinData[0]) && coin.getId() == Long.parseLong(coinData[1])){
                    String list = getOrderBookByExchange(exchange,coinData);

                    JsonObject json     = gson.fromJson(list, JsonObject.class);
                    JsonArray bid       = json.getAsJsonArray("bid");
                    JsonObject firstBid = bid.get(0).getAsJsonObject();
                    JsonArray ask       = json.getAsJsonArray("ask");
                    JsonObject firstAsk = ask.get(0).getAsJsonObject();

                    BigDecimal bidValue  = new BigDecimal( firstBid.get("price").getAsString() ).stripTrailingZeros();
                    BigDecimal askValue  = new BigDecimal( firstAsk.get("price").getAsString() ).stripTrailingZeros();

                    returnMap.put(UtilsData.MODE_BUY, bidValue.toPlainString());
                    returnMap.put(UtilsData.MODE_SELL, askValue.toPlainString());
                    break;
                }
            }
        }catch (Exception e){
            log.error("[GET FIRST TICK] Error {}",e.getMessage());
            e.printStackTrace();
        }
        return returnMap;
    }

    /**
     * 현재 매도/매수에 대한 최소/최대 호가 조회
     * @param exchange
     * @param mode
     * @return
     */
    public Map<String, JsonArray> getTick(String coinBeforeSplit, Exchange exchange){

        Map<String, JsonArray> returnMap = new HashMap<>();

        try{
            Thread.sleep(1200); // 매도/매수 후 바로 조회 시, 반영이 안됨. 1.2초 정도 대기해보자..
            String[] coinData = Utils.splitCoinWithId(coinBeforeSplit);

            for(ExchangeCoin coin : exchange.getExchangeCoin()){
                if(coin.getCoinCode().equals(coinData[0]) && coin.getId() == Long.parseLong(coinData[1])){
                    String list = getOrderBookByExchange(exchange,coinData);

                    JsonObject json     = gson.fromJson(list, JsonObject.class);
                    JsonArray bid       = json.getAsJsonArray("bid");
                    JsonArray ask       = json.getAsJsonArray("ask");

                    returnMap.put(UtilsData.MODE_BUY, bid);
                    returnMap.put(UtilsData.MODE_SELL, ask);
                    break;
                }
            }
        }catch (Exception e){
            log.error("[GET TICK] Error {}",e.getMessage());
            e.printStackTrace();
        }
        return returnMap;
    }

    /** 거래소별 order book 값을 맞춰 가져오게 변경 */
    private String getOrderBookByExchange(Exchange findedEx, String[] coinData) throws Exception{
        AbstractExchange abstractExchange = Utils.getInstance(findedEx.getExchangeCode());
        String rowList = abstractExchange.getOrderBook(findedEx, coinData);

        return orderBookService.parseOrderBook(findedEx.getExchangeCode(), rowList);
    }
}
