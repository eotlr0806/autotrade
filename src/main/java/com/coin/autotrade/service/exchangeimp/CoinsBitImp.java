package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.enumeration.LogAction;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.common.enumeration.Trade;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Slf4j
public class CoinsBitImp extends AbstractExchange {
    final private String BUY            = "buy";
    final private String SELL           = "sell";
    final private String ALREADY_CANCEL = "Trade order not found";

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
            log.info("[COINSBIT][SET API KEY] First Key setting in instance API:{}, secret:{}, min_Amount:{}",keyList.get(PUBLIC_KEY), keyList.get(SECRET_KEY), keyList.get(MIN_AMOUNT));
        }
    }

    // Get min amount
    private String getMinAmount(String[] coinData, Exchange exchange) throws Exception{
        String url            = UtilsData.COINSBIT_URL + UtilsData.COINSBIT_GET_COININFO;
        JsonObject object     = gson.fromJson(getHttpMethod(url), JsonObject.class);
        JsonArray coinInfoArr = object.getAsJsonArray("result");
        String symbol         = getSymbol(coinData, exchange);
        JsonObject targetObj  = null;
        for(JsonElement element : coinInfoArr){
            JsonObject tempObj = element.getAsJsonObject();
            if(tempObj.get("name").getAsString().equals(symbol)){
                targetObj = tempObj;
                break;
            }
        }
        if(targetObj == null){
            throw new Exception("Not Available API" + symbol);
        }

        return targetObj.get("minAmount").getAsString();
    }


    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[COINSBIT][AUTOTRADE] START");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String[] coinWithId = Utils.splitCoinWithId(autoTrade.getCoin());
            Exchange exchange   = autoTrade.getExchange();
            String symbol       = getSymbol(coinWithId, exchange);
            Trade mode          = getMode(autoTrade.getMode());
            String firstAction  = (mode == Trade.BUY) ? BUY : SELL;
            String secondAction = (mode == Trade.BUY) ? SELL : BUY;

            String firstOrderId  = createOrder(firstAction,price, cnt, coinWithId, exchange);
            if(Utils.isSuccessOrder(firstOrderId)){
                String secondOrderId = createOrder(secondAction, price, cnt, coinWithId, exchange);

                Thread.sleep(1000);
                cancelOrder(symbol, firstOrderId);
                if(Utils.isSuccessOrder(secondOrderId)){
                    cancelOrder(symbol, secondOrderId);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[COINSBIT][AUTOTRADE] Error : {}", e.getMessage());
        }
        log.info("[COINSBIT][AUTOTRADE] END");
        return returnCode;
    }

    @Override
    public int startLiquidity(Map<String, LinkedList<String>> list){
        int returnCode = ReturnCode.SUCCESS.getCode();

        Queue<String> sellQueue  = list.get("sell");
        Queue<String> buyQueue   = list.get("buy");
        Queue<String> cancelList = new LinkedList<>();

        try{
            log.info("[COINSBIT][LIQUIDITY] START");
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
                    Thread.sleep(1500);
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
            log.error("[COINSBIT][LIQUIDITY] Error {}", e.getMessage());
        }
        log.info("[COINSBIT][LIQUIDITY] END");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[COINSBIT][FISHINGTRADE] START");

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
            log.info("[COINSBIT][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt = Utils.getRandomString(fishing.getMinContractCnt(), fishing.getMaxContractCnt());
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
            log.info("[COINSBIT][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");

            /* Sell Start */
            log.info("[COINSBIT][FISHINGTRADE][START BUY OR SELL TARGET PIECE COIN ]");
            boolean isSameFirstTick = true;    // ?????? ???????????? ????????? ??????/????????? ?????? ????????? ?????? ????????? ?????? ????????? ?????? ????????? ????????? ?????? ?????? ??????
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = Utils.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("cnt"));

                while (cnt.compareTo(BigDecimal.ZERO) > 0) {
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
                        log.info("[COINSBIT][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        isSameFirstTick = false;
                        break;
                    }

                    String orderId = (mode == Trade.BUY) ?
                            createOrder(SELL, copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange) :
                            createOrder(BUY,  copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange);

                    if(Utils.isSuccessOrder(orderId)){
                        cnt = cnt.subtract(executionCnt);
                    }else{
                        log.error("[COINSBIT][FISHINGTRADE] While loop is broken, Because create order is failed");
                        break;
                    }
                }
                // ????????? ?????? ??????
                Thread.sleep(500);
                cancelOrder(symbol, orderList.get(i).get("order_id"));
            }
            log.info("[COINSBIT][FISHINGTRADE][END BUY OR SELL TARGET PIECE COIN ]");
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[COINSBIT][FISHINGTRADE] Error {}", e.getMessage());
        }
        log.info("[COINSBIT][FISHINGTRADE] END");
        return returnCode;
    }

    /**
     * Realtime Sync ??????
     * @param realtime
     * @return
     */
    public int startRealtimeTrade(JsonObject realtime, boolean resetFlag) {
        log.info("[COINSBIT][REALTIME SYNC TRADE] START");
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
                log.info("[COINSBIT][REALTIME SYNC TRADE] Set init open rate : {} ", realtimeTargetInitRate);
            }
            String openingPrice  = realtimeTargetInitRate;
            String currentPrice  = currentTick[1];
            log.info("[COINSBIT][REALTIME SYNC TRADE] open:{}, current:{} ", openingPrice, currentPrice);

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
                            log.info("[COINSBIT][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                        Thread.sleep(500);
                    }

                    Thread.sleep(500);
                    // ????????? ?????? ?????? ?????? ?????? ????????? ????????? ????????? ?????? ??????
                    cancelOrder(symbol, orderId);
                }
            }

        }catch (Exception e){
            log.error("[COINSBIT][REALTIME SYNC TRADE] ERROR :{} ", e.getMessage());
        }
        log.info("[COINSBIT][REALTIME SYNC TRADE] END");
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
        String url           = UtilsData.COINSBIT_URL + UtilsData.COINSBIT_TICK + "?market=" + getSymbol(coinWithId, exchange);
        JsonObject response  = gson.fromJson(getHttpMethod(url), JsonObject.class);
        JsonObject obj       = response.getAsJsonObject("result");

        BigDecimal current   = obj.get("last").getAsBigDecimal();    // ?????? ???
        BigDecimal percent   = obj.get("change").getAsBigDecimal().divide(new BigDecimal(100),15, BigDecimal.ROUND_UP);
        BigDecimal open      = current.divide(new BigDecimal(1).add(percent),15, BigDecimal.ROUND_UP);  // ????????? 11???????????? ?????????

        returnRes[0] = open.toPlainString();
        returnRes[1] = current.toPlainString();

        return returnRes;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = "";
        try{
            log.info("[COINSBIT][ORDER BOOK] START");
            String sellRequest      = UtilsData.COINSBIT_URL + UtilsData.COINSBIT_ORDERBOOK + "?market=" + getSymbol(coinWithId, exchange) +"&side=sell";
            JsonObject sellResponse = gson.fromJson(getHttpMethod(sellRequest),JsonObject.class);
            String buyRequest       = UtilsData.COINSBIT_URL + UtilsData.COINSBIT_ORDERBOOK + "?market=" + getSymbol(coinWithId, exchange) +"&side=buy";
            JsonObject buyResponse  = gson.fromJson(getHttpMethod(buyRequest), JsonObject.class);
            if(isSuccess(sellResponse) && isSuccess(buyResponse)){
                JsonObject resObj = new JsonObject();
                JsonObject data   = new JsonObject();
                data.add("bids", parseOrderBook(buyResponse));
                data.add("asks", parseOrderBook(sellResponse));
                resObj.add("data", data);
                returnRes = gson.toJson(resObj);
            }else{
                insertLog(Arrays.toString(coinWithId), LogAction.ORDER_BOOK, gson.toJson(sellResponse));
            }

            log.info("[COINSBIT][ORDER BOOK] END");

        }catch (Exception e){
            log.error("[COINSBIT][ORDER BOOK] Error {}",e.getMessage());
            insertLog(Arrays.toString(coinWithId), LogAction.ORDER_BOOK, e.getMessage());
        }

        return returnRes;
    }

    private JsonArray parseOrderBook(JsonObject obj) throws Exception{
        JsonArray result = new JsonArray();
        JsonArray orders = obj.getAsJsonObject("result").getAsJsonArray("orders");

        for (JsonElement element : orders){
            JsonObject targetObj = element.getAsJsonObject();

            JsonObject addObj    = new JsonObject();
            addObj.add("price", targetObj.get("price"));
            addObj.add("quantity", targetObj.get("amount"));
            result.add(addObj);
        }

        return result;
    }

    @Override
    public String getBalance(String[] coinData, Exchange exchange) throws Exception{
        String returnValue = ReturnCode.NO_DATA.getValue();;

        setCoinToken(coinData, exchange);
        Map<String, String> object = new LinkedHashMap<>();
        object.put("request", UtilsData.COINSBIT_BALANCE);
        object.put("nonce",   String.valueOf(Instant.now().getEpochSecond()));

        String url = UtilsData.COINSBIT_URL + UtilsData.COINSBIT_BALANCE;
        JsonObject returnRes = postHttpMethod(url, gson.toJson(object));
        if(isSuccess(returnRes)){
            returnValue = gson.toJson(returnRes.get("result"));
            log.info("[COINSBIT][GET BALANCE] Success response");
        }else{
            log.error("[COINSBIT][GET BALANCE] Fail response : {}", gson.toJson(returnRes));
            insertLog(gson.toJson(object), LogAction.BALANCE, gson.toJson(returnRes));
        }

        return returnValue;
    }

    @Override
    public String createOrder(String type, String price, String cnt , String[] coinData, Exchange exchange){
        String orderId = ReturnCode.FAIL_CREATE.getValue();
        try {
            Map<String, String> object = new LinkedHashMap<>();
            String action     = parseAction(type);
            String symbol     = getSymbol(coinData, exchange);
            String downCut    = roundDownCnt(cnt);

            object.put("request", UtilsData.COINSBIT_CREATE_ORDER);
            object.put("nonce",   String.valueOf(Instant.now().getEpochSecond()));
            object.put("market",  symbol);
            object.put("side",    action);
            object.put("amount",  downCut);
            object.put("price",   price);
            String url = UtilsData.COINSBIT_URL + UtilsData.COINSBIT_CREATE_ORDER;
            JsonObject returnRes = postHttpMethod(url, gson.toJson(object));
            if(isSuccess(returnRes)){
                orderId = returnRes.getAsJsonObject("result").get("orderId").getAsString();
                log.info("[COINSBIT][CREATE ORDER] Success response : {}", gson.toJson(returnRes));
            }else{
                log.error("[COINSBIT][CREATE ORDER] Fail response : {}", gson.toJson(returnRes));
                insertLog(gson.toJson(object), LogAction.CREATE_ORDER, gson.toJson(returnRes));
            }
        }catch (Exception e){
            log.error("[COINSBIT][CREATE ORDER] Error : {}",e.getMessage());
            insertLog("", LogAction.CREATE_ORDER, e.getMessage());
        }
        return orderId;
    }

    private int cancelOrder(String symbol, String orderId) {
        int returnValue = ReturnCode.FAIL.getCode();
        try {
            Map<String, String> object = new LinkedHashMap<>();
            object.put("request", UtilsData.COINSBIT_CANCEL_ORDER);
            object.put("nonce",  String.valueOf(Instant.now().getEpochSecond()));
            object.put("market", symbol);
            object.put("orderId", orderId);

            String url = UtilsData.COINSBIT_URL + UtilsData.COINSBIT_CANCEL_ORDER;
            JsonObject returnRes = postHttpMethod(url, gson.toJson(object));
            if(isSuccess(returnRes)){
                log.info("[COINSBIT][CANCEL ORDER] Success cancel response : {}", gson.toJson(returnRes));
            }else if(isAlreadyCanceled(returnRes)){
                log.info("[COINSBIT][CANCEL ORDER] Already cancel response : {}", gson.toJson(returnRes));
            }else{
                log.error("[COINSBIT][CANCEL ORDER] Fail response : {}", gson.toJson(returnRes));
                insertLog(gson.toJson(object), LogAction.CANCEL_ORDER, gson.toJson(returnRes));
            }
        }catch (Exception e){
            log.error("[COINSBIT][CANCEL ORDER] Error orderId:{}, response:{}",orderId, e.getMessage());
            insertLog("", LogAction.CANCEL_ORDER, e.getMessage());
        }
        return returnValue;
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

    /* Http post method */
    public JsonObject postHttpMethod(String endPoint, String body) throws Exception{
        log.info("[COINSBIT][POST HTTP] post http start");
        log.info("[COINSBIT][POST HTTP] url:{}, body:{}", endPoint, body);

        String payload = Base64.getEncoder().encodeToString(body.getBytes());
        String sign    = HmacAndHex(payload);
        Map<String, String> map = new LinkedHashMap<>();
        map.put("X-TXC-PAYLOAD", payload);
        map.put("X-TXC-SIGNATURE", sign);

        HttpEntity<String> response = restTemplate.exchange(
                endPoint,
                HttpMethod.POST,
                new HttpEntity<String>(body, getHeader(map)),
                String.class
        );

        log.info("[COINSBIT][POST HTTP] post http end");
        return gson.fromJson(response.getBody(), JsonObject.class);
    }

    private String HmacAndHex(String value) throws Exception{
        byte[] keyBytes = keyList.get(SECRET_KEY).getBytes(StandardCharsets.UTF_8);
        SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA512");
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(signingKey);
        byte[] hexBytes = new Hex().encode(mac.doFinal(value.getBytes()));
        return new String(hexBytes, "UTF-8");
    }




    // ???????????? ?????? ?????? ??????
    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0].toUpperCase() + "_" + getCurrency(exchange, coinData[0], coinData[1]).toUpperCase();
    }

    private boolean isSuccess(JsonObject object) throws Exception{
        if(object.get("success").getAsString().equals("true")){
            return true;
        }
        return false;
    }

    private boolean isAlreadyCanceled(JsonObject object) throws Exception {
        JsonArray array     = object.getAsJsonArray("message");
        JsonElement element = array.get(0);
        if(ALREADY_CANCEL.equals(element.getAsString())){
            return true;
        }else{
            return false;
        }
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
    private void insertLog(String request, LogAction action, String msg){
        exceptionLog.makeLogAndInsert("????????????",request, action, msg);
    }
}
