package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.enumeration.LogAction;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.common.enumeration.Trade;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.BithumbHttpService;
import com.coin.autotrade.service.CoinService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringEscapeUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
public class BithumbImp extends AbstractExchange {
    final private String ALREADY_TRADED   = "3000";
    final private String SUCCESS          = "0000";
    final private String BUY              = "bid";
    final private String SELL             = "ask";


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
        super.coinService = coinService;
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
    }

    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[BITHUMB][AUTOTRADE] START");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String[] coinWithId = Utils.splitCoinWithId(autoTrade.getCoin());
            Exchange exchange   = autoTrade.getExchange();
            String currency     = getCurrency(exchange, coinWithId[0], coinWithId[1]);
            Trade mode          = getMode(autoTrade.getMode());
            String firstAction  = (mode == Trade.BUY) ? BUY : SELL;
            String secondAction = (mode == Trade.BUY) ? SELL : BUY;

            String firstOrderId  = createOrder(firstAction,price, cnt, coinWithId, exchange);
            if(Utils.isSuccessOrder(firstOrderId)){
                String secondOrderId = createOrder(secondAction, price, cnt, coinWithId, exchange);

                Thread.sleep(500);
                cancelOrder(firstAction,firstOrderId, coinWithId[0], currency);                      // ?????? ?????? ???, ?????? ??????
                if(Utils.isSuccessOrder(secondOrderId)){
                    cancelOrder(secondAction,secondOrderId, coinWithId[0], currency);                      // ?????? ?????? ???, ?????? ??????
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[BITHUMB][AUTOTRADE] Error : {}", e.getMessage());
        }

        log.info("[BITHUMB][AUTOTRADE] END");
        return returnCode;
    }

    /** ??????????????? function */
    @Override
    public int startLiquidity(Map<String, LinkedList<String>> list){
        int returnCode = ReturnCode.SUCCESS.getCode();

        Queue<String> sellQueue = list.get("sell");
        Queue<String> buyQueue  = list.get("buy");
        Queue<Map<String,String>> cancelList = new LinkedList<>();

        try{
            log.info("[BITHUMB][LIQUIDITY] START");
            String[] coinData = Utils.splitCoinWithId(liquidity.getCoin());
            Exchange exchange = liquidity.getExchange();
            String currency   = getCurrency(exchange, coinData[0], coinData[1]);

            while (!sellQueue.isEmpty() || !buyQueue.isEmpty() || !cancelList.isEmpty()) {
                Trade mode         = getMode();
                boolean cancelFlag = (Utils.getRandomInt(1, 2) == 1) ? true : false;
                String orderId     = ReturnCode.FAIL_CREATE.getValue();
                String action      = (mode == Trade.BUY) ? BUY : SELL;
                String cnt         = Utils.getRandomString(liquidity.getMinCnt(), liquidity.getMaxCnt());
                String price       = null;

                if(!buyQueue.isEmpty() && mode == Trade.BUY){
                    price = buyQueue.poll();
                }else if(!sellQueue.isEmpty() && mode == Trade.SELL){
                    price = sellQueue.poll();
                }

                // ?????? ??????
                if(price != null){
                    orderId = createOrder(action, price, cnt, coinData, exchange);
                    if(Utils.isSuccessOrder(orderId)){
                        Map<String, String> cancel = new HashMap<>();
                        cancel.put("orderId", orderId);
                        cancel.put("action",  action);
                        cancelList.add(cancel);
                    }
                    Thread.sleep(1000);
                }
                // ?????? ??????
                if(!cancelList.isEmpty() && cancelFlag){
                    Map<String, String> cancelMap = cancelList.poll();
                    String cancelAction           = cancelMap.get("action");
                    String cancelId               = cancelMap.get("orderId");
                    cancelOrder(cancelAction, cancelId, coinData[0], currency);
                    Thread.sleep(500);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[BITHUMB][LIQUIDITY] ERROR : {}", e.getMessage());
        }
        log.info("[BITHUMB][LIQUIDITY] END");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[BITHUMB][FISHINGTRADE] START");

        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String[] coinWithId = Utils.splitCoinWithId(fishing.getCoin());
            Exchange exchange   = fishing.getExchange();
            String currency     = getCurrency(exchange, coinWithId[0], coinWithId[1]);

            // mode ??????
            Trade mode = Trade.valueOf(String.valueOf(list.keySet().toArray()[0]));
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode.getVal());
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            /* Start */
            log.info("[BITHUMB][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
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
                    if(mode == Trade.BUY){
                        orderMap.put("type", BUY);
                    }else{
                        orderMap.put("type", SELL);
                    }
                    orderList.add(orderMap);
                }
            }
            log.info("[BITHUMB][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");

            /* Sell Start */
            log.info("[BITHUMB][FISHINGTRADE][START BUY OR SELL TARGET PIECE COIN ]");
            boolean isSameFirstTick = true;    // ?????? ???????????? ????????? ??????/????????? ?????? ????????? ?????? ????????? ?????? ????????? ?????? ????????? ????????? ?????? ?????? ??????
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = Utils.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("cnt"));

                while (cnt.compareTo(BigDecimal.ZERO) > 0) {
                    if (!isSameFirstTick) break;                                        // ?????? ??????/?????? ??? ?????? ???????????? ??? ?????? ??????.
                    if(cnt.compareTo(new BigDecimal(copiedOrderMap.get("cnt"))) != 0){  // ????????? ??????/?????? ??????????????? interval ?????? X
                        Thread.sleep(intervalTime);                                     // intervalTime ?????? ?????? ??? ?????? ??????
                    }

                    BigDecimal executionCnt = new BigDecimal(Utils.getRandomString(fishing.getMinExecuteCnt(), fishing.getMaxExecuteCnt()));
                    executionCnt            = (cnt.compareTo(executionCnt) < 0) ? cnt : executionCnt;    // ?????? ?????? ?????? ??????/????????? ???????????? ???????????? ???, ?????? ?????? ?????? ??? ????????? ?????? cnt?????? ??????/??????

                    // ??????/?????? ??????????????? ?????? ??????/???????????? ?????? ??? ?????? ????????? ??????
                    String nowFirstTick = (mode == Trade.BUY) ?
                            coinService.getFirstTick(fishing.getCoin(), exchange).get(UtilsData.MODE_BUY) :
                            coinService.getFirstTick(fishing.getCoin(), exchange).get(UtilsData.MODE_SELL);

                    String orderPrice = copiedOrderMap.get("price");
                    if (!orderPrice.equals(nowFirstTick)) {
                        log.info("[BITHUMB][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        isSameFirstTick = false;
                        break;
                    }

                    String orderId = (mode == Trade.BUY) ?
                                    createOrder(SELL, copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange) :
                                    createOrder(BUY,  copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange);

                    if(Utils.isSuccessOrder(orderId)){
                        cnt = cnt.subtract(executionCnt);
                    }else{
                        log.error("[BITHUMB][FISHINGTRADE] While loop is broken, Because create order is failed");
                        break;
                    }
                }
                // ????????? ????????? ????????? ?????? ?????? ??????
                Thread.sleep(500);
                cancelOrder(orderList.get(i).get("type"), orderList.get(i).get("order_id"), coinWithId[0], currency);
                Thread.sleep(2000);
            }
            log.info("[BITHUMB][FISHINGTRADE][END BUY OR SELL TARGET PIECE COIN ]");
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[BITHUMB][FISHINGTRADE] ERROR {}", e.getMessage());
        }

        log.info("[BITHUMB][FISHINGTRADE] END");
        return returnCode;
    }


    @Override
    public int startRealtimeTrade(JsonObject realtime, boolean resetFlag) {
        log.info("[BITHUMB][REALTIME SYNC TRADE] START");
        int returnCode   = ReturnCode.SUCCESS.getCode();
        String realtimeChangeRate = "signed_change_rate";

        try {

            boolean isStart      = false;
            String[] coinWithId  = Utils.splitCoinWithId(realtimeSync.getCoin());
            Exchange exchange    = realtimeSync.getExchange();
            String currency      = getCurrency(exchange, coinWithId[0], coinWithId[1]);
            String symbol        = getSymbol(coinWithId, exchange);
            String[] currentTick = getTodayTick();
            //            String openingPrice  = currentTick[0];
            if(resetFlag){
                realtimeTargetInitRate = currentTick[1];
                log.info("[BITHUMB][REALTIME SYNC TRADE] Set init open rate : {} ", realtimeTargetInitRate);
            }
            String openingPrice  = realtimeTargetInitRate;
            String currentPrice  = currentTick[1];
            log.info("[BITHUMB][REALTIME SYNC TRADE] open:{}, current:{} ", openingPrice, currentPrice);

            String targetPrice   = "";
            String action        = "";
            String mode          = "";
            String cnt           = Utils.getRandomString(realtimeSync.getMinTradeCnt(), realtimeSync.getMaxTradeCnt());

            int isInRange = isMoreOrLessPrice(currentPrice);
            if(isInRange != 0){              // ?????? ?????? ??????
                if(isInRange == -1){         // ??????????????? ?????? ??????
                    action       = BUY;
                    mode         = UtilsData.MODE_BUY;
                    targetPrice  = realtimeSync.getMinPrice();
                }else if(isInRange == 1){    // ??????????????? ?????? ??????
                    action       = SELL;
                    mode         = UtilsData.MODE_SELL;
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

            if(isStart){
                String orderId = createOrder(action, targetPrice, cnt, coinWithId, exchange);
                if(Utils.isSuccessOrder(orderId)){
                    Thread.sleep(300);

                    // 3. bestoffer set ??????
                    JsonArray array = makeBestofferAfterRealtimeSync(targetPrice, mode);
                    for (int i = 0; i < array.size(); i++) {
                        JsonObject object       = array.get(i).getAsJsonObject();
                        String bestofferPrice   = object.get("price").getAsString();
                        String bestofferCnt     = object.get("cnt").getAsString();
                        String bestofferOrderId = createOrder(action, bestofferPrice, bestofferCnt, coinWithId, exchange);
                        if(Utils.isSuccessOrder(bestofferOrderId)){
                            log.info("[BITHUMB][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                    }
                    cancelOrder(action, orderId, coinWithId[0], currency);
                }
            }
        }catch (Exception e){
            log.error("[BITHUMB][REALTIME SYNC TRADE] Error :{} ", e.getMessage());
        }
        log.info("[BITHUMB][REALTIME SYNC TRADE] END");
        return returnCode;
    }


    private String[] getTodayTick() throws Exception{

        String[] returnRes   = new String[2];
        String request       = UtilsData.BITHUMB_TICK + "/" + getSymbol(Utils.splitCoinWithId(realtimeSync.getCoin()),realtimeSync.getExchange());
        String response      = getHttpMethod(request);
        JsonObject resObject = gson.fromJson(response, JsonObject.class);
        String returnCode    = resObject.get("status").getAsString();
        if(SUCCESS.equals(returnCode)){
            JsonObject data = resObject.get("data").getAsJsonObject();
            returnRes[0]    = data.get("prev_closing_price").getAsString();
            returnRes[1]    = data.get("closing_price").getAsString();
        }else{
            log.error("[BITHUMB][GET TODAY TICK] response : {}", response);
            throw new Exception(response);
        }
        return returnRes;
    }


    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = ReturnCode.FAIL.getValue();
        try{
            String request  = UtilsData.BITHUMB_ORDERBOOK + "/" + getSymbol(coinWithId,exchange);
            returnRes       = getHttpMethod(request);
            JsonObject json = gson.fromJson(returnRes, JsonObject.class);
            String status   = json.get("status").getAsString();
            if(!SUCCESS.equals(status)){
                log.error("[BITHUMB][ORDER BOOK] ERROR : {}", returnRes);
                insertLog(request, LogAction.ORDER_BOOK, returnRes);
            }
        }catch (Exception e){
            log.error("[BITHUMB][ORDER BOOK] ERROR : {}", e.getMessage());
            insertLog(Arrays.toString(coinWithId), LogAction.ORDER_BOOK, e.getMessage());
        }
        return returnRes;
    }


    @Override
    public String getBalance(String[] coinData, Exchange exchange) throws Exception{
        String returnValue = ReturnCode.NO_DATA.getValue();;

        setCoinToken(coinData, exchange);

        HashMap<String, String> rgParams = new HashMap<String, String>();
        rgParams.put("currency", "ALL");
        String apiHost                      = UtilsData.BITHUMB_URL + UtilsData.BITHUMB_ENDPOINT_BALANCE;
        HashMap<String, String> httpHeaders = getHttpHeaders(UtilsData.BITHUMB_ENDPOINT_BALANCE, rgParams);
        String rgResultDecode               = postHttpMethod(apiHost,  rgParams, httpHeaders);
        JsonObject returnVal = gson.fromJson(rgResultDecode, JsonObject.class);
        String status        = returnVal.get("status").getAsString();

        if(status.equals(SUCCESS)){
            returnValue = gson.toJson(returnVal.get("data"));
            log.info("[BITHUMB][GET BALANCE] SUCCESS");
        }else{
            returnValue = rgResultDecode;
            log.error("[BITHUMB][GET BALANCE] response :{}", rgResultDecode);
            insertLog(createLog(rgParams, httpHeaders), LogAction.BALANCE, rgResultDecode);
        }

        return returnValue;
    }
    @Override
    public String createOrder(String type, String price, String cnt, String[] coinData, Exchange exchange){
        String response = ReturnCode.FAIL_CREATE.getValue();

        try{
            setCoinToken(coinData, exchange);
            String currency = getCurrency(exchange, coinData[0], coinData[1]);
            String action   = parseAction(type);

            HashMap<String, String> rgParams = new HashMap<String, String>();
            rgParams.put("order_currency", coinData[0]);
            rgParams.put("payment_currency", currency);
            rgParams.put("units", cnt);
            rgParams.put("price", price);
            rgParams.put("type", action);

            String apiHost                      = UtilsData.BITHUMB_URL + UtilsData.BITHUMB_ENDPOINT_CREATE_ORDER;
            HashMap<String, String> httpHeaders = getHttpHeaders(UtilsData.BITHUMB_ENDPOINT_CREATE_ORDER, rgParams);
            String rgResultDecode               = postHttpMethod(apiHost,  rgParams, httpHeaders);

            JsonObject returnVal = gson.fromJson(rgResultDecode, JsonObject.class);
            String status        = returnVal.get("status").getAsString();
            if(status.equals(SUCCESS)){
                response = returnVal.get("order_id").getAsString();
                log.info("[BITHUMB][CREATE ORDER] response : {}", rgResultDecode);
            }else{
                log.error("[BITHUMB][CREATE ORDER] response :{}", rgResultDecode);
                insertLog(createLog(rgParams, httpHeaders), LogAction.CREATE_ORDER, rgResultDecode);
            }
        }catch (Exception e){
            log.error("[BITHUMB][CREATE ORDER] ERROR {}",e.getMessage());
            insertLog("", LogAction.CREATE_ORDER, e.getMessage());
        }
        return response;
    }

    private int cancelOrder(String type, String orderId, String coin, String currency) {

        int returnValue = ReturnCode.NO_DATA.getCode();
        try {
            HashMap<String, String> rgParams = new HashMap<String, String>();
            rgParams.put("type", type);
            rgParams.put("order_id", orderId);
            rgParams.put("order_currency", coin);
            rgParams.put("payment_currency", currency);

            String api_host                     = UtilsData.BITHUMB_URL + UtilsData.BITHUMB_ENDPOINT_CANCEL_ORDER;
            HashMap<String, String> httpHeaders = getHttpHeaders(UtilsData.BITHUMB_ENDPOINT_CANCEL_ORDER, rgParams);
            String rgResultDecode               = postHttpMethod(api_host,  rgParams, httpHeaders);
            JsonObject returnVal = gson.fromJson(rgResultDecode, JsonObject.class);
            String status        = returnVal.get("status").getAsString();
            if(status.equals(SUCCESS) || status.equals(ALREADY_TRADED)){
                log.info("[BITHUMB][CANCEL ORDER] response : {}", rgResultDecode);
            }else{
                log.error("[BITHUMB][CANCEL ORDER] response :{}", rgResultDecode);
                insertLog(createLog(rgParams, httpHeaders), LogAction.CANCEL_ORDER, rgResultDecode);
            }
        }catch(Exception e){
            log.error("[BITHUMB][CANCEL ORDER] ERROR : {}", e.getMessage());
            insertLog("", LogAction.CANCEL_ORDER, e.getMessage());
        }
        return returnValue;
    }


    /** Bithum ?????? ???????????? ???????????? ????????? http */
    private String postHttpMethod(String targetUrl, HashMap<String, String> rgParams,  HashMap<String, String> httpHeaders) throws Exception{
        String response = ReturnCode.NO_DATA.getValue();

        log.info("[BITHUMB][POST HTTP] url : {} , request : {}", targetUrl, rgParams.toString());
        BithumbHttpService request = new BithumbHttpService(targetUrl, "POST");
        request.readTimeout(10000);
        if (httpHeaders != null && !httpHeaders.isEmpty()) {    // setRequestProperty on header
            httpHeaders.put("api-client-type", "2");
            request.headers(httpHeaders);
        }
        if (rgParams != null && !rgParams.isEmpty()) {
            request.form(rgParams);
        }
        response = StringEscapeUtils.unescapeJava(request.body());
        log.info("[BITTHUMB][POST HTTP] Response : {}", response);
        request.disconnect();

        return response;
    }

    /** Bithum ?????? ???????????? ???????????? ????????? http??? ???????????? ?????? header??? ????????? ?????? */
    private HashMap<String, String> getHttpHeaders(String endpoint, HashMap<String, String> rgData) throws Exception{

        String strData = mapToQueryString(rgData);
        String nNonce  = String.valueOf(System.currentTimeMillis());
        strData        = encodeURIComponent(strData);

        HashMap<String, String> array = new HashMap<String, String>();
        String str                    = endpoint + ";"	+ strData + ";" + nNonce;
        String encoded                = asHex(hmacSha512(str, keyList.get(SECRET_KEY)));

        array.put("Api-Key",   keyList.get(PUBLIC_KEY));
        array.put("Api-Sign",  encoded);
        array.put("Api-Nonce", nNonce);

        return array;
    }

    // Map?????? ?????? ???????????? ????????? ?????? ????????? ???????????? ??????
    private String mapToQueryString(Map<String, String> map) throws Exception{
       return map.keySet().stream().map(key -> key + "=" + map.get(key)).collect(Collectors.joining("&"));
    }

    // Bithum ?????? ???????????? ???????????? ?????????
    private String encodeURIComponent(String s) {
        String result = null;
        try {
            result = URLEncoder.encode(s, "UTF-8")
                    .replaceAll("\\+", "%20")
                    .replaceAll("\\%21", "!")
                    .replaceAll("\\%27", "'")
                    .replaceAll("\\%28", "(")
                    .replaceAll("\\%29", ")")
                    .replaceAll("\\%26", "&")
                    .replaceAll("\\%3D", "=")
                    .replaceAll("\\%7E", "~");
        }catch (UnsupportedEncodingException e) {
            result = s;
        }

        return result;
    }

    // Bithum ?????? ???????????? ???????????? ?????????
    private byte[] hmacSha512(String value, String key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA512");
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(keySpec);

        final byte[] macData = mac.doFinal(value.getBytes());
        byte[] hex = new Hex().encode(macData);
        return hex;
    }

    // Bithumb ?????? ???????????? ?????????
    private String asHex(byte[] bytes){
        return new String(Base64.encodeBase64(bytes));
    }

    // ???????????? ?????? ?????? ??????
    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0]+ "_" + getCurrency(exchange,coinData[0], coinData[1]);
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

    private String createLog(Map<String, String> param, Map<String, String> header){
        Map<String , String> map = new HashMap<>();
        map.putAll(param);
        map.putAll(header);

        return gson.toJson(map);
    }

    private void insertLog(String request, LogAction action, String msg){
        exceptionLog.makeLogAndInsert("??????",request, action, msg);
    }

}
