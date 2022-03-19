package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.JsonArray;
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
import java.net.URLEncoder;
import java.util.*;

@Slf4j
public class BithumbGlobalImp extends AbstractExchange {
    final private String BUY            = "buy";
    final private String SELL           = "sell";
    final private String SUCCESS        = "0";
    final private String SUCCESS_CANCEL = "20012";

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
    public void initClass(Fishing fishing, CoinService coinService) throws Exception{
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

    /**
     * Bithumb global 자전 거래
     * @param symbol coin + "-" + symbol
     */
    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[BITHUMBGLOBAL][AUTOTRADE] START");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String symbol       = getSymbol(Utils.splitCoinWithId(autoTrade.getCoin()), autoTrade.getExchange());
            String firstAction  = "";
            String secondAction = "";
            cnt                 = setCutCoinCnt(symbol, cnt);

            // mode 처리
            String mode = autoTrade.getMode();
            if(UtilsData.MODE_RANDOM.equals(mode)){
                mode = (Utils.getRandomInt(0,1) == 0) ? UtilsData.MODE_BUY : UtilsData.MODE_SELL;
            }

            if(UtilsData.MODE_BUY.equals(mode)){
                firstAction  = BUY;
                secondAction = SELL;
            }else{
                firstAction  = SELL;
                secondAction = BUY;
            }

            String orderId = ReturnCode.NO_DATA.getValue();
            if(!(orderId = createOrder(firstAction, price, cnt, symbol)).equals(ReturnCode.NO_DATA.getValue())){   // 매수
                Thread.sleep(300);
                if(createOrder(secondAction,price, cnt, symbol).equals(ReturnCode.NO_DATA.getValue())){               // 매도
                    cancelOrder(orderId, symbol);                      // 매도 실패 시, 매수 취소
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[BITHUMBGLOBAL][AUTOTRADE] Error : {}", e.getMessage());
            e.printStackTrace();
        }

        log.info("[BITHUMBGLOBAL][AUTOTRADE] End");
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
            log.info("[BITHUMBGLOBAL][LIQUIDITY] START");
            String symbol = getSymbol(Utils.splitCoinWithId(liquidity.getCoin()), liquidity.getExchange());

            while (!sellQueue.isEmpty() || !buyQueue.isEmpty() || !cancelList.isEmpty()) {
                String mode           = (Utils.getRandomInt(1, 2) == 1) ? UtilsData.MODE_BUY : UtilsData.MODE_SELL;
                boolean cancelFlag    = (Utils.getRandomInt(1, 2) == 1) ? true : false;
                String orderId        = ReturnCode.NO_DATA.getValue();
                String price          = "";
                String action         = "";
                String cnt            = setCutCoinCnt(symbol, Utils.getRandomString(liquidity.getMinCnt(), liquidity.getMaxCnt()));

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
            log.error("[BITHUMBGLOBAL][LIQUIDITY] Error {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[BITHUMBGLOBAL][LIQUIDITY] END");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[BITHUMBGLOBAL][FISHINGTRADE] START");

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
            log.info("[BITHUMBGLOBAL][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt = Utils.getRandomString(fishing.getMinContractCnt(), fishing.getMaxContractCnt());
                cnt        = setCutCoinCnt(symbol, cnt);
                String orderId = ReturnCode.NO_DATA.getValue();
                if(UtilsData.MODE_BUY.equals(mode)) {
                    orderId = createOrder(BUY,  tickPriceList.get(i), cnt, symbol);
                }else{
                    orderId = createOrder(SELL, tickPriceList.get(i), cnt, symbol);
                }
                if(!orderId.equals(ReturnCode.NO_DATA.getValue())){  // 매수/매도가 정상적으로 이뤄졌을 경우 데이터를 list에 담는다
                    Map<String, String> orderMap = new HashMap<>();
                    orderMap.put("price" ,tickPriceList.get(i));
                    orderMap.put("cnt" ,cnt);
                    orderMap.put("order_id" ,orderId);
                    orderList.add(orderMap);
                }
            }
            log.info("[BITHUMBGLOBAL][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");

            /* Sell Start */
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = Utils.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("cnt"));
                cnt                                = new BigDecimal(setCutCoinCnt(symbol, cnt.toPlainString()));

                while (cnt.compareTo(new BigDecimal("0")) > 0) {
                    if (!noMatchFirstTick) break;                   // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                    if (noIntervalFlag) Thread.sleep(intervalTime); // intervalTime 만큼 휴식 후 매수 시작
                    String orderId            = ReturnCode.NO_DATA.getValue();
                    BigDecimal cntForExcution = new BigDecimal(Utils.getRandomString(fishing.getMinExecuteCnt(), fishing.getMaxExecuteCnt()));
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
                    if(UtilsData.MODE_BUY.equals(mode)) {
                        nowFirstTick = coinService.getFirstTick(fishing.getCoin(), fishing.getExchange()).get(UtilsData.MODE_BUY);
                    }else{
                        nowFirstTick = coinService.getFirstTick(fishing.getCoin(), fishing.getExchange()).get(UtilsData.MODE_SELL);
                    }
                    String orderPrice = copiedOrderMap.get("price");
                    if (!orderPrice.equals(nowFirstTick)) {
                        log.info("[BITHUMBGLOBAL][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
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
                        log.error("[BITHUMBGLOBAL][FISHINGTRADE] While loop is broken, Because create order is failed");
                        break;
                    }
                }
                // 무조건 일단 취소
                Thread.sleep(1000);
                cancelOrder(orderList.get(i).get("order_id"), symbol);
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[BITHUMBGLOBAL][FISHINGTRADE] Error {}", e.getMessage());
            e.printStackTrace();
        }

        log.info("[BITHUMBGLOBAL][FISHINGTRADE] END");
        return returnCode;
    }

    @Override
    public int startRealtimeTrade(JsonObject realtime) {
        log.info("[BITHUMBGLOBAL][REALTIME SYNC TRADE] START");
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
            cnt                  = setCutCoinCnt(symbol, cnt);
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
                        bestofferCnt            = setCutCoinCnt(symbol, bestofferCnt);
                        String bestofferOrderId = ReturnCode.NO_DATA.getValue();

                        if( !(bestofferOrderId = createOrder(action, bestofferPrice, bestofferCnt, symbol)).equals(ReturnCode.NO_DATA.getValue())){
                            log.info("[BITHUMBGLOBAL][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                    }
                    cancelOrder(orderId,symbol);
                }
            }
        }catch (Exception e){
            log.error("[BITHUMBGLOBAL][REALTIME SYNC TRADE] Error :{} ", e.getMessage());
            e.printStackTrace();
        }
        log.info("[BITHUMBGLOBAL][REALTIME SYNC TRADE] END");
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
        String request       = UtilsData.BITHUMB_GLOBAL_TICK + "?symbol=" + URLEncoder.encode(symbol);
        String response      = getHttpMethod(request);
        JsonObject resObject = gson.fromJson(response, JsonObject.class);
        String returnCode    = resObject.get("code").getAsString();
        if(SUCCESS.equals(returnCode)){
            JsonObject data = resObject.get("data").getAsJsonArray().get(0).getAsJsonObject();
            BigDecimal percent = data.get("p").getAsBigDecimal();
            BigDecimal current = data.get("c").getAsBigDecimal();
            BigDecimal open    = current.divide(new BigDecimal(1).add(percent),10, BigDecimal.ROUND_UP);  // 소수점 11번째에서 반올림

            returnRes[0] = open.toPlainString();
            returnRes[1] = current.toPlainString();
            log.info("[BITHUMBGLOBAL][GET TODAY TICK] response : {}", Arrays.toString(returnRes));
        }else{
            log.error("[BITHUMBGLOBAL][GET TODAY TICK] response : {}", response);
            throw new Exception(response);
        }

        return returnRes;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = ReturnCode.FAIL.getValue();

        log.info("[BITHUMBGLOBAL][ORDER BOOK] START");
        try{
            String symbol   = getSymbol(coinWithId,exchange);
            String request  = UtilsData.BITHUMB_GLOBAL_ORDERBOOK + "?symbol=" + symbol;
            returnRes       = getHttpMethod(request);
        }catch (Exception e){
            log.error("[BITHUMBGLOBAL][ORDER BOOK] Error {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[BITHUMBGLOBAL][ORDER BOOK] END");

        return returnRes;
    }

    /** Biyhumb global 매수/매도 로직 */
    public String createOrder(String type, String price, String cnt, String symbol){

        String orderId = ReturnCode.NO_DATA.getValue();

        try{
            JsonObject header = new JsonObject();
            header.addProperty("apiKey",    keyList.get(PUBLIC_KEY));
            header.addProperty("msgNo",     System.currentTimeMillis());
            header.addProperty("price",     price);
            header.addProperty("quantity",  cnt);
            header.addProperty("side",      type);
            header.addProperty("symbol",    symbol);
            header.addProperty("timestamp", System.currentTimeMillis());
            header.addProperty("type",      "limit"); // 지정가
            header.addProperty("signature", setSignature(header));

            JsonObject returnVal = postHttpMethod(UtilsData.BITHUMB_GLOBAL_CREATE_ORDER, gson.toJson(header));
            String status        = returnVal.get("code").getAsString();
            if(status.equals(SUCCESS)){
                JsonObject obj  = returnVal.get("data").getAsJsonObject();
                orderId         = obj.get("orderId").getAsString();
                log.info("[BITHUMBGLOBAL][CREATE ORDER] Response : {}", gson.toJson(returnVal));
            }else{
                log.error("[BITHUMBGLOBAL][CREATE ORDER] Response :{}", gson.toJson(returnVal));
            }
        }catch (Exception e){
            log.error("[BITHUMBGLOBAL][CREATE ORDER] Error {}",e.getMessage());
            e.printStackTrace();
        }
        return orderId;
    }

    /* Bithumb global 거래 취소 */
    public int cancelOrder(String orderId, String symbol) {

        int returnValue = ReturnCode.FAIL.getCode();

        try {
            JsonObject header = new JsonObject();
            header.addProperty("apiKey",    keyList.get(PUBLIC_KEY));
            header.addProperty("msgNo",     System.currentTimeMillis());
            header.addProperty("orderId",   orderId);
            header.addProperty("symbol",    symbol);
            header.addProperty("timestamp", System.currentTimeMillis());
            header.addProperty("signature", setSignature(header));

            JsonObject json = postHttpMethod(UtilsData.BITHUMB_GLOBAL_CANCEL_ORDER, gson.toJson(header));
            String status   = json.get("code").getAsString();
            if (status.equals(SUCCESS) || status.equals(SUCCESS_CANCEL)) {
                returnValue = ReturnCode.SUCCESS.getCode();
                log.info("[BITHUMBGLOBAL][CANCEL ORDER] Response:{}", gson.toJson(json));
            } else {
                log.error("[BITHUMBGLOBAL][CANCEL ORDER] Response:{}", gson.toJson(json));
            }
        }catch(Exception e){
            log.error("[BITHUMBGLOBAL][CANCEL ORDER] Error {}", e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }


    /* Http post method */
    private JsonObject postHttpMethod(String targetUrl, String payload) throws Exception{

        log.info("[BITHUMBGLOBAL][POST HTTP] targetUrl : {} , request : {}",targetUrl, payload);
        URL url = new URL(targetUrl);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setConnectTimeout(UtilsData.TIMEOUT_VALUE);
        connection.setReadTimeout(UtilsData.TIMEOUT_VALUE);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
        // Writing the post data to the HTTP request body
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
        bw.write(payload);
        bw.close();
        StringBuffer response = new StringBuffer();
        if(connection.getResponseCode() == HttpsURLConnection.HTTP_OK){
            BufferedReader br = null;
            if(connection.getInputStream() != null){
                br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            }else if(connection.getErrorStream() != null){
                br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            }else{
                log.error("[BITHUMBGLOBAL][POST HTTP] Return Code is 200. But inputstream and errorstream is null");
                throw new Exception();
            }
            String inputLine = "";
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();
        }else{
            log.error("[BITHUMBGLOBAL][POST HTTP] Return code : {}, msg : {}",connection.getResponseCode(), connection.getResponseMessage());
            throw new Exception();
        }
        return gson.fromJson(response.toString(), JsonObject.class);
    }


    /* Hmac sha 256 start **/
    private String getHmacSha256(String message) throws Exception{
        String returnVal = "";

        String secret            = keyList.get(SECRET_KEY);
        Mac sha256_HMAC          = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] bytes = sha256_HMAC.doFinal(message.getBytes());
        returnVal    =  byteArrayToHexString(bytes);

        return returnVal.toLowerCase();
    }

    /* Hmac sha 256 end */
    private String byteArrayToHexString(byte[] b) throws Exception{
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
        int dot = 0;    // 화폐 단위가 BTC일 경우 0
        if(!symbol.split("-")[1].equals("BTC")) {
            dot = 1;    // 아닐경우 1
        }

        double doubleCnt = Double.parseDouble(cnt);
        int pow          = (dot == 0) ? 1 : (int) Math.pow(10,dot);

        return String.valueOf(Math.floor(doubleCnt * pow) / pow);
    }

    /* making signature method */
    private String setSignature(JsonObject header) throws Exception{
        String sign = "";
        int idx = 0;
        for(String key : header.keySet()){
            if(idx == header.size() - 1){
                sign += key + "=" + header.get(key).getAsString();
            }else{
                sign += key + "=" + header.get(key).getAsString() + "&";
            }
            idx++;
        }
        return getHmacSha256(sign);
    }

    // 거래소에 맞춰 심볼 반환
    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0] + "-" + getCurrency(exchange,coinData[0], coinData[1]);
    }

}
