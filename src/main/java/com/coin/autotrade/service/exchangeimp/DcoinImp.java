package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.JsonArray;
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
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
public class DcoinImp extends AbstractExchange {

    final private String ACCESS_TOKEN         = "apiToken";
    final private String SECRET_KEY           = "secretKey";
    final private String BUY                  = "BUY";
    final private String SELL                 = "SELL";
    final private String SUCCESS              = "0";
    final private String SUCCESS_CANCEL       = "2";
    private Map<String, String> keyList       = new HashMap<>();


    @Override
    public void initClass(AutoTrade autoTrade) throws Exception{
        super.autoTrade = autoTrade;
        setApiKey(Utils.splitCoinWithId(autoTrade.getCoin()), autoTrade.getExchange());
    }

    @Override
    public void initClass(Liquidity liquidity) throws Exception{
        super.liquidity = liquidity;
        setApiKey(Utils.splitCoinWithId(liquidity.getCoin()), liquidity.getExchange());
    }

    @Override
    public void initClass(RealtimeSync realtimeSync, CoinService coinService) throws Exception{
        super.realtimeSync = realtimeSync;
        super.coinService  = coinService;
        setApiKey(Utils.splitCoinWithId(realtimeSync.getCoin()), realtimeSync.getExchange());
    }

    @Override
    public void initClass(Fishing fishing ,CoinService coinService) throws Exception{
        super.fishing     = fishing;
        super.coinService = coinService;
        setApiKey(Utils.splitCoinWithId(fishing.getCoin()), fishing.getExchange());
    }

