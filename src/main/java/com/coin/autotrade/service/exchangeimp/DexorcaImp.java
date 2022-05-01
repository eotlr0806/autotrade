package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.common.enumeration.Trade;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

@Slf4j
public class DexorcaImp extends AbstractExchange {
    private String USER_ID               = "userId";
    private String SELL                  = "place_sell";
    private String BUY                   = "place_buy";
    private String SUCCESS               = "0";
    private String ALREADY_TRADED        = "19414";
    private String noChangedUserId       = "";
    private String ORDER_DATE            = "order_date";
    private String ORDER_ID              = "order_id";
    private int SLEEP_TIME               = 600000;
    private long VARIABLE                = 100000000L;

    private enum Error {
        NO_MONEY(19208,"잔액 부족"),
        GAS(19212,"가스필요");

        int code;
        String msg;
        Error(int code, String msg){
            this.code = code;
            this.msg  = msg;
        }
    }

    /* Foblgate Function initialize for autotrade */
    @Override
    public void initClass(AutoTrade autoTrade) throws Exception{
        super.autoTrade = autoTrade;
        setApiKey(Utils.splitCoinWithId(autoTrade.getCoin()), autoTrade.getExchange());
    }

    /* Foblgate Function initialize for liquidity */
    @Override
    public void initClass(Liquidity liquidity) throws Exception{
        super.liquidity = liquidity;
        setApiKey(Utils.splitCoinWithId(liquidity.getCoin()), liquidity.getExchange());
    }

    @Override
    public void initClass(RealtimeSync realtimeSync, CoinService coinService) throws Exception{
        super.realtimeSync = realtimeSync;
        super.coinService  = coinService;
        setApiKey(Utils.splitCoinWithId(realtimeSync.getCoin()), realtimeSync.getExchange());
    }

    /* Foblgate Function initialize for fishing */
    @Override
    public void initClass(Fishing fishing, CoinService coinService) throws Exception{
        super.fishing     = fishing;
        super.coinService = coinService;
        setApiKey(Utils.splitCoinWithId(fishing.getCoin()), fishing.getExchange());
    }

