package com.coin.autotrade.service.exchangeimp;


import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.tomcat.util.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Coin one 에서 사용할 class
 */
@Slf4j
public class CoinOneImp extends AbstractExchange {
    final private String CANCEL_SUCCESS   = "116";
    final private String SUCCESS          = "0";
    final private int CANCEL_AGAIN        = 5;
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

    @Override
    public int startAutoTrade(String price, String cnt){

        log.info("[COINONE][AUTOTRADE] START");

        int returnCode      = ReturnCode.SUCCESS.getCode();
        String firstAction  = "";
        String secondAction = "";

        try{
            // mode 처리
            String mode = autoTrade.getMode();
            String coin = autoTrade.getCoin().split(";")[0];
            if(UtilsData.MODE_RANDOM.equals(mode)){
                mode = (Utils.getRandomInt(0,1) == 0) ? UtilsData.MODE_BUY : UtilsData.MODE_SELL;
            }
            Map<String, String> orderMap = setDefaultMap(cnt, coin, price);
            // Trade 모드에 맞춰 순서에 맞게 거래 타입 생성
            if(UtilsData.MODE_BUY.equals(mode)){
                orderMap.put("is_ask","0"); // 1 if order is sell
                firstAction  = BUY;
                secondAction = SELL;
            }else if(UtilsData.MODE_SELL.equals(mode)){
                orderMap.put("is_ask","1"); // 0 if order is sell
                firstAction  = SELL;
                secondAction = BUY;
            }
            String orderId = ReturnCode.NO_DATA.getValue();
            if(!(orderId = createOrder(firstAction,price, cnt, coin)).equals(ReturnCode.NO_DATA.getValue())){
                orderMap.put("order_id", orderId);
                if(createOrder(secondAction,price, cnt, coin).equals(ReturnCode.NO_DATA.getValue())){          // SELL 모드가 실패 시,
                    cancelOrder(orderMap);
                }
            }
        }catch(Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[COINONE][ERROR][AUTOTRADE] {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[COINONE][AUTOTRADE] END");

        return returnCode;
    }


    /**
     * 호가 유동성
     * @param list
     * @return
     */
    @Override
    public int startLiquidity(Map list) {
        int returnCode = ReturnCode.SUCCESS.getCode();

        Queue<String> sellQueue = (LinkedList) list.get("sell");
        Queue<String> buyQueue  = (LinkedList) list.get("buy");
        Queue<Map<String,String>> cancelList = new LinkedList<>();

        try{
            log.info("[COINONE][LIQUIDITY] START");
            String coin = liquidity.getCoin().split(";")[0];

            while (!sellQueue.isEmpty() || !buyQueue.isEmpty() || !cancelList.isEmpty()) {
                String mode           = (Utils.getRandomInt(1, 2) == 1) ? UtilsData.MODE_BUY : UtilsData.MODE_SELL;
                boolean cancelFlag    = (Utils.getRandomInt(1, 2) == 1) ? true : false;
                String orderId        = ReturnCode.NO_DATA.getValue();
                String price          = "";
                String action         = "";
                String cnt            = Utils.getRandomString(liquidity.getMinCnt(), liquidity.getMaxCnt());

                Map<String, String> cancelMap  = setDefaultMap(cnt, coin, price);
                if(!buyQueue.isEmpty() && mode.equals(UtilsData.MODE_BUY)){
                    price     = buyQueue.poll();
                    action    = BUY;
                    cancelMap.put("is_ask", "0");
                }else if(!sellQueue.isEmpty() && mode.equals(UtilsData.MODE_SELL)){
                    price   = sellQueue.poll();
                    action  = SELL;
                    cancelMap.put("is_ask", "1");
                }
                // 매수 로직
                if(!action.equals("")){
                    orderId = createOrder(action, price, cnt, coin);
                    if(!orderId.equals(ReturnCode.NO_DATA.getValue())){
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
            log.error("[COINONE][LIQUIDITY] ERROR : {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[COINONE][LIQUIDITY] END");
        return returnCode;
    }


    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){

        log.info("[COINONE][FISHINGTRADE] START");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String mode              = "";
            boolean noIntervalFlag   = true;    // 해당 플래그를 이용해 마지막 매도/매수 후 바로 intervalTime 없이 바로 다음 매수/매도 진행
            boolean noMatchFirstTick = true;    // 해당 플래그를 이용해 매수/매도를 올린 가격이 현재 최상위 값이 맞는지 다른 사람의 코인을 사지 않게 방지

            for(String temp : list.keySet()){  mode = temp; }
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode);
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            String coin = fishing.getCoin().split(";")[0];

            /* Start */
            log.info("[COINONE][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt     = Utils.getRandomString(fishing.getMinContractCnt(), fishing.getMaxContractCnt());
                String orderId = ReturnCode.NO_DATA.getValue();
                Map<String, String> orderMap = setDefaultMap(cnt, coin, tickPriceList.get(i));
                if(UtilsData.MODE_BUY.equals(mode)) {
                    orderId = createOrder(BUY,  tickPriceList.get(i), cnt, coin);
                    orderMap.put("is_ask","0");
                }else{
                    orderId = createOrder(SELL, tickPriceList.get(i), cnt, coin);
                    orderMap.put("is_ask","1");
                }
                if(!orderId.equals(ReturnCode.NO_DATA.getValue())) {
                    orderMap.put("order_id",orderId);
                    orderList.add(orderMap);
                }
            }
            log.info("[COINONE][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");

            /* Sell Start */
            log.info("[COINONE][FISHINGTRADE][START BUY OR SELL TARGET PIECE COIN ]");
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = Utils.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("qty"));

                while (cnt.compareTo(new BigDecimal("0")) > 0) {
                    if (!noMatchFirstTick) break;                   // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                    if (noIntervalFlag) Thread.sleep(intervalTime); // intervalTime 만큼 휴식 후 매수 시작

                    BigDecimal cntForExcution = new BigDecimal(Utils.getRandomString(fishing.getMinExecuteCnt(), fishing.getMaxExecuteCnt()));
                    if (cnt.compareTo(cntForExcution) < 0) {    // 남은 코인 수와 매도/매수할 코인수를 비교했을 때, 남은 코인 수가 더 적다면.
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

                    if (!copiedOrderMap.get("price").equals(nowFirstTick)) {
                        log.info("[COINONE][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        noMatchFirstTick = false;
                        break;
                    }

                    String orderId = ReturnCode.NO_DATA.getValue();
                    if(UtilsData.MODE_BUY.equals(mode)) {
                        orderId = createOrder(SELL, copiedOrderMap.get("price"), cntForExcution.toPlainString(), coin);
                    }else{
                        orderId = createOrder(BUY,  copiedOrderMap.get("price"), cntForExcution.toPlainString(), coin);
                    }

                    if(!orderId.equals(ReturnCode.NO_DATA.getValue())){
                        copiedOrderMap.replace("qty", cntForExcution.toPlainString());
                        copiedOrderMap.replace("order_id", orderId);
                        cnt = cnt.subtract(cntForExcution);
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
            log.info("[COINONE][FISHINGTRADE][END BUY OR SELL TARGET PIECE COIN ]");
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[COINONE][FISHINGTRADE] ERROR : {}", e.getMessage());
            e.printStackTrace();
        }

        log.info("[COINONE][FISHINGTRADE] END");
        return returnCode;


    }

    /**
     * Realtime Sync 거래
     * @param realtime
     * @return
     */
    public int startRealtimeTrade(JsonObject realtime, boolean resetFlag) {
        log.info("[COINONE][REALTIME SYNC TRADE] START");
        int returnCode                      = ReturnCode.SUCCESS.getCode();
        String realtimeChangeRate           = "signed_change_rate";
        Map<String, String> cancelMap       = null;

        try {
            boolean isStart      = false;
            String coin          = realtimeSync.getCoin().split(";")[0];
            String[] currentTick = getTodayTick(coin);
            //            String openingPrice  = currentTick[0];
            if(resetFlag){
                realtimeTargetInitRate = currentTick[1];
                log.info("[COINONE][REALTIME SYNC TRADE] Set init open rate : {} ", realtimeTargetInitRate);
            }
            String openingPrice  = realtimeTargetInitRate;
            String currentPrice  = currentTick[1];
            log.info("[COINONE][REALTIME SYNC TRADE] open:{}, current:{} ", openingPrice, currentPrice);

            String orderId       = ReturnCode.NO_DATA.getValue();
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
                    cancelMap  = setDefaultMap(cnt, coin, targetPrice);
                    cancelMap.put("is_ask", "0");
                }else if(isInRange == 1){    // 저항선보다 높을 경우
                    action       = SELL;
                    mode         = UtilsData.MODE_SELL;
                    targetPrice  = realtimeSync.getMaxPrice();
                    cancelMap  = setDefaultMap(cnt, coin, targetPrice);
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
                        cancelMap   = setDefaultMap(cnt, coin, targetPrice);
                        cancelMap.put("is_ask", "0");
                    }else{
                        action      = SELL;
                        mode        = UtilsData.MODE_SELL;
                        cancelMap  = setDefaultMap(cnt, coin, targetPrice);
                        cancelMap.put("is_ask", "1");
                    }
                    isStart = true;
                }
            }

            if(isStart){
                if( !(orderId = createOrder(action, targetPrice, cnt, coin)).equals(ReturnCode.NO_DATA.getValue())){    // 매수/OrderId가 있으면 성공
                    Thread.sleep(300);
                    cancelMap.put("order_id", orderId);

                    // 3. bestoffer set 로직
                    JsonArray array = makeBestofferAfterRealtimeSync(targetPrice, mode);
                    for (int i = 0; i < array.size(); i++) {
                        JsonObject object       = array.get(i).getAsJsonObject();
                        String bestofferPrice   = object.get("price").getAsString();
                        String bestofferCnt     = object.get("cnt").getAsString();
                        String bestofferOrderId = ReturnCode.NO_DATA.getValue();

                        if( !(bestofferOrderId = createOrder(action, bestofferPrice, bestofferCnt, coin)).equals(ReturnCode.NO_DATA.getValue())){
                            log.info("[COINONE][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                    }

                    Thread.sleep(300);
                    cancelOrder(cancelMap);
                }
            }
        }catch (Exception e){
            log.error("[COINONE][REALTIME SYNC TRADE] ERROR :{} ", e.getMessage());
            e.printStackTrace();
        }
        log.info("[COINONE][REALTIME SYNC TRADE] END");
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
            log.error("[COINONE][GET TODAY TICK] response : {}", response);
            throw new Exception(response);
        }
        return returnRes;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = "";
        try{
            log.info("[COINONE][GET ORDER BOOK] START");
            String coin    = coinWithId[0];
            String request = UtilsData.COINONE_ORDERBOOK + "?currency=" + coin;
            returnRes = getHttpMethod(request);
            log.info("[COINONE][Order book] END");
        }catch (Exception e){
            log.error("[COINONE][ORDER BOOK] {}",e.getMessage());
            e.printStackTrace();
        }

        return returnRes;
    }

    /**
     * 매수/매도 로직
     * @param type BUY / SELL
     * @param price
     * @param cnt
     * @param coin
     * @return
     */
    public String createOrder(String type, String price, String cnt ,String coin){
        String orderId = ReturnCode.NO_DATA.getValue();

        try{
            String url        = ( type.equals(BUY) ) ? UtilsData.COINONE_LIMIT_BUY : UtilsData.COINONE_LIMIT_SELL;
            JsonObject header = setDefaultRequest(cnt, coin, price);
            JsonObject json   = postHttpMethod(url, gson.toJson(header));
            String returnCode = json.get("errorCode").getAsString();
            if(SUCCESS.equals(returnCode)){
                orderId = json.get("orderId").getAsString();
                log.info("[COINONE][CREATE ORDER] Response. mode :{}, response :{}", type, gson.toJson(json));
            }else{
                log.error("[COINONE][CREATE ORDER] Response. mode :{}, response :{}", type, gson.toJson(json));
            }
        }catch (Exception e){
            log.error("[COINONE][CREATE ORDER] Error {}", e.getMessage());
            e.printStackTrace();
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
            JsonObject header = setDefaultRequest(data.get("qty"), data.get("currency"), data.get("price"));
            header.addProperty("is_ask",  Integer.parseInt(data.get("is_ask")));
            header.addProperty("order_id",data.get("order_id"));

            JsonObject json   = postHttpMethod(UtilsData.COINONE_CANCEL , gson.toJson(header));
            String returnCode = json.get("errorCode").getAsString();

            if(SUCCESS.equals(returnCode) || CANCEL_SUCCESS.equals(returnCode)){
                returnValue = ReturnCode.SUCCESS.getCode();
                log.info("[COINONE][CANCEL ORDER] Response : {} ",  gson.toJson(json) );
            }else{
                log.error("[COINONE][CANCEL ORDER] Response : {}" , gson.toJson(json));
            }
        }catch (Exception e){
            log.error("[COINONE][CANCEL ORDER] ERROR {}", e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }

    @Override
    public String getBalance(String[] coinData, Exchange exchange) throws Exception{
        String returnValue = ReturnCode.NO_DATA.getValue();;

        setCoinToken(coinData, exchange);
        JsonObject header = new JsonObject();
        header.addProperty("access_token", keyList.get(PUBLIC_KEY));
        header.addProperty("nonce",System.currentTimeMillis());
        JsonObject json   = postHttpMethod(UtilsData.COINONE_BALANCE , gson.toJson(header));
        String returnCode = json.get("errorCode").getAsString();
        if(SUCCESS.equals(returnCode)){
            returnValue = gson.toJson(json);
            log.info("[COINONE][GET BALANCE] Success Response");
        }else{
            log.error("[COINONE][GET BALANCE] Response : {}" , gson.toJson(json));
            throw new Exception(gson.toJson(json));
        }
        return returnValue;
    }

    /* HMAC Signature 만드는 method */
    // TODO : 안정화 될 경우 삭제.
//     private String makeHmacSignature(String payload, String secret) throws Exception{
//        String result;
//
//        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA512");
//         Mac hmacSHA512 = Mac.getInstance("HmacSHA512");
//         hmacSHA512.init(secretKeySpec);
//        byte[] digest = hmacSHA512.doFinal(payload.getBytes());
//        BigInteger hash = new BigInteger(1, digest);
//        result = hash.toString(16);
//        if ((result.length() % 2) != 0) {
//            result = "0" + result;
//        }
//        return result;
//    }

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
    private JsonObject setDefaultRequest(String cnt, String currency, String price) throws Exception{
        Thread.sleep(300);
        long nonce = System.currentTimeMillis();
        JsonObject defaultRequest = new JsonObject();

        defaultRequest.addProperty("access_token", keyList.get(PUBLIC_KEY));
        defaultRequest.addProperty("nonce",nonce);
        defaultRequest.addProperty("qty",Double.parseDouble(cnt));
        defaultRequest.addProperty("currency",currency);
        defaultRequest.addProperty("price",price);

        return defaultRequest;
    }

    /* HTTP POST Method for coinone */
    private JsonObject postHttpMethod(String targetUrl, String payload) throws Exception {

        log.info("[COINONE][POST REQUEST] Target url : {}, Request : {}", targetUrl, payload);

        URL url = new URL(targetUrl);
        String encodingPayload = Base64.encodeBase64String(payload.getBytes());    // Encoding to base 64

        String signature = makeHmacSignature(encodingPayload, keyList.get(SECRET_KEY).toUpperCase());

        HttpURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setConnectTimeout(UtilsData.TIMEOUT_VALUE);
        connection.setReadTimeout(UtilsData.TIMEOUT_VALUE);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("X-COINONE-PAYLOAD", encodingPayload);
        connection.setRequestProperty("X-COINONE-SIGNATURE", signature);

        // Writing the post data to the HTTP request body
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
        bw.write(encodingPayload);
        bw.close();
        StringBuffer response = new StringBuffer();
        if(connection.getResponseCode() == HttpsURLConnection.HTTP_OK){
            BufferedReader br = null;
            if(connection.getInputStream() != null){
                br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            }else if(connection.getErrorStream() != null){
                br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            }else{
                log.error("[COINONE][POST HTTP] Return Code is 200. But inputstream and errorstream is null");
                throw new Exception();
            }
            String inputLine = "";
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();
        }else{
            log.error("[COINONE][POST HTTP] Return code : {}, msg : {}",connection.getResponseCode(), connection.getResponseMessage());
            throw new Exception();
        }
        return gson.fromJson(response.toString(), JsonObject.class);

    }


}