    private void setApiKey(String[] coinData, Exchange exchange) throws Exception{

        // Set token key
        for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
            if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1]) ){
                keyList.put(ACCESS_TOKEN, exCoin.getPublicKey());
                keyList.put(SECRET_KEY,   exCoin.getPrivateKey());
            }
        }
        if(keyList.isEmpty()){
            String msg = "There is no match coin. " + Arrays.toString(coinData) + " " + exchange.getExchangeCode();
            throw new Exception(msg);
        }
    }


    /**
     * Auto Trade Start
     */
    @Override
    public int startAutoTrade(String price, String cnt){

        log.info("[DCOIN][AUTOTRADE] START");

        int returnCode = ReturnCode.SUCCESS.getCode();
        try{
            String     symbol   = getSymbol(Utils.splitCoinWithId(autoTrade.getCoin()), autoTrade.getExchange());
            String firstAction  = "";
            String secondAction = "";
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
                    cancelOrder(symbol, orderId);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[DCOIN][AUTOTRADE] Error {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[DCOIN][AUTOTRADE] END");
        return returnCode;
    }

    /* 호가유동성 메서드 */
    @Override
    public int startLiquidity(Map list){
        int returnCode = ReturnCode.SUCCESS.getCode();

        Queue<String> sellQueue = (LinkedList) list.get("sell");
        Queue<String> buyQueue  = (LinkedList) list.get("buy");
        List<Map<String,String>> CancelList = new ArrayList();

        try{
            log.info("[DCOIN][LIQUIDITY] START");
            String symbol = getSymbol(Utils.splitCoinWithId(liquidity.getCoin()), liquidity.getExchange());

            while(!sellQueue.isEmpty() || !buyQueue.isEmpty()){
                String mode = (Utils.getRandomInt(1,2) == 1) ? UtilsData.MODE_BUY : UtilsData.MODE_SELL;
                String firstOrderId    = ReturnCode.NO_DATA.getValue();
                String secondsOrderId  = ReturnCode.NO_DATA.getValue();
                String firstPrice      = "";
                String secondsPrice    = "";
                String firstAction     = "";
                String secondAction    = "";
                String firstCnt        = Utils.getRandomString(liquidity.getMinCnt(), liquidity.getMaxCnt());
                String secondsCnt      = Utils.getRandomString(liquidity.getMinCnt(), liquidity.getMaxCnt());

                if(!sellQueue.isEmpty() && !buyQueue.isEmpty() && mode.equals(UtilsData.MODE_BUY)){
                    firstPrice   = buyQueue.poll();
                    secondsPrice = sellQueue.poll();
                    firstAction  = BUY;
                    secondAction = SELL;
                }else if(!buyQueue.isEmpty() && !sellQueue.isEmpty() && mode.equals(UtilsData.MODE_SELL)){
                    firstPrice   = sellQueue.poll();
                    secondsPrice = buyQueue.poll();
                    firstAction  = SELL;
                    secondAction = BUY;
                }

                firstOrderId   = createOrder(firstAction,  firstPrice, firstCnt, symbol);
                Thread.sleep(300);
                secondsOrderId = createOrder(secondAction, secondsPrice, secondsCnt, symbol);

                // first / second 둘 중 하나라도 거래가 성사 되었을 경우
                if(!firstOrderId.equals(ReturnCode.NO_DATA.getValue())  || !secondsOrderId.equals(ReturnCode.NO_DATA.getValue())){
                    Thread.sleep(1000);
                    if(!firstOrderId.equals(ReturnCode.NO_DATA.getValue())){
                        cancelOrder(symbol,firstOrderId);
                    }
                    Thread.sleep(300);
                    if(!secondsOrderId.equals(ReturnCode.NO_DATA.getValue())){
                        cancelOrder(symbol, secondsOrderId);
                    }
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[DCOIN][LIQUIDITY] {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[DCOIN][LIQUIDITY] END");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[DCOIN][FISHINGTRADE] START");

        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String mode   = "";
            String symbol = getSymbol(Utils.splitCoinWithId(fishing.getCoin()), fishing.getExchange());

            boolean noIntervalFlag   = true;    // 해당 플래그를 이용해 마지막 매도/매수 후 바로 intervalTime 없이 바로 다음 매수/매도 진행
            boolean noMatchFirstTick = true;    // 해당 플래그를 이용해 매수/매도를 올린 가격이 현재 최상위 값이 맞는지 다른 사람의 코인을 사지 않게 방지

            for(String temp : list.keySet()){  mode = temp; }
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode);
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            /* Start */
            log.info("[DCOIN][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt     = Utils.getRandomString(fishing.getMinContractCnt(), fishing.getMaxContractCnt());
                String orderId = ReturnCode.NO_DATA.getValue();
                if(mode.equals(UtilsData.MODE_BUY)) {
                    orderId = createOrder(BUY, tickPriceList.get(i), cnt, symbol);
                }else{
                    orderId = createOrder(SELL, tickPriceList.get(i), cnt, symbol);
                }
                if(!orderId.equals(ReturnCode.NO_DATA.getValue())){                                                // 매수/매도가 정상적으로 이뤄졌을 경우 데이터를 list에 담는다
                    Map<String, String> orderMap = new HashMap<>();
                    orderMap.put("price" ,tickPriceList.get(i));
                    orderMap.put("cnt" ,cnt);
                    orderMap.put("order_id" ,orderId);
                    orderList.add(orderMap);
                }
            }
            log.info("[DCOIN][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");

            /* Sell Start */
            log.info("[DCOIN][FISHINGTRADE][START BUY OR SELL TARGET PIECE COIN ]");
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = Utils.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("cnt"));

                while (cnt.compareTo(new BigDecimal("0")) > 0) {
                    if (!noMatchFirstTick) break;                   // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                    if (noIntervalFlag) Thread.sleep(intervalTime); // intervalTime 만큼 휴식 후 매수 시작
                    String orderId = ReturnCode.NO_DATA.getValue();
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
                        log.info("[DCOIN][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        noMatchFirstTick = false;
                        break;
                    }

                    if(UtilsData.MODE_BUY.equals(mode)) {
                        orderId = createOrder(SELL, copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol);
                    }else{
                        orderId = createOrder(BUY, copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol);
                    }

                    if(!orderId.equals(ReturnCode.NO_DATA.getValue())){
                        cnt = cnt.subtract(cntForExcution);
                    }else{
                        break;
                    }
                }
                // 무조건 취소
                Thread.sleep(1000);
                cancelOrder(symbol, orderList.get(i).get("order_id"));

            }
            log.info("[DCOIN][FISHINGTRADE][END BUY OR SELL TARGET PIECE COIN ]");
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[DCOIN][FISHINGTRADE] {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[DCOIN][FISHINGTRADE END]");
        return returnCode;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = ReturnCode.FAIL.getValue();
        try{
            String symbol      = getSymbol(coinWithId, exchange);
            String encodedData = "symbol=" + URLEncoder.encode(symbol) + "&type=" + URLEncoder.encode("step0");
            returnRes          = getHttpMethod(UtilsData.DCOIN_ORDERBOOK + "?" + encodedData);
        }catch (Exception e){
            log.error("[DCOIN][ORDER BOOK] {}",e.getMessage());
            e.printStackTrace();
        }

        return returnRes;
    }


    /**
     * Realtime Sync 거래
     * @param realtime
     * @return
     */
    public int startRealtimeTrade(JsonObject realtime) {
        log.info("[DCOIN][REALTIME SYNC TRADE START]");
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
                    mode         = UtilsData.MODE_BUY;
                    action       = BUY;
                    targetPrice  = realtimeSync.getMinPrice();
                }else if(isInRange == 1){    // 저항선보다 높을 경우
                    mode         = UtilsData.MODE_SELL;
                    action       = SELL;
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
                            log.info("[DCOIN][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                    }

                    Thread.sleep(300);
                    cancelOrder(symbol, orderId);
                }
            }
        }catch (Exception e){
            log.error("[DCOIN][REALTIME SYNC TRADE] Error :{} ", e.getMessage());
            e.printStackTrace();
        }
        log.info("[DCOIN][REALTIME SYNC TRADE END]");
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
        String request       = UtilsData.DCOIN_TICK + "?symbol=" + URLEncoder.encode(symbol);
        String response      = getHttpMethod(request);
        JsonObject resObject = gson.fromJson(response, JsonObject.class);
        String returnCode    = resObject.get("code").getAsString();
        if(SUCCESS.equals(returnCode)){
            returnRes[0] = resObject.get("data").getAsJsonObject().get("open").getAsString();
            returnRes[1] = resObject.get("data").getAsJsonObject().get("close").getAsString();
            log.info("[DCOIN][GET TODAY TICK] response : {}", Arrays.toString(returnRes));
        }else{
            log.error("[DCOIN][GET TODAY TICK] response : {}", response);
            throw new Exception(response);
        }

        return returnRes;
    }




    /**
     * 매수 매도 로직
     * @param side   - SELL, BUY
     * @param symbol - coin + currency
     */
    public String createOrder(String side, String price, String cnt, String symbol) {

        String orderId   = ReturnCode.NO_DATA.getValue();

        try {
            // DCoin 의 경우, property 값들이 오름차순으로 입력되야 해서, 공통 함수로 빼기 어려움.
            JsonObject header = new JsonObject();
            header.addProperty("api_key", keyList.get(ACCESS_TOKEN));
            header.addProperty("price",   Double.parseDouble(price));
            header.addProperty("side",    side);
            header.addProperty("symbol",  symbol.toLowerCase());
            header.addProperty("type", 1);
            header.addProperty("volume",  Double.parseDouble(cnt));
            header.addProperty("sign",    createSign(gson.toJson(header)));

            String params   = makeEncodedParas(header);
            JsonObject json = postHttpMethod(UtilsData.DCOIN_CREATE_ORDER, params);
            String returnCode   = json.get("code").getAsString();
            if (SUCCESS.equals(returnCode)) {
                JsonObject dataObj = json.get("data").getAsJsonObject();
                orderId            = dataObj.get("order_id").getAsString();
                log.info("[DCOIN][CREATE ORDER] response :{}", gson.toJson(json));
            } else {
                log.error("[DCOIN][CREATE ORDER] response {}", gson.toJson(json));
            }
        }catch(Exception e){
            log.error("[DCOIN][CREATE ORDER] Error {}", e.getMessage());
            e.printStackTrace();
        }
        return orderId;
    }

    /**
     * 매도/매수 거래 취소 로직
     * @param symbol   - coin + currency
     */
    public int cancelOrder(String symbol, String orderId) {

        int returnValue = ReturnCode.FAIL.getCode();
        try {
            JsonObject header = new JsonObject();
            header.addProperty("api_key",  keyList.get(ACCESS_TOKEN));
            header.addProperty("order_id", orderId);
            header.addProperty("symbol",   symbol.toLowerCase());
            header.addProperty("sign",     createSign(gson.toJson(header)));

            JsonObject json   = postHttpMethod(UtilsData.DCOIN_CANCEL_ORDER, makeEncodedParas(header));
            String returnCode = json.get("code").getAsString();
            if (SUCCESS.equals(returnCode) || SUCCESS_CANCEL.equals(returnCode)) {
                returnValue = ReturnCode.SUCCESS.getCode();
                log.info("[DCOIN][CANCEL ORDER] response:{}", gson.toJson(json));
            } else {
                log.error("[DCOIN][CANCEL ORDER] response:{}", gson.toJson(json));
            }
        }catch(Exception e){
            log.error("[DCOIN][CANCEL ORDER] Error: {}", e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }

    /** 암호화된 값 생성 */
    private String createSign(String params) throws Exception{
        String returnVal = ReturnCode.FAIL.getValue();
        String replaceParams = params.replace("\"","").replace("{","").replace("}","").replace(":","").replace(",","");
        String message = replaceParams.concat(keyList.get(SECRET_KEY));

        MessageDigest md5 = MessageDigest.getInstance("md5");
        byte[] code = md5.digest(message.getBytes());
        StringBuffer sb = new StringBuffer();
        for (byte b : code) {
            sb.append(String.format("%02x", b));
        }
        returnVal = sb.toString();
        return returnVal;
    }


    private String makeEncodedParas(JsonObject header) throws Exception {
        String returnVal = "";
        int i = 0;
        for(String key : header.keySet()){
            String value = header.get(key).getAsString();
            if(i < header.size() -1){
                returnVal += (key + "=" + value + "&");
            }else{
                returnVal += (key + "=" + value);
            }
            i++;
        }
        return returnVal;
    }


    /* HTTP POST Method for coinone */
    private JsonObject postHttpMethod(String targetUrl, String payload) throws Exception{

        log.info("[DCOIN][POST HTTP] url : {}, payload : {}", targetUrl, payload);

        URL url = new URL(targetUrl);
        HttpURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(UtilsData.TIMEOUT_VALUE);
        connection.setReadTimeout(UtilsData.TIMEOUT_VALUE);
        connection.setRequestProperty("Context-Type", "application/x-www-form-urlencoded");
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
                log.error("[DCOIN][POST HTTP] Return Code is 200. But inputstream and errorstream is null");
                throw new Exception();
            }
            String inputLine = "";
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();
        }else{
            log.error("[DCOIN][POST HTTP] Return code : {}, msg : {}",connection.getResponseCode(), connection.getResponseMessage());
            throw new Exception();
        }

        return gson.fromJson(response.toString(), JsonObject.class);
    }


    // 거래소에 맞춰 심볼 반환
    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0].toLowerCase() + getCurrency(exchange,coinData[0], coinData[1]);
    }
}
