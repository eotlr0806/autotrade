package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.TradeData;
import com.coin.autotrade.common.TradeService;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.beans.property.StringProperty;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
public class FoblGateImp extends AbstractExchange {

    private String API_KEY               = "apiKey";
    private String SECRET_KEY            = "secretKey";
    private String USER_ID               = "userId";
    private String SELL                  = "ask";
    private String BUY                   = "bid";
    private String SUCCESS               = "0";
    private String ALREADY_TRADED        = "5004";
    private Map<String, String> keyList  = new HashMap<>();

    /* Foblgate Function initialize for autotrade */
    @Override
    public void initClass(AutoTrade autoTrade){
        super.autoTrade = autoTrade;
    }

    /* Foblgate Function initialize for liquidity */
    @Override
    public void initClass(Liquidity liquidity){
        super.liquidity = liquidity;
    }

    @Override
    public void initClass(RealtimeSync realtimeSync){
        super.realtimeSync = realtimeSync;
    }

    /* Foblgate Function initialize for fishing */
    @Override
    public void initClass(Fishing fishing, CoinService coinService){
        super.fishing     = fishing;
        super.coinService = coinService;
    }

    /** 해당 정보를 이용해 API 키를 셋팅한다 */
    public void setApiKey(String coin, String coinId, Exchange exchange) throws Exception{
        if(keyList.isEmpty()){
            for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
                if(exCoin.getCoinCode().equals(coin) && exCoin.getId() == Long.parseLong(coinId)){
                    keyList.put(USER_ID,     exCoin.getExchangeUserId());
                    keyList.put(API_KEY,     exCoin.getPublicKey());
                    keyList.put(SECRET_KEY,  exCoin.getPrivateKey());
                }
            }
            log.info("[FOBLGATE][SET API KEY] First Key setting in instance API:{}, secret:{}",keyList.get(API_KEY), keyList.get(SECRET_KEY));
        }
    }

    /* 포블게이트 자전거래 로직 */
    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[FOBLGATE][AUTOTRADE START]");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String[] coinData = TradeService.splitCoinWithId(autoTrade.getCoin());
            String symbol     = coinData[0] + "/" + getCurrency(autoTrade.getExchange(), coinData[0], coinData[1]);
            setApiKey(coinData[0], coinData[1], autoTrade.getExchange());    // Key 값 셋팅

            // mode 처리
            String firstAction  = "";
            String secondAction = "";
            String mode         = autoTrade.getMode();
            if(TradeData.MODE_RANDOM.equals(mode)){    // Trade Mode 가 랜덤일 경우 생성
                mode = (TradeService.getRandomInt(0,1) == 0) ? TradeData.MODE_BUY : TradeData.MODE_SELL;
            }
            // Trade 모드에 맞춰 순서에 맞게 거래 타입 생성
            if(TradeData.MODE_BUY.equals(mode)){
                firstAction  = BUY;
                secondAction = SELL;
            }else if(TradeData.MODE_SELL.equals(mode)){
                firstAction  = SELL;
                secondAction = BUY;
            }

            String orderId    = "";
            if( !(orderId  = createOrder(firstAction, price, cnt, symbol)).equals(ReturnCode.NO_DATA.getValue())){    // 매수/OrderId가 있으면 성공
                Thread.sleep(300);
                if(createOrder(secondAction,price,cnt,symbol).equals(ReturnCode.NO_DATA.getValue())){                   // 매도/OrderId가 없으면 실패
                    Thread.sleep(300);
                    cancelOrder(orderId,firstAction, price, symbol);                      // 매도 실패 시, 매수 취소
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[FOBLGATE][AUTOTRADE] Error : {}", e.getMessage());
        }
        log.info("[FOBLGATE][AUTOTRADE END]");
        return returnCode;
    }

    /** 호가유동성 function */
    @Override
    public int startLiquidity(Map list){
        int returnCode = ReturnCode.SUCCESS.getCode();

        Queue<String> sellQueue = (LinkedList) list.get("sell");
        Queue<String> buyQueue  = (LinkedList) list.get("buy");
        List<Map<String,String>> CancelList = new ArrayList();

        try{
            log.info("[FOBLGATE][LIQUIDITY] Start");
            String[] coinData = TradeService.splitCoinWithId(liquidity.getCoin());
            setApiKey(coinData[0], coinData[1], liquidity.getExchange());    // Key 값 셋팅
            String symbol = coinData[0] + "/" + getCurrency(liquidity.getExchange(), coinData[0], coinData[1]);
            int minCnt = liquidity.getMinCnt();
            int maxCnt = liquidity.getMaxCnt();

            while(sellQueue.size() > 0 || buyQueue.size() > 0){
                String mode = (TradeService.getRandomInt(1,2) == 1) ? BUY : SELL;
                String firstOrderId    = "";
                String secondsOrderId  = "";
                String firstPrice      = "";
                String secondsPrice    = "";
                String firstAction     = "";
                String secondAction    = "";
                String firstCnt        = String.valueOf(Math.floor(TradeService.getRandomDouble((double)minCnt, (double)maxCnt) * TradeData.TICK_DECIMAL) / TradeData.TICK_DECIMAL);
                String secondsCnt      = String.valueOf(Math.floor(TradeService.getRandomDouble((double)minCnt, (double)maxCnt) * TradeData.TICK_DECIMAL) / TradeData.TICK_DECIMAL);

                if(sellQueue.size() > 0 && buyQueue.size() > 0 && mode.equals(BUY)){
                    firstPrice   = buyQueue.poll();
                    secondsPrice = sellQueue.poll();
                    firstAction  = BUY;
                    secondAction = SELL;
                }else if(buyQueue.size() > 0 && sellQueue.size() > 0 && mode.equals(SELL)){
                    firstPrice   = sellQueue.poll();
                    secondsPrice = buyQueue.poll();
                    firstAction  = SELL;
                    secondAction = BUY;
                }
                firstOrderId   = createOrder(firstAction,  firstPrice, firstCnt, symbol);
                Thread.sleep(300);
                secondsOrderId = createOrder(secondAction, secondsPrice, secondsCnt, symbol);

                // first / second 둘 중 하나라도 거래가 성사 되었을 경우
                if(!firstOrderId.equals(ReturnCode.NO_DATA.getValue())  || !secondsOrderId.equals(ReturnCode.NO_DATA.getValue())){
                    Thread.sleep(1000);
                    if(!firstOrderId.equals(ReturnCode.NO_DATA.getValue())){
                        cancelOrder(firstOrderId, firstAction, firstPrice, symbol);
                    }
                    Thread.sleep(300);
                    if(!secondsOrderId.equals(ReturnCode.NO_DATA.getValue())){
                        cancelOrder(secondsOrderId, secondAction, secondsPrice, symbol);
                    }
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[FOBLGATE][LIQUIDITY] Error {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[FOBLGATE][LIQUIDITY] End");
        return returnCode;
    }

    /* 매매 긁기 */
    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[FOBLGATE][FISHINGTRADE START]");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String mode       = "";
            String[] coinData = TradeService.splitCoinWithId(fishing.getCoin());
            setApiKey(coinData[0], coinData[1], fishing.getExchange());    // Key 값 셋팅
            String symbol     = coinData[0] + "/" + getCurrency(fishing.getExchange(), coinData[0], coinData[1]);

            boolean noIntervalFlag   = true;    // 해당 플래그를 이용해 마지막 매도/매수 후 바로 intervalTime 없이 바로 다음 매수/매도 진행
            boolean noMatchFirstTick = true;    // 해당 플래그를 이용해 매수/매도를 올린 가격이 현재 최상위 값이 맞는지 다른 사람의 코인을 사지 않게 방지

            for(String temp : list.keySet()){  mode = temp; }
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode);

            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            /* Buy Start */
            log.info("[FOBLGATE][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt = String.valueOf(Math.floor(TradeService.getRandomDouble((double) fishing.getMinContractCnt(), (double) fishing.getMaxContractCnt()) * TradeData.TICK_DECIMAL) / TradeData.TICK_DECIMAL);
                String orderId = "";
                if(TradeData.MODE_BUY.equals(mode)) {
                    orderId = createOrder(BUY, tickPriceList.get(i), cnt, symbol);      // 매수
                }else{
                    orderId = createOrder(SELL, tickPriceList.get(i), cnt, symbol);     // 매도
                }
                if(!orderId.equals(ReturnCode.NO_DATA.getValue())){                                                // 매수/매도가 정상적으로 이뤄졌을 경우 데이터를 list에 담는다
                    Map<String, String> orderMap = new HashMap<>();
                    orderMap.put("order_id" ,orderId);
                    orderMap.put("price"    ,tickPriceList.get(i));
                    orderMap.put("symbol"   ,symbol);
                    orderMap.put("cnt"      ,cnt);
                    orderList.add(orderMap);
                }
                Thread.sleep(500);
            }
            log.info("[FOBLGATE][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");


            /* Sell Start */
            log.info("[FOBLGATE][FISHINGTRADE][START BUY OR SELL TARGET PIECE COIN ]");
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = TradeService.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("cnt"));

                while (cnt.compareTo(new BigDecimal("0")) > 0) {
                    if (!noMatchFirstTick) break;                   // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                    if (noIntervalFlag) Thread.sleep(intervalTime); // intervalTime 만큼 휴식 후 매수 시작
                    String orderId            = "";
                    BigDecimal cntForExcution = new BigDecimal(String.valueOf(Math.floor(TradeService.getRandomDouble((double) fishing.getMinExecuteCnt(), (double) fishing.getMaxExecuteCnt()) * TradeData.TICK_DECIMAL) / TradeData.TICK_DECIMAL));
                    // 남은 코인 수와 매도/매수할 코인수를 비교했을 때, 남은 코인 수가 더 적다면.
                    if (cnt.compareTo(cntForExcution) < 0) {
                        cntForExcution = cnt;
                        noIntervalFlag = false;
                    } else {
                        noIntervalFlag = true;
                    }
                    // 매도/매수 날리기전에 최신 매도/매수값이 내가 건 값이 맞는지 확인
                    String nowFirstTick = "";
                    if(TradeData.MODE_BUY.equals(mode)) {
                        nowFirstTick = coinService.getFirstTick(fishing.getCoin(), fishing.getExchange()).get(TradeData.MODE_BUY);
                    }else{
                        nowFirstTick = coinService.getFirstTick(fishing.getCoin(), fishing.getExchange()).get(TradeData.MODE_SELL);
                    }

                    if (!copiedOrderMap.get("price").equals(nowFirstTick)) {
                        log.info("[FOBLGATE][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        noMatchFirstTick = false;
                        break;
                    }

                    if(TradeData.MODE_BUY.equals(mode)) {
                        orderId = createOrder(SELL, copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol);
                    }else{
                        orderId = createOrder(BUY, copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol);
                    }

                    if(!orderId.equals(ReturnCode.NO_DATA.getValue())){
                        cnt = cnt.subtract(cntForExcution);
                        Thread.sleep(500);
                        if(TradeData.MODE_BUY.equals(mode)) {
                            cancelOrder(orderId, SELL, copiedOrderMap.get("price"),symbol );
                        }else{
                            cancelOrder(orderId, BUY, copiedOrderMap.get("price"),symbol );
                        }
                    }else{
                        break;
                    }
                }
                // 무조건 취소
                Thread.sleep(500);
                if(TradeData.MODE_BUY.equals(mode)) {
                    cancelOrder(orderList.get(i).get("order_id"), BUY, orderList.get(i).get("price") ,symbol);
                }else{
                    cancelOrder(orderList.get(i).get("order_id"), SELL, orderList.get(i).get("price") ,symbol);
                }
            }
            log.info("[FOBLGATE][FISHINGTRADE][END BUY OR SELL TARGET PIECE COIN ]");
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[FOBLGATE][FISHINGTRADE] Error {}", e.getMessage());
            e.printStackTrace();
        }

        log.info("[FOBLGATE][FISHINGTRADE END]");
        return returnCode;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {

        String returnRes = ReturnCode.FAIL.getValue();
        String coin      = coinWithId[0];
        String coinId    = coinWithId[1];
        try{
            setApiKey(coin, coinId, exchange);
            String pairName = coin + "/" + getCurrency(exchange, coin, coinId);

            Map<String, String> header = new HashMap<>();
            header.put("apiKey",keyList.get(API_KEY));
            header.put("pairName",pairName);

            String secretHash    = makeApiHash(keyList.get(API_KEY) + pairName + keyList.get(SECRET_KEY));
            JsonObject returnVal = postHttpMethod(TradeData.FOBLGATE_ORDERBOOK, secretHash, header);
            String status        = returnVal.get("status").getAsString();
            if(status.equals(SUCCESS)){
                returnRes = gson.toJson(returnVal);
                log.info("[FOBLGATE][GET ORDER BOOK] Response : {}", returnRes);
            }else{
                log.error("[FOBLGATE][GET ORDER BOOK] Response:{}", gson.toJson(returnVal));
            }
        }catch (Exception e){
            log.error("[FOBLGATE][GET ORDER BOOK] Error : {}",e.getMessage());
            e.printStackTrace();
        }
        return returnRes;
    }


    /**
     * Realtime Sync 거래
     * @param realtime
     * @return
     */
    public int startRealtimeTrade(JsonObject realtime) {
        log.info("[FOBLGATE][REALTIME SYNC TRADE START]");
        int returnCode   = ReturnCode.SUCCESS.getCode();
        String realtimeChangeRate = "signed_change_rate";

        try {
            String[] currentTick = getCurrentData(TradeService.splitCoinWithId(realtimeSync.getCoin()));
            String openingPrice  = currentTick[0];
            String currentPrice  = currentTick[1];
            String orderId       = "";
            String targetPrice   = "";
            String mode          = "";
            String cnt           = realtimeSync.getLimitTradeCnt().toString();
            String[] coinData    = TradeService.splitCoinWithId(realtimeSync.getCoin());
            setApiKey(coinData[0], coinData[1], realtimeSync.getExchange());    // Key 값 셋팅
            String symbol = coinData[0] + "/" + getCurrency(realtimeSync.getExchange(), coinData[0], coinData[1]);

            int flag = isMoreOrLessPrice(currentPrice);
            if(flag != 0){              // 구간 밖일 경우
                if(flag == -1){         // 지지선보다 낮을 경우
                    mode        = BUY;
                    targetPrice = realtimeSync.getMinPrice();
                }else if(flag == 1){    // 저항선보다 높을 경우
                    mode        = SELL;
                    targetPrice = realtimeSync.getMaxPrice();
                }
                // 그 양만큼 매수하고, 일단 무조건 취소
                if(!(orderId = createOrder(mode,targetPrice, cnt,symbol)).equals(ReturnCode.NO_DATA.getValue())){
                    cancelOrder(orderId, mode, targetPrice, symbol);
                }
            }else{
                // 지정한 범위 안에 없을 경우 매수 혹은 매도로 맞춰준다.
                Map<String,String> tradeInfo = getTargetTick(openingPrice, currentPrice, realtime.get(realtimeChangeRate).getAsString());
                if(!tradeInfo.isEmpty()){
                    targetPrice = tradeInfo.get("price");
                    mode        = tradeInfo.get("mode");
                    if(!(orderId = createOrder(mode,targetPrice, cnt,symbol)).equals(ReturnCode.NO_DATA.getValue())){
                        cancelOrder(orderId, mode, targetPrice, symbol);
                    }
                }
            }
        }catch (Exception e){
            log.error("[FOBLGATE][REALTIME SYNC TRADE] Error :{} ", e.getMessage());
            e.printStackTrace();
        }
        log.info("[FOBLGATE][REALTIME SYNC TRADE END]");
        return returnCode;
    }

    /**
     * minPrice / maxPrice 보다 낮거나 높으면 매수
     * @param currentPriceStr
     * @param realtimeSync
     * @return 해당 구간에 있을 경우 0, 지지선보다 낮을 경우 -1, 저항선보다 높을 경우 1
     */
    private int isMoreOrLessPrice(String currentPriceStr){
        BigDecimal minPrice     = new BigDecimal(realtimeSync.getMinPrice());
        BigDecimal maxPrice     = new BigDecimal(realtimeSync.getMaxPrice());
        BigDecimal currentPrice = new BigDecimal(currentPriceStr);

        if(currentPrice.compareTo(minPrice) < 0){
            return -1;
        }else if(currentPrice.compareTo(maxPrice) > 0){
            return 1;
        }else{
            return 0;
        }
    }

    /**
     * 설정한 구간안에 있는지 확인
     * @param openingPrice
     * @param currentPrice
     * @param realtimePercent
     * @return empty 일 경우 거래할 필요 없음. 그게 아닐 경우 Map에 mode:buy or sell / price:0.00025 형식으로 반환
     * @throws Exception
     */
    private Map<String, String> getTargetTick(String openingPrice, String currentPrice, String realtimePercent) throws Exception{
        int roundUpScale   = 15;     // 반올림 소수점
        Map<String,String> returnMap = new HashMap<>();

        BigDecimal openingDecimalPrice = new BigDecimal(openingPrice);                          // 시가
        BigDecimal currentDecimalPrice = new BigDecimal(currentPrice);                          // 현재가
        BigDecimal differencePrice     = currentDecimalPrice.subtract(openingDecimalPrice);     // 현재가 - 시가
        BigDecimal differencePercent    = differencePrice.divide(openingDecimalPrice, roundUpScale, BigDecimal.ROUND_CEILING); // 시가 대비 현재가 증가 및 감소율

        BigDecimal realtimeDecimalPercent  = new BigDecimal(realtimePercent);                // 실시간 연동하는 코인의 증감률
        BigDecimal syncPercent             = new BigDecimal(realtimeSync.getPricePercent()); // 1~100 까지의 %값
        BigDecimal realtimeTargetPercent   = realtimeDecimalPercent.multiply(syncPercent).divide(new BigDecimal(100),roundUpScale, BigDecimal.ROUND_CEILING); // 실시간 연동 코인 증감률 * 설정 값

        // 1의 자리가 같을 경우 패스 ex) 1.9 == 1.1 같은 선상이라고 보고, 패스함.
        // 추가적으로 -0.3은 -1 로 취급
        BigDecimal realtimeTargetPercentFloor = realtimeTargetPercent.setScale(2, BigDecimal.ROUND_FLOOR);
        BigDecimal differencePercentFloor     = differencePercent.setScale(2, BigDecimal.ROUND_FLOOR);
        if(realtimeTargetPercentFloor.compareTo(differencePercentFloor) != 0){  // 두개의 차이가 같은 구간이 아닐 경우
            if(realtimeTargetPercent.compareTo(differencePercent) < 0){    // 동기화 코인 %가 기준 코인 상승률보다 적으면 true
                returnMap.put("mode",SELL);
            }else if(realtimeTargetPercent.compareTo(differencePercent) > 0){
                returnMap.put("mode",BUY);
            }
        }

        if(!returnMap.isEmpty()){
            // Set target price
            BigDecimal targetPrice = makeTargetPrice(openingDecimalPrice, realtimeTargetPercent);
            returnMap.put("price",targetPrice.toPlainString());

            log.info("[FOBLGATE][REALTIME SYNC TRADE] Realtime target percent with sync: {}, " +
                            "Local target coin : {}, " +
                            "Local target percent : {} , " +
                            "Local target price : {}, " +
                            "Mode : {}",
                    realtimeTargetPercent.multiply(new BigDecimal(100)), realtimeSync.getCoin(),
                    differencePercent.multiply(new BigDecimal(100)), targetPrice.toPlainString(), returnMap.get("mode"));
        }else{
            log.info("[FOBLGATE][REALTIME SYNC TRADE] target price within realtime target price.");
        }

        return returnMap;
    }

    /**
     * realtimeSync 에서 사용하며, 매수/매도해야 할 특정 가격을 구함.
     * @param openingPrice
     * @param targetPercent
     * @throws Exception
     */
    private BigDecimal makeTargetPrice(BigDecimal openingPrice, BigDecimal targetPercent) throws Exception{
        String coinPriceStr   = "";
        String[] coinArr      = TradeService.splitCoinWithId(realtimeSync.getCoin());
        for(ExchangeCoin coin : realtimeSync.getExchange().getExchangeCoin()){
            if(coin.getCoinCode().equals(coinArr[0]) && coin.getId() == Long.parseLong(coinArr[1])){
                coinPriceStr = coin.getCoinPrice();
                break;
            }
        }

        int scale = coinPriceStr.length() - (coinPriceStr.indexOf(".") + 1);    // 어디서 버림을 해야 하는지 값 체크
        BigDecimal coinPrice              = new BigDecimal(coinPriceStr);
        BigDecimal targetPriceWithoutDrop = openingPrice.add(openingPrice.multiply(targetPercent));
        BigDecimal returnTargetPrice      = null;
        if(coinPrice.compareTo(new BigDecimal("1.0")) < 0){
            returnTargetPrice = targetPriceWithoutDrop.setScale(scale, BigDecimal.ROUND_FLOOR);
        }else{
            returnTargetPrice = targetPriceWithoutDrop.subtract(targetPriceWithoutDrop.remainder(coinPrice));
        }
        return returnTargetPrice;
    }

    /**
     * 현재 Tick 가져오기
     * @param exchange
     * @param coinWithId
     * @return [ 시가 , 종가 ] String Array
     */
    private String[] getCurrentData(String[] coinWithId) throws Exception{
        String[] returnRes   = new String[2];
        String coin          = coinWithId[0];
        String coinId        = coinWithId[1];
        String typeDay       = "1";     // 1일 경우 일단위로 데이터 반환
        String min           = "240";   // typeDay 가 0일 경우에만 의미있음.
        String startDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String dateCount     = "2";     // 하루치(2일 경우 어제, 3일경우 그제까지 반환)

        setApiKey(coin, coinId, realtimeSync.getExchange());
        String pairName = coin + "/" + getCurrency(realtimeSync.getExchange(), coin, coinId);
        Map<String, String> header = new HashMap<>();
        header.put("apiKey",keyList.get(API_KEY));
        header.put("pairName",pairName);
        header.put("type",typeDay);
        header.put("min",min);
        header.put("startDateTime",startDateTime);
        header.put("cnt", dateCount);
        String secretHash    = makeApiHash(keyList.get(API_KEY) + pairName + typeDay + min + startDateTime + dateCount + keyList.get(SECRET_KEY));
        JsonObject returnVal = postHttpMethod(TradeData.FOBLGATE_TICK, secretHash, header);
        String status        = returnVal.get("status").getAsString();
        if(status.equals(SUCCESS)){
            // 전날의 종가가 대비로 %가 움직이기에, 해당 값에 맞게 맞춰줘야 함.
            // [ 조회시간 | 시가 | 고가 | 저가 | 종가(현재가) | volume? ] String Array
            JsonObject object = returnVal.get("data").getAsJsonObject();
            JsonArray array   = object.get("series").getAsJsonArray();
            returnRes[0]      = array.get(0).getAsString().split("\\|")[4];  // 전날의 종가가 오늘의 시가
            returnRes[1]      = array.get(1).getAsString().split("\\|")[4];  // 현재의 종가(현재가)
            log.info("[FOBLGATE][GET CURRENT TICK] Response : {}", Arrays.toString(returnRes));
        }else{
            log.error("[FOBLGATE][GET CURRENT TICK] Response:{}", gson.toJson(returnVal));
            throw new Exception(gson.toJson(returnVal));
        }

        return returnRes;
    }



    /** 포블게이트 매수/매도 로직 */

    /**
     * 매수/매도 로직
     * @return 성공 시, order Id. 실패 시, ReturnCode.NO_DATA
     */
    public String createOrder(String type, String price, String cnt, String symbol){

        String orderId = ReturnCode.NO_DATA.getValue();
        try{
            Map<String, String> header = setDefaultRequest(keyList.get(USER_ID), symbol,type,keyList.get(API_KEY));
            header.put("price",     price);   // price
            header.put("amount",    cnt);     // cnt
            String secretHash = makeApiHash(keyList.get(API_KEY) + keyList.get(USER_ID) + symbol + type + price+ cnt+ keyList.get(SECRET_KEY));

            JsonObject returnVal = postHttpMethod(TradeData.FOBLGATE_CREATE_ORDER, secretHash, header);
            String status        = gson.fromJson(returnVal.get("status"), String.class);
            if(status.equals(SUCCESS)){
                orderId = gson.fromJson(returnVal.get("data"), String.class);
                log.info("[FOBLEGATE][CREATE ORDER] Success response : {}", gson.toJson(returnVal));
            }else{
                log.error("[FOBLGATE][CREATE ORDER] Fail response :{}", gson.toJson(returnVal));
            }
        }catch (Exception e){
            log.error("[FOBLGATE][CREATE ORDER] Occur error :{}",e.getMessage());
            e.printStackTrace();
        }
        return orderId;
    }

    /**
     * 취소 로직
     * @return 성공 시, ReturnCode.Success. 실패 시, ReturnCode.Fail
     */
    public int cancelOrder(String ordNo, String type, String price, String symbol){

        int returnCode = ReturnCode.FAIL.getCode();
        try{

            Map<String, String> header = setDefaultRequest(keyList.get(USER_ID), symbol, type, keyList.get(API_KEY));
            header.put("ordNo",     ordNo);                 // order Id
            header.put("ordPrice",  price);                 // price
            String secretHash = makeApiHash(keyList.get(API_KEY) + keyList.get(USER_ID) + symbol + ordNo + type + price+ keyList.get(SECRET_KEY));

            JsonObject returnVal = postHttpMethod(TradeData.FOBLGATE_CANCEL_ORDER, secretHash, header);
            String status        = returnVal.get("status").getAsString();
            if(status.equals(SUCCESS) || status.equals(ALREADY_TRADED)){
                returnCode = ReturnCode.SUCCESS.getCode();
                log.info("[FOBLGATE][CANCEL ORDER] Success response:{}", gson.toJson(returnVal));
            }else{
                log.error("[FOBLGATE][CANCEL ORDER] Fail response:{}", gson.toJson(returnVal));
            }
        }catch (Exception e){
            log.error("[FOBLGATE][CANCEL ORDER] Error {}",e.getMessage());
            e.printStackTrace();
        }
        return returnCode;
    }




    /**
     * 해당 코인 저장 시, 등록한 화폐 정보 반환
     * @throws Exception 호출한 곳에서 예외 처리. / 해당 거래소에 등록된 코인이 없을 경우 Exception 발생
     */
    public String getCurrency(Exchange exchange, String coin, String coinId) throws Exception{
        String returnVal = ReturnCode.FAIL.getValue();
        // 거래소를 체크하는 이유는 거래소에서 같은 코인을 여러개 등록 할 경우 체크하기 위함.
        if(!exchange.getExchangeCoin().isEmpty()){
            for(ExchangeCoin data : exchange.getExchangeCoin()){
                if(data.getCoinCode().equals(coin) && data.getId() == Long.parseLong(coinId)){
                    returnVal = data.getCurrency();
                }
            }
        }
        if(returnVal.equals(ReturnCode.FAIL.getValue())){
            String msg = coin + "is not existed in " + exchange.getExchangeCode();
            throw new Exception(msg);
        }
        return returnVal;
    }


    /**
     * API 이용 전 Hash 값을 만드는 작업
     * @param targetStr
     * @throws Exception 예외는 호출한 곳에서 처리하도록 진행.
     */
    public String makeApiHash(String targetStr) throws Exception{
        StringBuffer sb  = new StringBuffer();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(targetStr.getBytes());
        byte byteData[] = md.digest();
        for (int i = 0; i < byteData.length; i++) {
            sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
        }
        //convert the byte to hex format method 2
        StringBuffer hexString = new StringBuffer();
        for (int i=0;i<byteData.length;i++) {
            String hex=Integer.toHexString(0xff & byteData[i]);
            if(hex.length()==1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * HTTP POST Method for Foblgate
     * @param targetUrl  - target url
     * @param secretHash - 암호화한 값
     * @param formData   - post에 들어가는 body 데이터
     * @throws HTTP 예외 외에도, 서버 에러코드 전송 시에도 예외를 던져 호출한곳에서 처리하도록 진행하였음.
     */
    public JsonObject postHttpMethod(String targetUrl, String secretHash,  Map<String, String> datas ) throws Exception{

        String twoHyphens    = "--";
        String boundary      = "*******";
        String lineEnd       = "\r\n";
        String delimiter     = twoHyphens + boundary + lineEnd;

        log.info("[FOBLGATE][POST HTTP] Url :{} Request:{}, Secret-Hash:{}",targetUrl, datas.toString(), secretHash);
        URL url = new URL(targetUrl);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestProperty("secretheader", secretHash);
        connection.setRequestProperty("Content-Type","multipart/form-data;boundary="+boundary);
        connection.setRequestProperty("Accept"      , "*/*");
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(TradeData.TIMEOUT_VALUE);
        connection.setReadTimeout(TradeData.TIMEOUT_VALUE);
        connection.setDoOutput(true);
        connection.setDoInput(true);
        DataOutputStream dos = new DataOutputStream(connection.getOutputStream());
        for(String key : datas.keySet()){
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\""+ key +"\"" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.writeBytes(datas.get(key));
            dos.writeBytes(lineEnd);
        }
        dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
        dos.flush();
        dos.close();
        int returnCode = connection.getResponseCode();
        BufferedReader br = null;
        if(returnCode == HttpsURLConnection.HTTP_OK){   // OK 일 경우
            br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        }else{                                          // 아닐 경우
            br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
        }
        StringBuffer response = new StringBuffer();
        String inputLine = "";
        while ((inputLine = br.readLine()) != null) {
            response.append(inputLine);
        }
        br.close();


        // 200 응답이 아닐 경우 throw exception
        if(returnCode != HttpsURLConnection.HTTP_OK){
            log.error("[FOBLGATE][POST HTTP] Return Code is not 200. msg : {}", response.toString());
            throw new Exception(response.toString());
        }

        return gson.fromJson(response.toString(), JsonObject.class);
    }

    /**
     * request를 날릴때 Map에 데이터를 담아서 객체형식으로 보내줘야 하는데, 모든 요청에 공통으로 사용되는 값들
     * @param symbol symbol is pairName coin/currency
     * @param type type is action
     */
    private Map<String, String> setDefaultRequest(String userId, String symbol, String type, String apiKey){
        Map<String, String> mapForRequest = new HashMap<>();
        mapForRequest.put("mbId",userId);
        mapForRequest.put("pairName", symbol);
        mapForRequest.put("action", type);
        mapForRequest.put("apiKey", apiKey);

        return mapForRequest;
    }

}
