package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.enumeration.LogAction;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.common.enumeration.Trade;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
import java.util.stream.Collectors;

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

    /** ?????? ?????? ?????? ?????? **/
    private void setCoinToken(String[] coinData, Exchange exchange) throws Exception{
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
        log.info("[BITHUMBGLOBAL][AUTOTRADE] START");
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

                Thread.sleep(500);
                cancelOrder(firstOrderId, symbol);                      // ?????? ?????? ???, ?????? ??????
                if(Utils.isSuccessOrder(secondOrderId)){
                    cancelOrder(secondOrderId, symbol);                 // ?????? ?????? ???, ?????? ??????
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[BITHUMBGLOBAL][AUTOTRADE] Error : {}", e.getMessage());
        }

        log.info("[BITHUMBGLOBAL][AUTOTRADE] End");
        return returnCode;
    }

    @Override
    public int startLiquidity(Map<String, LinkedList<String>> list){
        int returnCode = ReturnCode.SUCCESS.getCode();

        Queue<String> sellQueue  = list.get("sell");
        Queue<String> buyQueue   = list.get("buy");
        Queue<String> cancelList = new LinkedList<>();

        try{
            log.info("[BITHUMBGLOBAL][LIQUIDITY] START");
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

                // price == "" ??? buy??? sell??? ????????? ??? ?????? ????????????, order??? ?????? ?????????.
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
                    cancelOrder(cancelId, symbol);
                    Thread.sleep(500);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[BITHUMBGLOBAL][LIQUIDITY] Error {}", e.getMessage());
        }
        log.info("[BITHUMBGLOBAL][LIQUIDITY] END");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[BITHUMBGLOBAL][FISHINGTRADE] START");

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
            log.info("[BITHUMBGLOBAL][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
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
            log.info("[BITHUMBGLOBAL][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");


            /* Sell Start */
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
                        log.info("[BITHUMBGLOBAL][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        isSameFirstTick = false;
                        break;
                    }

                    String orderId = (mode == Trade.BUY) ?
                                createOrder(SELL, copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange) :
                                createOrder(BUY,  copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange);

                    if(Utils.isSuccessOrder(orderId)){
                        cnt = cnt.subtract(executionCnt);
                    }else{
                        log.error("[BITHUMBGLOBAL][FISHINGTRADE] While loop is broken, Because create order is failed");
                        break;
                    }
                }
                // ????????? ?????? ??????
                Thread.sleep(1000);
                cancelOrder(orderList.get(i).get("order_id"), symbol);
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[BITHUMBGLOBAL][FISHINGTRADE] Error {}", e.getMessage());
        }

        log.info("[BITHUMBGLOBAL][FISHINGTRADE] END");
        return returnCode;
    }

    @Override
    public int startRealtimeTrade(JsonObject realtime, boolean resetFlag) {
        log.info("[BITHUMBGLOBAL][REALTIME SYNC TRADE] START");
        int returnCode   = ReturnCode.SUCCESS.getCode();
        String realtimeChangeRate = "signed_change_rate";

        try {
            boolean isStart      = false;
            String[] coinWithId  = Utils.splitCoinWithId(realtimeSync.getCoin());
            Exchange exchange    = realtimeSync.getExchange();
            String symbol        = getSymbol(coinWithId, realtimeSync.getExchange());
            String[] currentTick = getTodayTick(symbol);
//            String openingPrice  = currentTick[0];

            if(resetFlag){
                realtimeTargetInitRate = currentTick[1];
                log.info("[BITHUMBGLOBAL][REALTIME SYNC TRADE] Set init open rate : {} ", realtimeTargetInitRate);
            }
            String openingPrice  = realtimeTargetInitRate;
            String currentPrice  = currentTick[1];
            log.info("[BITHUMBGLOBAL][REALTIME SYNC TRADE] open:{}, current:{} ", openingPrice, currentPrice);

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
                            log.info("[BITHUMBGLOBAL][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                    }
                    cancelOrder(orderId,symbol);
                }
            }
        }catch (Exception e){
            log.error("[BITHUMBGLOBAL][REALTIME SYNC TRADE] Error :{} ", e.getMessage());
        }
        log.info("[BITHUMBGLOBAL][REALTIME SYNC TRADE] END");
        return returnCode;
    }


    /**
     * ?????? Tick ????????????
     * @param exchange
     * @param coinWithId
     * @return [ ?????? , ?????? ] String Array
     */
    private String[] getTodayTick(String symbol) throws Exception{

        String[] returnRes   = new String[2];
        String request       = UtilsData.BITHUMB_GLOBAL_TICK + "?symbol=" + URLEncoder.encode(symbol);
        String response      = getHttpMethod(request);
        JsonObject resObject = gson.fromJson(response, JsonObject.class);
        String returnCode    = resObject.get("code").getAsString();
        if(SUCCESS.equals(returnCode)){
            JsonObject data    = resObject.get("data").getAsJsonArray().get(0).getAsJsonObject();
            BigDecimal percent = data.get("p").getAsBigDecimal();
            BigDecimal current = data.get("c").getAsBigDecimal();
            BigDecimal open    = current.divide(new BigDecimal(1).add(percent),10, BigDecimal.ROUND_UP);  // ????????? 11???????????? ?????????

            returnRes[0] = open.toPlainString();
            returnRes[1] = current.toPlainString();
        }else{
            log.error("[BITHUMBGLOBAL][GET TODAY TICK] response : {}", response);
            throw new Exception(response);
        }

        return returnRes;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        log.info("[BITHUMBGLOBAL][ORDER BOOK] START");
        String returnRes = ReturnCode.FAIL.getValue();

        try{
            String symbol  = getSymbol(coinWithId,exchange);
            String request = UtilsData.BITHUMB_GLOBAL_ORDERBOOK + "?symbol=" + symbol;
            returnRes = getHttpMethod(request);
            String status = gson.fromJson(returnRes, JsonObject.class).get("code").getAsString();
            if (!status.equals(SUCCESS)){
                insertLog(request, LogAction.ORDER_BOOK, returnRes);
                log.error("[BITHUMBGLOBAL][ORDER BOOK] Response:{}", returnRes);
                returnRes = ReturnCode.FAIL.getValue();
            }
        }catch (Exception e){
            log.error("[BITHUMBGLOBAL][ORDER BOOK] Error {}", e.getMessage());
            insertLog(Arrays.toString(coinWithId),LogAction.ORDER_BOOK,e.getMessage());
        }

        log.info("[BITHUMBGLOBAL][ORDER BOOK] END");
        return returnRes;
    }


    @Override
    public String getBalance(String[] coinData, Exchange exchange) throws Exception{
        String returnValue = ReturnCode.NO_DATA.getValue();;

        setCoinToken(coinData, exchange);
        Map<String, String> header = new LinkedHashMap<>();
        header.put("apiKey", keyList.get(PUBLIC_KEY));
        header.put("assetType", "spot");
        header.put("timestamp", String.valueOf(System.currentTimeMillis()));
        header.put("signature", setSignature(header));

        JsonObject returnVal = postHttpMethod(UtilsData.BITHUMB_GLOBAL_BALANCE, mapper.writeValueAsString(header));
        String status        = returnVal.get("code").getAsString();
        if(status.equals(SUCCESS)){
            returnValue = gson.toJson(returnVal.get("data"));
            log.info("[BITHUMBGLOBAL][GET BALANCE] Response");
        }else{
            log.error("[BITHUMBGLOBAL][GET BALANCE] Response :{}", gson.toJson(returnVal));
            insertLog(gson.toJson(header), LogAction.BALANCE, gson.toJson(returnVal));
        }
        return returnValue;
    }


    @Override
    public String createOrder(String type, String price, String cnt, String[] coinData, Exchange exchange){
        String response = ReturnCode.FAIL_CREATE.getValue();
        try{
            String cutCnt = setCutCoinCnt(getSymbol(coinData, exchange), cnt);
            setCoinToken(coinData, exchange);
            String action     = parseAction(type);

            Map<String, String> header = new LinkedHashMap<>();
            header.put("apiKey", keyList.get(PUBLIC_KEY));
            header.put("msgNo", String.valueOf(System.currentTimeMillis()));
            header.put("price", price);
            header.put("quantity", cutCnt);
            header.put("side", action);
            header.put("symbol", getSymbol(coinData, exchange));
            header.put("timestamp", String.valueOf(System.currentTimeMillis()));
            header.put("type", "limit");
            header.put("signature", setSignature(header));

            JsonObject returnVal = postHttpMethod(UtilsData.BITHUMB_GLOBAL_CREATE_ORDER, mapper.writeValueAsString(header));
            String status        = returnVal.get("code").getAsString();
            if(status.equals(SUCCESS)){
                JsonObject obj  = returnVal.get("data").getAsJsonObject();
                response        = obj.get("orderId").getAsString();
                log.info("[BITHUMBGLOBAL][CREATE ORDER] Response : {}", gson.toJson(returnVal));
            }else{
                insertLog(gson.toJson(header), LogAction.CREATE_ORDER, gson.toJson(returnVal));
                log.error("[BITHUMBGLOBAL][CREATE ORDER] Response :{}", gson.toJson(returnVal));
            }
        }catch (Exception e){
            insertLog("", LogAction.CREATE_ORDER, e.getMessage());
            log.error("[BITHUMBGLOBAL][CREATE ORDER] Error {}",e.getMessage());
        }
        return response;
    }

    /* Bithumb global ?????? ?????? */
    public int cancelOrder(String orderId, String symbol) {

        int returnValue = ReturnCode.FAIL.getCode();

        try {
            Map<String, String> header = new LinkedHashMap<>();
            header.put("apiKey", keyList.get(PUBLIC_KEY));
            header.put("msgNo", String.valueOf(System.currentTimeMillis()));
            header.put("orderId", orderId);
            header.put("symbol", symbol);
            header.put("timestamp", String.valueOf(System.currentTimeMillis()));
            header.put("signature", setSignature(header));

            JsonObject json = postHttpMethod(UtilsData.BITHUMB_GLOBAL_CANCEL_ORDER, mapper.writeValueAsString(header));
            String status   = json.get("code").getAsString();
            if (status.equals(SUCCESS) || status.equals(SUCCESS_CANCEL)) {
                returnValue = ReturnCode.SUCCESS.getCode();
                log.info("[BITHUMBGLOBAL][CANCEL ORDER] Response:{}", gson.toJson(json));
            } else {
                insertLog(gson.toJson(header), LogAction.CANCEL_ORDER, gson.toJson(json));
                log.error("[BITHUMBGLOBAL][CANCEL ORDER] Response:{}", gson.toJson(json));
            }
        }catch(Exception e){
            log.error("[BITHUMBGLOBAL][CANCEL ORDER] Error {}", e.getMessage());
            insertLog("", LogAction.CANCEL_ORDER, e.getMessage());
        }
        return returnValue;
    }

    private JsonObject postHttpMethod(String targetUrl, String payload) throws Exception{
        log.info("[BITHUMBGLOBAL][POST HTTP] start request");
        log.info("[BITHUMBGLOBAL][POST HTTP] targetUrl : {} , request : {}",targetUrl, payload);

        HttpEntity<String> response = restTemplate.exchange(
                targetUrl,
                HttpMethod.POST,
                new HttpEntity<String>(payload, getHeader(null)),
                String.class
        );
        log.info("[BITHUMBGLOBAL][POST HTTP] end request");
        return gson.fromJson(response.getBody(), JsonObject.class);
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

    /* Cnt ??? ????????? ???????????? ????????? ????????? ?????? */
    private String setCutCoinCnt(String symbol, String cnt){
        int dot = 0;    // ?????? ????????? BTC??? ?????? 0
        if(!symbol.split("-")[1].equals("BTC")) {
            dot = 1;    // ???????????? 1
        }

        double doubleCnt = Double.parseDouble(cnt);
        int pow          = (dot == 0) ? 1 : (int) Math.pow(10,dot);

        return String.valueOf(Math.floor(doubleCnt * pow) / pow);
    }

    private String setSignature(Map<String, String> header) throws Exception{
        String sign = header.keySet().stream()
                .map(key -> key+"="+header.get(key))
                .collect(Collectors.joining("&"));
        return getHmacSha256(sign);
    }

    // ???????????? ?????? ?????? ??????
    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0] + "-" + getCurrency(exchange,coinData[0], coinData[1]);
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
        exceptionLog.makeLogAndInsert("???????????????",request, action, msg);
    }

}