    /** 해당 정보를 이용해 API 키를 셋팅한다 */
    private void setApiKey(String[] coinData, Exchange exchange) throws Exception{

        String userId = "";
        for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
            if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1])){
                userId = exCoin.getExchangeUserId();
                setUserId(userId);
                if(!UtilsData.DEXORCA_SESSION_KEY.containsKey(exCoin.getExchangeUserId())){

                    JsonObject object = getOjbect("login");
                    JsonObject params = new JsonObject();
                    params.addProperty("account",  userId);
                    params.addProperty("password", exCoin.getApiPassword());
                    object.add("params", params);

                    JsonObject response = postHttpMethod(object);
                    String sessionId    = response.getAsJsonObject("result").get("session_id").getAsString();
                    UtilsData.DEXORCA_SESSION_KEY.put(exCoin.getExchangeUserId(), sessionId);

                    log.info("[DEXORCA][MAKE SESSION KEY] First set account id:{}, session:{}", userId, UtilsData.DEXORCA_SESSION_KEY.get(userId));
                }else{
                    log.info("[DEXORCA][MAKE SESSION KEY] Already set account id:{}, session:{}", userId, UtilsData.DEXORCA_SESSION_KEY.get(userId));
                }
            }
        }

        if(!UtilsData.DEXORCA_SESSION_KEY.containsKey(userId)){
            String msg = "There is no match coin. " + Arrays.toString(coinData) + " " + exchange.getExchangeCode();
            throw new Exception(msg);
        }
    }

    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[DEXORCA][AUTOTRADE START]");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String[] coinWithId = Utils.splitCoinWithId(autoTrade.getCoin());
            Exchange exchange   = autoTrade.getExchange();
            setApiKey(coinWithId, exchange);
            String symbol = getSymbol(coinWithId, exchange);

            Trade mode          = getMode(autoTrade.getMode());
            String firstAction  = (mode == Trade.BUY) ? BUY : SELL;
            String secondAction = (mode == Trade.BUY) ? SELL : BUY;

            Map<String, String> firstOrderMap  = parseStringToMap(createOrder(firstAction,price, cnt, coinWithId, exchange));
            if(firstOrderMap != null){
                Map<String, String> secondOrderMap = parseStringToMap(createOrder(secondAction, price, cnt, coinWithId, exchange));
                cancelOrder(firstOrderMap,  firstAction, price, cnt, symbol);
                if(secondOrderMap != null){
                    cancelOrder(secondOrderMap, secondAction,price, cnt, symbol);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[DEXORCA][AUTOTRADE] Error : {}", e.getMessage());
        }
        log.info("[DEXORCA][AUTOTRADE END]");
        return returnCode;
    }

    /** 호가유동성 function */
    @Override
    public int startLiquidity(Map list){
        int returnCode = ReturnCode.SUCCESS.getCode();

        Queue<String> sellQueue              = (LinkedList) list.get("sell");
        Queue<String> buyQueue               = (LinkedList) list.get("buy");
        Queue<Map<String,Object>> cancelList = new LinkedList<>();

        try{
            String[] coinWithId = Utils.splitCoinWithId(liquidity.getCoin());
            setApiKey(coinWithId, liquidity.getExchange());
            log.info("[DEXORCA][LIQUIDITY] START");
            String symbol = getSymbol(Utils.splitCoinWithId(liquidity.getCoin()), liquidity.getExchange());

            while (!sellQueue.isEmpty() || !buyQueue.isEmpty() || !cancelList.isEmpty()) {
                String mode                  = (Utils.getRandomInt(1, 2) == 1) ? UtilsData.MODE_BUY : UtilsData.MODE_SELL;
                boolean cancelFlag           = (Utils.getRandomInt(1, 2) == 1) ? true : false;
                Map<String,String> orderMap  = null;
                String price                 = "";
                String action                = "";
                String cnt                   = Utils.getRandomString(liquidity.getMinCnt(), liquidity.getMaxCnt());

                if(!buyQueue.isEmpty() && mode.equals(UtilsData.MODE_BUY)){
                    price   = buyQueue.poll();
                    action  = BUY;
                }else if(!sellQueue.isEmpty() && mode.equals(UtilsData.MODE_SELL)){
                    price   = sellQueue.poll();
                    action  = SELL;
                }
                // 매수 로직
                if(!action.equals("")){
                    orderMap = parseStringToMap(createOrder(action, price, cnt, coinWithId, liquidity.getExchange()));
                    if((orderMap != null)){
                        Map<String, Object> cancel = new HashMap<>();
                        cancel.put("orderMap",orderMap);
                        cancel.put("action",  action);
                        cancel.put("price",   price);
                        cancel.put("cnt",      cnt);
                        cancelList.add(cancel);
                    }
                    Thread.sleep(1000);
                }

                // 취소 로직
                if(!cancelList.isEmpty() && cancelFlag){
                    Map<String, Object> cancelMap = cancelList.poll();
                    Map<String, String> cancelId  = (Map<String, String>) cancelMap.get("orderMap");
                    String cancelAction           = String.valueOf(cancelMap.get("action"));
                    String cancelprice            = String.valueOf(cancelMap.get("price"));
                    String cancelcnt              = String.valueOf(cancelMap.get("cnt"));
                    cancelOrder(cancelId, cancelAction, cancelprice,cancelcnt, symbol);
                    Thread.sleep(500);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[DEXORCA][LIQUIDITY] Error {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[DEXORCA][LIQUIDITY] END");
        return returnCode;
    }

    /* 매매 긁기 */
    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[DEXORCA][FISHINGTRADE] START");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String[] coinWithId = Utils.splitCoinWithId(fishing.getCoin());
            setApiKey(coinWithId, fishing.getExchange());
            String mode   = "";
            String symbol = getSymbol(Utils.splitCoinWithId(fishing.getCoin()), fishing.getExchange());

            boolean noIntervalFlag   = true;    // 해당 플래그를 이용해 마지막 매도/매수 후 바로 intervalTime 없이 바로 다음 매수/매도 진행
            boolean noMatchFirstTick = true;    // 해당 플래그를 이용해 매수/매도를 올린 가격이 현재 최상위 값이 맞는지 다른 사람의 코인을 사지 않게 방지

            for(String temp : list.keySet()){  mode = temp; }
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode);
            ArrayList<Map<String, Object>> orderList = new ArrayList<>();

            /* Buy Start */
            log.info("[DEXORCA][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt                   = Utils.getRandomString(fishing.getMinContractCnt(), fishing.getMaxContractCnt());
                Map<String, String> orderMap = null;
                String action                = null;
                if(UtilsData.MODE_BUY.equals(mode)) {
                    orderMap = parseStringToMap(createOrder(BUY, tickPriceList.get(i), cnt, coinWithId, fishing.getExchange()));      // 매수
                    action   = BUY;
                }else{
                    orderMap = parseStringToMap(createOrder(SELL, tickPriceList.get(i), cnt, coinWithId, fishing.getExchange()));     // 매도
                    action   = SELL;
                }
                if((orderMap != null) ){                                                // 매수/매도가 정상적으로 이뤄졌을 경우 데이터를 list에 담는다
                    Map<String, Object> map = new HashMap<>();
                    map.put("orderMap" ,map);
                    map.put("action",  action);
                    map.put("price",   tickPriceList.get(i));
                    map.put("cnt",      cnt);
                    orderList.add(map);
                }
                Thread.sleep(500);
            }
            log.info("[DEXORCA][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");


            /* Sell Start */
            log.info("[DEXORCA][FISHINGTRADE][START BUY OR SELL TARGET PIECE COIN ]");
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, Object> copiedOrderMap = orderList.get(i);
                BigDecimal cnt                     = new BigDecimal(String.valueOf(copiedOrderMap.get("cnt")));

                while (cnt.compareTo(new BigDecimal("0")) > 0) {
                    if (!noMatchFirstTick) break;                   // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                    if (noIntervalFlag) Thread.sleep(intervalTime); // intervalTime 만큼 휴식 후 매수 시작
                    Map<String, String> orderId = null;
                    BigDecimal cntForExcution = new BigDecimal(Utils.getRandomString(fishing.getMinExecuteCnt(), fishing.getMaxExecuteCnt()));
                    // 남은 코인 수와 매도/매수할 코인수를 비교했을 때, 남은 코인 수가 더 적다면.
                    if (cnt.compareTo(cntForExcution) < 0) {
                        cntForExcution = cnt;
                        noIntervalFlag = false;
                    } else {
                        noIntervalFlag = true;
                    }
                    // 매도/매수 날리기전에 최신 매도/매수값이 내가 건 값이 맞는지 확인
                    String nowFirstTick = "";
                    if(UtilsData.MODE_BUY.equals(mode)) {
                        nowFirstTick = coinService.getFirstTick(fishing.getCoin(), fishing.getExchange()).get(UtilsData.MODE_BUY);
                    }else{
                        nowFirstTick = coinService.getFirstTick(fishing.getCoin(), fishing.getExchange()).get(UtilsData.MODE_SELL);
                    }

                    if (!String.valueOf(copiedOrderMap.get("price")).equals(nowFirstTick)) {
                        log.info("[DEXORCA][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        noMatchFirstTick = false;
                        break;
                    }

                    if(UtilsData.MODE_BUY.equals(mode)) {
                        orderId = parseStringToMap(createOrder(SELL, String.valueOf(copiedOrderMap.get("price")), cntForExcution.toPlainString(), coinWithId, fishing.getExchange()));
                    }else{
                        orderId = parseStringToMap(createOrder(BUY, String.valueOf(copiedOrderMap.get("price")), cntForExcution.toPlainString(), coinWithId, fishing.getExchange()));
                    }

                    if((orderId != null)){
                        cnt = cnt.subtract(cntForExcution);
                        Thread.sleep(500);
                        if(UtilsData.MODE_BUY.equals(mode)) {
                            cancelOrder(orderId, SELL, String.valueOf(copiedOrderMap.get("price")) ,cntForExcution.toPlainString(), symbol );
                        }else{
                            cancelOrder(orderId, BUY, String.valueOf(copiedOrderMap.get("price")), cntForExcution.toPlainString(), symbol );
                        }
                    }else{
                        break;
                    }
                }
                // 무조건 취소
                Thread.sleep(500);
                if(UtilsData.MODE_BUY.equals(mode)) {
                    cancelOrder((Map<String, String>) orderList.get(i).get("order_id"), BUY, String.valueOf(orderList.get(i).get("price")),  String.valueOf(orderList.get(i).get("cnt")) ,symbol);
                }else{
                    cancelOrder((Map<String, String>) orderList.get(i).get("order_id"), SELL, String.valueOf(orderList.get(i).get("price")), String.valueOf(orderList.get(i).get("cnt")) ,symbol);
                }
            }
            log.info("[DEXORCA][FISHINGTRADE][END BUY OR SELL TARGET PIECE COIN ]");
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[DEXORCA][FISHINGTRADE] ERROR {}", e.getMessage());
            e.printStackTrace();
        }

        log.info("[DEXORCA][FISHINGTRADE] END");
        return returnCode;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {

        String returnRes = ReturnCode.FAIL.getValue();
        try{
            // SET API KEY
            setApiKey(coinWithId, exchange);

            JsonObject body   = getOjbect("quote_enquiry");
            JsonObject params = new JsonObject();
            params.addProperty("issue", getSymbol(coinWithId, exchange));
            body.add("params", params);

            JsonObject returnVal = postHttpMethod(body);
            JsonObject result    = returnVal.getAsJsonObject("result");
            if(result.get("rc").getAsString().equals(SUCCESS)){
                returnRes = gson.toJson(result);
                log.info("[DEXORCA][GET ORDER BOOK] SUCCESS");
            }else{
                log.error("[DEXORCA][GET ORDER BOOK] Fail Response:{}", gson.toJson(returnVal));
            }
        }catch (Exception e){
            log.error("[DEXORCA][GET ORDER BOOK] Error : {}",e.getMessage());
            e.printStackTrace();
        }
        return returnRes;
    }


    /**
     * Realtime Sync 거래
     * @param realtime
     * @return
     */
    public int startRealtimeTrade(JsonObject realtime, boolean resetFlag) {
        log.info("[DEXORCA][REALTIME SYNC TRADE START]");
        int returnCode   = ReturnCode.SUCCESS.getCode();
        String realtimeChangeRate = "signed_change_rate";

        try {
            String[] coinWithId = Utils.splitCoinWithId(realtimeSync.getCoin());
            setApiKey(coinWithId, realtimeSync.getExchange());
            boolean isStart      = false;
            String symbol        = getSymbol(Utils.splitCoinWithId(realtimeSync.getCoin()), realtimeSync.getExchange());
            String[] currentTick = getTodayTick(symbol);
            //            String openingPrice  = currentTick[0];
            if(resetFlag){
                realtimeTargetInitRate = currentTick[1];
                log.info("[DEXORCA][REALTIME SYNC TRADE] Set init open rate : {} ", realtimeTargetInitRate);
            }
            String openingPrice  = realtimeTargetInitRate;
            String currentPrice  = currentTick[1];
            log.info("[DEXORCA][REALTIME SYNC TRADE] open:{}, current:{} ", openingPrice, currentPrice);

            Map<String,String> orderId = null;
            String targetPrice   = "";
            String action        = "";
            String mode          = "";
            String cnt           = Utils.getRandomString(realtimeSync.getMinTradeCnt(), realtimeSync.getMaxTradeCnt());

            // 1. 최소/최대 매수 구간에 있는지 확인
            int isInRange        = isMoreOrLessPrice(currentPrice);
            if(isInRange != 0){              // 구간 밖일 경우
                if(isInRange == -1){         // 지지선보다 낮을 경우
                    mode         = UtilsData.MODE_BUY;
                    action       = BUY;
                    targetPrice  = realtimeSync.getMinPrice();
                }else if(isInRange == 1){    // 저항선보다 높을 경우
                    mode         = UtilsData.MODE_SELL;
                    action       = SELL;
                    targetPrice  = realtimeSync.getMaxPrice();
                }
                isStart = true;
            }else{
                // 지정한 범위 안에 없을 경우 매수 혹은 매도로 맞춰준다.
                Map<String,String> tradeInfo = getTargetTick(openingPrice, currentPrice, realtime.get(realtimeChangeRate).getAsString());
                if(!tradeInfo.isEmpty()){
                    targetPrice = tradeInfo.get("price");
                    mode        = tradeInfo.get("mode");
                    action      = (mode.equals(UtilsData.MODE_BUY)) ? BUY : SELL;
                    isStart     = true;
                }
            }

            // 2. %를 맞추기 위한 매수/매도 로직
            if(isStart){
                if((orderId = parseStringToMap(createOrder(action, targetPrice, cnt, coinWithId, realtimeSync.getExchange()))) != null){    // 매수/OrderId가 있으면 성공
                    Thread.sleep(300);

                    // 3. bestoffer set 로직
                    JsonArray array = makeBestofferAfterRealtimeSync(targetPrice, mode);
                    for (int i = 0; i < array.size(); i++) {
                        JsonObject object       = array.get(i).getAsJsonObject();
                        String bestofferPrice   = object.get("price").getAsString();
                        String bestofferCnt     = object.get("cnt").getAsString();
                        Map<String, String> bestofferOrderId = null;

                        if((bestofferOrderId = parseStringToMap(createOrder(action, bestofferPrice, bestofferCnt, coinWithId, realtimeSync.getExchange()))) != null){
                            log.info("[DEXORCA][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                    }

                    // 베스트 오퍼 체크 작업 이후 기존에 걸었던 매수에 대해 캔슬
                    cancelOrder(orderId, action, targetPrice, cnt, symbol);
                }
            }

        }catch (Exception e){
            log.error("[DEXORCA][REALTIME SYNC TRADE] Error :{} ", e.getMessage());
            e.printStackTrace();
        }
        log.info("[DEXORCA][REALTIME SYNC TRADE END]");
        return returnCode;
    }


    /**
     * 현재 Tick 가져오기
     * @param exchange
     * @param coinWithId
     * @return [ 시가 , 종가 ] String Array
     */
    private String[] getTodayTick(String symbol) throws Exception{
        String[] returnRes   = new String[2];

        JsonObject body   = getOjbect("price_enquiry");
        JsonObject params = new JsonObject();
        params.addProperty("issue", symbol);
        body.add("params", params);

        JsonObject returnVal = postHttpMethod(body);
        JsonObject result    = returnVal.getAsJsonObject("result");
        if(result.get("rc").getAsString().equals(SUCCESS)){
            returnRes[0] = result.get("open_price").getAsString();
            returnRes[1] = result.get("price").getAsString();
            log.info("[DEXORCA][GET TODAY TICK] SUCCESS");
        }else{
            log.error("[DEXORCA][GET TODAY TICK] Fail Response:{}", gson.toJson(returnVal));
        }
        return returnRes;
    }



    @Override
    public String createOrder(String type, String price, String cnt, String[] coinData, Exchange exchange){

        String returnMsg        = ReturnCode.NO_DATA.getValue();
        StringBuilder builder   = new StringBuilder();

        try{
            setApiKey(coinData, exchange);
            String action       = parseAction(type);
            BigDecimal bigCnt   = new BigDecimal(cnt);
            BigDecimal bigPrice = new BigDecimal(price);

            JsonObject object = getOjbect(action);
            JsonObject params = new JsonObject();
            params.addProperty("issue", getSymbol(coinData, exchange));//operation
            params.addProperty("operation", "place");//operation
            params.addProperty("account",  getUserId());
            params.addProperty("volume",  bigCnt.multiply(new BigDecimal(VARIABLE)).toPlainString() );
            params.addProperty("price",   bigPrice.multiply(new BigDecimal(VARIABLE)).toPlainString() );
            params.addProperty("session_id", UtilsData.DEXORCA_SESSION_KEY.get(getUserId()));
            object.add("params", params);

            JsonObject response = postHttpMethod(object);
            JsonObject result    = response.getAsJsonObject("result");
            if(result.size() == 0){
                log.info("[DEXORCA][CREATE ORDER] Maybe success result is null. {}" , gson.toJson(response));
            }else if(result.get("rc").getAsString().equals(SUCCESS)){
                builder.append(result.get(ORDER_DATE).getAsString())
                        .append(",")
                        .append(result.get(ORDER_ID).getAsString());
                returnMsg = builder.toString();

                log.info("[DEXORCA][CREATE ORDER] CREATE SUCCESS:{}" , gson.toJson(response));
            }else{
                log.error("[DEXORCA][CREATE ORDER] CREATE FAIL:{}", gson.toJson(response));
            }
            Thread.sleep(500);
        }catch (SocketTimeoutException e){
            log.error("[DEXORCA][CREATE ORDER] Maybe is locked ");
            log.error("[DEXORCA][CREATE ORDER] Occur error :{}",e.getMessage());
            e.printStackTrace();
            try {
                Thread.sleep(SLEEP_TIME);
            }catch (InterruptedException exception){
                log.error("[DEXORCA][CREATE ORDER] Thread sleep error.");
                exception.printStackTrace();
            }
        }catch (Exception e){
            log.error("[DEXORCA][CREATE ORDER] Occur error :{}",e.getMessage());
            e.printStackTrace();
        }
        return returnMsg;
    }

    private Map<String, String> parseStringToMap(String msg) throws Exception {
        Map<String, String> map = new HashMap<>();
        String[] msgArr         = msg.split(",");
        if(msgArr.length > 1){
            map.put(ORDER_DATE, msgArr[0]);
            map.put(ORDER_ID,   msgArr[1]);
            return map;
        }else{
            return null;
        }
    }

    /**
     * 취소 로직
     * @return 성공 시, ReturnCode.Success. 실패 시, ReturnCode.Fail
     */
    public int cancelOrder(Map<String,String> ordNo, String type, String price, String cnt, String symbol){

        int returnCode = ReturnCode.FAIL.getCode();
        try{
            if(ordNo == null || ordNo.isEmpty()){
                log.info("[DEXORCA][CANCEL ORDER] Order Response Map is null. So cancel is not executed.");
                return returnCode;
            }
            BigDecimal bigCnt   = new BigDecimal(cnt);
            BigDecimal bigPrice = new BigDecimal(price);

            JsonObject object = getOjbect(parseAction(type));
            JsonObject params = new JsonObject();
            params.addProperty("issue", symbol);//operation
            params.addProperty("operation", "void");//operation
            params.addProperty("account",  getUserId());
            params.addProperty("volume",  bigCnt.multiply(new BigDecimal(VARIABLE)).toPlainString() );
            params.addProperty("price",   bigPrice.multiply(new BigDecimal(VARIABLE)).toPlainString() );
            params.addProperty("session_id", UtilsData.DEXORCA_SESSION_KEY.get(getUserId()));
            params.addProperty("org_order_date", ordNo.get(ORDER_DATE));
            params.addProperty("org_order_id"  , ordNo.get(ORDER_ID));
            object.add("params", params);

            JsonObject response  = postHttpMethod(object);
            JsonObject result    = response.getAsJsonObject("result");
            if(result.get("rc").getAsString().equals(SUCCESS)){
                returnCode = ReturnCode.SUCCESS.getCode();
                log.info("[DEXORCA][CANCEL ORDER] CANCEL SUCCESS {}", gson.toJson(response));
            }else if(result.get("rc").getAsString().equals(ALREADY_TRADED)){
                log.info("[DEXORCA][CANCEL ORDER] Already Traded {}", gson.toJson(response));
            }else{
                log.error("[DEXORCA][CANCEL ORDER] CANCEL FAIL:{}", gson.toJson(response));
            }
            Thread.sleep(300);
        }catch (SocketTimeoutException e){
            log.error("[DEXORCA][CANCEL ORDER] Maybe is locked ");
            log.error("[DEXORCA][CANCEL ORDER] Occur error :{}",e.getMessage());
            e.printStackTrace();
            try {
                Thread.sleep(SLEEP_TIME);
            }catch (InterruptedException exception){
                log.error("[DEXORCA][CANCEL ORDER] Thread sleep error.");
                exception.printStackTrace();
            }
        }catch (Exception e){
            log.error("[DEXORCA][CANCEL ORDER] Error {}",e.getMessage());
            e.printStackTrace();
        }
        return returnCode;
    }




    private JsonObject postHttpMethod(JsonObject body) throws Exception{

        disableSslVerification();
        String bodyStr = gson.toJson(body);
        log.info("[DEXORCA][POST HTTP] request :{}", bodyStr);
        URL url = new URL(UtilsData.DEXORCA_URL);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestProperty("Content-Type","application/json;charset=utf-8");
        connection.setRequestProperty("Accept"      , "*/*");
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(UtilsData.TIMEOUT_VALUE);
        connection.setReadTimeout(UtilsData.TIMEOUT_VALUE);
        connection.setDoOutput(true);
        connection.setDoInput(true);

        // Writing the post data to the HTTP request body
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
        bw.write(bodyStr);
        bw.close();

        String response = (connection.getErrorStream() == null)
                ? getResponseMsg(connection.getInputStream()) : getResponseMsg(connection.getErrorStream());
        JsonObject returnObj = gson.fromJson(response, JsonObject.class);

        log.info("[DEXORCA][POST HTTP] response :{}", gson.toJson(returnObj));
        return returnObj;
    }

    // Get Input response message
    private String getResponseMsg(InputStream stream) throws Exception{
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        StringBuffer response = new StringBuffer();
        String inputLine;
        while ((inputLine = br.readLine()) != null) {
            response.append(inputLine);
        }
        br.close();

        return response.toString();
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

    // 거래소에 맞춰 심볼 반환
    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0] + "/" + getCurrency(exchange, coinData[0], coinData[1]);
    }

    // Return Object
    private JsonObject getOjbect(String method) throws Exception {
        JsonObject object = new JsonObject();
        object.addProperty("jsonrpc", "2.0");
        object.addProperty("id", String.valueOf(Utils.getRandomInt(1000,9999)));
        object.addProperty("method", method);

        return object;
    }

    private void disableSslVerification(){
        try
        {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                }
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkServerTrusted(X509Certificate[] certs, String authType){
                }
            }
            };

            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session){
                    return true;
                }
            };

            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
    }

    private void setUserId(String userId){
        this.noChangedUserId = userId;
    }
    private String getUserId(){
        return this.noChangedUserId;
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
}
