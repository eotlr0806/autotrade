package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.enumeration.LogAction;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.common.enumeration.Trade;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class DcoinImp extends AbstractExchange {
    final private String BUY                  = "BUY";
    final private String SELL                 = "SELL";
    final private String SUCCESS              = "0";
    final private String LOCK                 = "250";
    final private String SUCCESS_CANCEL       = "2";

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


    /**
     * Auto Trade Start
     */
    @Override
    public int startAutoTrade(String price, String cnt){

        log.info("[DCOIN][AUTOTRADE] START");

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
                cancelOrder(symbol, firstOrderId);
                if(Utils.isSuccessOrder(secondOrderId)){
                    cancelOrder(symbol, secondOrderId);
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

    /* ??????????????? ????????? */
    @Override
    public int startLiquidity(Map list){
        int returnCode = ReturnCode.SUCCESS.getCode();

        Queue<String> sellQueue  = (LinkedList) list.get("sell");
        Queue<String> buyQueue   = (LinkedList) list.get("buy");
        Queue<String> cancelList = new LinkedList<>();

        try{
            log.info("[DCOIN][LIQUIDITY] START");
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
            String[] coinWithId = Utils.splitCoinWithId(fishing.getCoin());
            Exchange exchange   = fishing.getExchange();
            String symbol       = getSymbol(coinWithId, exchange);

            // mode ??????
            Trade mode = Trade.valueOf(String.valueOf(list.keySet().toArray()[0]));
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode.getVal());
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            /* Start */
            log.info("[DCOIN][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
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
            log.info("[DCOIN][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");

            /* Sell Start */
            log.info("[DCOIN][FISHINGTRADE][START BUY OR SELL TARGET PIECE COIN ]");
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
                        break;
                    }
                }
                // ????????? ??????
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
    public int startRealtimeTrade(JsonObject realtime, boolean resetFlag) {
        log.info("[DCOIN][REALTIME SYNC TRADE START]");
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
                log.info("[DCOIN][REALTIME SYNC TRADE] Set init open rate : {} ", realtimeTargetInitRate);
            }
            String openingPrice  = realtimeTargetInitRate;
            String currentPrice  = currentTick[1];
            log.info("[DCOIN][REALTIME SYNC TRADE] open:{}, current:{} ", openingPrice, currentPrice);

            String targetPrice   = "";
            String action        = "";
            String mode          = "";
            String cnt           = Utils.getRandomString(realtimeSync.getMinTradeCnt(), realtimeSync.getMaxTradeCnt());

            int isInRange = isMoreOrLessPrice(currentPrice);
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
     * ?????? Tick ????????????
     * @param exchange
     * @param coinWithId
     * @return [ ?????? , ?????? ] String Array
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
        }else{
            log.error("[DCOIN][GET TODAY TICK] response : {}", response);
            throw new Exception(response);
        }

        return returnRes;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = ReturnCode.FAIL.getValue();
        try{
            String symbol      = getSymbol(coinWithId, exchange);
            String encodedData = "symbol=" + URLEncoder.encode(symbol) + "&type=" + URLEncoder.encode("step0");
            JsonObject json    = gson.fromJson(getHttpMethod(UtilsData.DCOIN_ORDERBOOK + "?" + encodedData), JsonObject.class);

            if(SUCCESS.equals(json.get("code").getAsString())){
                returnRes = gson.toJson(json);
                log.info("[DCOIN][GET BALANCE] Success response");
            }else{
                log.error("[DCOIN][GET BALANCE] Fail response : {}", gson.toJson(json));
                insertLog(gson.toJson(json), LogAction.ORDER_BOOK, gson.toJson(json));
            }
        }catch (Exception e){
            log.error("[DCOIN][ORDER BOOK] {}",e.getMessage());
            insertLog("", LogAction.BALANCE, e.getMessage());
        }

        return returnRes;
    }


    @Override
    public String getBalance(String[] coinData, Exchange exchange) throws Exception{
        String returnValue = ReturnCode.NO_DATA.getValue();
        setApiKey(coinData, exchange);
        // DCoin ??? ??????, property ????????? ?????????????????? ???????????? ??????, ?????? ????????? ?????? ?????????.
        JsonObject header = new JsonObject();
        header.addProperty("api_key", keyList.get(PUBLIC_KEY));
        String request       = UtilsData.DCOIN_BALANCE + "?api_key=" + URLEncoder.encode(keyList.get(PUBLIC_KEY))
                                                       + "&sign=" + createSign(gson.toJson(header));
        String response      = getHttpMethod(request);
        JsonObject resObject = gson.fromJson(response, JsonObject.class);
        String returnCode    = resObject.get("code").getAsString();
        if(SUCCESS.equals(returnCode)){
            returnValue = gson.toJson(resObject.get("data").getAsJsonObject().getAsJsonArray("coin_list"));
            log.info("[DCOIN][GET BALANCE] Success response");
        }else{
            log.error("[DCOIN][GET BALANCE] Fail response : {}", gson.toJson(resObject));
            insertLog(request, LogAction.BALANCE, response);
        }

        return returnValue;
    }

    @Override
    public String createOrder(String type, String price, String cnt, String[] coinData, Exchange exchange) {

        String orderId   = ReturnCode.FAIL_CREATE.getValue();

        try {
            setApiKey(coinData, exchange);
            // DCoin ??? ??????, property ????????? ?????????????????? ???????????? ??????, ?????? ????????? ?????? ?????????.
            String symbol = getSymbol(coinData, exchange);
            Map<String, String> header    = new LinkedHashMap();
            header.put("api_key", keyList.get(PUBLIC_KEY));
            header.put("price",   price);
            header.put("side",    parseAction(type));
            header.put("symbol",  symbol.toLowerCase());
            header.put("type",    "1"); // 1: ?????????, 2:?????????
            header.put("volume",  cnt);
            header.put("sign",    createSign(gson.toJson(header)));

            String params   = makeEncodedParas(header);
            JsonObject json = postHttpMethod(UtilsData.DCOIN_CREATE_ORDER, params);
            String returnCode   = json.get("code").getAsString();
            if (SUCCESS.equals(returnCode)) {
                JsonObject dataObj = json.get("data").getAsJsonObject();
                orderId            = dataObj.get("order_id").getAsString();
                log.info("[DCOIN][CREATE ORDER] response :{}", gson.toJson(json));
            } else {
                log.error("[DCOIN][CREATE ORDER] response {}", gson.toJson(json));
                insertLog(gson.toJson(header), LogAction.CREATE_ORDER, returnCode);
            }
            Thread.sleep(600);
        }catch(Exception e){
            log.error("[DCOIN][CREATE ORDER] Error {}", e.getMessage());
            insertLog("", LogAction.CREATE_ORDER, e.getMessage());
        }
        return orderId;
    }

    /**
     * ??????/?????? ?????? ?????? ??????
     * @param symbol   - coin + currency
     */
    public int cancelOrder(String symbol, String orderId) {
        int returnValue = ReturnCode.FAIL.getCode();
        try {
            Map<String, String> header = new LinkedHashMap<>();
            header.put("api_key",  keyList.get(PUBLIC_KEY));
            header.put("order_id", orderId);
            header.put("symbol",   symbol.toLowerCase());
            header.put("sign",     createSign(gson.toJson(header)));

            JsonObject json   = postHttpMethod(UtilsData.DCOIN_CANCEL_ORDER, makeEncodedParas(header));
            String returnCode = json.get("code").getAsString();
            if (SUCCESS.equals(returnCode) || SUCCESS_CANCEL.equals(returnCode)) {
                returnValue = ReturnCode.SUCCESS.getCode();
                log.info("[DCOIN][CANCEL ORDER] SUCCESS CANCEL:{}", gson.toJson(json));
            } else if(LOCK.equals(returnCode)){
                log.error("[DCOIN][CANCEL ORDER] LOCK CANCEL.. RETRY SOON");
                Thread.sleep(65000);
                JsonObject reAgainJson = postHttpMethod(UtilsData.DCOIN_CANCEL_ORDER, makeEncodedParas(header));
                log.error("[DCOIN][CANCEL ORDER] CANCEL AGAIN:{}", gson.toJson(reAgainJson));
                insertLog(gson.toJson(header), LogAction.CANCEL_ORDER, returnCode);
            } else {
                log.error("[DCOIN][CANCEL ORDER] FAIL CANCEL:{}", gson.toJson(json));
                insertLog(gson.toJson(header), LogAction.CANCEL_ORDER, returnCode);
            }
            Thread.sleep(600);
        }catch(Exception e){
            log.error("[DCOIN][CANCEL ORDER] Error: {}", e.getMessage());
            insertLog("", LogAction.CANCEL_ORDER, e.getMessage());
        }
        return returnValue;
    }

    /** ???????????? ??? ?????? */
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

    private String makeEncodedParas(Map<String, String> header) throws Exception {
        return header.keySet().stream()
                .map(key -> key + "=" + header.get(key))
                .collect(Collectors.joining("&"));
    }

    /* HTTP POST Method for coinone */
    private JsonObject postHttpMethod(String targetUrl, String payload) throws Exception{
        log.info("[DCOIN][POST HTTP] url : {}, payload : {}", targetUrl, payload);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
        HttpEntity<String> response = restTemplate.exchange(
                targetUrl,
                HttpMethod.POST,
                new HttpEntity<String>(payload, headers),
                String.class
        );

        return gson.fromJson(response.getBody(), JsonObject.class);
    }


    // ???????????? ?????? ?????? ??????
    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0].toLowerCase() + getCurrency(exchange,coinData[0], coinData[1]);
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
        }
        return false;
    }

    private void insertLog(String request, LogAction action, String msg){
        exceptionLog.makeLogAndInsert("?????????",request, action, msg);
    }
}
