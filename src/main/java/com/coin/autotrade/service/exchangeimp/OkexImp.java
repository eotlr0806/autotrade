package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Slf4j
public class OkexImp extends AbstractExchange {
    final private String BUY            = "buy";
    final private String SELL           = "sell";
    final private String ORDERBOOK_SIZE = "100";
    final private String SUCCESS        = "0";
    final private String ALREADY_TRADED = "51402";

    @Override
    public void initClass(AutoTrade autoTrade) throws Exception{
        super.autoTrade = autoTrade;
        setCoinToken(Utils.splitCoinWithId(autoTrade.getCoin()), autoTrade.getExchange());
    }

    @Override
    public void initClass(Liquidity liquidity) throws Exception{
        super.liquidity = liquidity;
        setCoinToken(Utils.splitCoinWithId(liquidity.getCoin()), liquidity.getExchange());
    }

    @Override
    public void initClass(RealtimeSync realtimeSync, CoinService coinService) throws Exception{
        super.realtimeSync = realtimeSync;
        super.coinService  = coinService;
        setCoinToken(Utils.splitCoinWithId(realtimeSync.getCoin()), realtimeSync.getExchange());
    }

    @Override
    public void initClass(Fishing fishing,  CoinService coinService) throws Exception{
        super.fishing     = fishing;
        super.coinService = coinService;
        setCoinToken(Utils.splitCoinWithId(fishing.getCoin()), fishing.getExchange());
    }

