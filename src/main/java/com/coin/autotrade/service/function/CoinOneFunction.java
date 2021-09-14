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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Coin one 에서 사용할 class
 */
@Slf4j
public class CoinOneFunction {

    private User user                   = null;
    private AutoTrade autoTrade         = null;
    private Exchange exchange           = null;
    private Liquidity liquidity         = null;
    private Fishing fishing             = null;
    private String ACCESS_TOKEN         = "access_token";
    private String SECRET_KEY           = "secret_key";
    private Map<String, String> keyList = new HashMap<>();
    Gson gson                           = new Gson();
    private String ALREADY_TRADED       = "116";
    private int CANCEL_AGAIN            = 8;
    private CoinService coinService     = null; // Fishing 시, 사용하기 위한 coin Service class

    /** 자전 거래를 이용하기위한 초기값 설정 */
    public void initCoinOne(AutoTrade autoTrade, User user, Exchange exchange) throws Exception {
        this.autoTrade = autoTrade;
        setCommonValue(user, exchange, ServiceCommon.setCoinData(autoTrade.getCoin()));
    }

    /** 호가 유동성을 이용하기 위한 초기값 설정 */
    public void initCoinOne(Liquidity liquidity, User user, Exchange exchange) throws Exception{
        this.liquidity  = liquidity;
        setCommonValue(user, exchange, ServiceCommon.setCoinData(liquidity.getCoin()));
    }

    /** 매매 긁기를 이용하기 위한 초기값 설정 */
    public void initCoinOne(Fishing fishing, User user, Exchange exchange, CoinService coinService) throws Exception{
        this.fishing     = fishing;
        this.coinService = coinService;
        setCommonValue(user, exchange, ServiceCommon.setCoinData(fishing.getCoin()));
    }

