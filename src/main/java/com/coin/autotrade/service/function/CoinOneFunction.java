package com.coin.autotrade.service.function;


import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
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
import java.util.*;

/**
 * Coin one 에서 사용할 class
 */
@Slf4j
public class CoinOneFunction extends ExchangeFunction{

    final private String ACCESS_TOKEN     = "access_token";
    final private String SECRET_KEY       = "secret_key";
    final private String ALREADY_TRADED   = "116";
    final private int CANCEL_AGAIN        = 5;
    final private String BUY              = "BUY";
    final private String SELL             = "SELL";
    private Map<String, String> keyList   = new HashMap<>();



    /** 자전 거래를 이용하기위한 초기값 설정 */
    @Override
    public void initClass(AutoTrade autoTrade, User user, Exchange exchange) {
        super.autoTrade = autoTrade;
        setCommonValue(user, exchange, ServiceCommon.setCoinData(autoTrade.getCoin()));
    }

    /** 호가 유동성을 이용하기 위한 초기값 설정 */
    @Override
    public void initClass(Liquidity liquidity, User user, Exchange exchange) {
        super.liquidity  = liquidity;
        setCommonValue(user, exchange, ServiceCommon.setCoinData(liquidity.getCoin()));
    }

    /** 매매 긁기를 이용하기 위한 초기값 설정 */
    @Override
    public void initClass(Fishing fishing, User user, Exchange exchange, CoinService coinService) {
        super.fishing     = fishing;
        super.coinService = coinService;
        setCommonValue(user, exchange, ServiceCommon.setCoinData(fishing.getCoin()));
    }

