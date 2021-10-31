package com.coin.autotrade.service.function;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.*;

@Slf4j
public class OkexFunction extends ExchangeFunction{

    final private String ACCESS_TOKEN   = "access_token";
    final private String SECRET_KEY     = "secret_key";
    final private String API_PASSWORD   = "apiPassword";
    final private String BUY            = "buy";
    final private String SELL           = "sell";
    final private String ORDERBOOK_SIZE = "100";
    final private String ALREADY_TRADED = "51402";
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
                    keyList.put(API_PASSWORD, exCoin.getApiPassword());
                }
            }
        }catch (Exception e){
            log.error("[OKEX][ERROR][SET COIN TOKEN] {}", e.getMessage());
        }
    }

    /**
     * okex global 자전 거래
     * @param symbol coin + "-" + symbol
     */
    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[OKEX][AUTOTRADE START]");
        int returnCode    = DataCommon.CODE_SUCCESS;

        try{

            String[] coinData = ServiceCommon.setCoinData(autoTrade.getCoin());
            String symbol     = coinData[0] + "-" + getCurrency(getExchange(), coinData[0], coinData[1]);

            // mode 처리
            String mode = autoTrade.getMode();
            if(DataCommon.MODE_RANDOM.equals(mode)){
                mode = (ServiceCommon.getRandomInt(0,1) == 0) ? DataCommon.MODE_BUY : DataCommon.MODE_SELL;
            }

            // 1 : 매수 , 2 : 매도
            String firstOrderId  = "";
            String secondOrderId = "";
            if(DataCommon.MODE_BUY.equals(mode)){
                if( !(firstOrderId = createOrder(BUY, price, cnt, symbol)).equals("")){   // 매수
                    if((secondOrderId = createOrder(SELL,price, cnt, symbol)).equals("")){               // 매도
                        Thread.sleep(3000);
                        cancelOrder(firstOrderId, symbol);                      // 매도 실패 시, 매수 취소
                    }
                }
            }else if(DataCommon.MODE_SELL.equals(mode)){
                if( !(firstOrderId = createOrder(SELL,price, cnt, symbol)).equals("")){
                    if((secondOrderId = createOrder(BUY,price, cnt, symbol)).equals("")){
                        Thread.sleep(3000);
                        cancelOrder(firstOrderId, symbol);
                    }
                }
            }
            // 최초던진 값이 거래가 될 수 있기에 2번째 값은 무조건 취소진행
            if(!firstOrderId.equals("") || !secondOrderId.equals("")){
                Thread.sleep(3000);
                if(!firstOrderId.equals("")){
                    cancelOrder(firstOrderId, symbol);
                }
                if(!secondOrderId.equals("")){
                    cancelOrder(secondOrderId, symbol);
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[OKEX][ERROR][AUTOTRADE] {}", e.getMessage());
        }

        log.info("[OKEX][AUTOTRADE End]");
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
            log.info("[OKEX][LIQUIDITY] Start");
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
                String secondsCnt      = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);


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
            log.error("[OKEX][ERROR][LIQUIDITY] {}", e.getMessage());
        }
        log.info("[OKEX][LIQUIDITY] End");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[OKEX][FISHINGTRADE START]");

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

                while (cnt.compareTo(new BigDecimal("0")) > 0) {
                    if (!noMatchFirstTick) break;                   // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                    if (noIntervalFlag) Thread.sleep(intervalTime); // intervalTime 만큼 휴식 후 매수 시작
                    String orderId            = "";
                    BigDecimal cntForExcution = new BigDecimal(String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double) fishing.getMinExecuteCnt(), (double) fishing.getMaxExecuteCnt()) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL));

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
                        log.info("[OKEX][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
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
                        log.error("[OKEX][FISHINGTRADE] While loop is broken, Because create order is failed");
                        break;
                    }
                }
                // 무조건 일단 취소
                Thread.sleep(1000);
                cancelOrder(orderList.get(i).get("order_id"), symbol);
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[OKEX][ERROR][FISHINGTRADE] {}", e.getMessage());
        }

        log.info("[OKEX][FISHINGTRADE END]");
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

            log.info("[OKEX][ORDER BOOK - REQUEST] symbol:{}" ,  coin);

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
            header.addProperty("instId",    symbol);
            header.addProperty("tdMode",    "cash");  // 지정가
            header.addProperty("side",      type);
            header.addProperty("ordType",   "limit"); // 지정가
            header.addProperty("px",        price);         // price
            header.addProperty("sz",        cnt);


            JsonObject returnVal = postHttpMethod(DataCommon.OKEX_ENDPOINT_CREATE_ORDER, gson.toJson(header));
            String status        = gson.fromJson(returnVal.get("code"), String.class);
            if(status.equals("0")){
                JsonArray objArr = gson.fromJson(returnVal.get("data"), JsonArray.class);
                JsonObject obj  = gson.fromJson(objArr.get(0), JsonObject.class);
                orderId         = gson.fromJson(obj.get("ordId"), String.class);
                log.info("[OKEX][SUCCESS][CREATE ORDER - response] response : {}", gson.toJson(returnVal));
            }else{
                log.error("[OKEX][ERROR][CREATE ORDER - response] response :{}", gson.toJson(returnVal));
            }

        }catch (Exception e){
            log.error("[OKEX][ERROR][CREATE ORDER] {}",e.getMessage());
        }
        return orderId;
    }

    /* okex global 거래 취소 */
    public int cancelOrder(String orderId, String symbol) {

        int returnValue = DataCommon.CODE_ERROR;

        try {
            JsonObject header = new JsonObject();
            header.addProperty("instId",    symbol);
            header.addProperty("ordId",   orderId);

            JsonObject returnVal = postHttpMethod(DataCommon.OKEX_ENDPOINT_CANCEL_ORDER, gson.toJson(header));
            String status       = gson.fromJson(returnVal.get("code"), String.class);
            JsonArray objArr    = gson.fromJson(returnVal.get("data"), JsonArray.class);
            JsonObject obj      = gson.fromJson(objArr.get(0), JsonObject.class);

            if (status.equals("0") || ALREADY_TRADED.equals(gson.fromJson(obj.get("sCode"), String.class))) {
                returnValue = DataCommon.CODE_SUCCESS;
                orderId         = gson.fromJson(obj.get("ordId"), String.class);
                log.info("[OKEX][SUCCESS][CANCEL ORDER - response] response : {}", gson.toJson(returnVal));
            } else {
                log.error("[OKEX][ERROR][CANCEL ORDER - response] response:{}", gson.toJson(returnVal));
            }
        }catch(Exception e){
            log.error("[OKEX][ERROR][CANCEL ORDER] {}", e.getMessage());
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
            log.error("[OKEX][ERROR][GET CURRENCY] {}",e.getMessage());
        }
        return returnVal;
    }


    /* Http post method */
    public JsonObject postHttpMethod(String endPoint, String body) {
        URL url;
        String inputLine;
        String encodingPayload;
        JsonObject returnObj = null;

        try{
            log.info("[OKEX][POST HTTP] request : {}", body);

            String currentTime = getCurrentTime();
            String sign = makeSignature(currentTime, "POST", endPoint, body);

            url = new URL(DataCommon.OKEX_URL + endPoint);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setReadTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            // Set Header for OKKE API
            connection.setRequestProperty("OK-ACCESS-KEY", keyList.get(ACCESS_TOKEN));
            connection.setRequestProperty("OK-ACCESS-SIGN", sign);
            connection.setRequestProperty("OK-ACCESS-TIMESTAMP", currentTime);
            connection.setRequestProperty("OK-ACCESS-PASSPHRASE", keyList.get(API_PASSWORD));

            // Writing the post data to the HTTP request body
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
            bw.write(body);
            bw.close();

            String response = (connection.getErrorStream() == null)
                    ? getResponseMsg(connection.getInputStream()) : getResponseMsg(connection.getErrorStream());

            Gson gson = new Gson();
            returnObj = gson.fromJson(response, JsonObject.class);

        } catch(Exception e){
            log.error("[OKEX][ERROR][POST HTTP] {}", e.getMessage());
        }

        return returnObj;
    }

    // Get Input response message
    public String getResponseMsg(InputStream stream) throws Exception{
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        StringBuffer response = new StringBuffer();
        String inputLine;
        while ((inputLine = br.readLine()) != null) {
            response.append(inputLine);
        }
        br.close();

        return response.toString();
    }

    // 2021-10-27T22:59:11.222Z 형식으로 Return
    public String getCurrentTime(){
        StringBuilder nowStr = new StringBuilder(Instant.now().toString());
        return nowStr.toString();
    }

    public String makeSignature(String timestamp, String method, String endPoint, String body){
        StringBuilder builder = new StringBuilder();
        builder.append(timestamp);
        builder.append(method);
        builder.append(endPoint);
        builder.append(body);

        return getHmacSha256(builder.toString());
    }

    /* Hmac sha 256 start **/
    public String getHmacSha256(String message){
        String returnVal = "";
        try{
            String secretKey         = keyList.get(SECRET_KEY);
            SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] bytes = sha256_HMAC.doFinal(message.getBytes(StandardCharsets.UTF_8));
            returnVal = Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e){
            log.error("[OKEX][ERROR][GET JMACSHA256] {}",e.getMessage());
        }
        return returnVal;
    }

    public Exchange getExchange() {  return super.exchange;  }
    public void setExchange(Exchange exchange) {  super.exchange = exchange; }

}