    /** 코인 토큰 정보 셋팅 **/
    private void setCoinToken(String[] coinData, Exchange exchange)  throws Exception{
        // Set token key

        for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
            if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1])){
                keyList.put(PUBLIC_KEY, exCoin.getPublicKey());
                keyList.put(SECRET_KEY,   exCoin.getPrivateKey());
                keyList.put(API_PASSWORD, exCoin.getApiPassword());
            }
        }

        log.info("[OKEX][SET API KEY] First Key setting in instance API:{}, secret:{}, password:{}",keyList.get(PUBLIC_KEY), keyList.get(SECRET_KEY), keyList.get(API_PASSWORD));
        if(keyList.isEmpty()){
            String msg = "There is no match coin. " + Arrays.toString(coinData) + " " + exchange.getExchangeCode();
            throw new Exception(msg);
        }
    }

    /**
     * okex global 자전 거래
     * @param symbol coin + "-" + symbol
     */
    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[OKEX][AUTOTRADE] START");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String symbol        = getSymbol(Utils.splitCoinWithId(autoTrade.getCoin()), autoTrade.getExchange());
            String firstAction   = "";
            String secondAction  = "";
            // mode 처리
            String mode = autoTrade.getMode();
            if(UtilsData.MODE_RANDOM.equals(mode)){
                mode = (Utils.getRandomInt(0,1) == 0) ? UtilsData.MODE_BUY : UtilsData.MODE_SELL;
            }
            // Trade 모드에 맞춰 순서에 맞게 거래 타입 생성
            if(UtilsData.MODE_BUY.equals(mode)){
                firstAction  = BUY;
                secondAction = SELL;
            }else if(UtilsData.MODE_SELL.equals(mode)){
                firstAction  = SELL;
                secondAction = BUY;
            }
            String orderId = ReturnCode.NO_DATA.getValue();
            if(!(orderId = createOrder(firstAction,price, cnt, symbol)).equals(ReturnCode.NO_DATA.getValue())){
                if(createOrder(secondAction,price, cnt, symbol).equals(ReturnCode.NO_DATA.getValue())){          // SELL 모드가 실패 시,
                    Thread.sleep(3000);
                    cancelOrder(orderId, symbol);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[OKEX][AUTOTRADE] {}", e.getMessage());
            e.printStackTrace();
        }

        log.info("[OKEX][AUTOTRADE] END");
        return returnCode;
    }

    /** 호가유동성 function */
    @Override
    public int startLiquidity(Map list){
        int returnCode = ReturnCode.SUCCESS.getCode();

        Queue<String> sellQueue  = (LinkedList) list.get("sell");
        Queue<String> buyQueue   = (LinkedList) list.get("buy");
        Queue<String> cancelList = new LinkedList<>();

        try{
            log.info("[OKEX][LIQUIDITY] START");
            String symbol     = getSymbol(Utils.splitCoinWithId(liquidity.getCoin()), liquidity.getExchange());

            while (!sellQueue.isEmpty() || !buyQueue.isEmpty() || !cancelList.isEmpty()) {
                String mode           = (Utils.getRandomInt(1, 2) == 1) ? UtilsData.MODE_BUY : UtilsData.MODE_SELL;
                boolean cancelFlag    = (Utils.getRandomInt(1, 2) == 1) ? true : false;
                String orderId        = ReturnCode.NO_DATA.getValue();
                String price          = "";
                String action         = "";
                String cnt            = Utils.getRandomString(liquidity.getMinCnt(), liquidity.getMaxCnt());

                if (!buyQueue.isEmpty() && mode.equals(UtilsData.MODE_BUY)) {
                    price   = buyQueue.poll();
                    action  = BUY;
                } else if (!sellQueue.isEmpty() && mode.equals(UtilsData.MODE_SELL)) {
                    price   = sellQueue.poll();
                    action  = SELL;
                }
                // 매수 로직
                if(!action.equals("")){
                    orderId = createOrder(action, price, cnt, symbol);
                    if(!orderId.equals(ReturnCode.NO_DATA.getValue())){
                        cancelList.add(orderId);
                    }
                    Thread.sleep(1000);
                }

                // 취소 로직
                if(!cancelList.isEmpty() && cancelFlag){
                    String cancelId = cancelList.poll();
                    cancelOrder(cancelId, symbol);
                    Thread.sleep(500);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[OKEX][LIQUIDITY] ERROR : {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[OKEX][LIQUIDITY] END");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[OKEX][FISHINGTRADE] START");

        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String symbol = getSymbol(Utils.splitCoinWithId(fishing.getCoin()), fishing.getExchange());

            // mode 처리
            String mode = fishing.getMode();
            if(UtilsData.MODE_RANDOM.equals(mode)){
                mode = (Utils.getRandomInt(0,1) == 0) ? UtilsData.MODE_BUY : UtilsData.MODE_SELL;
            }

            boolean noIntervalFlag   = true;    // 해당 플래그를 이용해 마지막 매도/매수 후 바로 intervalTime 없이 바로 다음 매수/매도 진행
            boolean noMatchFirstTick = true;    // 해당 플래그를 이용해 매수/매도를 올린 가격이 현재 최상위 값이 맞는지 다른 사람의 코인을 사지 않게 방지

            for(String temp : list.keySet()){  mode = temp; }
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode);
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            /* Start */
            log.info("[OKEX][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt     = Utils.getRandomString(fishing.getMinContractCnt(), fishing.getMaxContractCnt());
                String orderId = ReturnCode.NO_DATA.getValue();
                if(UtilsData.MODE_BUY.equals(mode)) {
                    orderId = createOrder(BUY,  tickPriceList.get(i), cnt, symbol);
                }else{
                    orderId = createOrder(SELL, tickPriceList.get(i), cnt, symbol);
                }
                if(!orderId.equals(ReturnCode.NO_DATA.getValue())){          // 매수/매도가 정상적으로 이뤄졌을 경우 데이터를 list에 담는다
                    Map<String, String> orderMap = new HashMap<>();
                    orderMap.put("price" ,tickPriceList.get(i));
                    orderMap.put("cnt" ,cnt);
                    orderMap.put("order_id" ,orderId);
                    orderList.add(orderMap);
                }
            }
            log.info("[OKEX][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");

            /* Sell Start */
            log.info("[OKEX][FISHINGTRADE][START BUY OR SELL TARGET PIECE COIN ]");
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = Utils.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("cnt"));

                while (cnt.compareTo(new BigDecimal("0")) > 0) {
                    if (!noMatchFirstTick) break;                   // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                    if (noIntervalFlag) Thread.sleep(intervalTime); // intervalTime 만큼 휴식 후 매수 시작
                    String orderId            = ReturnCode.NO_DATA.getValue();
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
                    String orderPrice = copiedOrderMap.get("price");
                    if (!orderPrice.equals(nowFirstTick)) {
                        log.info("[OKEX][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        noMatchFirstTick = false;
                        break;
                    }

                    if(UtilsData.MODE_BUY.equals(mode)) {
                        orderId = createOrder(SELL, copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol);
                    }else{
                        orderId = createOrder(BUY,  copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol);
                    }

                    if(!orderId.equals(ReturnCode.NO_DATA.getValue())){
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
            log.info("[OKEX][FISHINGTRADE][END BUY OR SELL TARGET PIECE COIN ]");
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[OKEX][FISHINGTRADE] ERROR {}", e.getMessage());
            e.printStackTrace();
        }

        log.info("[OKEX][FISHINGTRADE] END");
        return returnCode;
    }

    @Override
    public int startRealtimeTrade(JsonObject realtime) {
        log.info("[BITHUMB][REALTIME SYNC TRADE] START");
        int returnCode   = ReturnCode.SUCCESS.getCode();
        String realtimeChangeRate = "signed_change_rate";

        try {

            boolean isStart      = false;
            String symbol        = getSymbol(Utils.splitCoinWithId(realtimeSync.getCoin()), realtimeSync.getExchange());
            String[] currentTick = getTodayTick(symbol);
            String openingPrice  = currentTick[0];
            String currentPrice  = currentTick[1];
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
                }else if(isInRange == 1){    // 저항선보다 높을 경우
                    action       = SELL;
                    mode         = UtilsData.MODE_SELL;
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

            if(isStart){
                if( !(orderId = createOrder(action, targetPrice, cnt, symbol)).equals(ReturnCode.NO_DATA.getValue())){    // 매수/OrderId가 있으면 성공

                    Thread.sleep(300);

                    // 3. bestoffer set 로직
                    JsonArray array = makeBestofferAfterRealtimeSync(targetPrice, mode);
                    for (int i = 0; i < array.size(); i++) {
                        JsonObject object       = array.get(i).getAsJsonObject();
                        String bestofferPrice   = object.get("price").getAsString();
                        String bestofferCnt     = object.get("cnt").getAsString();
                        String bestofferOrderId = ReturnCode.NO_DATA.getValue();

                        if( !(bestofferOrderId = createOrder(action, bestofferPrice, bestofferCnt, symbol)).equals(ReturnCode.NO_DATA.getValue())){
                            log.info("[BITHUMB][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                    }
                    cancelOrder(orderId, symbol);
                }
            }
        }catch (Exception e){
            log.error("[BITHUMB][REALTIME SYNC TRADE] Error :{} ", e.getMessage());
            e.printStackTrace();
        }
        log.info("[BITHUMB][REALTIME SYNC TRADE] END");
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
        String request       = UtilsData.OKEX_TICK + "?instType=SPOT";
        String response      = getHttpMethod(request);
        JsonObject resObject = gson.fromJson(response, JsonObject.class);
        JsonArray resArr     = resObject.get("data").getAsJsonArray();
        for (JsonElement jsonElement : resArr){
            JsonObject object = jsonElement.getAsJsonObject();
            String symbolId   = object.get("instId").getAsString();
            if(symbol.equals(symbolId)){
                returnRes[0]    = object.get("sodUtc0").getAsString();
                returnRes[1]    = object.get("last").getAsString();
                log.info("[OKEX][GET TODAY TICK] response : {}", Arrays.toString(returnRes));
                break;
            }
        }

        if(returnRes[0] == null && returnRes[1] == null){
            log.error("[BITHUMB][GET TODAY TICK] response : {}", response);
            throw new Exception(response);
        }

        return returnRes;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = "";
        try{
            log.info("[OKEX][ORDER BOOK] START");
            String instId  = getSymbol(coinWithId, exchange);
            String request = UtilsData.OKEX_ORDERBOOK + "?instId=" + instId + "&sz=" + ORDERBOOK_SIZE;
            returnRes      = getHttpMethod(request);
            log.info("[OKEX][ORDER BOOK] END");

        }catch (Exception e){
            log.error("[OKEX][ORDER BOOK] ERROR {}",e.getMessage());
            e.printStackTrace();
        }

        return returnRes;
    }


    /** Biyhumb global 매수/매도 로직 */
    public String createOrder(String type, String price, String cnt, String symbol){
        String orderId = ReturnCode.NO_DATA.getValue();

        try{
            JsonObject header = new JsonObject();
            header.addProperty("instId",    symbol);
            header.addProperty("tdMode",    "cash");  // 지정가
            header.addProperty("side",      type);
            header.addProperty("ordType",   "limit"); // 지정가
            header.addProperty("px",        price);         // price
            header.addProperty("sz",        cnt);


            JsonObject returnVal = postHttpMethod(UtilsData.OKEX_ENDPOINT_CREATE_ORDER, gson.toJson(header));
            String status        = returnVal.get("code").getAsString();
            if(status.equals(SUCCESS)){
                JsonArray objArr = returnVal.get("data").getAsJsonArray();
                JsonObject obj   = objArr.get(0).getAsJsonObject();
                orderId          = obj.get("ordId").getAsString();
                log.info("[OKEX][CREATE ORDER] Response : {}", gson.toJson(returnVal));
            }else{
                log.error("[OKEX][CREATE ORDER] Response :{}", gson.toJson(returnVal));
            }
        }catch (Exception e){
            log.error("[OKEX][CREATE ORDER] Error {}",e.getMessage());
            e.printStackTrace();
        }
        return orderId;
    }

    /* okex global 거래 취소 */
    public int cancelOrder(String orderId, String symbol) {

        int returnValue = ReturnCode.FAIL.getCode();

        try {
            JsonObject header = new JsonObject();
            header.addProperty("instId",    symbol);
            header.addProperty("ordId",   orderId);

            JsonObject returnVal = postHttpMethod(UtilsData.OKEX_ENDPOINT_CANCEL_ORDER, gson.toJson(header));
            String status        = returnVal.get("code").getAsString();
            JsonArray objArr     = returnVal.get("data").getAsJsonArray();
            JsonObject obj       = objArr.get(0).getAsJsonObject();

            if (SUCCESS.equals(status) || ALREADY_TRADED.equals(obj.get("sCode").getAsString())) {
                returnValue = ReturnCode.SUCCESS.getCode();
                orderId     = obj.get("ordId").getAsString();
                log.info("[OKEX][CANCEL ORDER] response : {}", gson.toJson(returnVal));
            } else {
                log.error("[OKEX][CANCEL ORDER] response:{}", gson.toJson(returnVal));
            }
        }catch(Exception e){
            log.error("[OKEX][CANCEL ORDER] ERROR : {}", e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }


    /* Http post method */
    public JsonObject postHttpMethod(String endPoint, String body) {

        JsonObject returnObj = null;

        try{
            log.info("[OKEX][POST HTTP] request : {}", body);

            String currentTime = getCurrentTime();
            String sign = makeSignature(currentTime, "POST", endPoint, body);

            URL url = new URL(UtilsData.OKEX_URL + endPoint);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(UtilsData.TIMEOUT_VALUE);
            connection.setReadTimeout(UtilsData.TIMEOUT_VALUE);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            // Set Header for OKKE API
            connection.setRequestProperty("OK-ACCESS-KEY", keyList.get(PUBLIC_KEY));
            connection.setRequestProperty("OK-ACCESS-SIGN", sign);
            connection.setRequestProperty("OK-ACCESS-TIMESTAMP", currentTime);
            connection.setRequestProperty("OK-ACCESS-PASSPHRASE", keyList.get(API_PASSWORD));

            // Writing the post data to the HTTP request body
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
            bw.write(body);
            bw.close();

            String response = (connection.getErrorStream() == null)
                    ? getResponseMsg(connection.getInputStream()) : getResponseMsg(connection.getErrorStream());

            returnObj = gson.fromJson(response, JsonObject.class);

        } catch(Exception e){
            log.error("[OKEX][POST HTTP] {}", e.getMessage());
            e.printStackTrace();
        }

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

    // 2021-10-27T22:59:11.222Z 형식으로 Return
    private String getCurrentTime() throws Exception{
        StringBuilder nowStr = new StringBuilder(Instant.now().toString());
        return nowStr.toString();
    }

    private String makeSignature(String timestamp, String method, String endPoint, String body) throws Exception{
        StringBuilder builder = new StringBuilder();
        builder.append(timestamp);
        builder.append(method);
        builder.append(endPoint);
        builder.append(body);

        return getHmacSha256(builder.toString());
    }

    /* Hmac sha 256 start **/
    private String getHmacSha256(String message) throws Exception{
        String returnVal = "";

        String secretKey         = keyList.get(SECRET_KEY);
        SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Mac sha256_HMAC          = Mac.getInstance("HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] bytes = sha256_HMAC.doFinal(message.getBytes(StandardCharsets.UTF_8));
        returnVal    = Base64.getEncoder().encodeToString(bytes);

        return returnVal;
    }

    // 거래소에 맞춰 심볼 반환
    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0] + "-" + getCurrency(exchange, coinData[0], coinData[1]);
    }

}
