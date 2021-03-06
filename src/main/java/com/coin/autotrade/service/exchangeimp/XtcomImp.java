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
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
public class XtcomImp extends AbstractExchange {
    final private String BUY            = "buy";
    final private String SELL           = "sell";
    final private String SUCCESS        = "200";

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

    /** ?????? ?????? ?????? ?????? **/
    private void setCoinToken(String[] coinData, Exchange exchange) throws Exception{
        // Set token key
        if(keyList.isEmpty()){
            for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
                if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1])){
                    keyList.put(PUBLIC_KEY, exCoin.getPublicKey());
                    keyList.put(SECRET_KEY,   exCoin.getPrivateKey());
                }
            }


            if(keyList.isEmpty()){
                String msg = "There is no match coin. " + Arrays.toString(coinData) + " " + exchange.getExchangeCode();
                throw new Exception(msg);
            }
            // XTCOM??? ?????? min amount ?????? ??????.
            keyList.put(MIN_AMOUNT, getMinAmount(coinData, exchange));
            log.info("[XTCOM][SET API KEY] First Key setting in instance API:{}, secret:{}, min_Amount:{}",keyList.get(PUBLIC_KEY), keyList.get(SECRET_KEY), keyList.get(MIN_AMOUNT));
        }
    }

    // Get min amount
    private String getMinAmount(String[] coinData, Exchange exchange) throws Exception{
        JsonObject object = gson.fromJson(getHttpMethod(UtilsData.XTCOM_GET_COININFO), JsonObject.class);
        String symbol     = getSymbol(coinData, exchange);
        String minAmount  = object.getAsJsonObject(symbol).get("minAmount").getAsString();

        return minAmount;
    }

    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[XTCOM][AUTOTRADE] START");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String[] coinWithId = Utils.splitCoinWithId(autoTrade.getCoin());
            Exchange exchange   = autoTrade.getExchange();
            String symbol       = getSymbol(coinWithId, exchange);
            Trade mode          = getMode(autoTrade.getMode());
            String firstAction  = (mode == Trade.BUY) ? BUY : SELL;
            String secondAction = (mode == Trade.BUY) ? SELL : BUY;

            String firstOrderId  = createOrder(firstAction, price, cnt, coinWithId, exchange);
            if(Utils.isSuccessOrder(firstOrderId)){
                String secondOrderId = createOrder(secondAction, price, cnt, coinWithId, exchange);

                Thread.sleep(1000);
                cancelOrder(symbol, firstOrderId);                      // ?????? ?????? ???, ?????? ??????
                if(Utils.isSuccessOrder(secondOrderId)){
                    cancelOrder(symbol, secondOrderId);                 // ?????? ?????? ???, ?????? ??????
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[XTCOM][AUTOTRADE] Error : {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[XTCOM][AUTOTRADE] END");
        return returnCode;
    }

    /** ??????????????? function */
    @Override
    public int startLiquidity(Map list){
        int returnCode = ReturnCode.SUCCESS.getCode();

        Queue<String> sellQueue  = (LinkedList) list.get("sell");
        Queue<String> buyQueue   = (LinkedList) list.get("buy");
        Queue<String> cancelList = new LinkedList<>();

        try{
            log.info("[XTCOM][LIQUIDITY] START");
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

                // ?????? ??????
                if(price != null){
                    orderId = createOrder(action, price, cnt, coinWithId, exchange);
                    if(Utils.isSuccessOrder(orderId)){
                        cancelList.add(orderId);
                    }
                    Thread.sleep(1000);
                }
                // ?????? ??????
                if(!cancelList.isEmpty() && cancelFlag){
                    String cancelId = cancelList.poll();
                    cancelOrder(symbol, cancelId);
                    Thread.sleep(500);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.SUCCESS.getCode();
            log.error("[XTCOM][LIQUIDITY] Error {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[XTCOM][LIQUIDITY] END");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[XTCOM][FISHINGTRADE] START");

        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String[] coinWithId = Utils.splitCoinWithId(fishing.getCoin());
            Exchange exchange   = fishing.getExchange();
            String symbol       = getSymbol(coinWithId, exchange);

            // mode ??????
            Trade mode = Trade.valueOf(String.valueOf(list.keySet().toArray()[0]));
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode.getVal());
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            /* Start */
            log.info("[XTCOM][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
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
                Thread.sleep(500);
            }
            log.info("[XTCOM][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");

            /* Sell Start */
            log.info("[XTCOM][FISHINGTRADE][START BUY OR SELL TARGET PIECE COIN ]");
            boolean isSameFirstTick = true;    // ?????? ???????????? ????????? ??????/????????? ?????? ????????? ?????? ????????? ?????? ????????? ?????? ????????? ????????? ?????? ?????? ??????
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = Utils.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("cnt"));

                while (cnt.compareTo(new BigDecimal("0")) > 0) {
                    if (!isSameFirstTick) break;                   // ?????? ??????/?????? ??? ?????? ???????????? ??? ?????? ??????.
                    if(cnt.compareTo(new BigDecimal(copiedOrderMap.get("cnt"))) != 0){
                        Thread.sleep(intervalTime); // intervalTime ?????? ?????? ??? ?????? ??????
                    }
                    BigDecimal executionCnt = new BigDecimal(Utils.getRandomString(fishing.getMinExecuteCnt(), fishing.getMaxExecuteCnt()));  // ?????? ??????
                    executionCnt            = (cnt.compareTo(executionCnt) < 0) ? cnt : executionCnt;    // ?????? ?????? ?????? ??????/????????? ???????????? ???????????? ???, ?????? ?????? ?????? ??? ????????? ?????? cnt?????? ??????/??????

                    // ??????/?????? ??????????????? ?????? ??????/???????????? ?????? ??? ?????? ????????? ??????
                    String nowFirstTick = (mode == Trade.BUY) ?
                            coinService.getFirstTick(fishing.getCoin(), exchange).get(UtilsData.MODE_BUY) :
                            coinService.getFirstTick(fishing.getCoin(), exchange).get(UtilsData.MODE_SELL);

                    String orderPrice = copiedOrderMap.get("price");
                    if (!orderPrice.equals(nowFirstTick)) {
                        log.info("[XTCOM][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        isSameFirstTick = false;
                        break;
                    }

                    String orderId = (mode == Trade.BUY) ?
                            createOrder(SELL, copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange) :
                            createOrder(BUY,  copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange);

                    if(Utils.isSuccessOrder(orderId)){
                        cnt = cnt.subtract(executionCnt);
                    }else{
                        log.error("[XTCOM][FISHINGTRADE] While loop is broken, Because create order is failed");
                        break;
                    }
                }
                // ????????? ?????? ??????
                Thread.sleep(1000);
                cancelOrder(symbol, orderList.get(i).get("order_id"));
            }
            log.info("[XTCOM][FISHINGTRADE][END BUY OR SELL TARGET PIECE COIN ]");
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[XTCOM][FISHINGTRADE] Error {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[XTCOM][FISHINGTRADE] END");
        return returnCode;
    }

    @Override
    public int startRealtimeTrade(JsonObject realtime, boolean resetFlag) {
        log.info("[XTCOM][REALTIME SYNC TRADE] START");
        int returnCode   = ReturnCode.SUCCESS.getCode();
        String realtimeChangeRate = "signed_change_rate";

        try {
            boolean isStart      = false;
            String[] coinWithId  = Utils.splitCoinWithId(realtimeSync.getCoin());
            Exchange exchange    = realtimeSync.getExchange();
            String symbol        = getSymbol(coinWithId, realtimeSync.getExchange());
            String[] currentTick = getTodayTick(exchange, coinWithId);
            //            String openingPrice  = currentTick[0];
            if(resetFlag){
                realtimeTargetInitRate = currentTick[1];
                log.info("[XTCOM][REALTIME SYNC TRADE] Set init open rate : {} ", realtimeTargetInitRate);
            }
            String openingPrice  = realtimeTargetInitRate;
            String currentPrice  = currentTick[1];
            log.info("[XTCOM][REALTIME SYNC TRADE] open:{}, current:{} ", openingPrice, currentPrice);

            String targetPrice   = "";
            String action        = "";
            String mode          = "";
            String cnt           = Utils.getRandomString(realtimeSync.getMinTradeCnt(), realtimeSync.getMaxTradeCnt());

            // 1. ??????/?????? ?????? ????????? ????????? ??????
            int isInRange        = isMoreOrLessPrice(currentPrice);
            if(isInRange != 0){              // ?????? ?????? ??????
                if(isInRange == -1){         // ??????????????? ?????? ??????
                    mode         = UtilsData.MODE_BUY;
                    action       = BUY;
                    targetPrice  = realtimeSync.getMinPrice();
                }else if(isInRange == 1){    // ??????????????? ?????? ??????
                    mode         = UtilsData.MODE_SELL;
                    action       = SELL;
                    targetPrice  = realtimeSync.getMaxPrice();
                }
                isStart = true;
            }else{
                // ????????? ?????? ?????? ?????? ?????? ?????? ?????? ????????? ????????????.
                Map<String,String> tradeInfo = getTargetTick(openingPrice, currentPrice, realtime.get(realtimeChangeRate).getAsString());
                if(!tradeInfo.isEmpty()){
                    targetPrice = tradeInfo.get("price");
                    mode        = tradeInfo.get("mode");
                    action      = (mode.equals(UtilsData.MODE_BUY)) ? BUY : SELL;
                    isStart     = true;
                }
            }

            // 2. %??? ????????? ?????? ??????/?????? ??????
            if(isStart){
                String orderId = createOrder(action, targetPrice, cnt, coinWithId, exchange);
                if(Utils.isSuccessOrder(orderId)){    // ??????/OrderId??? ????????? ??????
                    Thread.sleep(300);

                    // 3. bestoffer set ??????
                    JsonArray array = makeBestofferAfterRealtimeSync(targetPrice, mode);
                    for (int i = 0; i < array.size(); i++) {
                        JsonObject object       = array.get(i).getAsJsonObject();
                        String bestofferPrice   = object.get("price").getAsString();
                        String bestofferCnt     = object.get("cnt").getAsString();
                        String bestofferOrderId = createOrder(action, bestofferPrice, bestofferCnt, coinWithId, exchange);
                        if(Utils.isSuccessOrder(bestofferOrderId)){
                            log.info("[XTCOM][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                        Thread.sleep(1000);
                    }

                    Thread.sleep(1000);
                    // ????????? ?????? ?????? ?????? ?????? ????????? ????????? ????????? ?????? ??????
                    cancelOrder(symbol, orderId);
                }
            }

        }catch (Exception e){
            log.error("[XTCOM][REALTIME SYNC TRADE] ERROR :{} ", e.getMessage());
            e.printStackTrace();
        }
        log.info("[XTCOM][REALTIME SYNC TRADE] END");
        return returnCode;
    }


    /**
     * ?????? Tick ????????????
     * @param exchange
     * @param coinWithId
     * @return [ ?????? , ?????? ] String Array
     */
    private String[] getTodayTick(Exchange exchange, String[] coinWithId) throws Exception{

        String[] returnRes   = new String[2];
        String symbol        = getSymbol(coinWithId, exchange);
        String response      = getHttpMethod(UtilsData.XTCOM_TICK);
        JsonObject ticks     = gson.fromJson(response, JsonObject.class);
        JsonObject obj       = gson.fromJson(ticks.get(symbol), JsonObject.class);

        BigDecimal current   = obj.get("price").getAsBigDecimal();    // ?????? ???
        BigDecimal percent   = obj.get("rate").getAsBigDecimal();
        BigDecimal open      = current.divide(new BigDecimal(1).add(percent),15, BigDecimal.ROUND_UP);  // ????????? 11???????????? ?????????

        returnRes[0] = open.toPlainString();
        returnRes[1] = current.toPlainString();

        return returnRes;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = "";
        try{
            log.info("[XTCOM][ORDER BOOK] START");
            String request = UtilsData.XTCOM_ORDERBOOK + "?market=" + getSymbol(coinWithId, exchange);
            returnRes = getHttpMethod(request);
            log.info("[XTCOM][ORDER BOOK] END");

        }catch (Exception e){
            log.error("[XTCOM][ORDER BOOK] Error {}",e.getMessage());
            e.printStackTrace();
        }

        return returnRes;
    }

    @Override
    public String getBalance(String[] coinData, Exchange exchange) throws Exception{
        String returnValue = ReturnCode.NO_DATA.getValue();

        setCoinToken(coinData, exchange);
        String url = UtilsData.XTCOM_BALANCE;
        long nonce = System.currentTimeMillis();
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("accesskey", keyList.get(PUBLIC_KEY));
        map.put("nonce", nonce);

        url += "?accesskey=" + keyList.get(PUBLIC_KEY)
                + "&nonce="  + nonce
                + "&signature=" + getSignature(map);

        JsonObject returnVal = gson.fromJson(getHttpMethod(url), JsonObject.class);
        String status        = returnVal.get("code").getAsString();
        if(status.equals(SUCCESS)){
            returnValue = gson.toJson(returnVal.get("data"));
            log.info("[XTCOM][GET BALANCE] Success response");
        }else{
            log.error("[XTCOM][CREATE ORDER] Response :{}", gson.toJson(returnVal));
        }

        return returnValue;
    }

    @Override
    public String createOrder(String type, String price, String cnt, String[] coinData, Exchange exchange){
        String orderId = ReturnCode.FAIL_CREATE.getValue();
        try {
            setCoinToken(coinData, exchange);
            String symbol  = getSymbol(coinData, exchange);
            String downCut = roundDownCnt(cnt);
            String action  = parseAction(type);

            Map<String, Object> map = new HashMap<String, Object>();
            map.put("accesskey", keyList.get(PUBLIC_KEY));
            map.put("nonce", System.currentTimeMillis());
            map.put("market", symbol);
            map.put("price", price);
            map.put("number", downCut);
            map.put("type", (action.equals(SELL)) ? 0 : 1 );	// 0.sell 1.buy
            map.put("entrustType", 0);	                    // 0.Limited price  1.Market price matching
            map.put("signature", getSignature(map));        // Signature

            JsonObject returnRes = postHttpMethod(UtilsData.XTCOM_CREATE_ORDER, map);
            if(returnRes.get("code").getAsString().equals(SUCCESS)){
                orderId = returnRes.getAsJsonObject("data").get("id").getAsString();
                log.info("[XTCOM][CREATE ORDER] Success response : {}", gson.toJson(returnRes));
            }else{
                log.error("[XTCOM][CREATE ORDER] Fail response : {}", gson.toJson(returnRes));
            }
        }catch (Exception e){
            log.error("[XTCOM][CREATE ORDER] Error : {}",e.getMessage());
            e.printStackTrace();
        }
        return orderId;
    }

    // ????????? 2?????? ??????????????? ???????????????
    private String roundDownCnt(String cnt) throws Exception{

        BigDecimal decimalCnt = new BigDecimal(cnt);
        BigDecimal setValue   = decimalCnt.setScale(getMinAmountLength(), BigDecimal.ROUND_DOWN);
        return setValue.toPlainString();
    }

    // Get MinCnt
    private int getMinAmountLength() throws Exception{
        String minAmount    = new BigDecimal(keyList.get(MIN_AMOUNT)).toPlainString();
        int returnLength    = -1;
        int dotLength       = -1;

        for (int i = 0; i < minAmount.length(); i++) {
            // ????????? ?????????
            if(Character.compare(minAmount.charAt(i),'1') == 0){
                if(dotLength == -1){
                    returnLength = 0;   // 1??????
                    break;
                }else{
                    // 0.01
                    // 0123
                    returnLength = i - dotLength;
                    break;
                }
            }else if(Character.compare(minAmount.charAt(i),'.') == 0){
                dotLength = i;
            }
        }

        return returnLength;
    }

    private JsonObject postHttpMethod(String url, Map<String, Object> params) {
        String result = null;
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(url);
        httpPost.setConfig(RequestConfig.custom().setSocketTimeout(UtilsData.TIMEOUT_VALUE).setConnectTimeout(UtilsData.TIMEOUT_VALUE).build());
        try {
            log.info("[XTCOM][POST HTTP] url:{}, params:{}", url, params.toString());

            httpPost.setHeader("User-Agent",
                    "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36");
            httpPost.setHeader("Accept", "application/json, text/javascript, */*; q=0.01");
            httpPost.setHeader("Accept-Encoding", " deflate, sdch");
            httpPost.setHeader("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
            if (params != null && !params.isEmpty()) {
                List<NameValuePair> urlParams = new ArrayList<NameValuePair>();
                for (String key : params.keySet()) {
                    urlParams.add(new BasicNameValuePair(key, params.get(key).toString()));
                }
                UrlEncodedFormEntity entity = new UrlEncodedFormEntity(urlParams, StandardCharsets.UTF_8);
                httpPost.setEntity(entity);
            }
            HttpResponse httpResponse = httpClient.execute(httpPost);
            HttpEntity httpEntity = httpResponse.getEntity();
            if (httpEntity != null) {
                result = EntityUtils.toString(httpEntity, StandardCharsets.UTF_8);
                EntityUtils.consume(httpEntity);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                httpClient.close();
                httpPost.releaseConnection();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return gson.fromJson(result,JsonObject.class);
    }

    // xtcom lib??? ?????????????????? hmacsha256
    private String getSignature(final Map<String, Object> data) throws Exception{

        Set<String> keySet = data.keySet();
        String[] keyArray = keySet.toArray(new String[keySet.size()]);
        Arrays.sort(keyArray);
        StringBuilder sb = new StringBuilder();
        for (String k : keyArray) {
            sb.append(k).append("=").append(data.get(k).toString().trim()).append("&");
        }
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return hmacSha256(sb.toString());
    }

    // xtcom lib??? ?????????????????? hmacsha256
    private String hmacSha256(String value) throws Exception{
        String result = null;
        byte[] keyBytes = keyList.get(SECRET_KEY).getBytes(StandardCharsets.UTF_8);
        SecretKeySpec localMac = new SecretKeySpec(keyBytes, "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(localMac);
        byte[] arrayOfByte = mac.doFinal(value.getBytes());
        BigInteger localBigInteger = new BigInteger(1, arrayOfByte);
        result = String.format("%0" + (arrayOfByte.length << 1) + "x", new Object[] { localBigInteger });

        return result;
    }

    /* XTCOM global ?????? ?????? */
    private int cancelOrder(String symbol, String orderId) {
        int returnValue = ReturnCode.FAIL.getCode();
        try {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("accesskey", keyList.get(PUBLIC_KEY));
            map.put("nonce", System.currentTimeMillis());
            map.put("market", symbol);
            map.put("id", orderId);
            map.put("signature", getSignature(map));

            JsonObject returnRes = postHttpMethod(UtilsData.XTCOM_CANCEL_ORDER, map);
            if(returnRes.get("code").getAsString().equals(SUCCESS)){
                log.info("[XTCOM][CANCEL ORDER] Success cancel orderId : {} response : {}",orderId, gson.toJson(returnRes));
            }else{
                log.error("[XTCOM][CANCEL ORDER] Fail response : {}", gson.toJson(returnRes));
            }
        }catch (Exception e){
            log.error("[XTCOM][CANCEL ORDER] Error orderId:{}, response:{}",orderId, e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }

    // ???????????? ?????? ?????? ??????
    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0].toLowerCase() + "_" + getCurrency(exchange, coinData[0], coinData[1]).toLowerCase();
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
