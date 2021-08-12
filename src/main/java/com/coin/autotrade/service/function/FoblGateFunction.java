package com.coin.autotrade.service.function;

import com.coin.autotrade.common.BeanUtils;
import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.*;
import com.coin.autotrade.repository.ExchangeRepository;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class FoblGateFunction {

    private String API_KEY               = "apiKey";
    private String SECRET_KEY            = "secretKey";
    private String USER_ID               = "userId";
    private Map<String, String> keyList  = new HashMap<>();
    Gson gson                            = new Gson();
    Exchange exchange                    = null;
    AutoTrade autoTrade                  = null;
    Liquidity liquidity                  = null;
    User user                            = null;
    String SELL                          = "ask";
    String BUY                           = "bid";

    ExchangeRepository exchageRepository;

    /** 생성자로서, 생성될 때, injection**/
    public FoblGateFunction(){
        exchageRepository   = (ExchangeRepository) BeanUtils.getBean(ExchangeRepository.class);
    }

    /**
     * Foblgate Function initialize for autotrade
     * @param autoTrade
     * @param user
     */
    public void initFoblGateAutoTrade(AutoTrade autoTrade, User user, Exchange exchange){
        this.user      = user;
        this.autoTrade = autoTrade;
        this.exchange  = exchange;
    }

    /**
     * Foblgate Function initialize for liquidity
     * @param liquidity
     * @param user
     * @param exchange
     */
    public void initFoblGateLiquidity(Liquidity liquidity, User user, Exchange exchange){
        this.user      = user;
        this.liquidity = liquidity;
        this.exchange  = exchange;
    }

    /** 해당 user 정보를 이용해 API 키를 셋팅한다 */
    public void setApiKey(String coin, String coinId){
        try{
            // 키 값이 셋팅되어 있지 않다면
            if(keyList.size() < 1){
                // Set token key
                for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
                    if(exCoin.getCoinCode().equals(coin) && exCoin.getId() == Long.parseLong(coinId)){
                        keyList.put(USER_ID,     exCoin.getExchangeUserId());
                        keyList.put(API_KEY,     exCoin.getPublicKey());
                        keyList.put(SECRET_KEY,  exCoin.getPrivateKey());
                    }
                }
                log.info("[FOBLGATE][Set Key] First Key setting in instance API:{}, secret:{}",keyList.get(API_KEY), keyList.get(SECRET_KEY));
            }
        }catch (Exception e){
            log.error("[FOBLGATE][ERROR][Set Key] {}",e.getMessage());
        }

    }

    /**
     * 포블게이트 자전거래 로직
     * @param price
     * @param cnt
     * @param coin
     * @param symbol
     * @param mode
     * @return
     */
    public int startAutoTrade(String price, String cnt, String coin,String coinId, String symbol, String mode){
        log.info("[FOBLGATE][AUTOTRADE START]");
        setApiKey(coin, coinId);    // Key 값 셋팅
        int returnCode    = DataCommon.CODE_ERROR;

        try{

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
                        cancelOrder(buyOrderId,BUY, price, symbol);                      // 매도 실패 시, 매수 취소
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
                        cancelOrder(sellOrderId,SELL, price, symbol);
                    }
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[ERROR][FOBLGATE][AUTOTRADE] {}", e.getMessage());
        }

        log.info("[FOBLGATE][AUTOTRADE END]");
        return returnCode;
    }


    /** 호가유동성 function */
    public int startLiquidity(Map list, int minCnt, int maxCnt, String coin,String coinId, String symbol){
        setApiKey(coin, coinId);    // Key 값 셋팅

        int returnCode = DataCommon.CODE_ERROR;
        List sellList = (ArrayList) list.get("sell");
        List buyList  = (ArrayList) list.get("buy");
        List<HashMap<String,String>> sellCancelList = new ArrayList();
        List<HashMap<String,String>> buyCancelList = new ArrayList();

        try{

            Thread.sleep(1000);
            /** 매도 **/
            log.info("[FOBLGATE][LIQUIDITY SELL START]");
            for(int i = 0; i < sellList.size(); i++){

                HashMap<String,String> sellValue = new HashMap<>();
                String price        = (String) sellList.get(i);
                String cnt          = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                String orderId      = createOrder(SELL, price, cnt, symbol);
                if(!orderId.equals("0")){
                    sellValue.put("orderId",orderId);
                    sellValue.put("price",price);
                    sellCancelList.add(sellValue);
                }
                Thread.sleep(300);
            }
            log.info("[FOBLGATE][LIQUIDITY SELL END]");
            Thread.sleep(700);

            /** 매도 취소 **/
            log.info("[FOBLGATE][LIQUIDITY SELL CANCEL START]");
            for(int i=0; i < sellCancelList.size(); i++){
                Map<String,String> cancelData = (HashMap) sellCancelList.get(i);
                String orderId = cancelData.get("orderId");
                String price   = cancelData.get("price");
                int returnStr = cancelOrder(orderId, SELL, price, symbol);
                // Coinone 방어로직
                Thread.sleep(300);
            }
            log.info("[FOBLGATE][LIQUIDITY SELL CANCEL END]");

            Thread.sleep(1000);

            /** 매수 **/
            log.info("[FOBLGATE][LIQUIDITY BUY START]");
            for(int i = 0; i < buyList.size(); i++){

                HashMap<String,String> buyValue = new HashMap<>();
                String price        = (String) buyList.get(i);
                String cnt          = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                String orderId      = createOrder(BUY, price, cnt, symbol);
                if(!orderId.equals("0")){
                    buyValue.put("orderId",orderId);
                    buyValue.put("price",price);
                    buyCancelList.add(buyValue);
                }
                // Coinone 방어로직
                Thread.sleep(300);
            }
            log.info("[FOBLGATE][LIQUIDITY BUY END]");

            // sleep
            Thread.sleep(700);

            /** 매수 취소 **/
            log.info("[FOBLGATE][LIQUIDITY BUY CANCEL START]");
            for(int i=0; i < buyCancelList.size(); i++){
                Map<String, String> cancelData = (HashMap) buyCancelList.get(i);
                String orderId = cancelData.get("orderId");
                String price   = cancelData.get("price");
                int returnStr = cancelOrder(orderId,BUY, price, symbol);
                // Coinone 방어로직
                Thread.sleep(300);
            }
            log.info("[FOBLGATE][LIQUIDITY BUY CANCEL END]");

            Thread.sleep(1000);
        }catch(Exception e){
            log.error("[ERROR][FOBLGATE] {}", e.getMessage());
        }
        return returnCode;
    }

    /** 포블게이트 매수/매도 로직 */
    public String createOrder(String type, String price, String cnt, String symbol){

        String orderId = "";
        try{

            Map<String, String> header = new HashMap<>();
            header.put("mbId",      keyList.get(USER_ID));  // userId
            header.put("pairName",  symbol);                // coin/currency ex) ATX/BTC
            header.put("action",    type);                  // ask(sell), bid(buy)
            header.put("price",     price);                 // price
            header.put("amount",    cnt);                   // cnt
            header.put("apiKey",    keyList.get(API_KEY));  // api key
            String secretHash = makeApiHash(keyList.get(API_KEY) + keyList.get(USER_ID) + symbol + type + price+ cnt+ keyList.get(SECRET_KEY));


            log.info("[FOBLGATE][CREATE ORDER] mbId:{},pairName:{},action:{},price:{},amount:{},apiKey:{},secretHash:{}",
                    keyList.get(USER_ID), symbol, type, price, cnt, keyList.get(API_KEY), secretHash);

            JsonObject returnVal = postHttpMethod(DataCommon.FOBLGATE_CREATE_ORDER, secretHash, header);
            String status        = gson.fromJson(returnVal.get("status"), String.class);
            if(status.equals("0")){
                orderId = gson.fromJson(returnVal.get("data"), String.class);
                log.info("[SUCCESS][FOBLEGATE][CREATE ORDER - response] response : {}", gson.toJson(returnVal));
            }else{
                String errorMsg = returnVal.get("message").toString();
                log.error("[ERROR][FOBLGATE][CREATE ORDER - response] response :{}", gson.toJson(returnVal));
            }

        }catch (Exception e){
            log.error("[ERROR][FOBLGATE][CREATE ORDER] {}",e.getMessage());
        }
        return orderId;
    }

    /** 포블게이트 매수/매도 취소로직 */
    public int cancelOrder(String ordNo, String type, String price, String symbol){

        int returnCode = DataCommon.CODE_ERROR;
        try{

            Map<String, String> header = new HashMap<>();
            header.put("mbId",      keyList.get(USER_ID));  // userId
            header.put("pairName",  symbol);                // coin/currency ex) ATX/BTC
            header.put("ordNo",     ordNo);                 // order Id
            header.put("action",    type);                  // order Id
            header.put("ordPrice",  price);                 // price
            header.put("apiKey",    keyList.get(API_KEY));  // api key
            String secretHash = makeApiHash(keyList.get(API_KEY) + keyList.get(USER_ID) + symbol + ordNo + type + price+ keyList.get(SECRET_KEY));


            log.info("[FOBLGATE][CANCEL ORDER] mbId:{},pairName:{},action:{},price:{},ordNo:{},apiKey:{},secretHash:{}",
                    keyList.get(USER_ID), symbol, type, price, ordNo, keyList.get(API_KEY), secretHash);

            JsonObject returnVal = postHttpMethod(DataCommon.FOBLGATE_CANCEL_ORDER, secretHash, header);
            String status        = gson.fromJson(returnVal.get("status"), String.class);
            if(status.equals("0")){
                returnCode = DataCommon.CODE_SUCCESS;
                log.info("[SUCCESS][FOBLGATE][CANCEL ORDER - response] response:{}", gson.toJson(returnVal));
            }else{
                String errorMsg = returnVal.get("message").toString();
                log.error("[ERROR][FOBLGATE][CANCEL ORDER - response] response:{}", gson.toJson(returnVal));
            }

        }catch (Exception e){
            log.error("[ERROR][FOBLGATE][CANCEL ORDER] {}",e.getMessage());
        }
        return returnCode;
    }

    /**
     * Fobl Gate Order book api
     * @param coin
     * @return
     */
    public String getOrderBook(Exchange exchange, String coin, String coinId){
        if(getExchange() == null){ setExchange(exchange); } // Exchange setting
        setApiKey(coin, coinId);

        String returnRes = "";
        try{
            log.info("[FOBLGATE][ORDER BOOK START]");
            String currency = getCurrency(exchange, coin, coinId);
            if(currency.equals("")){
                log.error("[FOBLGATE][ERROR][ORDER BOOK] There is no coin");
            }
            String pairName = coin+"/"+currency;

            Map<String, String> header = new HashMap<>();
            header.put("apiKey",keyList.get(API_KEY));
            header.put("pairName",pairName);
            String secretHash = makeApiHash(keyList.get(API_KEY) + pairName + keyList.get(SECRET_KEY));

            log.info("[FOBLGATE][ORDER BOOK - REQUEST] apiKey:{}, parName:{}, hash:{}", keyList.get(API_KEY), pairName, secretHash);
            JsonObject returnVal = postHttpMethod(DataCommon.FOBLGATE_ORDERBOOK, secretHash, header);
            String status        = gson.fromJson(returnVal.get("status"), String.class);
            if(status.equals("0")){
                returnRes = gson.toJson(returnVal);
                log.info("[SUCCESS][FOBLGATE][ORDER BOOK]");
            }else{
                log.error("[ERROR][FOBLGATE][ORDER BOOK - RESPONSE] response:{}", gson.toJson(returnVal));

            }

            log.info("[FOBLGATE][ORDER BOOK END]");

        }catch (Exception e){
            log.error("[ERROR][FOBLGATE][ORDER BOOK] {}",e.getMessage());
        }

        return returnRes;
    }


    /**
     * Fobl Gate 의 경우 통화 기준으로 필요함.
     * @param coin
     * @return
     */
    public String getCurrency(Exchange exchange, String coin, String coinId){
        String returnVal = "";
        try {
            // 거래소를 체크하는 이유는 여러거래소에서 같은 코인을 할 수 있기에
            if(exchange.getExchangeCoin().size() > 0){
                for(ExchangeCoin data : exchange.getExchangeCoin()){
                    if(data.getCoinCode().equals(coin) && data.getId() == Long.parseLong(coinId)){
                        returnVal = data.getCurrency();
                    }
                }
            }
        }catch(Exception e){
            log.error("[ERROR][FOBLGATE][GET CURRENCY] {}",e.getMessage());
        }
        return returnVal;
    }


    /**
     * API 이용하기 전, 해쉬값으로 변환하기 위한 메서드
     * @param targetStr
     * @return
     */
    public String makeApiHash(String targetStr){
        StringBuffer sb = new StringBuffer();
        try{
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
        }catch (Exception e){
            log.error("[ERROR][FOBLGATE][API HASH] {}",e.getMessage());
        }

        return "";
    }

    /**
     * HTTP POST Method for Foblgate
     * @param targetUrl  - target url
     * @param secretHash - 암호화한 값
     * @param formData   - post에 들어가는 body 데이터
     * @return
     */
    public JsonObject postHttpMethod(String targetUrl, String secretHash,  Map<String, String> datas ) {
        URL url;
        JsonObject returnObj = new JsonObject();
        String inputLine     = "";
        String twoHyphens    = "--";
        String boundary      = "*******";
        String lineEnd       = "\r\n";
        String delimiter     = twoHyphens + boundary + lineEnd;


        try{
            url = new URL(targetUrl);

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestProperty("secretheader", secretHash);
            connection.setRequestProperty("Content-Type","multipart/form-data;boundary="+boundary);
            connection.setRequestProperty("Accept"      , "*/*");
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setReadTimeout(DataCommon.TIMEOUT_VALUE);
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
            log.error("[FOBLGATE][ERROR][FOBLGATE POST HTTP] {}", e.getMessage());
        }


        return returnObj;
    }




    public Exchange getExchange() { return exchange;  }
    public void setExchange(Exchange exchange) { this.exchange = exchange; }

}
