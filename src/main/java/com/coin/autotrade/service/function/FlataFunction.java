package com.coin.autotrade.service.function;

import com.coin.autotrade.common.BeanUtils;
import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.*;
import com.coin.autotrade.repository.ExchangeRepository;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class FlataFunction {

    Exchange exchange   = null;
    User user           = null;
    AutoTrade autoTrade = null;
    Liquidity liquidity = null;
    Gson gson           = new Gson();
    String coinMinCnt   = "";
    private String BUY    = "1";
    private String SELL   = "2";

    private ExchangeRepository exchageRepository;


    /** 생성자로서, 생성될 때, injection**/
    public FlataFunction(){
        exchageRepository   = (ExchangeRepository) BeanUtils.getBean(ExchangeRepository.class);
    }


    /**
     * Flata Function initialize
     * @param autoTrade
     * @param user
     */
    public void initFlataAutoTrade(AutoTrade autoTrade, User user, Exchange exchange){
        this.user      = user;
        this.autoTrade = autoTrade;
        this.exchange  = exchange;
    }

    /**
     * Flata Function initialize
     * @param autoTrade
     * @param user
     */
    public void initFlataLiquidity(Liquidity liquidity, User user, Exchange exchange){
        this.user      = user;
        this.liquidity = liquidity;
        this.exchange  = exchange;
    }


    /**
     * Session key 생성
     * 해당 코인에 등록된 key를 가져와 그 코인에 맞는 session key 생성
     * @return
     */
    public String setSessionKey(String userPublicKey, String coinCode, String coinId){

        // 해당 계정에 대해 세션 키가 있을 경우 반환
        if(DataCommon.FLATA_SESSION_KEY.get(userPublicKey) != null){
            return DataCommon.FLATA_SESSION_KEY.get(userPublicKey);
        }

        String publicKey     = "";
        String secretKey     = "";
        String returnValue   = "";
        JsonObject header    = new JsonObject();
        try{

            if(exchange.getExchangeCoin().size() > 0){
                for(ExchangeCoin coin : exchange.getExchangeCoin()){
                    if(coin.getCoinCode().equals(coinCode) && coin.getId() == Long.parseLong(coinId)){
                        publicKey   = coin.getPublicKey();
                        secretKey = coin.getPrivateKey();
                        break;
                    }
                }
            }
            header.addProperty("acctid",publicKey);
            header.addProperty("acckey",secretKey);

            JsonObject response =  postHttpMethod(DataCommon.FLATA_MAKE_SESSION, gson.toJson(header));
            JsonObject item     =  response.getAsJsonObject("item");
            returnValue         =  item.get("sessionId").toString().replace("\"","");

            // 메모리에 저장
            DataCommon.FLATA_SESSION_KEY.put(userPublicKey, returnValue);
            log.info("[SUCCESS][FLATA][SET SESSION KEY] First session key {}: mapperedPublicKey : {}", returnValue, userPublicKey );

        }catch (Exception e){
            log.error("[ERROR][FLATA][MAKE SESSION KEY] {}" , e.getMessage());
        }

        return DataCommon.FLATA_SESSION_KEY.get(userPublicKey);
    }

    /**
     * 최초에 등록한 세션키를 가져옴.
     * @param coin
     * @return
     */
    public String getSessionKey(String coin, String coinId){
        String publicKey  = "";
        String sessionKey = "";
        try{
            for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
                if(exCoin.getCoinCode().equals(coin) && exCoin.getId() == Long.parseLong(coinId)){
                    publicKey = exCoin.getPublicKey();
                    break;
                }
            }

            if(!publicKey.equals("")){
                sessionKey = setSessionKey(publicKey, coin, coinId);
            }
        }catch (Exception e){
            log.error("[ERROR][FLATA][GET SESSION] {}", e.getMessage());
        }

        log.info("[FLATA][GET SESSION] Session key : {}", sessionKey );
        return sessionKey;
    }


    /**
     * Start auto trade function
     * @param symbol - coin / currency
     * @return
     */
    public int startAutoTrade(String price, String cnt, String coin, String coinId, String symbol, String mode){
        log.info("[FLATA][AUTOTRADE START]");

        int returnCode    = DataCommon.CODE_ERROR;
        String sessionKey = getSessionKey(coin, coinId);
        try{
            // mode 처리
            if(DataCommon.MODE_RANDOM.equals(mode)){
                mode = (ServiceCommon.getRandomInt(0,1) == 0) ? DataCommon.MODE_BUY : DataCommon.MODE_SELL;
            }

            // 1 : 매수 , 2 : 매도
            if(DataCommon.MODE_BUY.equals(mode)){
                String buyOrderId  = "0";
                if( !(buyOrderId = createOrder(BUY,price, cnt, symbol, sessionKey)).equals("0")){   // 매수
                    if(!createOrder(SELL,price, cnt, symbol,sessionKey).equals("0")){               // 매도
                        returnCode = DataCommon.CODE_SUCCESS;
                    }else{
                        cancelOrder(buyOrderId, sessionKey);                                        // 매도 실패 시, 매수 취소
                    }
                }
            }else if(DataCommon.MODE_SELL.equals(mode)){
                String sellOrderId  = "0";
                if( !(sellOrderId = createOrder(SELL,price, cnt, symbol, sessionKey)).equals("0")){
                    if(!createOrder(BUY,price, cnt, symbol, sessionKey).equals("0")){
                        returnCode = DataCommon.CODE_SUCCESS;
                    }else{
                        cancelOrder(sellOrderId, sessionKey);
                    }
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[ERROR][FLATA][AUTOTRADE] {}", e.getMessage());
        }

        log.info("[FLATA][AUTOTRADE END]");
        return returnCode;
    }


    /** 호가유동성 function */
    public int startLiquidity(Map list, int minCnt, int maxCnt, String coin, String coinId,  String symbol){
        int returnCode = DataCommon.CODE_ERROR;

        List sellList = (ArrayList) list.get("sell");
        List buyList  = (ArrayList) list.get("buy");
        List<HashMap<String,String>> sellCancelList = new ArrayList();
        List<HashMap<String,String>> buyCancelList = new ArrayList();
        String sessionKey = getSessionKey(coin, coinId);

        try{

            Thread.sleep(1000);
            /** 매도 **/
            log.info("[FLATA][LIQUIDITY SELL START]");
            for(int i = 0; i < sellList.size(); i++){

                HashMap<String,String> sellValue = new HashMap<>();
                String price        = (String) sellList.get(i);
                String cnt          = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                String orderId      = createOrder(SELL,  price, cnt, symbol, sessionKey);
                if(!orderId.equals("0")){
                    sellValue.put("orderId",orderId);
                    sellCancelList.add(sellValue);
                }
                Thread.sleep(300);
            }
            log.info("[FLATA][LIQUIDITY SELL END]");
            Thread.sleep(700);

            /** 매도 취소 **/
            log.info("[FLATA][LIQUIDITY SELL CANCEL END]");
            for(int i=0; i < sellCancelList.size(); i++){
                Map<String,String> cancelData = (HashMap) sellCancelList.get(i);
                int returnStr = cancelOrder(cancelData.get("orderId"), sessionKey);
                // Coinone 방어로직
                Thread.sleep(300);
            }
            log.info("[FLATA][LIQUIDITY SELL CANCEL END]");

            Thread.sleep(1000);

            /** 매수 **/
            log.info("[FLATA][LIQUIDITY BUY START]");
            for(int i = 0; i < buyList.size(); i++){

                HashMap<String,String> buyValue = new HashMap<>();
                String price        = (String) buyList.get(i);
                String cnt          = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                String orderId      = createOrder(BUY,  price, cnt, symbol, sessionKey);
                if(!orderId.equals("0")){
                    buyValue.put("orderId",orderId);
                    buyCancelList.add(buyValue);
                }
                // Coinone 방어로직
                Thread.sleep(300);
            }
            log.info("[FLATA][LIQUIDITY BUY END]");

            // sleep
            Thread.sleep(700);

            /** 매수 취소 **/
            log.info("[FLATA][LIQUIDITY BUY CANCEL START]");
            for(int i=0; i < buyCancelList.size(); i++){
                Map<String, String> cancelData = (HashMap) buyCancelList.get(i);
                int returnStr = cancelOrder(cancelData.get("orderId"), sessionKey);
                // Coinone 방어로직
                Thread.sleep(300);
            }
            log.info("[FLATA][LIQUIDITY BUY CANCEL END]");

            Thread.sleep(1000);
        }catch(Exception e){
            log.error("[ERROR][FLATA] {}", e.getMessage());
        }
        return returnCode;
    }


    /**
     * 매도/매수 function
     * @param type   - 1: 매수 / 2 : 매도
     * @param symbol - coin + / + currency
     * @return
     */
    public String createOrder(String type, String price, String cnt, String symbol, String sessionKey){

        String orderId = "";
        try{
            BigDecimal decimalPrice = new BigDecimal(price);
            BigDecimal decimalCnt   = new BigDecimal(cnt);
            BigDecimal tax          = new BigDecimal("1000");
            BigDecimal total        = decimalPrice.multiply(decimalCnt);
            BigDecimal resultTax    = total.divide(tax);
            String minCntFix        = getCoinMinCount(symbol);

            int dotIdx = minCntFix.indexOf(".");
            int oneIdx = minCntFix.indexOf("1");
            int length = minCntFix.length();
            int primary = 0;
            double doubleCnt = Double.parseDouble(cnt);
            // 소수
            if(dotIdx < oneIdx){
                // 34.232
                // 0.01
                primary = oneIdx - dotIdx;  // 소수점 2째 자리.
                int num = (int) Math.pow(10,primary);
                doubleCnt = Math.floor(doubleCnt * num) / num;
            }else{
                // 34.232
                //  1.0
                primary = dotIdx - 1; // 해당 자리에서 버림
                int num = ((int) Math.pow(10,primary) == 0) ? 1 : (int) Math.pow(10,primary);
                doubleCnt = Math.floor(doubleCnt / num) * num;
            }

            JsonObject header = new JsonObject();
            header.addProperty("symbol",symbol);
            header.addProperty("buySellType",type);
            header.addProperty("ordPrcType", "2");  // 1은 시장가, 2는 지정가
            header.addProperty("ordPrc",Double.parseDouble(price));
            header.addProperty("ordQty",doubleCnt);
            header.addProperty("ordFee",resultTax.doubleValue());

            String modeKo = (type.equals("1")) ? "BUY":"SELL";
            log.info("[FLATA][CREATE ORDER START] mode {} , valeus {}", modeKo, gson.toJson(header));

            JsonObject response = postHttpMethodWithSession(DataCommon.FLATA_CREATE_ORDER,gson.toJson(header), sessionKey);
            JsonObject item     = gson.fromJson(response.get("item").toString(), JsonObject.class);
            orderId             = item.get("ordNo").toString();

            // Order ID 가 0이면 에러
            if(!orderId.equals("0")){
                log.info("[SUCCESS][FLATA][CREATE ORDER] response", gson.toJson(response));
            }else{
                String msg = item.get("message").toString();
                log.info("[ERROR][FLATA][CREATE ORDER] response :{}", gson.toJson(response));
            }
        }catch (Exception e){
            log.error("[ERROR][FLATA][CREATE ORDER] {}",e.getMessage());
        }
        return orderId;
    }

    /**
     * 주문 취소 function
     * @param orderId
     * @param sessionKey
     * @return
     */
    public int cancelOrder(String orderId, String sessionKey){
        int returnVal = DataCommon.CODE_ERROR;
        try{
            JsonObject header = new JsonObject();
            header.addProperty("orgOrdNo", orderId);

            JsonObject response = postHttpMethodWithSession(DataCommon.FLATA_CANCEL_ORDER, gson.toJson(header), sessionKey);
            JsonObject item     = gson.fromJson(response.get("item").toString(), JsonObject.class);
            orderId             = item.get("ordNo").toString();

            if(orderId.equals("0") || "".equals(orderId)){
                String msg = item.get("message").toString();
                log.info("[ERROR][FLATA][Start Cancel Order] msg:{} , valeus:{}",  gson.toJson(header));
            }else{
                returnVal = DataCommon.CODE_SUCCESS;
                log.info("[SUCCESS][FLATA][Cancel Order]  orderId {}, valeus {}",  orderId, gson.toJson(header));
            }

        }catch (Exception e){
            log.error("[ERROR][FLATA][Cancel Order] {}", e.getMessage());
        }

        return returnVal;
    }

    /**
     * 호가 조회 API
     * @param coin
     * @return
     */
    public String getOrderBook(Exchange exchange, String coin, String coinId) {
        String returnRes = "";
        try{
            log.info("[FLATA][ORDER BOOK] START");
            String inputLine;
            String symbol = getCurrency(exchange, coin, coinId);
            String request  = DataCommon.FLATA_ORDERBOOK + "?symbol=" + coin + "/" + symbol + "&level=10";
            URL url = new URL(request);

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setReadTimeout(DataCommon.TIMEOUT_VALUE);

            log.info("[FLATA][ORDER BOOK - REQUEST] symbol:{}", coin);

            int returnCode = connection.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer response = new StringBuffer();
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();
            returnRes = response.toString();
            log.info("[FLATA][ORDER BOOK] End");

        }catch (Exception e){
            log.error("[ERROR][FLATA][ORDER BOOK] {}",e.getMessage());
        }

        return returnRes;
    }


    /**
     * 해당 코인의 최소 매수/매도 단위 조회
     * @param symbol
     * @return
     */
    public String getCoinMinCount(String symbol) {
        String returnRes = "";

        // 과거에 값을 부여한 케이스가 있다면 그걸 쓰면됨
        if(!coinMinCnt.equals("")){
            return coinMinCnt;
        }

        try{
            log.info("[FLATA][GET COIN INFO] Start");
            String inputLine;
            String request  = DataCommon.FLATA_COININFO + "?symbol=" + symbol + "&lang=ko";
            URL url = new URL(request);

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            log.info("[FLATA][GET COIN INFO - REQUEST] symbol:{}", symbol);

            int returnCode = connection.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer response = new StringBuffer();
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();

            JsonObject object = gson.fromJson(response.toString(), JsonObject.class);
            JsonArray array   = gson.fromJson(object.get("record").toString(), JsonArray.class);
            JsonObject data   = gson.fromJson(array.get(0).toString(), JsonObject.class);


            returnRes = data.get("ordUnitQty").toString();
            coinMinCnt = returnRes;
            log.info("[SUCCESS][FLATA][GET COIN INFO] coinMinCnt {} ",  coinMinCnt);
            log.info("[FLATA][GET COIN INFO] End");

        }catch (Exception e){
            log.error("[ERROR][FLATA][GET COIN INFO] {}",e.getMessage());
        }

        return returnRes;
    }


    /**
     * Coin의 등록된 화폐를 가져오는 로직
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
            log.error("[ERROR][FLATA][GET CUREENCY] {}",e.getMessage());
        }
        return returnVal;
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
        JsonObject returnObj = null;

        try{
            url = new URL(targetUrl);

            HttpURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");

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
            log.error("[ERROR][FLATA][FLATA POST HTTP] {}", e.getMessage());
        }

        return returnObj;
    }


    public JsonObject postHttpMethodWithSession(String targetUrl, String payload, String SessionKey) {
        URL url;
        String inputLine;
        JsonObject returnObj = null;

        try{
            url = new URL(targetUrl);

            HttpURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setReadTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Session", SessionKey);

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
            log.error("[ERROR][FLATA][FLATA HTTP POST] {}", e.getMessage());
        }

        return returnObj;
    }

    public void setExchange(Exchange exchange){
        this.exchange = exchange;
    }
    public Exchange getExchange(){
        return this.exchange;
    }

}
