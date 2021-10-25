package com.coin.autotrade.service.function;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.net.URL;
import java.util.*;

@Slf4j
public class OkexFunction extends ExchangeFunction{

    final private String ACCESS_TOKEN   = "access_token";
    final private String SECRET_KEY     = "secret_key";
    final private String BUY            = "buy";
    final private String SELL           = "sell";
    final private String ORDERBOOK_SIZE = "100";
    Map<String, String> keyList         = new HashMap<>();

    @Override
    public void initClass(AutoTrade autoTrade, User user, Exchange exchange){
        super.autoTrade = autoTrade;
        setCommonValue(user, exchange);
        setCoinToken(ServiceCommon.setCoinData(autoTrade.getCoin()));
    }

    @Override
    public void initClass(Liquidity liquidity, User user, Exchange exchange){
        super.liquidity = liquidity;
        setCommonValue(user, exchange);
        setCoinToken(ServiceCommon.setCoinData(liquidity.getCoin()));
    }

    @Override
    public void initClass(Fishing fishing, User user, Exchange exchange, CoinService coinService){
        super.fishing     = fishing;
        super.coinService = coinService;
        setCommonValue(user, exchange);
        setCoinToken(ServiceCommon.setCoinData(fishing.getCoin()));
    }

    private void setCommonValue(User user,  Exchange exchange){
        super.user     = user;
        super.exchange = exchange;
    }