    @Override
    public int startLiquidity(Map list){
        int returnCode = DataCommon.CODE_SUCCESS;

        Queue<String> sellQueue = (LinkedList) list.get("sell");
        Queue<String> buyQueue  = (LinkedList) list.get("buy");
        List<Map<String,String>> CancelList = new ArrayList();

        try{
            log.info("[COINONE][LIQUIDITY] Start");
            int minCnt  = liquidity.getMinCnt();
            int maxCnt  = liquidity.getMaxCnt();
            String coin = liquidity.getCoin().split(";")[0];

            while(sellQueue.size() > 0 || buyQueue.size() > 0){
                String randomMode = (ServiceCommon.getRandomInt(1,2) == 1) ? BUY : SELL;
                String firstOrderId    = "";
                String secondsOrderId  = "";
                String firstPrice      = "";
                String secondsPrice    = "";
                String firstCnt        = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                String secondsCnt      = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);

                if(sellQueue.size() > 0 && buyQueue.size() > 0 && randomMode.equals(BUY)){
                    firstPrice   = buyQueue.poll();
                    firstOrderId = createOrder(BUY, firstPrice, firstCnt, coin);

                    Thread.sleep(300);
                    secondsPrice   = sellQueue.poll();
                    secondsOrderId = createOrder(SELL, secondsPrice, secondsCnt, coin);
                }else if(buyQueue.size() > 0 && sellQueue.size() > 0 && randomMode.equals(SELL)){
                    firstPrice   = sellQueue.poll();
                    firstOrderId = createOrder(SELL, firstPrice, firstCnt, coin);

                    Thread.sleep(300);
                    secondsPrice   = buyQueue.poll();
                    secondsOrderId = createOrder(BUY, secondsPrice, secondsCnt, coin);
                }

                if(!firstOrderId.equals("") || !secondsOrderId.equals("")){
                    Thread.sleep(1000);
                    if(!firstOrderId.equals("")){
                        Map<String, String> cancelMap = setDefaultMap(firstCnt, coin, firstPrice);
                        cancelMap.put("order_id",firstOrderId);
                        cancelMap.put("is_ask","1");
                        cancelTrade(cancelMap);
                    }
                    if(!secondsOrderId.equals("")){
                        Thread.sleep(300);
                        Map<String, String> cancelMap = setDefaultMap(secondsCnt, coin, secondsPrice);
                        cancelMap.put("order_id",secondsOrderId);
                        cancelMap.put("is_ask","1");
                        cancelTrade(cancelMap);
                    }
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[COINONE][ERROR][LIQUIDITY] {}", e.getMessage());
        }
        log.info("[COINONE][LIQUIDITY] End");
        return returnCode;
    }

    @Override
    public int startAutoTrade(String price, String cnt){

        log.info("[COINONE][AUTOTRADE] Start");

        int returnCode = DataCommon.CODE_SUCCESS;

        try{
            // mode 처리
            String mode = autoTrade.getMode();
            if(DataCommon.MODE_RANDOM.equals(mode)){
                mode = (ServiceCommon.getRandomInt(0,1) == 0) ? DataCommon.MODE_BUY : DataCommon.MODE_SELL;
            }

            String coin = autoTrade.getCoin().split(";")[0];
            if(DataCommon.MODE_BUY.equals(mode)){
                String orderId ="";
                Map<String, String> buyValue = setDefaultMap(cnt, coin, price);
                buyValue.put("is_ask","1");

                if(!(orderId = createOrder(BUY, price, cnt, coin)).equals("")){
                    buyValue.put("order_id",orderId);
                    if(createOrder(SELL ,price, cnt, coin).equals("")){
                        cancelTrade(buyValue);      // 매수 후 매도 실패 시, 매수 값 취소
                    }
                }
            }else if(DataCommon.MODE_SELL.equals(mode)){
                String orderId ="";
                Map<String, String> sellValue = setDefaultMap(cnt, coin, price);
                sellValue.put("is_ask","1");

                if(!(orderId = createOrder(SELL, price, cnt, coin)).equals("")){
                    sellValue.put("order_id",orderId);
                    if(createOrder(BUY, price, cnt, coin).equals("")){
                        cancelTrade(sellValue);      // 매수 후 매도 실패 시, 매수 값 취소
                    }
                }
            }
        }catch(Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[COINONE][ERROR][AUTOTRADE] {}", e.getMessage());
        }
        log.info("[COINONE][AUTOTRADE] End");

        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){

        log.info("[COINONE][FISHINGTRADE] Start");
        int returnCode = DataCommon.CODE_SUCCESS;

        try{
            String mode              = "";
            boolean noIntervalFlag   = true;    // 해당 플래그를 이용해 마지막 매도/매수 후 바로 intervalTime 없이 바로 다음 매수/매도 진행
            boolean noMatchFirstTick = true;    // 해당 플래그를 이용해 매수/매도를 올린 가격이 현재 최상위 값이 맞는지 다른 사람의 코인을 사지 않게 방지

            for(String temp : list.keySet()){  mode = temp; }
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode);
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            String coin = fishing.getCoin().split(";")[0];

            /* Buy Start */
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt                = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double) fishing.getMinContractCnt(), (double) fishing.getMaxContractCnt()) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                String orderId            = "";
                Map<String, String> orderMap = setDefaultMap(cnt, coin, tickPriceList.get(i));
                if(DataCommon.MODE_BUY.equals(mode)) {
                    orderId = createOrder(BUY,  tickPriceList.get(i), cnt, coin);
                }else{
                    orderId = createOrder(SELL, tickPriceList.get(i), cnt, coin);
                }
                if(!orderId.equals("")) {
                    orderMap.put("order_id",orderId);
                    orderMap.put("is_ask","1");
                    orderList.add(orderMap);
                }
                Thread.sleep(500);
            }

            /* Sell Start */
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = ServiceCommon.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("qty"));

                while (cnt.compareTo(new BigDecimal("0")) > 0) {
                    if (!noMatchFirstTick) break;                   // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                    if (noIntervalFlag) Thread.sleep(intervalTime); // intervalTime 만큼 휴식 후 매수 시작

                    BigDecimal cntForExcution = new BigDecimal(String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double) fishing.getMinExecuteCnt(), (double) fishing.getMaxExecuteCnt()) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL));
                    if (cnt.compareTo(cntForExcution) < 0) {    // 남은 코인 수와 매도/매수할 코인수를 비교했을 때, 남은 코인 수가 더 적다면.
                        cntForExcution = cnt;
                        noIntervalFlag = false;
                    } else {
                        noIntervalFlag = true;
                    }

                    // 매도/매수 날리기전에 최신 매도/매수값이 내가 건 값이 맞는지 확인
                    String nowFirstTick = "";
                    if(DataCommon.MODE_BUY.equals(mode)) {
                        nowFirstTick = coinService.getFirstTick(fishing.getCoin(), fishing.getExchange()).get(DataCommon.MODE_BUY);
                    }else{
                        nowFirstTick = coinService.getFirstTick(fishing.getCoin(), fishing.getExchange()).get(DataCommon.MODE_SELL);
                    }

                    if (!copiedOrderMap.get("price").equals(nowFirstTick)) {
                        log.info("[COINONE][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        noMatchFirstTick = false;
                        break;
                    }

                    String orderId = "";
                    if(DataCommon.MODE_BUY.equals(mode)) {
                        orderId = createOrder(SELL, copiedOrderMap.get("price"), cntForExcution.toPlainString(), coin);
                    }else{
                        orderId = createOrder(BUY, copiedOrderMap.get("price"), cntForExcution.toPlainString(), coin);
                    }

                    if(!orderId.equals("")){
                        copiedOrderMap.replace("qty", cntForExcution.toPlainString());
                        copiedOrderMap.replace("order_id", orderId);
                        cnt = cnt.subtract(cntForExcution);
                        Thread.sleep(500);

                        cancelTrade(copiedOrderMap);
                    }else{
                        break;
                    }
                }
                // 혹여나 남은 개수가 있을 수 있어 취소 request
                Thread.sleep(500);
                cancelTrade(orderList.get(i));
            }

        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[COINONE][ERROR][FISHINGTRADE] {}", e.getMessage());
        }

        log.info("[COINONE][FISHINGTRADE] END");
        return returnCode;


    }

    // init 시, keyList 값 세팅
    private void setCommonValue(User user, Exchange exchange, String[] coinData){
        super.user       = user;
        super.exchange   = exchange;
        try{
            // Set token key
            for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
                if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1]) ){
                    keyList.put(ACCESS_TOKEN, exCoin.getPublicKey());
                    keyList.put(SECRET_KEY,   exCoin.getPrivateKey());
                }
            }
        }catch (Exception e){
            log.error("[COINONE][SET COMMON VALUE] error : {}", e.getMessage());
        }
    }

    /* Create Order */
    // type - BUY / SELL
    public String createOrder(String type, String price, String cnt ,String coin){
        String orderId   = "";
        String errorCode = "";
        String errorMsg  = "";

        try{
            String url        = ( type.equals(BUY) ) ? DataCommon.COINONE_LIMIT_BUY : DataCommon.COINONE_LIMIT_SELL;
            JsonObject header = setDefaultRequest(cnt, coin, price);
            JsonObject json   = postHttpMethod(url, gson.toJson(header));

            String result = json.get("result").toString().replace("\"","");
            if("success".equals(result)){
                orderId = json.get("orderId").toString().replace("\"","");
                log.info("[COINONE][SUCCESS][CREATE ORDER - response] mode :{}, response :{}", type, gson.toJson(json));
            }else{
                log.error("[COINONE][ERROR][CREATE ORDER - response]  mode :{}, response :{}", type, gson.toJson(json));
            }
        }catch (Exception e){
            log.error("[COINONE][ERROR][BUYTRADE] {}", e.getMessage());
        }

        return orderId;
    }


    public int cancelTrade(Map<String,String> data){
        int returnValue = DataCommon.CODE_ERROR;

        try{

            JsonObject header = setDefaultRequest(data.get("qty"), data.get("currency"), data.get("price"));
            header.addProperty("is_ask",Integer.parseInt(data.get("is_ask").toString()));
            header.addProperty("order_id",data.get("order_id").toString());

            for (int i = 0; i < CANCEL_AGAIN; i++) {
                JsonObject json  = postHttpMethod(DataCommon.COINONE_CANCEL , gson.toJson(header));
                String result    = json.get("result").toString().replace("\"","");
                String errorCode = json.get("errorCode").toString().replace("\"","");

                if("success".equals(result) || errorCode.equals(ALREADY_TRADED)){
                    returnValue = DataCommon.CODE_SUCCESS;
                    log.info("[COINONE][SUCCESS][CANCELTRADE - response] try count :{}, response : {} ",i, gson.toJson(json) );
                    break;
                }else{
                    log.error("[COINONE][ERROR][CANCELTRADE - response] try count :{}, response :{}" ,i, gson.toJson(json));
                    Thread.sleep(1500); // 1초 후 재시도.
                }
            }
        }catch (Exception e){
            log.error("[COINONE][ERROR][CANCELTRADE] {}", e.getMessage());
        }

        return returnValue;
    }



    /* HMAC Signature 만드는 method */
     public String makeHmacSignature(String payload, String secret) {
        String result;
        try {
            Mac hmacSHA512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA512");
            hmacSHA512.init(secretKeySpec);

            byte[] digest = hmacSHA512.doFinal(payload.getBytes());
            BigInteger hash = new BigInteger(1, digest);
            result = hash.toString(16);
            if ((result.length() % 2) != 0) {
                result = "0" + result;
            }
        } catch (Exception ex) {
            throw new RuntimeException("Problemas calculando HMAC", ex);
        }
        return result;
    }


    /**
     * Coin one Order book api
     * DESC : only krw market
     */
    public String getOrderBook(String coin){
         String returnRes = "";
         try{
             log.info("[COINONE][Order book] Start");
             String inputLine;
             String request = DataCommon.COINONE_ORDERBOOK + "?currency=" + coin;
             URL url = new URL(request);

             HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
             connection.setRequestMethod("GET");
             connection.setConnectTimeout(DataCommon.TIMEOUT_VALUE);
             connection.setReadTimeout(DataCommon.TIMEOUT_VALUE);

             log.info("[COINONE][Order book - Request] exchagne:{},   currency:{}", "COINONE",  coin);

             int returnCode = connection.getResponseCode();
             BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
             StringBuffer response = new StringBuffer();
             while ((inputLine = br.readLine()) != null) {
                 response.append(inputLine);
             }
             br.close();
             returnRes = response.toString();
             log.info("[COINONE][Order book] End");

         }catch (Exception e){
             log.error("[COINONE][ERROR][ORDER BOOK] {}",e.getMessage());
         }

        return returnRes;
    }

    /* default 로 필요한 데이터를 받아 buy/sell/cancel 메서드에 전달 */
    private Map<String,String> setDefaultMap(String cnt, String currency, String price){
        Map<String, String> defaultMap = new HashMap<>();
        defaultMap.put("qty",cnt);
        defaultMap.put("currency",currency);
        defaultMap.put("price", price);

        return defaultMap;
    }

    /* default 로 필요한 데이터를 받아 request 전 셋팅 후 반환 */
    private JsonObject setDefaultRequest(String cnt, String currency, String price){

        long nonce = System.currentTimeMillis();;
        JsonObject defaultRequest = new JsonObject();

        defaultRequest.addProperty(ACCESS_TOKEN, keyList.get(ACCESS_TOKEN));
        defaultRequest.addProperty("nonce",System.currentTimeMillis());
        defaultRequest.addProperty("qty",Double.parseDouble(cnt));
        defaultRequest.addProperty("currency",currency);
        defaultRequest.addProperty("price",price);

        return defaultRequest;
    }

    /* HTTP POST Method for coinone */
    public JsonObject postHttpMethod(String targetUrl, String payload) {
        URL url;
        String inputLine;
        String encodingPayload;
        JsonObject returnObj = null;

        try{
            log.info("[COINONE][POST REQUEST] request : {}", payload);

            url = new URL(targetUrl);
            encodingPayload = Base64.encodeBase64String(payload.getBytes());    // Encoding to base 64

            String signature = makeHmacSignature(encodingPayload, keyList.get(SECRET_KEY).toUpperCase());
            HttpURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setReadTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-COINONE-PAYLOAD", encodingPayload);
            connection.setRequestProperty("X-COINONE-SIGNATURE", signature);

            // Writing the post data to the HTTP request body
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
            bw.write(encodingPayload);
            bw.close();

            connection.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer response = new StringBuffer();
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();

            Gson gson = new Gson();
            returnObj = gson.fromJson(response.toString(), JsonObject.class);

        }catch(Exception e){
            log.error("[COINONE][ERROR][CoinOne post http] {}", e.getMessage());
        }

        return returnObj;
    }
}

