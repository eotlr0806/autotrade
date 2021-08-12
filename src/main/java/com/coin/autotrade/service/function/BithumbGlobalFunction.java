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
import java.math.BigDecimal;
import java.net.URL;
import java.security.PublicKey;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class BithumbGlobalFunction {

    Exchange  exchange            = null;
    AutoTrade autoTrade           = null;
    Liquidity liquidity           = null;
    User      user                = null;
    Gson      gson                = new Gson();
    String ACCESS_TOKEN           = "access_token";
    String SECRET_KEY             = "secret_key";
    Map<String, String> keyList   = new HashMap<>();

    final String BUY        = "buy";
    final String SELL       = "sell";
    final String version    = "V1.0.0";

    /** init */
    public void initBithumbGlobalAutoTrade(AutoTrade autoTrade, User user, Exchange exchange){
        this.user      = user;
        this.autoTrade = autoTrade;
        this.exchange  = exchange;

        String[] coinData = ServiceCommon.setCoinData(autoTrade.getCoin());
        // Set token key
        for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
            if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1])){
                keyList.put(ACCESS_TOKEN, exCoin.getPublicKey());
                keyList.put(SECRET_KEY,   exCoin.getPrivateKey());
            }
        }
    }

    /** init */
    public void initBithumbGlobalLiquidity(Liquidity liquidity, User user, Exchange exchange){
        this.user      = user;
        this.liquidity = liquidity;
        this.exchange  = exchange;

        String[] coinData = ServiceCommon.setCoinData(liquidity.getCoin());
        // Set token key
        for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
            if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1])){
                keyList.put(ACCESS_TOKEN, exCoin.getPublicKey());
                keyList.put(SECRET_KEY,   exCoin.getPrivateKey());
            }
        }
    }



    /**
     * Bithumb global 자전 거래
     * @param symbol coin + "-" + symbol
     * @return
     */
    public int startAutoTrade(String price, String cnt, String coin, String coinId, String symbol, String mode){
        log.info("[BITHUMBGLOBAL][AUTOTRADE START]");
        int returnCode    = DataCommon.CODE_ERROR;

        try{
            if(symbol.split("-")[1].equals("BTC")){
                cnt = setCutCoinCnt(cnt,0);
            }else{
                cnt = setCutCoinCnt(cnt,1);
            }
            // mode 처리
            if(DataCommon.MODE_RANDOM.equals(mode)){
                mode = (ServiceCommon.getRandomInt(0,1) == 0) ? DataCommon.MODE_BUY : DataCommon.MODE_SELL;
            }

            // 1 : 매수 , 2 : 매도
            if(DataCommon.MODE_BUY.equals(mode)){
                String buyOrderId  = "";
                if( !(buyOrderId = createOrder(BUY, price, cnt, symbol)).equals("")){   // 매수
                    Thread.sleep(300);
                    if(!createOrder(SELL,price, cnt, symbol).equals("")){               // 매도
                        returnCode = DataCommon.CODE_SUCCESS;
                    }else{
                        Thread.sleep(300);
                        cancelOrder(buyOrderId, symbol);                      // 매도 실패 시, 매수 취소
                    }
                }
            }else if(DataCommon.MODE_SELL.equals(mode)){
                String sellOrderId  = "";
                if( !(sellOrderId = createOrder(SELL,price, cnt, symbol)).equals("")){
                    Thread.sleep(300);
                    if(!createOrder(BUY,price, cnt, symbol).equals("")){
                        returnCode = DataCommon.CODE_SUCCESS;
                    }else{
                        Thread.sleep(300);
                        cancelOrder(sellOrderId, symbol);
                    }
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[ERROR][BITHUMBGLOBAL][AUTOTRADE] {}", e.getMessage());
        }

        log.info("[BITHUMBGLOBAL][AUTOTRADE End]");
        return returnCode;
    }


    /** 호가유동성 function */
    public int startLiquidity(Map list, int minCnt, int maxCnt, String coin,String coinId, String symbol){

        int returnCode = DataCommon.CODE_ERROR;
        List sellList = (ArrayList) list.get("sell");
        List buyList  = (ArrayList) list.get("buy");
        List<HashMap<String,String>> sellCancelList = new ArrayList();
        List<HashMap<String,String>> buyCancelList = new ArrayList();

        try{

            Thread.sleep(1000);
            /** 매도 **/
            log.info("[BITHUMBGLOBAL][LIQUIDITY SELL START]");
            for(int i = 0; i < sellList.size(); i++){

                HashMap<String,String> sellValue = new HashMap<>();
                String price        = String.valueOf(sellList.get(i));
                String cnt          = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                if(symbol.split("-")[1].equals("BTC")){  cnt = setCutCoinCnt(cnt,0); }
                else{ cnt = setCutCoinCnt(cnt,1); }

                String orderId      = createOrder(SELL, price, cnt, symbol);
                if(!orderId.equals("0")){
                    sellValue.put("orderId",orderId);
                    sellCancelList.add(sellValue);
                }
                Thread.sleep(300);
            }
            log.info("[BITHUMBGLOBAL][LIQUIDITY SELL END]");
            Thread.sleep(700);

            /** 매도 취소 **/
            log.info("[BITHUMBGLOBAL][LIQUIDITY SELL CANCEL START]");
            for(int i=0; i < sellCancelList.size(); i++){
                Map<String,String> cancelData = (HashMap) sellCancelList.get(i);
                String orderId = cancelData.get("orderId");
                int returnStr  = cancelOrder(orderId, symbol);
                // Coinone 방어로직
                Thread.sleep(300);
            }
            log.info("[BITHUMBGLOBAL][LIQUIDITY SELL CANCEL END]");

            Thread.sleep(1500);

            /** 매수 **/
            log.info("[BITHUMBGLOBAL][LIQUIDITY BUY START]");
            for(int i = 0; i < buyList.size(); i++){

                HashMap<String,String> buyValue = new HashMap<>();
                String price        = ServiceCommon.setFormatNum(String.valueOf(buyList.get(i)));
                String cnt          = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                if(symbol.split("-")[1].equals("BTC")){  cnt = setCutCoinCnt(cnt,0); }
                else{ cnt = setCutCoinCnt(cnt,1); }

                String orderId      = createOrder(BUY, price, cnt, symbol);
                if(!orderId.equals("0")){
                    buyValue.put("orderId",orderId);
                    buyCancelList.add(buyValue);
                }
                // Coinone 방어로직
                Thread.sleep(300);
            }
            log.info("[BITHUMBGLOBAL][LIQUIDITY BUY END]");

            // sleep
            Thread.sleep(700);

            /** 매수 취소 **/
            log.info("[BITHUMBGLOBAL][LIQUIDITY BUY CANCEL START]");
            for(int i=0; i < buyCancelList.size(); i++){
                Map<String, String> cancelData = (HashMap) buyCancelList.get(i);
                String orderId = cancelData.get("orderId");
                int returnStr = cancelOrder(orderId, symbol);
                // Coinone 방어로직
                Thread.sleep(300);
            }
            log.info("[BITHUMBGLOBAL][LIQUIDITY BUY CANCEL END]");

            Thread.sleep(1000);
        }catch(Exception e){
            log.error("[ERROR][BITHUMBGLOBAL] {}", e.getMessage());
        }
        return returnCode;
    }


    /** Biyhumb global 매수/매도 로직 */
    public String createOrder(String type, String price, String cnt, String symbol){

        String orderId = "";

        try{

            JsonObject header = new JsonObject();
            header.addProperty("apiKey",    keyList.get(ACCESS_TOKEN));
            header.addProperty("msgNo",     System.currentTimeMillis());
            header.addProperty("price",     price);
            header.addProperty("quantity",  cnt);
            header.addProperty("side",      type);
            header.addProperty("symbol",    symbol);
            header.addProperty("timestamp", System.currentTimeMillis());
            header.addProperty("type",      "limit"); // 지정가
            header.addProperty("signature", setSignature(header));

            log.info("[BITHUMBGLOBAL][CREATE ORDER - reuqest] request : {}", gson.toJson(header));
            JsonObject returnVal = postHttpMethod(DataCommon.BITHUMB_GLOBAL_CREATE_ORDER, gson.toJson(header));
            String status        = gson.fromJson(returnVal.get("code"), String.class);
            if(status.equals("0")){
                JsonObject obj  = gson.fromJson(returnVal.get("data"), JsonObject.class);
                orderId         = gson.fromJson(obj.get("orderId"), String.class);
                log.info("[SUCCESS][BITHUMBGLOBAL][CREATE ORDER - response] response : {}", gson.toJson(returnVal));
            }else{
                log.error("[ERROR][BITHUMBGLOBAL][CREATE ORDER - response] response :{}", gson.toJson(returnVal));
            }

        }catch (Exception e){
            log.error("[ERROR][BITHUMBGLOBAL][CREATE ORDER] {}",e.getMessage());
        }
        return orderId;
    }

    /**
     * Bithumb global 거래 취소
     * @param symbol
     * @param orderId
     * @return
     */
    public int cancelOrder(String orderId, String symbol) {

        int returnValue = DataCommon.CODE_ERROR;
        String errorCode = "";
        String errorMsg = "";

        try {
            JsonObject header = new JsonObject();
            header.addProperty("apiKey",    keyList.get(ACCESS_TOKEN));
            header.addProperty("msgNo",     System.currentTimeMillis());
            header.addProperty("orderId",   orderId);
            header.addProperty("symbol",    symbol);
            header.addProperty("timestamp", System.currentTimeMillis());
            header.addProperty("signature", setSignature(header));

            log.info("[BITHUMBGLOBAL][CANCEL ORDER - request] userId:{}, value:{}", user.getUserId(), gson.toJson(header));

            JsonObject json = postHttpMethod(DataCommon.BITHUMB_GLOBAL_CANCEL_ORDER, gson.toJson(header));
            String status   = gson.fromJson(json.get("code"), String.class);
            if (status.equals("0")) {
                returnValue = DataCommon.CODE_SUCCESS;
                log.info("[SUCCESS][BITHUMBGLOBAL][CANCEL ORDER - response] response:{}", gson.toJson(json));
            } else {
                log.error("[ERROR][BITHUMBGLOBAL][CANCEL ORDER - response] response:{}", gson.toJson(json));
            }
        }catch(Exception e){
            log.error("[ERROR][BITHUMBGLOBAL][CANCEL ORDER] {}", e.getMessage());
        }
        return returnValue;
    }




    /**
     * Bithumb global Order book api
     * @param coin
     * @return
     */
    public String getOrderBook(Exchange exchange, String coin, String coinId) {
        String returnRes = "";
        try{
            log.info("[BITHUMBGLOBAL][ORDER BOOK START]");
            String inputLine;
            String symbol   = getCurrency(exchange, coin, coinId);
            String request  = DataCommon.BITHUMB_GLOBAL_ORDERBOOK + "?symbol=" + coin + "-" + symbol;
            URL url = new URL(request);

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            connection.setRequestMethod("GET");

            log.info("[BITHUMBGLOBAL][ORDER BOOK - REQUEST] symbol:{}", "BITHUMBGLOBAL",  coin);

            int returnCode = connection.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer response = new StringBuffer();
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();
            returnRes = response.toString();

            log.info("[BITHUMBGLOBAL][ORDER BOOK END]");

        }catch (Exception e){
            log.error("[ERROR][BITHUMBGLOBAL][ORDER BOOK] {}",e.getMessage());
        }

        return returnRes;
    }


    /**
     * Get 각 코인에 등록한 통화
     * @param exchange
     * @param coin
     * @return
     */
    public String getCurrency(Exchange exchange,String coin, String coinId){
        String returnVal = "";
        try {
            // Thread로 돌때는 최초에 셋팅을 해줘서 DB 조회가 필요 없음.
            if(exchange.getExchangeCoin().size() > 0){
                for(ExchangeCoin data : exchange.getExchangeCoin()){
                    if(data.getCoinCode().equals(coin) && data.getId() == Long.parseLong(coinId)){
                        returnVal = data.getCurrency();
                    }
                }
            }
        }catch(Exception e){
            log.error("[ERROR][BITHUMBGLOBAL][GET CURRENCY] {}",e.getMessage());
        }
        return returnVal;
    }


    /**
     * Http post method
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
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setReadTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");

            // Writing the post data to the HTTP request body
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
            bw.write(payload);
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


    /**
     *  Hmac sha 256 start
     *  **/
    public String getHmacSha256(String message){
        String returnVal = "";
        try{
            String secret = keyList.get(SECRET_KEY);

            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] bytes = sha256_HMAC.doFinal(message.getBytes());
            returnVal =  byteArrayToHexString(bytes);
        } catch (Exception e){
            log.error("[ERROR][BITHUMBGLOBAL][GET JMACSHA256] {}",e.getMessage());
        }
        return returnVal.toLowerCase();
    }

    private static String byteArrayToHexString(byte[] b) {
        StringBuilder hs = new StringBuilder();
        String stmp;
        for (int n = 0; b != null && n < b.length; n++) {
            stmp = Integer.toHexString(b[n] & 0XFF);
            if (stmp.length() == 1)
                hs.append('0');
            hs.append(stmp);
        }
        return hs.toString().toLowerCase();
    }
    /**
     *  Hmac sha 256 end
     *  **/

    /**
     * Cnt 를 소수점 첫째짜리 까지만 하도록 변경
     * @param cnt
     * @return
     */
    private String setCutCoinCnt(String cnt, int dot){
        double doubleCnt = Double.parseDouble(cnt);
        int pow          = (dot == 0) ? 1 : (int) Math.pow(10,dot);

        String cutCnt    = String.valueOf(Math.floor(doubleCnt * pow) / pow);

        return cutCnt;
    }

    /**
     * making signature method
     * @param header
     * @return
     */
    public String setSignature(JsonObject header){
        String sign = "";
        int idx = 0;
        for(String key : header.keySet()){
            if(idx == header.size() - 1){  sign += key + "=" + (gson.toJson(header.get(key)).replace("\"","") );  }
            else{  sign += key + "=" + (gson.toJson(header.get(key))).replace("\"","") + "&";  }
            idx++;
        }
        return getHmacSha256(sign);
    }


    public Exchange getExchange() {  return exchange;  }
    public void setExchange(Exchange exchange) {  this.exchange = exchange; }

}
