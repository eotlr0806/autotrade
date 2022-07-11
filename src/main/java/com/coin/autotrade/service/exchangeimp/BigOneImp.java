package com.coin.autotrade.service.exchangeimp;


import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.enumeration.LogAction;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.common.enumeration.Trade;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.*;

/**
 * Coin one 에서 사용할 class
 */
@Slf4j
public class BigOneImp extends AbstractExchange {
    final private String CANCEL_SUCCESS   = "116";
    final private String SUCCESS          = "0";
    final private String BUY              = "BUY";
    final private String SELL             = "SELL";

    /** 자전 거래를 이용하기위한 초기값 설정 */
    @Override
    public void initClass(AutoTrade autoTrade) throws Exception{
        super.autoTrade = autoTrade;
        setCoinToken(Utils.splitCoinWithId(autoTrade.getCoin()), autoTrade.getExchange());
    }

    /** 호가 유동성을 이용하기 위한 초기값 설정 */
    @Override
    public void initClass(Liquidity liquidity) throws Exception {
        super.liquidity  = liquidity;
        setCoinToken(Utils.splitCoinWithId(liquidity.getCoin()), liquidity.getExchange());
    }

    @Override
    public void initClass(RealtimeSync realtimeSync, CoinService coinService) throws Exception{
        super.realtimeSync = realtimeSync;
        super.coinService  = coinService;
        setCoinToken(Utils.splitCoinWithId(realtimeSync.getCoin()), realtimeSync.getExchange());
    }

