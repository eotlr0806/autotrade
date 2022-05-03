package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.common.enumeration.Trade;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
public class DigifinexImp extends AbstractExchange {
    final private String BUY            = "buy";
    final private String SELL           = "sell";
    final private String SUCCESS        = "0";

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
        if(keyList.isEmpty()){
            for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
                if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1])){
                    keyList.put(PUBLIC_KEY, exCoin.getPublicKey());
                    keyList.put(SECRET_KEY,   exCoin.getPrivateKey());

                }
            }
            log.info("[DIGIFINEX][SET API KEY] First Key setting in instance API:{}, secret:{}",keyList.get(PUBLIC_KEY), keyList.get(SECRET_KEY));
            if(keyList.isEmpty()){
                String msg = "There is no match coin. " + Arrays.toString(coinData) + " " + exchange.getExchangeCode();
                throw new Exception(msg);
            }
        }
    }

    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[DIGIFINEX][AUTOTRADE] START");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            Exchange exchange   = autoTrade.getExchange();
            String[] coinWithId = Utils.splitCoinWithId(autoTrade.getCoin());
            String symbol       = getSymbol(coinWithId, exchange);
            Trade mode          = getMode(autoTrade.getMode());
            String firstAction  = (mode == Trade.BUY) ? BUY : SELL;
            String secondAction = (mode == Trade.BUY) ? SELL : BUY;

            String firstOrderId  = createOrder(firstAction,price, cnt, coinWithId, exchange);
            if(Utils.isSuccessOrder(firstOrderId)){
                String secondOrderId = createOrder(secondAction, price, cnt, coinWithId, exchange);
                cancelOrder(firstOrderId);
                if(Utils.isSuccessOrder(secondOrderId)){
                    cancelOrder(secondOrderId);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[DIGIFINEX][AUTOTRADE] Error : {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[DIGIFINEX][AUTOTRADE] END");
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
            log.info("[DIGIFINEX][LIQUIDITY] START");
            String[] coinWithId = Utils.splitCoinWithId(liquidity.getCoin());
            Exchange exchange   = liquidity.getExchange();
            String symbol       = getSymbol(coinWithId, exchange);


            while (!sellQueue.isEmpty() || !buyQueue.isEmpty() || !cancelList.isEmpty()) {
                Trade mode         = getMode();
                boolean cancelFlag = (Utils.getRandomInt(1, 2) == 1) ? true : false;
                String orderId     = ReturnCode.FAIL_CREATE.getValue();
                String action      = (mode == Trade.BUY) ? BUY : SELL;
                String cnt         = Utils.getRandomString(liquidity.getMinCnt(), liquidity.getMaxCnt());
                String price       = null;

                if (!buyQueue.isEmpty() && mode == Trade.BUY) {
                    price = buyQueue.poll();
                } else if (!sellQueue.isEmpty() && mode == Trade.SELL) {
                    price = sellQueue.poll();
                }

                // 매수 로직
                if(price != null){
                    orderId = createOrder(action, price, cnt, Utils.splitCoinWithId(liquidity.getCoin()), liquidity.getExchange());
                    if(Utils.isSuccessOrder(orderId)){
                        cancelList.add(orderId);
                    }
                    Thread.sleep(1500);
                }

                // 취소 로직
                if(!cancelList.isEmpty() && cancelFlag){
                    String cancelId = cancelList.poll();
                    cancelOrder(cancelId);
                    Thread.sleep(500);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.SUCCESS.getCode();
            log.error("[DIGIFINEX][LIQUIDITY] Error {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[DIGIFINEX][LIQUIDITY] END");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[DIGIFINEX][FISHINGTRADE] START");

        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String[] coinWithId = Utils.splitCoinWithId(fishing.getCoin());
            Exchange exchange   = fishing.getExchange();
            String symbol       = getSymbol(coinWithId, exchange);

            // mode 처리
            // mode 처리
            Trade mode = Trade.valueOf(String.valueOf(list.keySet().toArray()[0]));
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode.getVal());
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            /* Start */
            log.info("[DIGIFINEX][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt     = Utils.getRandomString(fishing.getMinContractCnt(), fishing.getMaxContractCnt());
                String orderId = (mode == Trade.BUY) ?
                        createOrder(BUY,  tickPriceList.get(i), cnt, coinWithId, exchange) :
                        createOrder(SELL, tickPriceList.get(i), cnt, coinWithId, exchange);

                if(Utils.isSuccessOrder(orderId)){
                    Map<String, String> orderMap = new HashMap<>();
                    orderMap.put("price" ,tickPriceList.get(i));
                    orderMap.put("cnt" ,cnt);
                    orderMap.put("order_id" ,orderId);
                    orderList.add(orderMap);
                }
            }
            log.info("[DIGIFINEX][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");

            /* Sell Start */
            log.info("[DIGIFINEX][FISHINGTRADE][START BUY OR SELL TARGET PIECE COIN ]");
            boolean isSameFirstTick = true;    // 해당 플래그를 이용해 매수/매도를 올린 가격이 현재 최상위 값이 맞는지 다른 사람의 코인을 사지 않게 방지
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = Utils.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("cnt"));

                while (cnt.compareTo(BigDecimal.ZERO) > 0) {
                    if (!isSameFirstTick) break;                   // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                    if(cnt.compareTo(new BigDecimal(copiedOrderMap.get("cnt"))) != 0){
                        Thread.sleep(intervalTime); // intervalTime 만큼 휴식 후 매수 시작
                    }

                    BigDecimal executionCnt = new BigDecimal(Utils.getRandomString(fishing.getMinExecuteCnt(), fishing.getMaxExecuteCnt()));  // 실행 코인
                    executionCnt            = (cnt.compareTo(executionCnt) < 0) ? cnt : executionCnt;    // 남은 코인 수와 매도/매수할 코인수를 비교했을 때, 남은 코인 수가 더 적다면 남은 cnt만큼 매수/매도


                    // 매도/매수 날리기전에 최신 매도/매수값이 내가 건 값이 맞는지 확인
                    String nowFirstTick = (mode == Trade.BUY) ?
                            coinService.getFirstTick(fishing.getCoin(), exchange).get(UtilsData.MODE_BUY) :
                            coinService.getFirstTick(fishing.getCoin(), exchange).get(UtilsData.MODE_SELL);

                    String orderPrice = copiedOrderMap.get("price");
                    if (!orderPrice.equals(nowFirstTick)) {
                        log.info("[DIGIFINEX][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        isSameFirstTick = false;
                        break;
                    }

                    String orderId = (mode == Trade.BUY) ?
                            createOrder(SELL, copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange) :
                            createOrder(BUY,  copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange);

                    if(Utils.isSuccessOrder(orderId)){
                        cnt = cnt.subtract(executionCnt);
                    }else{
                        log.error("[DIGIFINEX][FISHINGTRADE] While loop is broken, Because create order is failed");
                        break;
                    }
                }
                // 무조건 일단 취소
                Thread.sleep(1000);
                cancelOrder(orderList.get(i).get("order_id"));
            }
            log.info("[DIGIFINEX][FISHINGTRADE][END BUY OR SELL TARGET PIECE COIN ]");
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[DIGIFINEX][FISHINGTRADE] Error {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[DIGIFINEX][FISHINGTRADE] END");
        return returnCode;
    }

    @Override
    public int startRealtimeTrade(JsonObject realtime, boolean resetFlag) {
        log.info("[DIGIFINEX][REALTIME SYNC TRADE] START");
        int returnCode   = ReturnCode.SUCCESS.getCode();
        String realtimeChangeRate = "signed_change_rate";

        try {
            boolean isStart      = false;
            String[] coinWithId  = Utils.splitCoinWithId(realtimeSync.getCoin());
            Exchange exchange    = realtimeSync.getExchange();
            String symbol        = getSymbol(coinWithId, realtimeSync.getExchange());
            String[] currentTick = getTodayTick(realtimeSync.getExchange(), Utils.splitCoinWithId(realtimeSync.getCoin()));
            //            String openingPrice  = currentTick[0];
            if(resetFlag){
                realtimeTargetInitRate = currentTick[1];
                log.info("[DIGIFINEX][REALTIME SYNC TRADE] Set init open rate : {} ", realtimeTargetInitRate);
            }
            String openingPrice  = realtimeTargetInitRate;
            String currentPrice  = currentTick[1];
            log.info("[DIGIFINEX][REALTIME SYNC TRADE] open:{}, current:{} ", openingPrice, currentPrice);

            String targetPrice   = "";
            String action        = "";
            String mode          = "";
            String cnt           = Utils.getRandomString(realtimeSync.getMinTradeCnt(), realtimeSync.getMaxTradeCnt());

            // 1. 최소/최대 매수 구간에 있는지 확인
            int isInRange        = isMoreOrLessPrice(currentPrice);
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

            // 2. %를 맞추기 위한 매수/매도 로직
            if(isStart){
                String orderId = createOrder(action, targetPrice, cnt, coinWithId, exchange);
                if(Utils.isSuccessOrder(orderId)){    // 매수/OrderId가 있으면 성공
                    Thread.sleep(300);

                    // 3. bestoffer set 로직
                    JsonArray array = makeBestofferAfterRealtimeSync(targetPrice, mode);
                    for (int i = 0; i < array.size(); i++) {
                        JsonObject object       = array.get(i).getAsJsonObject();
                        String bestofferPrice   = object.get("price").getAsString();
                        String bestofferCnt     = object.get("cnt").getAsString();
                        String bestofferOrderId = createOrder(action, bestofferPrice, bestofferCnt, coinWithId, exchange);
                        if(Utils.isSuccessOrder(bestofferOrderId)){
                            log.info("[DIGIFINEX][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                    }

                    // 베스트 오퍼 체크 작업 이후 기존에 걸었던 매수에 대해 캔슬
                    cancelOrder(orderId);
                }
            }
        }catch (Exception e){
            log.error("[DIGIFINEX][REALTIME SYNC TRADE] ERROR :{} ", e.getMessage());
            e.printStackTrace();
        }
        log.info("[DIGIFINEX][REALTIME SYNC TRADE] END");
        return returnCode;
    }


    /**
     * 현재 Tick 가져오기
     * @param exchange
     * @param coinWithId
     * @return [ 시가 , 종가 ] String Array
     */
    private String[] getTodayTick(Exchange exchange, String[] coinWithId) throws Exception{

        String[] returnRes   = new String[2];
        String request       = UtilsData.DIGIFINEX_TICK + "?symbol=" + getSymbol(coinWithId, exchange);
        String response      = getHttpMethod(request);
        JsonObject object    = gson.fromJson(response, JsonObject.class);
        JsonArray resArr     = object.get("ticker").getAsJsonArray();
        JsonObject resObj    = resArr.get(0).getAsJsonObject();

        BigDecimal current   = resObj.get("last").getAsBigDecimal();    // 현재 값
        BigDecimal percent   = resObj.get("change").getAsBigDecimal().divide(new BigDecimal(100),10, BigDecimal.ROUND_UP);
        BigDecimal open      = current.divide(new BigDecimal(1).add(percent),10, BigDecimal.ROUND_UP);  // 소수점 11번째에서 반올림

        returnRes[0] = open.toPlainString();
        returnRes[1] = current.toPlainString();

        return returnRes;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = "";
        try{
            log.info("[DIGIFINEX][ORDER BOOK] START");
            String request = UtilsData.DIGIFINEX_ORDERBOOK + "?symbol=" + getSymbol(coinWithId, exchange);
            returnRes = getHttpMethod(request);
            log.info("[DIGIFINEX][ORDER BOOK] END");

        }catch (Exception e){
            log.error("[DIGIFINEX][ORDER BOOK] Error {}",e.getMessage());
            e.printStackTrace();
        }

        return returnRes;
    }

    @Override
    public String createOrder(String type, String price, String cnt, String[] coinData, Exchange exchange){
        String orderId = ReturnCode.FAIL_CREATE.getValue();
        try {
            setCoinToken(coinData,exchange);
            String symbol = getSymbol(coinData, exchange);
            JsonObject params = new JsonObject();
            params.addProperty("market","spot");
            params.addProperty("symbol",symbol);
            params.addProperty("type",  parseAction(type));
            params.addProperty("amount",cnt);
            params.addProperty("price", price);
            JsonObject returnRes = postHttpMethod(UtilsData.DIGIFINEX_CREATE_ORDER, params);
            if(returnRes.get("code").getAsString().equals(SUCCESS)){
                orderId = returnRes.get("order_id").getAsString();
                log.info("[DIGIFINEX][CREATE ORDER] Success response : {}", gson.toJson(returnRes));
            }else{
                log.error("[DIGIFINEX][CREATE ORDER] Fail response : {}", gson.toJson(returnRes));
            }
        }catch (Exception e){
            log.error("[DIGIFINEX][CREATE ORDER] Error : {}",e.getMessage());
            e.printStackTrace();
        }
        return orderId;
    }


    /* Http post method */
    public JsonObject postHttpMethod(String endPoint, JsonObject params) {

        JsonObject returnObj = null;

        try{
            String body = makeParamsTypeofUrl(params);
            String sign = HmacAndHex(body);
            log.info("[DIGIFINEX][POST HTTP] url:{}, body:{}, sign:{}", endPoint, body, sign);

            URL url = new URL(endPoint);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(UtilsData.TIMEOUT_VALUE);
            connection.setReadTimeout(UtilsData.TIMEOUT_VALUE);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            // Set Header for OKKE API
            connection.setRequestProperty("ACCESS-KEY", keyList.get(PUBLIC_KEY));
            connection.setRequestProperty("ACCESS-TIMESTAMP", getTimestamp());
            connection.setRequestProperty("ACCESS-SIGN", sign);


            // Writing the post data to the HTTP request body
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
            bw.write(body);
            bw.close();

            String response = (connection.getErrorStream() == null)
                    ? getResponseMsg(connection.getInputStream()) : getResponseMsg(connection.getErrorStream());

            returnObj = gson.fromJson(response, JsonObject.class);

        } catch(Exception e){
            log.error("[DIGIFINEX][POST HTTP] {}", e.getMessage());
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

    private String makeParamsTypeofUrl(JsonObject object) throws Exception{
        int executeCnt = 1;
        StringBuilder builder = new StringBuilder();
        for(String key : object.keySet()){
            if(executeCnt < object.size()){
                builder.append(key).append("=").append(object.get(key).getAsString()).append("&");
            }else{
                builder.append(key).append("=").append(object.get(key).getAsString());
            }
            executeCnt++;
        }
        return builder.toString();
    }

    private String HmacAndHex(String data) throws Exception {
        String secret = keyList.get(SECRET_KEY);
        //1. SecretKeySpec 클래스를 사용한 키 생성
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        //2. 지정된  MAC 알고리즘을 구현하는 Mac 객체를 작성합니다.
        Mac hasher = Mac.getInstance("HmacSHA256");
        //3. 키를 사용해 이 Mac 객체를 초기화
        hasher.init(secretKey);
        //3. 암호화 하려는 데이터의 바이트의 배열을 처리해 MAC 조작을 종료
        byte[] hash = hasher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        //4. Hex Encode to String
        return Hex.encodeHexString(hash);
    }




    // Server timestamp 시간을 가져오기 위해 사용
    private String getTimestamp() throws Exception{
        JsonObject object = gson.fromJson(getHttpMethod(UtilsData.DIGIFINEX_TIMESTAMP), JsonObject.class);
        String returnRes  = object.get("server_time").getAsString();
        log.info("[DIGIFINEX][GET TIMESTAMP] timestamp : {}", returnRes);
        return returnRes;
    }


    /* DIGIFINEX global 거래 취소 */
    private int cancelOrder(String orderId) {
        int returnValue = ReturnCode.FAIL.getCode();
        try {
            JsonObject params = new JsonObject();
            params.addProperty("market","spot");
            params.addProperty("order_id",orderId);
            JsonObject returnRes = postHttpMethod(UtilsData.DIGIFINEX_CANCEL_ORDER, params);
            if(returnRes.get("code").getAsString().equals(SUCCESS)){
                log.info("[DIGIFINEX][CANCEL ORDER] Success cancel orderId : {} response : {}",orderId, gson.toJson(returnRes));
            }else{
                log.error("[DIGIFINEX][CANCEL ORDER] Fail response : {}", gson.toJson(returnRes));
            }
        }catch (Exception e){
            log.error("[DIGIFINEX][CANCEL ORDER] Error orderId:{}, response:{}",orderId, e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }

    // 거래소에 맞춰 심볼 반환
    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0] + "_" + getCurrency(exchange, coinData[0], coinData[1]);
    }

    private String parseAction(String action){
        if(isExternalAction(action)){
            if(Trade.BUY.equals(action)){
                return BUY;
            }else{
                return SELL;
            }
        }
        return action;
    }

    private boolean isExternalAction(String action){
        if(!action.equals(BUY) && !action.equals(SELL)){
            return true;
        }else{
            return false;
        }
    }

}
