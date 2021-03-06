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
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.awt.*;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.*;
import java.util.List;

/**
 * Coin one 에서 사용할 class
 */
@Slf4j
public class BigOneImp extends AbstractExchange {
    final private String CANCEL_SUCCESS   = "10013";
    final private String SUCCESS          = "0";
    final private String BUY              = "BID";
    final private String SELL             = "ASK";

    /** 자전 거래를 이용하기위한 초기값 설정 */
    @Override
    public void initClass(AutoTrade autoTrade) throws Exception{
        super.autoTrade = autoTrade;
    }

    /** 호가 유동성을 이용하기 위한 초기값 설정 */
    @Override
    public void initClass(Liquidity liquidity) throws Exception {
        super.liquidity  = liquidity;
    }

    @Override
    public void initClass(RealtimeSync realtimeSync, CoinService coinService) throws Exception{
        super.realtimeSync = realtimeSync;
        super.coinService  = coinService;
    }

    /** 매매 긁기를 이용하기 위한 초기값 설정 */
    @Override
    public void initClass(Fishing fishing, CoinService coinService) throws Exception {
        super.fishing     = fishing;
        super.coinService = coinService;
    }

    private String getJwtToken(String[] coinData, Exchange exchange) throws Exception{

        String token = "";
        for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
            if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1])){
                    LinkedHashMap header = new LinkedHashMap();
                    header.put("typ","JWT");
                    header.put("alg","HS256");

                    LinkedHashMap payload = new LinkedHashMap();
                    payload.put("type","OpenAPIV2");
                    payload.put("sub", exCoin.getPublicKey());
                    payload.put("nonce", getPing());

                    token = Utils.getJwtToken(header, payload, exCoin.getPrivateKey());
            }
        }

        if(!StringUtils.hasText(token)){
            String msg = "There is no match coin. " + Arrays.toString(coinData) + " " + exchange.getExchangeCode();
            log.error("[BIGONE][getJwtToken] get token error");
            log.error("[BIGONE][getJwtToken] error : {}", msg);
            throw new Exception(msg);
        }
        return token;
    }

    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[BIGONE][AUTOTRADE] START");

        int returnCode = ReturnCode.SUCCESS.getCode();
        try{
            // mode 처리
            String[] coinData   = Utils.splitCoinWithId(autoTrade.getCoin());
            Exchange exchange   = autoTrade.getExchange();
            Trade mode          = getMode(autoTrade.getMode());
            String firstAction  = (mode == Trade.BUY) ? BUY : SELL;
            String secondAction = (mode == Trade.BUY) ? SELL : BUY;

            String firstOrderId  = createOrder(firstAction,price, cnt, coinData, exchange);
            if(Utils.isSuccessOrder(firstOrderId)){
                String secondOrderId = createOrder(secondAction, price, cnt, coinData, exchange);

                Thread.sleep(500);
                cancelOrder(firstOrderId, coinData, exchange);                      // 매도 실패 시, 매수 취소
                if(Utils.isSuccessOrder(secondOrderId)){
                    cancelOrder(secondOrderId, coinData, exchange);                      // 매도 실패 시, 매수 취소
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

        Queue<String> sellQueue   = list.get("sell");
        Queue<String> buyQueue    = list.get("buy");
        Queue<String> cancelQueue = new LinkedList<>();

        try{
            log.info("[BIGONE][LIQUIDITY] START");
            String[] coinData = Utils.splitCoinWithId(liquidity.getCoin());
            Exchange exchange = liquidity.getExchange();

            while (!sellQueue.isEmpty() || !buyQueue.isEmpty() || !cancelQueue.isEmpty()) {
                Trade mode         = getMode();
                boolean cancelFlag = (Utils.getRandomInt(1, 2) == 1) ? true : false;
                String orderId     = ReturnCode.FAIL_CREATE.getValue();
                String action      = (mode == Trade.BUY) ? BUY : SELL;
                String cnt         = Utils.getRandomString(liquidity.getMinCnt(), liquidity.getMaxCnt());
                String price       = null;

                if(!buyQueue.isEmpty() && mode == Trade.BUY){
                    price = buyQueue.poll();
                }else if(!sellQueue.isEmpty() &&  mode == Trade.SELL){
                    price = sellQueue.poll();
                }

                // 매수 로직
                if(price != null){
                    orderId = createOrder(action, price, cnt, coinData, exchange);
                    if(Utils.isSuccessOrder(orderId)){
                        cancelQueue.add(orderId);
                    }
                    Thread.sleep(1000);
                }
                // 취소 로직
                if(!cancelQueue.isEmpty() && cancelFlag){
                    cancelOrder(cancelQueue.poll(), coinData, exchange);
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

                if(Utils.isSuccessOrder(orderId)){
                    Map<String, String> orderMap = new HashMap<>();
                    orderMap.put("qty",cnt);
                    orderMap.put("price", tickPriceList.get(i));
                    orderMap.put("id",orderId);
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
                        copiedOrderMap.replace("id", orderId);
                        cnt = cnt.subtract(executionCnt);
                        Thread.sleep(500);

                        cancelOrder(copiedOrderMap.get("id"), coinWithId, exchange);
                    }else{
                        break;
                    }
                }
                // 무조건 취소
                Thread.sleep(500);
                cancelOrder(orderList.get(i).get("id"), coinWithId, exchange);
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
        Map<String, String> cancelMap       = new HashMap<>();

        try {
            boolean isStart      = false;
            String[] coinWithId  = Utils.splitCoinWithId(realtimeSync.getCoin());
            Exchange exchange    = realtimeSync.getExchange();
            String[] currentTick = getTodayTick(coinWithId, exchange);
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
                }else if(isInRange == 1){    // 저항선보다 높을 경우
                    action       = SELL;
                    mode         = UtilsData.MODE_SELL;
                    targetPrice  = realtimeSync.getMaxPrice();
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
                    }else{
                        action      = SELL;
                        mode        = UtilsData.MODE_SELL;
                    }
                    isStart = true;
                }
            }

            if(isStart){
                String orderId = createOrder(action, targetPrice, cnt, coinWithId, exchange);
                if(Utils.isSuccessOrder(orderId)){
                    Thread.sleep(300);
                    cancelMap.put("id", orderId);

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
                    cancelOrder(cancelMap.get("id"), coinWithId, exchange);
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
    private String[] getTodayTick(String[] coinWithId, Exchange exchange) throws Exception{

        String[] returnRes   = new String[2];
        String request       = UtilsData.BIGONE_URL + "/asset_pairs/" + getSymbol(coinWithId, exchange) + "/candles?period=day1&limit=1";
        String response      = getHttpMethod(request);
        JsonObject resObject = gson.fromJson(response, JsonObject.class);
        String returnCode    = resObject.get("code").getAsString();
        if(SUCCESS.equals(returnCode)){
            JsonObject data = resObject.get("data").getAsJsonArray().get(0).getAsJsonObject();
            returnRes[0] = data.get("open").getAsString();
            returnRes[1] = data.get("close").getAsString();
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
            String action     = parseAction(type);
            String url        = UtilsData.BIGONE_URL + UtilsData.BIGONE_CREATE_ORDER;

            Map<String, String> header = new LinkedHashMap<>();
            header.put("asset_pair_name", getSymbol(coinData, exchange));
            header.put("side", action);
            header.put("price", price);
            header.put("amount", cnt);
            header.put("type", "LIMIT");

            JsonObject json   = postHttpMethod(url, gson.toJson(header), getJwtToken(coinData, exchange));
            String returnCode = json.get("code").getAsString();
            if(SUCCESS.equals(returnCode)){
                orderId = json.get("data").getAsJsonObject().get("id").getAsString();
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


    public int cancelOrder(String id, String[] coinData, Exchange exchange){
        int returnValue = ReturnCode.FAIL.getCode();

        try{
            String token = getJwtToken(coinData, exchange);
            String url = UtilsData.BIGONE_URL + "/viewer/orders/" + id + "/cancel";

            JsonObject json   = postHttpMethod(url , "" , token);
            String returnCode = json.get("code").getAsString();

            if(SUCCESS.equals(returnCode) || CANCEL_SUCCESS.equals(returnCode)){
                returnValue = ReturnCode.SUCCESS.getCode();
                log.info("[BIGONE][CANCEL ORDER] Response : {} ",  gson.toJson(json) );
            }else{
                log.error("[BIGONE][CANCEL ORDER] Response : {}" , gson.toJson(json));
                insertLog(url, LogAction.CANCEL_ORDER, gson.toJson(json));
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

        String uri   = UtilsData.BIGONE_URL + UtilsData.BIGONE_BALANCE;
        String token = getJwtToken(coinData, exchange);
        Map<String, String> header = new HashMap<>();
        header.put("Authorization", "Bearer " + token);

        String response = getHttpMethod(uri, header);
        JsonObject json   = gson.fromJson(response, JsonObject.class);
        String returnCode = json.get("code").getAsString();
        if(SUCCESS.equals(returnCode)){
            returnValue = gson.toJson(json.get("data"));
            log.info("[BIGONE][GET BALANCE] Success Response");
        }else{
            log.error("[BIGONE][GET BALANCE] Response : {}" , response);
            insertLog(gson.toJson(header), LogAction.BALANCE, response);
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

    /* HTTP POST Method for coinone */
    private JsonObject postHttpMethod(String targetUrl, String payload, String token) throws Exception {
        log.info("[BIGONE][POST REQUEST] post http start");
        log.info("[BIGONE][POST REQUEST] Target url : {}, Request : {}", targetUrl, payload);

        Map<String, String> header = new HashMap<>();
        header.put("Authorization", "Bearer " + token);

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

    private String getPing() throws Exception {
        String returnTime = "";
        String time = getHttpMethod(UtilsData.BIGONE_URL + "/ping");
        JsonObject jsonObject = gson.fromJson(time, JsonObject.class);

        if(SUCCESS.equals(jsonObject.get("code").getAsString())){
            returnTime = jsonObject.get("data").getAsJsonObject().get("Timestamp").getAsString();
        }
        if(!StringUtils.hasText(returnTime)){
            log.error("[BIGONE][getPing] ping is error");
            log.error("[BIGONE][getPing] error : {}", time);
        }
        return returnTime;
    }
}

