package com.coin.autotrade.service;

import com.coin.autotrade.common.TradeData;
import com.coin.autotrade.common.TradeService;
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

    /** TODO : Service 어노테이션을 주었기때문에 Spring 이 올라오면서 주입됨.
     *  그렇기때문에 여러 거래소에 대해서 getOrderBook 을 사용해야 되는데 어떻게 설계를 해야될까...
      */
    @Autowired
    ExchangeRepository exchangeRepository;

    @Autowired
    OrderBookService orderBookService;

    private BithumbImp bithumbImp;
    private BithumbGlobalImp bithumbGlobalImp;
    private CoinOneImp coinOneImp;
    private DcoinImp dcoinImp;
    private FlataImp flataImp;
    private FoblGateImp foblGateImp;
    private KucoinImp kucoinImp;
    private OkexImp okexImp;
    private GateIoImp gateIoImp;

    private AbstractExchange abstractExchange;
    private String BUY_CODE  = "BUY";
    private String SELL_CODE = "SELL";

    /** best offer 구간 여부를 구하는 메서드
     * AutoTrade 를 이용하는데 사용됨. */
    public String getBestOffer(AutoTrade autoTrade){

        String returnVal  = "false";
        String list       = "";
        Gson gson         = new Gson();

        try{
            String[] coinData = TradeService.splitCoinWithId(autoTrade.getCoin());
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
            BigDecimal randomVal     = TradeService.getRandomDecimal(bidValue, askValue).multiply(floorFix);
            BigDecimal parseVal      = randomVal.subtract(randomVal.remainder(standardPrice));
            value                    = parseVal.divide(floorFix);
        }else{
            BigDecimal randomVal     = TradeService.getRandomDecimal(bidValue, askValue);
            BigDecimal parseVal      = randomVal.subtract(randomVal.remainder(coinPrice));
            value                    = parseVal;
        }

        return value;
    }

    public Map getLiquidityList(Liquidity liquidity){

        String list     = "";
        Gson gson       = new Gson();

        Map<String, LinkedList<String>> returnMap = new HashMap<>();
        LinkedList<String> sellList = new LinkedList<>();
        LinkedList<String> buyList  = new LinkedList<>();
        returnMap.put("sell" , sellList);
        returnMap.put("buy", buyList);

        try{
            String[] coinData = TradeService.splitCoinWithId(liquidity.getCoin());

            // Code 값으로 거래소 데이터 조회
            Exchange findedEx = liquidity.getExchange();
            for(ExchangeCoin coin : findedEx.getExchangeCoin()) {
                if(coin.getCoinCode().equals(coinData[0]) && coin.getId() == Long.parseLong(coinData[1])){
                    list = getOrderBookByExchange(findedEx,coinData);

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
                    if(TradeData.MODE_SELF_L.equals(liquidity.getMode())){
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
                        BigDecimal randomInx       = new BigDecimal(String.valueOf(TradeService.getRandomInt(0,4))) ;                  // 호가 0~4 인덱스
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

        Map<String, List>  returnMap    = new HashMap();
        log.info("[COIN SERVICE][GET FISHING LIST] START");
        try{
            String[] coinData = TradeService.splitCoinWithId(fishing.getCoin());
            Exchange findedEx = fishing.getExchange();
            for(ExchangeCoin coin : findedEx.getExchangeCoin()){
                if(coin.getCoinCode().equals(coinData[0]) && coin.getId() == Long.parseLong(coinData[1])){
                    String list = getOrderBookByExchange(findedEx,coinData);

                    JsonObject json     = TradeService.getGson().fromJson(list, JsonObject.class);
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
                    if(TradeData.MODE_RANDOM.equals(mode)){
                        mode = (TradeService.getRandomInt(0,1) == 1) ? TradeData.MODE_SELL : TradeData.MODE_BUY;
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
        if(type.equals(TradeData.MODE_BUY)){
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

        String list                   = "";
        Gson gson                     = TradeService.getGson();
        Map<String, String> returnMap = new HashMap<>();

        try{
            Thread.sleep(1200); // 매도/매수 후 바로 조회 시, 반영이 안됨. 1.2초 정도 대기해보자..
            String[] coinData = TradeService.splitCoinWithId(coinBeforeSplit);

            for(ExchangeCoin coin : exchange.getExchangeCoin()){
                if(coin.getCoinCode().equals(coinData[0]) && coin.getId() == Long.parseLong(coinData[1])){
                    list = getOrderBookByExchange(exchange,coinData);

                    JsonObject json     = gson.fromJson(list, JsonObject.class);
                    JsonArray bid       = json.getAsJsonArray("bid");
                    JsonObject firstBid = bid.get(0).getAsJsonObject();
                    JsonArray ask       = json.getAsJsonArray("ask");
                    JsonObject firstAsk = ask.get(0).getAsJsonObject();

                    BigDecimal bidValue  = new BigDecimal( firstBid.get("price").getAsString() ).stripTrailingZeros();
                    BigDecimal askValue  = new BigDecimal( firstAsk.get("price").getAsString() ).stripTrailingZeros();

                    returnMap.put(TradeData.MODE_BUY, bidValue.toPlainString());
                    returnMap.put(TradeData.MODE_SELL, askValue.toPlainString());
                    break;
                }
            }
        }catch (Exception e){
            log.error("[GET FIRST TICK] Error {}",e.getMessage());
            e.printStackTrace();
        }
        return returnMap;
    }

    /** 거래소별 order book 값을 맞춰 가져오게 변경 */
    private String getOrderBookByExchange(Exchange findedEx, String[] coinData) throws Exception{

        String exchange = findedEx.getExchangeCode();
        String rowList = null;
        if(exchange.equals(TradeData.BITHUMB)){
            if(bithumbImp == null) {
                bithumbImp = new BithumbImp();
            }
            rowList = bithumbImp.getOrderBook(findedEx, coinData);
        }else if(exchange.equals(TradeData.BITHUMB_GLOBAL)){
            if(bithumbGlobalImp == null) {
                bithumbGlobalImp = new BithumbGlobalImp();
            }
            rowList = bithumbGlobalImp.getOrderBook(findedEx, coinData);
        }else if(exchange.equals(TradeData.COINONE)){
            if(coinOneImp == null) {
                coinOneImp = new CoinOneImp();
            }
            rowList = coinOneImp.getOrderBook(findedEx, coinData);
        }else if(exchange.equals(TradeData.DCOIN)){
            if(dcoinImp == null) {
                dcoinImp = new DcoinImp();
            }
            rowList = dcoinImp.getOrderBook(findedEx, coinData);
        }else if(exchange.equals(TradeData.FLATA)){
            if(flataImp == null) {
                flataImp = new FlataImp();
            }
            rowList = flataImp.getOrderBook(findedEx, coinData);
        }else if(exchange.equals(TradeData.FOBLGATE)){
            if(foblGateImp == null) {
                foblGateImp = new FoblGateImp();
            }
            rowList = foblGateImp.getOrderBook(findedEx, coinData);
        }else if(exchange.equals(TradeData.KUCOIN)){
            if(kucoinImp == null) {
                kucoinImp = new KucoinImp();
            }
            rowList = kucoinImp.getOrderBook(findedEx, coinData);
        }else if(exchange.equals(TradeData.OKEX)){
            if(okexImp == null){
                okexImp = new OkexImp();
            }
            rowList = okexImp.getOrderBook(findedEx, coinData);
        }else if(exchange.equals(TradeData.GATEIO)){
            if(gateIoImp == null){
                gateIoImp = new GateIoImp();
            }
            rowList = gateIoImp.getOrderBook(findedEx, coinData);
        }
        String list = orderBookService.parseOrderBook(findedEx.getExchangeCode(), rowList);
        return list;
    }
}
