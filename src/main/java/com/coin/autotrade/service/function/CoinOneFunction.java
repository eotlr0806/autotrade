package com.coin.autotrade.service.function;


import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.*;
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
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
    public void initCoinOne(Fishing fishing, User user, Exchange exchange) throws Exception{
        this.fishing = fishing;
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
    public int startLiquidity(Map list, int minCnt, int maxCnt){
        int returnCode = DataCommon.CODE_ERROR;

        List sellList = (ArrayList) list.get("sell");
        List buyList  = (ArrayList) list.get("buy");
        List<HashMap<String,String>> sellCancelList = new ArrayList();
        List<HashMap<String,String>> buyCancelList = new ArrayList();

        try{
            Thread.sleep(1000);

            String coin = liquidity.getCoin().split(";")[0];
            /** 매도 **/
            log.info("[COINONE][Liquidity-sell] Start");
            for(int i = 0; i < sellList.size(); i ++){


                HashMap<String,String> sellValue = new HashMap<>();
                String price        = (String) sellList.get(i);
                String cnt          = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                String orderId      = sellTrade(price, cnt, coin);
                if(!orderId.equals("")){
                    sellValue.put("qty",cnt);
                    sellValue.put("price",price);
                    sellValue.put("currency",coin);
                    sellValue.put("order_id",orderId);
                    sellValue.put("is_ask","1");
                    sellCancelList.add(sellValue);
                }
                // Coinone 방어로직
                Thread.sleep(500);

            }
            log.info("[COINONE][Liquidity-sell] End");
            Thread.sleep(1000);

            /** 매도 취소 **/
            log.info("[COINONE][Liquidity-sell-cancel] Start");
            for(int i=0; i < sellCancelList.size(); i++){
                int returnStr = cancelTrade( (HashMap) sellCancelList.get(i));
                // Coinone 방어로직
                Thread.sleep(500);
            }
            log.info("[COINONE][Liquidity-sell-cancel] end");
            Thread.sleep(2000);

            /** 매수 **/
            log.info("[COINONE][Liquidity-buy] Start");
            for(int i = 0; i < buyList.size(); i ++){

                HashMap<String,String> buyValue = new HashMap<>();
                String price        = (String) buyList.get(i);
                String cnt          = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                String orderId      = buyTrade(price, cnt, coin);
                if(!orderId.equals("")){
                    buyValue.put("qty",cnt);
                    buyValue.put("price",price);
                    buyValue.put("currency",coin);
                    buyValue.put("order_id",orderId);
                    buyValue.put("is_ask","1");
                    buyCancelList.add(buyValue);
                }
                // Coinone 방어로직
                Thread.sleep(500);
            }
            log.info("[COINONE][Liquidity-buy] End");

            // sleep
            Thread.sleep(1000);

            /** 매수 취소 **/
            log.info("[COINONE][Liquidity-buy-cancel] Start");
            for(int i=0; i < buyCancelList.size(); i++){
                int returnStr = cancelTrade( (HashMap) buyCancelList.get(i));
                // Coinone 방어로직
                Thread.sleep(500);
            }
            log.info("[COINONE][Liquidity-buy-cancel] end");

            Thread.sleep(1000);

        }catch (Exception e){
            log.error("[ERROR][COINONE][Liquidity] {}", e.getMessage());
        }

        return returnCode;
    }

    /**
     * Auto Trade Start
     * @return
     */
    public int startAutoTrade(String price, String cnt, String mode){

        log.info("[COINONE][AutoTrade] Start");

        int returnCode = DataCommon.CODE_ERROR;

        try{
            // mode 처리
            if(DataCommon.MODE_RANDOM.equals(mode)){
                mode = (ServiceCommon.getRandomInt(0,1) == 0) ? DataCommon.MODE_BUY : DataCommon.MODE_SELL;
            }
            String coin = autoTrade.getCoin().split(";")[0];
            if(DataCommon.MODE_BUY.equals(mode)){
                String orderId ="";
                Map<String,String> buyValue = new HashMap<>();
                buyValue.put("qty",cnt);
                buyValue.put("price",price);
                buyValue.put("currency",coin);
                buyValue.put("is_ask","1");
                if(!(orderId = buyTrade(price, cnt, coin)).equals("")){
                    buyValue.put("order_id",orderId);

                    if(!sellTrade(price, cnt, coin).equals("")){
                        returnCode = DataCommon.CODE_SUCCESS;
                    }else{
                        cancelTrade(buyValue);      // 매수 후 매도 실패 시, 매수 값 취소
                    }
                }
            }else if(DataCommon.MODE_SELL.equals(mode)){
                String orderId ="";
                Map<String,String> sellValue = new HashMap<>();
                sellValue.put("qty",cnt);
                sellValue.put("price",price);
                sellValue.put("currency",coin );
                sellValue.put("is_ask","1");

                if(!(orderId = sellTrade(price, cnt, coin)).equals("")){
                    sellValue.put("order_id",orderId);

                    if(!buyTrade(price, cnt, coin).equals("")){
                        returnCode = DataCommon.CODE_SUCCESS;
                    }else{
                        cancelTrade(sellValue);      // 매수 후 매도 실패 시, 매수 값 취소
                    }
                }
            }
        }catch(Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[ERROR][COINONE][AutoTrade] {}", e.getMessage());

        }


        log.info("[COINONE][AutoTrade] End");

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
            JsonObject header = new JsonObject();
            header.addProperty(ACCESS_TOKEN,keyList.get(ACCESS_TOKEN));
            long nonce = System.currentTimeMillis();
            header.addProperty("nonce",nonce);
            header.addProperty("qty",Double.parseDouble(cnt));
            header.addProperty("currency",coin);
            header.addProperty("price",price);

            log.info("[COINONE][BuyTrade - request] userId:{}, value{}", user.getUserId(), gson.toJson(header));
            JsonObject json = postHttpMethod(DataCommon.COINONE_LIMIT_BUY, gson.toJson(header));

            String result = json.get("result").toString().replace("\"","");
            if("success".equals(result)){
                orderId = json.get("orderId").toString().replace("\"","");
                log.info("[SUCCESS][COINONE][BuyTrade - response] response :{}", gson.toJson(json));
            }else{
                log.error("[ERROR][COINONE][BuyTrade - response] response :{}", gson.toJson(json));
            }

        }catch (Exception e){
            log.error("[ERROR][COINONE][BuyTrade] {}", e.getMessage());
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
            JsonObject header = new JsonObject();
            header.addProperty(ACCESS_TOKEN,keyList.get(ACCESS_TOKEN));
            long nonce = System.currentTimeMillis();
            header.addProperty("nonce",nonce);
            header.addProperty("qty",Double.parseDouble(cnt));
            header.addProperty("currency",coin);
            header.addProperty("price",price);
            log.info("[COINONE][SellTrade - request] userId:{}, value {}", user.getUserId(), gson.toJson(header));

            JsonObject json = postHttpMethod(DataCommon.COINONE_LIMIT_SELL, gson.toJson(header));
            String result = json.get("result").toString().replace("\"","");
            if("success".equals(result)){
                orderId = json.get("orderId").toString().replace("\"","");
                log.info("[SUCCESS][COINONE][SellTrade - response] result:{},  orderId:{}", result, orderId);
            }else{
                log.error("[ERROR][COINONE][SellTrade - response] response :{}", gson.toJson(json));
            }
        }catch (Exception e){
            log.error("[ERROR][COINONE][SellTrade] {}", e.getMessage());
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
        String errorCode = "";
        String errorMsg  = "";


        try{
            JsonObject header = new JsonObject();
            header.addProperty(ACCESS_TOKEN, keyList.get(ACCESS_TOKEN));
            long nonce = System.currentTimeMillis();;
            header.addProperty("nonce",System.currentTimeMillis());
            header.addProperty("qty",Double.parseDouble(data.get("qty").toString()));
            header.addProperty("currency",data.get("currency").toString());
            header.addProperty("price",data.get("price").toString());
            header.addProperty("is_ask",Integer.parseInt(data.get("is_ask").toString()));
            header.addProperty("order_id",data.get("order_id").toString());

            log.info("[COINONE][CancelTrade - request] userId:{}, value{}", user.getUserId(), gson.toJson(header));

            JsonObject json = postHttpMethod(DataCommon.COINONE_CANCEL , gson.toJson(header));

            String result = json.get("result").toString().replace("\"","");
            if("success".equals(result)){
                returnValue = DataCommon.CODE_SUCCESS;
                log.info("[SUCCESS][COINONE][CancelTrade - response] response : {} ", gson.toJson(json) );
            }else{
                log.error("[ERROR][COINONE][CancelTrade - response] response :{}", gson.toJson(json));
            }
        }catch (Exception e){
            log.error("[ERROR][COINONE][CancelTrade] {}", e.getMessage());
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
             log.error("[ERROR][COINONE][ORDER BOOK] {}",e.getMessage());
         }

        return returnRes;
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

            log.info("[COINONE][Http Post - Request] Secret-Key {}, X-COINONE-PAYLOAD:{}, X-COINONE-SIGNATURE:{}",keyList.get(SECRET_KEY).toUpperCase(), encodingPayload ,signature);

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
            log.error("[ERROR][COINONE][CoinOne post http] {}", e.getMessage());
        }

        return returnObj;
    }
}