    /** 매매 긁기를 이용하기 위한 초기값 설정 */
    @Override
    public void initClass(Fishing fishing, CoinService coinService) throws Exception {
        super.fishing     = fishing;
        super.coinService = coinService;
        setCoinToken(Utils.splitCoinWithId(fishing.getCoin()), fishing.getExchange());
    }
    /** 코인 토큰 정보 셋팅 **/
    private void setCoinToken(String[] coinData, Exchange exchange) throws Exception{
        // Set token key
        if(keyList.isEmpty()){
            for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
                if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1]) ){
                    keyList.put(PUBLIC_KEY, exCoin.getPublicKey());
                    keyList.put(SECRET_KEY,   exCoin.getPrivateKey());
                }
            }
            if(keyList.isEmpty()){
                String msg = "There is no match coin. " + Arrays.toString(coinData) + " " + exchange.getExchangeCode();
                throw new Exception(msg);
            }
        }
    }

    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[BIGONE][AUTOTRADE] START");

        int returnCode = ReturnCode.SUCCESS.getCode();
        try{
            // mode 처리
            String[] coinData            = Utils.splitCoinWithId(autoTrade.getCoin());
            Exchange exchange            = autoTrade.getExchange();
            Map<String, String> orderMap = setDefaultMap(cnt, coinData[0], price);

            Trade mode          = getMode(autoTrade.getMode());
            String firstAction  = (mode == Trade.BUY) ? BUY : SELL;
            String secondAction = (mode == Trade.BUY) ? SELL : BUY;
            if(mode == Trade.BUY){
                orderMap.put("is_ask","0"); // 1 if order is sell
            }else{
                orderMap.put("is_ask","1"); // 0 if order is sell
            }

            String orderId = createOrder(firstAction, price, cnt, coinData, exchange);
            if(Utils.isSuccessOrder(orderId)){
                orderMap.put("order_id", orderId);
                if(!Utils.isSuccessOrder(createOrder(secondAction,price, cnt, coinData,exchange))){          // SELL 모드가 실패 시,
                    cancelOrder(orderMap);
                }
            }
        }catch(Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[BIGONE][ERROR][AUTOTRADE] {}", e.getMessage());
        }
        log.info("[BIGONE][AUTOTRADE] END");

        return returnCode;
    }


    @Override
    public int startLiquidity(Map<String, LinkedList<String>> list) {
        int returnCode = ReturnCode.SUCCESS.getCode();

        Queue<String> sellQueue = list.get("sell");
        Queue<String> buyQueue  = list.get("buy");
        Queue<Map<String,String>> cancelList = new LinkedList<>();

        try{
            log.info("[BIGONE][LIQUIDITY] START");
            String[] coinData = Utils.splitCoinWithId(liquidity.getCoin());
            Exchange exchange = liquidity.getExchange();

            while (!sellQueue.isEmpty() || !buyQueue.isEmpty() || !cancelList.isEmpty()) {
                Trade mode         = getMode();
                boolean cancelFlag = (Utils.getRandomInt(1, 2) == 1) ? true : false;
                String orderId     = ReturnCode.FAIL_CREATE.getValue();
                String action      = (mode == Trade.BUY) ? BUY : SELL;
                String cnt         = Utils.getRandomString(liquidity.getMinCnt(), liquidity.getMaxCnt());
                String price       = null;

                Map<String, String> cancelMap  = setDefaultMap(cnt, coinData[0], price);
                if(!buyQueue.isEmpty() && mode == Trade.BUY){
                    price     = buyQueue.poll();
                    cancelMap.put("is_ask", "0");
                }else if(!sellQueue.isEmpty() &&  mode == Trade.SELL){
                    price   = sellQueue.poll();
                    cancelMap.put("is_ask", "1");
                }

                // 매수 로직
                if(price != null){
                    orderId = createOrder(action, price, cnt, coinData, exchange);
                    if(Utils.isSuccessOrder(orderId)){
                        cancelMap.put("order_id", orderId);
                        cancelList.add(cancelMap);
                    }
                    Thread.sleep(1000);
                }
                // 취소 로직
                if(!cancelList.isEmpty() && cancelFlag){
                    Map<String, String> map = cancelList.poll();
                    cancelOrder(map);
                    Thread.sleep(500);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[BIGONE][LIQUIDITY] ERROR : {}", e.getMessage());
        }
        log.info("[BIGONE][LIQUIDITY] END");
        return returnCode;
    }


    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){

        log.info("[BIGONE][FISHINGTRADE] START");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String[] coinWithId = Utils.splitCoinWithId(fishing.getCoin());
            Exchange exchange   = fishing.getExchange();

            Trade mode = Trade.valueOf(String.valueOf(list.keySet().toArray()[0]));
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode.getVal());
            ArrayList<Map<String, String>> orderList = new ArrayList<>();
            String[] coinData = Utils.splitCoinWithId(fishing.getCoin());

            /* Start */
            log.info("[BIGONE][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt     = Utils.getRandomString(fishing.getMinContractCnt(), fishing.getMaxContractCnt());
                String orderId = (mode == Trade.BUY) ?
                        createOrder(BUY,  tickPriceList.get(i), cnt, coinWithId, exchange) :
                        createOrder(SELL, tickPriceList.get(i), cnt, coinWithId, exchange);

                Map<String, String> orderMap = setDefaultMap(cnt, coinData[0], tickPriceList.get(i));
                if(mode == Trade.BUY) {
                    orderMap.put("is_ask","0");
                }else{
                    orderMap.put("is_ask","1");
                }
                if(Utils.isSuccessOrder(orderId)){
                    orderMap.put("order_id",orderId);
                    orderList.add(orderMap);
                }
            }
            log.info("[BIGONE][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");

            /* Sell Start */
            log.info("[BIGONE][FISHINGTRADE][START BUY OR SELL TARGET PIECE COIN ]");
            boolean isSameFirstTick = true;    // 해당 플래그를 이용해 매수/매도를 올린 가격이 현재 최상위 값이 맞는지 다른 사람의 코인을 사지 않게 방지
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = Utils.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("qty"));

                while (cnt.compareTo(BigDecimal.ZERO) > 0) {
                    if (!isSameFirstTick) break;                                        // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                    if(cnt.compareTo(new BigDecimal(copiedOrderMap.get("qty"))) != 0){  // 최초에 매수/매도 주문시에는 interval 적용 X
                        Thread.sleep(intervalTime);                                     // intervalTime 만큼 휴식 후 매수 시작
                    }
                    BigDecimal executionCnt = new BigDecimal(Utils.getRandomString(fishing.getMinExecuteCnt(), fishing.getMaxExecuteCnt()));
                    executionCnt            = (cnt.compareTo(executionCnt) < 0) ? cnt : executionCnt;    // 남은 코인 수와 매도/매수할 코인수를 비교했을 때, 남은 코인 수가 더 적다면 남은 cnt만큼 매수/매도

                    // 매도/매수 날리기전에 최신 매도/매수값이 내가 건 값이 맞는지 확인
                    String nowFirstTick = (mode == Trade.BUY) ?
                            coinService.getFirstTick(fishing.getCoin(), exchange).get(UtilsData.MODE_BUY) :
                            coinService.getFirstTick(fishing.getCoin(), exchange).get(UtilsData.MODE_SELL);

                    String orderPrice = copiedOrderMap.get("price");
                    if (!orderPrice.equals(nowFirstTick)) {
                        log.info("[BIGONE][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        isSameFirstTick = false;
                        break;
                    }

                    String orderId = (mode == Trade.BUY) ?
                            createOrder(SELL, copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange) :
                            createOrder(BUY,  copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange);

                    if(Utils.isSuccessOrder(orderId)){
                        copiedOrderMap.replace("qty", executionCnt.toPlainString());
                        copiedOrderMap.replace("order_id", orderId);
                        cnt = cnt.subtract(executionCnt);
                        Thread.sleep(500);

                        cancelOrder(copiedOrderMap);
                    }else{
                        break;
                    }
                }
                // 무조건 취소
                Thread.sleep(500);
                cancelOrder(orderList.get(i));
            }
            log.info("[BIGONE][FISHINGTRADE][END BUY OR SELL TARGET PIECE COIN ]");
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[BIGONE][FISHINGTRADE] ERROR : {}", e.getMessage());
        }

        log.info("[BIGONE][FISHINGTRADE] END");
        return returnCode;


    }

    /**
     * Realtime Sync 거래
     * @param realtime
     * @return
     */
    public int startRealtimeTrade(JsonObject realtime, boolean resetFlag) {
        log.info("[BIGONE][REALTIME SYNC TRADE] START");
        int returnCode                      = ReturnCode.SUCCESS.getCode();
        String realtimeChangeRate           = "signed_change_rate";
        Map<String, String> cancelMap       = null;

        try {
            boolean isStart      = false;
            String[] coinWithId  = Utils.splitCoinWithId(realtimeSync.getCoin());
            Exchange exchange    = realtimeSync.getExchange();
            String[] currentTick = getTodayTick(coinWithId[0]);
            //            String openingPrice  = currentTick[0];
            if(resetFlag){
                realtimeTargetInitRate = currentTick[1];
                log.info("[BIGONE][REALTIME SYNC TRADE] Set init open rate : {} ", realtimeTargetInitRate);
            }
            String openingPrice  = realtimeTargetInitRate;
            String currentPrice  = currentTick[1];
            log.info("[BIGONE][REALTIME SYNC TRADE] open:{}, current:{} ", openingPrice, currentPrice);

            String targetPrice   = "";
            String action        = "";
            String mode          = "";
            String cnt           = Utils.getRandomString(realtimeSync.getMinTradeCnt(), realtimeSync.getMaxTradeCnt());
            int isInRange = isMoreOrLessPrice(currentPrice);

            if(isInRange != 0){              // 구간 밖일 경우
                if(isInRange == -1){         // 지지선보다 낮을 경우
                    action       = BUY;
                    mode         = UtilsData.MODE_BUY;
                    targetPrice  = realtimeSync.getMinPrice();
                    cancelMap    = setDefaultMap(cnt, coinWithId[0], targetPrice);
                    cancelMap.put("is_ask", "0");
                }else if(isInRange == 1){    // 저항선보다 높을 경우
                    action       = SELL;
                    mode         = UtilsData.MODE_SELL;
                    targetPrice  = realtimeSync.getMaxPrice();
                    cancelMap    = setDefaultMap(cnt, coinWithId[0], targetPrice);
                    cancelMap.put("is_ask", "1");
                }

                isStart = true;
            }else{
                // 지정한 범위 안에 없을 경우 매수 혹은 매도로 맞춰준다.
                Map<String,String> tradeInfo = getTargetTick(openingPrice, currentPrice, realtime.get(realtimeChangeRate).getAsString());
                if(!tradeInfo.isEmpty()){
                    targetPrice = tradeInfo.get("price");
                    if(tradeInfo.get("mode").equals(UtilsData.MODE_BUY)){
                        action      = BUY;
                        mode        = UtilsData.MODE_BUY;
                        cancelMap   = setDefaultMap(cnt, coinWithId[0], targetPrice);
                        cancelMap.put("is_ask", "0");
                    }else{
                        action      = SELL;
                        mode        = UtilsData.MODE_SELL;
                        cancelMap   = setDefaultMap(cnt, coinWithId[0], targetPrice);
                        cancelMap.put("is_ask", "1");
                    }
                    isStart = true;
                }
            }

            if(isStart){
                String orderId = createOrder(action, targetPrice, cnt, coinWithId, exchange);
                if(Utils.isSuccessOrder(orderId)){
                    Thread.sleep(300);
                    cancelMap.put("order_id", orderId);

                    // 3. bestoffer set 로직
                    JsonArray array = makeBestofferAfterRealtimeSync(targetPrice, mode);
                    for (int i = 0; i < array.size(); i++) {
                        JsonObject object       = array.get(i).getAsJsonObject();
                        String bestofferPrice   = object.get("price").getAsString();
                        String bestofferCnt     = object.get("cnt").getAsString();
                        String bestofferOrderId = createOrder(action, bestofferPrice, bestofferCnt, coinWithId, exchange);
                        if(Utils.isSuccessOrder(bestofferOrderId)){
                            log.info("[BIGONE][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                    }

                    Thread.sleep(300);
                    cancelOrder(cancelMap);
                }
            }
        }catch (Exception e){
            log.error("[BIGONE][REALTIME SYNC TRADE] ERROR :{} ", e.getMessage());
        }
        log.info("[BIGONE][REALTIME SYNC TRADE] END");
        return returnCode;
    }


    /**
     * 현재 Tick 가져오기
     * @param coin
     * @return [ 시가 , 종가 ] String Array
     */
    private String[] getTodayTick(String coin) throws Exception{

        String[] returnRes   = new String[2];
        String request       = UtilsData.COINONE_TICK + "?currency=" + URLEncoder.encode(coin);
        String response      = getHttpMethod(request);
        JsonObject resObject = gson.fromJson(response, JsonObject.class);
        String returnCode    = resObject.get("errorCode").getAsString();
        if(SUCCESS.equals(returnCode)){
            returnRes[0] = resObject.get("yesterday_last").getAsString();
            returnRes[1] = resObject.get("last").getAsString();
        }else{
            log.error("[BIGONE][GET TODAY TICK] response : {}", response);
            throw new Exception(response);
        }
        return returnRes;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = "";
        try{
            log.info("[BIGONE][GET ORDER BOOK] START");
            String request  = UtilsData.BIGONE_URL + "/asset_pairs/" + getSymbol(coinWithId, exchange) + "/depth";
            returnRes = getHttpMethod(request);
            String result   = gson.fromJson(returnRes, JsonObject.class).get("code").getAsString();
            if(!SUCCESS.equals(result)){
                log.error("[BIGONE][ORDER BOOK] Error : {}",returnRes);
                insertLog(request, LogAction.ORDER_BOOK, returnRes);
            }
            log.info("[BIGONE][Order book] END");
        }catch (Exception e){
            log.error("[BIGONE][ORDER BOOK] Error : {}",e.getMessage());
            insertLog(Arrays.toString(coinWithId), LogAction.ORDER_BOOK, e.getMessage());
        }

        return returnRes;
    }


    @Override
    public String createOrder(String type, String price, String cnt , String[] coinData, Exchange exchange){
        String orderId = ReturnCode.FAIL_CREATE.getValue();

        try{
            setCoinToken(coinData, exchange);
            String action     = parseAction(type);
            String url        = ( action.equals(BUY) ) ? UtilsData.COINONE_LIMIT_BUY : UtilsData.COINONE_LIMIT_SELL;
            Map<String, String> header = setDefaultRequest(cnt, coinData[0], price);
            JsonObject json   = postHttpMethod(url, gson.toJson(header));
            String returnCode = json.get("errorCode").getAsString();
            if(SUCCESS.equals(returnCode)){
                orderId = json.get("orderId").getAsString();
                log.info("[BIGONE][CREATE ORDER] Response. mode :{}, response :{}",  action, gson.toJson(json));
            }else{
                log.error("[BIGONE][CREATE ORDER] Response. mode :{}, response :{}", action, gson.toJson(json));
                insertLog(gson.toJson(header), LogAction.CREATE_ORDER, gson.toJson(json));
            }
        }catch (Exception e){
            log.error("[BIGONE][CREATE ORDER] Error {}", e.getMessage());
            insertLog("", LogAction.CREATE_ORDER, e.getMessage());
        }
        return orderId;
    }


    /**
     * 취소 로직
     * @param data
     * @return
     */
    public int cancelOrder(Map<String,String> data){
        int returnValue = ReturnCode.FAIL.getCode();

        try{
            Map<String, String> header = setDefaultRequest(data.get("qty"), data.get("currency"), data.get("price"));
            header.put("is_ask",  data.get("is_ask"));
            header.put("order_id",data.get("order_id"));

            JsonObject json   = postHttpMethod(UtilsData.COINONE_CANCEL , gson.toJson(header));
            String returnCode = json.get("errorCode").getAsString();

            if(SUCCESS.equals(returnCode) || CANCEL_SUCCESS.equals(returnCode)){
                returnValue = ReturnCode.SUCCESS.getCode();
                log.info("[BIGONE][CANCEL ORDER] Response : {} ",  gson.toJson(json) );
            }else{
                log.error("[BIGONE][CANCEL ORDER] Response : {}" , gson.toJson(json));
                insertLog(gson.toJson(header), LogAction.CANCEL_ORDER, gson.toJson(json));
            }
        }catch (Exception e){
            log.error("[BIGONE][CANCEL ORDER] ERROR {}", e.getMessage());
            insertLog("", LogAction.CANCEL_ORDER, e.getMessage());
        }
        return returnValue;
    }

    @Override
    public String getBalance(String[] coinData, Exchange exchange) throws Exception{
        String returnValue = ReturnCode.NO_DATA.getValue();;

        setCoinToken(coinData, exchange);
        Map<String, String> header = new LinkedHashMap<>();
        header.put("access_token", keyList.get(PUBLIC_KEY));
        header.put("nonce", String.valueOf(System.currentTimeMillis()));

        JsonObject json   = postHttpMethod(UtilsData.COINONE_BALANCE , gson.toJson(header));
        String returnCode = json.get("errorCode").getAsString();
        if(SUCCESS.equals(returnCode)){
            returnValue = gson.toJson(json);
            log.info("[BIGONE][GET BALANCE] Success Response");
        }else{
            log.error("[BIGONE][GET BALANCE] Response : {}" , gson.toJson(json));
            insertLog(gson.toJson(header), LogAction.BALANCE, gson.toJson(json));
            throw new Exception(gson.toJson(json));
        }
        return returnValue;
    }

    private String makeHmacSignature(String value, String secret) throws Exception{

        SecretKeySpec signingKey = new SecretKeySpec(secret.getBytes(), "HmacSHA512");
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(signingKey);
        byte[] hexBytes = new Hex().encode(mac.doFinal(value.getBytes()));
        return new String(hexBytes, "UTF-8");
    }

    /* default 로 필요한 데이터를 받아 buy/sell/cancel 메서드에 전달 */
    private Map<String,String> setDefaultMap(String cnt, String currency, String price) throws Exception{
        Map<String, String> defaultMap = new HashMap<>();
        defaultMap.put("qty",cnt);
        defaultMap.put("currency",currency);
        defaultMap.put("price", price);
        return defaultMap;
    }

    /* default 로 필요한 데이터를 받아 request 전 셋팅 후 반환 */
    private Map<String, String> setDefaultRequest(String cnt, String currency, String price) throws Exception{
        Thread.sleep(300);
        long nonce = System.currentTimeMillis();
        Map<String, String> defaultMap = new LinkedHashMap<>();
        defaultMap.put("access_token", keyList.get(PUBLIC_KEY));
        defaultMap.put("nonce", String.valueOf(nonce));
        defaultMap.put("qty", String.valueOf(Double.parseDouble(cnt)));
        defaultMap.put("currency", currency);
        defaultMap.put("price", price);
        return defaultMap;
    }

    /* HTTP POST Method for coinone */
    private JsonObject postHttpMethod(String targetUrl, String payload) throws Exception {
        log.info("[BIGONE][POST REQUEST] post http start");
        log.info("[BIGONE][POST REQUEST] Target url : {}, Request : {}", targetUrl, payload);

        String encodingPayload = Base64.encodeBase64String(payload.getBytes());    // Encoding to base 64
        String signature = makeHmacSignature(encodingPayload, keyList.get(SECRET_KEY).toUpperCase());
        Map<String, String> header = new HashMap<>();
        header.put("X-COINONE-PAYLOAD", encodingPayload);
        header.put("X-COINONE-SIGNATURE", signature);

        HttpEntity<String> response = restTemplate.exchange(
                targetUrl,
                HttpMethod.POST,
                new HttpEntity<String>(payload, getHeader(header)),
                String.class
        );
        log.info("[BIGONE][POST REQUEST] post http end");
        return gson.fromJson(response.getBody(), JsonObject.class);
    }

    private String parseAction(String action){
        if(isExternalAction(action)){
            if(Trade.BUY.equals(action)){
                return BUY;
            }else{
                return SELL;
            }
        }
        return action;
    }

    private boolean isExternalAction(String action){
        if(!action.equals(BUY) && !action.equals(SELL)){
            return true;
        }else{
            return false;
        }
    }
    private void insertLog(String request, LogAction action, String msg){
        exceptionLog.makeLogAndInsert("빅원",request, action, msg);
    }

    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0]+ "-" + getCurrency(exchange,coinData[0], coinData[1]);
    }

}