    /** 코인 토큰 정보 셋팅 **/
    private void setCoinToken(String[] coinData){
        // Set token key
        try{
            for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
                if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1])){
                    keyList.put(ACCESS_TOKEN, exCoin.getPublicKey());
                    keyList.put(SECRET_KEY,   exCoin.getPrivateKey());
                }
            }
        }catch (Exception e){
            log.error("[ERROR][BITHUMBGLOBAL][SET COIN TOKEN] {}", e.getMessage());
        }
    }

    /**
     * Bithumb global 자전 거래
     * @param symbol coin + "-" + symbol
     */
    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[BITHUMBGLOBAL][AUTOTRADE START]");
        int returnCode    = DataCommon.CODE_SUCCESS;

        try{

            String[] coinData = ServiceCommon.setCoinData(autoTrade.getCoin());
            String symbol     = coinData[0] + "-" + getCurrency(getExchange(), coinData[0], coinData[1]);
            cnt               = setCutCoinCnt(symbol, cnt);

            // mode 처리
            String mode = autoTrade.getMode();
            if(DataCommon.MODE_RANDOM.equals(mode)){
                mode = (ServiceCommon.getRandomInt(0,1) == 0) ? DataCommon.MODE_BUY : DataCommon.MODE_SELL;
            }

            // 1 : 매수 , 2 : 매도
            if(DataCommon.MODE_BUY.equals(mode)){
                String buyOrderId  = "";
                if( !(buyOrderId = createOrder(BUY, price, cnt, symbol)).equals("")){   // 매수
                    Thread.sleep(300);
                    if(createOrder(SELL,price, cnt, symbol).equals("")){               // 매도
                        cancelOrder(buyOrderId, symbol);                      // 매도 실패 시, 매수 취소
                    }
                }
            }else if(DataCommon.MODE_SELL.equals(mode)){
                String sellOrderId  = "";
                if( !(sellOrderId = createOrder(SELL,price, cnt, symbol)).equals("")){
                    Thread.sleep(300);
                    if(createOrder(BUY,price, cnt, symbol).equals("")){
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
    @Override
    public int startLiquidity(Map list){
        int returnCode = DataCommon.CODE_SUCCESS;

        Queue<String> sellQueue = (LinkedList) list.get("sell");
        Queue<String> buyQueue  = (LinkedList) list.get("buy");
        List<Map<String,String>> CancelList = new ArrayList();

        try{
            log.info("[BITHUMBGLOBAL][LIQUIDITY] Start");
            String[] coinData = ServiceCommon.setCoinData(liquidity.getCoin());
            String symbol     = coinData[0] + "-" + getCurrency(getExchange(), coinData[0], coinData[1]);
            int minCnt        = liquidity.getMinCnt();
            int maxCnt        = liquidity.getMaxCnt();




            while(sellQueue.size() > 0 || buyQueue.size() > 0){
                String randomMode = (ServiceCommon.getRandomInt(1,2) == 1) ? BUY : SELL;
                String firstOrderId    = "";
                String secondsOrderId  = "";
                String firstPrice      = "";
                String secondsPrice    = "";
                String firstCnt        = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                firstCnt               = setCutCoinCnt(symbol, firstCnt);
                String secondsCnt      = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                secondsCnt             = setCutCoinCnt(symbol, secondsCnt);

                if(sellQueue.size() > 0 && buyQueue.size() > 0 && randomMode.equals(BUY)){
                    firstPrice   = buyQueue.poll();
                    firstOrderId = createOrder(BUY, firstPrice, firstCnt, symbol);

                    Thread.sleep(300);
                    secondsPrice   = sellQueue.poll();
                    secondsOrderId = createOrder(SELL, secondsPrice, secondsCnt, symbol);
                }else if(buyQueue.size() > 0 && sellQueue.size() > 0 && randomMode.equals(SELL)){
                    firstPrice   = sellQueue.poll();
                    firstOrderId = createOrder(SELL, firstPrice, firstCnt, symbol);

                    Thread.sleep(300);
                    secondsPrice   = buyQueue.poll();
                    secondsOrderId = createOrder(BUY, secondsPrice, secondsCnt, symbol);
                }

                if(!firstOrderId.equals("") || !secondsOrderId.equals("")){
                    Thread.sleep(1000);
                    if(!firstOrderId.equals("")){
                        cancelOrder(firstOrderId, symbol);
                    }
                    if(!secondsOrderId.equals("")){
                        Thread.sleep(300);
                        cancelOrder(secondsOrderId, symbol);
                    }
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[BITHUMBGLOBAL][ERROR][LIQUIDITY] {}", e.getMessage());
        }
        log.info("[BITHUMBGLOBAL][LIQUIDITY] End");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[BITHUMBGLOBAL][FISHINGTRADE START]");

        int returnCode    = DataCommon.CODE_SUCCESS;

        try{
            String[] coinData = ServiceCommon.setCoinData(fishing.getCoin());
            String symbol     = coinData[0] + "-" + getCurrency(getExchange(), coinData[0], coinData[1]);

            // mode 처리
            String mode = fishing.getMode();
            if(DataCommon.MODE_RANDOM.equals(mode)){
                mode = (ServiceCommon.getRandomInt(0,1) == 0) ? DataCommon.MODE_BUY : DataCommon.MODE_SELL;
            }

            boolean noIntervalFlag   = true;    // 해당 플래그를 이용해 마지막 매도/매수 후 바로 intervalTime 없이 바로 다음 매수/매도 진행
            boolean noMatchFirstTick = true;    // 해당 플래그를 이용해 매수/매도를 올린 가격이 현재 최상위 값이 맞는지 다른 사람의 코인을 사지 않게 방지

            for(String temp : list.keySet()){  mode = temp; }
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode);
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            /* Start */
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double) fishing.getMinContractCnt(), (double) fishing.getMaxContractCnt()) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                cnt        = setCutCoinCnt(symbol, cnt);

                String orderId = "";
                if(DataCommon.MODE_BUY.equals(mode)) {
                    orderId = createOrder(BUY,  tickPriceList.get(i), cnt, symbol);
                }else{
                    orderId = createOrder(SELL, tickPriceList.get(i), cnt, symbol);
                }
                if(!orderId.equals("")){                                                // 매수/매도가 정상적으로 이뤄졌을 경우 데이터를 list에 담는다
                    Map<String, String> orderMap = new HashMap<>();
                    orderMap.put("price" ,tickPriceList.get(i));
                    orderMap.put("cnt" ,cnt);
                    orderMap.put("order_id" ,orderId);
                    orderList.add(orderMap);
                }
            }

            /* Sell Start */
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = ServiceCommon.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("cnt"));
                cnt                                = new BigDecimal(setCutCoinCnt(symbol, cnt.toPlainString()));

                while (cnt.compareTo(new BigDecimal("0")) > 0) {
                    if (!noMatchFirstTick) break;                   // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                    if (noIntervalFlag) Thread.sleep(intervalTime); // intervalTime 만큼 휴식 후 매수 시작
                    String orderId            = "";
                    BigDecimal cntForExcution = new BigDecimal(String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double) fishing.getMinExecuteCnt(), (double) fishing.getMaxExecuteCnt()) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL));
                    cntForExcution            = new BigDecimal(setCutCoinCnt(symbol, cntForExcution.toPlainString()));

                    // 남은 코인 수와 매도/매수할 코인수를 비교했을 때, 남은 코인 수가 더 적다면.
                    if (cnt.compareTo(cntForExcution) < 0) {
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
                    String orderPrice = copiedOrderMap.get("price");
                    if (!orderPrice.equals(nowFirstTick)) {
                        log.info("[BITHUMBGLOBAL][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        noMatchFirstTick = false;
                        break;
                    }

                    if(DataCommon.MODE_BUY.equals(mode)) {
                        orderId = createOrder(SELL, copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol);
                    }else{
                        orderId = createOrder(BUY,  copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol);
                    }

                    if(!orderId.equals("")){
                        cnt = cnt.subtract(cntForExcution);
                    }else{
                        log.error("[BITHUMBGLOBAL][FISHINGTRADE] While loop is broken, Because create order is failed");
                        break;
                    }
                }
                // 무조건 일단 취소
                Thread.sleep(1000);
                cancelOrder(orderList.get(i).get("order_id"), symbol);

            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[BITHUMBGLOBAL][ERROR][FISHINGTRADE] {}", e.getMessage());
        }

        log.info("[BITHUMBGLOBAL][FISHINGTRADE END]");
        return returnCode;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = "";
        try{
            log.info("[OKEX][ORDER BOOK START]");
            String coin   = coinWithId[0];
            String coinId = coinWithId[1];
            String inputLine;
            String symbol   = getCurrency(exchange, coin, coinId);
            // instId=BTC-USDT&sz=100
            String request  = DataCommon.OKEX_ORDERBOOK + "?instId=" + coin + "-" + symbol + "&sz=" + ORDERBOOK_SIZE;
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

            log.info("[OKEX][ORDER BOOK END]");

        }catch (Exception e){
            log.error("[OKEX][ERROR][ORDER BOOK] {}",e.getMessage());
        }

        return returnRes;
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

    /* Bithumb global 거래 취소 */
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


    /* Get 각 코인에 등록한 통화 */
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


    /* Http post method */
    public JsonObject postHttpMethod(String targetUrl, String payload) {
        URL url;
        String inputLine;
        String encodingPayload;
        JsonObject returnObj = null;

        try{
            log.info("[BITHUMBGLOBAL][POST HTTP] request : {}", payload);

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
            log.error("[ERROR][BITTHUMBGLOBAL][POST HTTP] {}", e.getMessage());
        }

        return returnObj;
    }


    /* Hmac sha 256 start **/
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

    /* Hmac sha 256 end */
    private String byteArrayToHexString(byte[] b) {
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

    /* Cnt 를 소수점 첫째짜리 까지만 하도록 변경 */
    private String setCutCoinCnt(String symbol, String cnt){
        int dot = 0;
        if(symbol.split("-")[1].equals("BTC")) {
            dot = 0;
        }else {
            dot = 1;
        }

        double doubleCnt = Double.parseDouble(cnt);
        int pow          = (dot == 0) ? 1 : (int) Math.pow(10,dot);

        String cutCnt    = String.valueOf(Math.floor(doubleCnt * pow) / pow);
        return cutCnt;
    }

    /* making signature method */
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


    public Exchange getExchange() {  return super.exchange;  }
    public void setExchange(Exchange exchange) {  super.exchange = exchange; }

}