    // init 시, keyList 값 세팅
    private void setCommonValue(User user, Exchange exchange, String[] coinData){
        this.user       = user;
        this.exchange   = exchange;

        // Set token key
        for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
            if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1]) ){
                keyList.put(ACCESS_TOKEN, exCoin.getPublicKey());
                keyList.put(SECRET_KEY,   exCoin.getPrivateKey());
            }
        }
    }

    /**
     * Liquidity Trade Start
     * @param price
     * @param cnt
     * @return
     */
    public int startLiquidity(Map list){
        int returnCode = DataCommon.CODE_SUCCESS;

        List sellList = (ArrayList) list.get("sell");
        List buyList  = (ArrayList) list.get("buy");
        List<Map<String,String>> sellCancelList = new ArrayList();
        List<Map<String,String>> buyCancelList = new ArrayList();

        try{
            Thread.sleep(1000);
            int minCnt = liquidity.getMinCnt();
            int maxCnt = liquidity.getMaxCnt();

            String coin = liquidity.getCoin().split(";")[0];
            /** 매도 **/
            log.info("[COINONE][LIQUIDITY-SELL] Start");
            for(int i = 0; i < sellList.size(); i ++){

                String price        = (String) sellList.get(i);
                String cnt          = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                String orderId      = sellTrade(price, cnt, coin);
                if(!orderId.equals("")){
                    Map<String, String> sellValue = setDefaultMap(cnt, coin, price);
                    sellValue.put("is_ask","1");
                    sellValue.put("order_id",orderId);
                    sellCancelList.add(sellValue);
                }
                // Coinone 방어로직
                Thread.sleep(500);

            }
            log.info("[COINONE][LIQUIDITY-SELL] End");
            Thread.sleep(1000);

            /** 매도 취소 **/
            log.info("[COINONE][LIQUIDITY-SELL-cancel] Start");
            for(int i=0; i < sellCancelList.size(); i++){
                int returnStr = cancelTrade( (HashMap) sellCancelList.get(i));
                // Coinone 방어로직
                Thread.sleep(500);
            }
            log.info("[COINONE][LIQUIDITY-SELL-cancel] end");
            Thread.sleep(2000);

            /** 매수 **/
            log.info("[COINONE][LIQUIDITY-BUY] Start");
            for(int i = 0; i < buyList.size(); i ++){

                String price        = (String) buyList.get(i);
                String cnt          = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                String orderId      = buyTrade(price, cnt, coin);
                if(!orderId.equals("")){
                    Map<String, String> buyValue = setDefaultMap(cnt, coin, price);
                    buyValue.put("order_id",orderId);
                    buyValue.put("is_ask","1");
                    buyCancelList.add(buyValue);
                }
                // Coinone 방어로직
                Thread.sleep(500);
            }
            log.info("[COINONE][LIQUIDITY-BUY] End");

            // sleep
            Thread.sleep(1000);

            /** 매수 취소 **/
            log.info("[COINONE][LIQUIDITY-BUY-cancel] Start");
            for(int i=0; i < buyCancelList.size(); i++){
                int returnStr = cancelTrade( (HashMap) buyCancelList.get(i));
                // Coinone 방어로직
                Thread.sleep(500);
            }
            log.info("[COINONE][LIQUIDITY-BUY-cancel] end");

            Thread.sleep(1000);

        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[COINONE][ERROR][LIQUIDITY] {}", e.getMessage());
        }

        return returnCode;
    }

    /**
     * Auto Trade Start
     * @return
     */
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

                if(!(orderId = buyTrade(price, cnt, coin)).equals("")){
                    buyValue.put("order_id",orderId);
                    if(sellTrade(price, cnt, coin).equals("")){
                        cancelTrade(buyValue);      // 매수 후 매도 실패 시, 매수 값 취소
                    }
                }
            }else if(DataCommon.MODE_SELL.equals(mode)){
                String orderId ="";
                Map<String, String> sellValue = setDefaultMap(cnt, coin, price);
                sellValue.put("is_ask","1");

                if(!(orderId = sellTrade(price, cnt, coin)).equals("")){
                    sellValue.put("order_id",orderId);
                    if(buyTrade(price, cnt, coin).equals("")){
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

    /***
     * Fishing Trade
     * @return
     */
    public int startFishingTrade(Map<String,List> list, int intervalTime){

        log.info("[COINONE][FISHINGTRADE] Start");
        int returnCode = DataCommon.CODE_SUCCESS;

        try{
            String mode              = "";
            boolean noIntervalFlag   = true;    // 해당 플래그를 이용해 마지막 매도/매수 후 바로 intervalTime 없이 바로 다음 매수/매도 진행
            boolean noMatchFirstTick = true;    // 해당 플래그를 이용해 매수/매도를 올린 가격이 현재 최상위 값이 맞는지 다른 사람의 코인을 사지 않게 방지

            for(String temp : list.keySet()){  mode = temp; }
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode);

            String coin = fishing.getCoin().split(";")[0];

            /* 모드가 매수우선일 경우 */
            if(DataCommon.MODE_BUY.equals(mode)){
                ArrayList<Map<String,String>> buyList = new ArrayList<>();

                /* Buy Start */
                for (int i = 0; i < tickPriceList.size(); i++) {
                    String cnt                = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double) fishing.getMinContractCnt(), (double) fishing.getMaxContractCnt()) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                    String orderId            = "";
                    Map<String, String> buyMap = setDefaultMap(cnt, coin, tickPriceList.get(i));
                    buyMap.put("is_ask","1");
                    if(!(orderId = buyTrade(tickPriceList.get(i), cnt, coin)).equals("")){
                        buyMap.put("order_id",orderId);
                        buyList.add(buyMap);
                    }
                    Thread.sleep(500);
                }

                /* Sell Start */
                for (int i = buyList.size()-1; i >= 0; i--) {

                    Map<String, String> copiedBuyMap  = ServiceCommon.deepCopy(buyList.get(i));
                    BigDecimal cnt                    = new BigDecimal(copiedBuyMap.get("qty"));
                    while(cnt.compareTo(new BigDecimal("0")) > 0){

                        if(!noMatchFirstTick) break;                   // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                        if(noIntervalFlag) Thread.sleep(intervalTime); // intervalTime 만큼 휴식 후 매수 시작

                        String orderId            = "";
                        BigDecimal cntForExcution = new BigDecimal(String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double) fishing.getMinExecuteCnt(), (double) fishing.getMaxExecuteCnt()) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL));

                        // 남은 코인 수와 매도/매수할 코인수를 비교했을 때, 남은 코인 수가 더 적다면.
                        if(cnt.compareTo(cntForExcution) < 0){
                            cntForExcution = cnt;
                            noIntervalFlag = false;
                        }else{
                            noIntervalFlag = true;
                        }

                        // 매도/매수 날리기전에 최신 매도/매수값이 내가 건 값이 맞는지 확인
                        String nowFirstTick = coinService.getFirstTick(fishing.getCoin(), fishing.getExchange()).get(DataCommon.MODE_BUY);
                        if(!copiedBuyMap.get("price").equals(nowFirstTick)){
                            log.info("[COINONE][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedBuyMap.get("price") , nowFirstTick);
                            noMatchFirstTick = false;
                            break;
                        }

                        if(!(orderId = sellTrade(copiedBuyMap.get("price"), cntForExcution.toPlainString(), coin)).equals("")){
                            copiedBuyMap.replace("qty", cntForExcution.toPlainString());
                            copiedBuyMap.replace("order_id", orderId);
                            cnt = cnt.subtract(cntForExcution);

                            Thread.sleep(500);
                            cancelTrade(copiedBuyMap);
                        } else{        // 매도 시, 에러발생하면 해당 매수건에 대해 그냥 바로 취소.
                            break;
                        }
                    }
                    // 혹여나 남은 개수가 있을 수 있어 취소 request
                    Thread.sleep(500);
                    cancelTrade(buyList.get(i));
                }
            }
            /* 모드가 매도우선일 경우 */
            else if(DataCommon.MODE_SELL.equals(mode)){
                ArrayList<Map<String,String>> sellList = new ArrayList<>();

                /* Sell Start */
                for (int i = 0; i < tickPriceList.size(); i++) {
                    String cnt                   = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double) fishing.getMinContractCnt(), (double) fishing.getMaxContractCnt()) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                    String orderId               = "";
                    Map<String, String> sellMap = setDefaultMap(cnt, coin, tickPriceList.get(i));
                    sellMap.put("is_ask","1");
                    if(!(orderId = sellTrade(tickPriceList.get(i), cnt, coin)).equals("")){
                        sellMap.put("order_id",orderId);
                        sellList.add(sellMap);
                    }
                    Thread.sleep(500);
                }

                /* Buy Start */
                for (int i = sellList.size()-1; i >= 0; i--) {

                    Map<String, String> copiedSellMap  = ServiceCommon.deepCopy(sellList.get(i));
                    BigDecimal cnt                     = new BigDecimal(copiedSellMap.get("qty"));
                    while(cnt.compareTo(new BigDecimal("0")) > 0){

                        if(!noMatchFirstTick) break;                   // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                        if(noIntervalFlag) Thread.sleep(intervalTime); // intervalTime 만큼 휴식 후 매수 시작

                        String orderId            = "";
                        BigDecimal cntForExcution = new BigDecimal(String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double) fishing.getMinExecuteCnt(), (double) fishing.getMaxExecuteCnt()) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL));

                        // 남은 코인 수와 매도/매수할 코인수를 비교했을 때, 남은 코인 수가 더 적다면.
                        if(cnt.compareTo(cntForExcution) < 0){
                            cntForExcution = cnt;
                            noIntervalFlag = false;
                        }else{
                            noIntervalFlag = true;
                        }
                        // 매도/매수 날리기전에 최신 매도/매수값이 내가 건 값이 맞는지 확인
                        String nowFirstTick = coinService.getFirstTick(fishing.getCoin(), fishing.getExchange()).get(DataCommon.MODE_SELL);
                        if(!copiedSellMap.get("price").equals(nowFirstTick) ){
                            log.info("[COINONE][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedSellMap.get("price") , nowFirstTick);
                            noMatchFirstTick = false;
                            break;
                        }

                        if(!(orderId = buyTrade(copiedSellMap.get("price"), cntForExcution.toPlainString(), coin)).equals("")){
                            copiedSellMap.replace("qty", cntForExcution.toPlainString());
                            copiedSellMap.replace("order_id", orderId);
                            cnt = cnt.subtract(cntForExcution);

                            Thread.sleep(500);
                            cancelTrade(copiedSellMap);
                        }else{      // 매도 시, 에러발생하면 해당 매수건에 대해 그냥 바로 취소.
                            break;
                        }
                    }
                    // 혹시 모르기에 다 끝나고 날림
                    Thread.sleep(500);
                    cancelTrade(sellList.get(i));
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[COINONE][ERROR][FISHINGTRADE] {}", e.getMessage());
        }

        log.info("[COINONE][FISHINGTRADE] End");
        return returnCode;
    }


    /**
     * API for buy method
     * @return
     */
    public String buyTrade(String price, String cnt ,String coin){

        String orderId   = "";
        String errorCode = "";
        String errorMsg  = "";

        try{

            JsonObject header = setDefaultRequest(cnt, coin, price);
            JsonObject json   = postHttpMethod(DataCommon.COINONE_LIMIT_BUY, gson.toJson(header));

            String result = json.get("result").toString().replace("\"","");
            if("success".equals(result)){
                orderId = json.get("orderId").toString().replace("\"","");
                log.info("[COINONE][SUCCESS][BUYTRADE - response] response :{}", gson.toJson(json));
            }else{
                log.error("[COINONE][ERROR][BUYTRADE - response] response :{}", gson.toJson(json));
            }
        }catch (Exception e){
            log.error("[COINONE][ERROR][BUYTRADE] {}", e.getMessage());
        }

        return orderId;
    }

    /**
     * API for sell method
     * @param price
     * @param cnt
     * @return
     */
    public String sellTrade(String price, String cnt,String coin){

        String orderId   = "";
        String errorCode = "";
        String errorMsg  = "";

        try{

            JsonObject header = setDefaultRequest(cnt, coin, price);
            JsonObject json   = postHttpMethod(DataCommon.COINONE_LIMIT_SELL, gson.toJson(header));
            String result = json.get("result").toString().replace("\"","");
            if("success".equals(result)){
                orderId = json.get("orderId").toString().replace("\"","");
                log.info("[COINONE][SUCCESS][SELLTRADE - response] result:{},  orderId:{}", result, orderId);
            }else{
                log.error("[COINONE][ERROR][SELLTRADE - response] response :{}", gson.toJson(json));
            }
        }catch (Exception e){
            log.error("[COINONE][ERROR][SELLTRADE] {}", e.getMessage());
        }

        return orderId;
    }

    /**
     * cancel
     * @param data
     * @return
     */
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



    /**
     * HMAC Signature 만드는 method
     * @param payload
     * @param secret
     * @return
     */
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
     * @param coin
     * @return
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

    /**
     * default 로 필요한 데이터를 받아 buy/sell/cancel 메서드에 전달
     * @param cnt
     * @param currency
     * @param price
     * @return
     */
    private Map<String,String> setDefaultMap(String cnt, String currency, String price){
        Map<String, String> defaultMap = new HashMap<>();
        defaultMap.put("qty",cnt);
        defaultMap.put("currency",currency);
        defaultMap.put("price", price);

        return defaultMap;
    }

    /**
     * default 로 필요한 데이터를 받아 request 전 셋팅 후 반환
     * @param cnt
     * @param currency
     * @param price
     * @return
     */
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




    /**
     * HTTP POST Method for coinone
     * @param targetUrl
     * @param payload
     * @return
     */
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

