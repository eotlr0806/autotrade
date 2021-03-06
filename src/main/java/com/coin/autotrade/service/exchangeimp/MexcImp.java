package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.common.enumeration.Trade;
import com.coin.autotrade.model.*;
import com.coin.autotrade.model.mexc.MexcResult;
import com.coin.autotrade.service.CoinService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class MexcImp extends AbstractExchange {
    final private String BUY            = "BID";
    final private String SELL           = "ASK";
    final private long httpTime         = 10;
    final private OkHttpClient OK_HTTP_CLIENT = createOkHttpClient();
    final private ObjectMapper objectMapper   = createObjectMapper();

    private OkHttpClient createOkHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(httpTime, TimeUnit.SECONDS)
                .readTimeout(httpTime,    TimeUnit.SECONDS)
                .writeTimeout(httpTime,   TimeUnit.SECONDS)
                .build();
    }

    private ObjectMapper createObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Override
    public void initClass(AutoTrade autoTrade) throws Exception {
        super.autoTrade = autoTrade;
        setCoinToken(Utils.splitCoinWithId(autoTrade.getCoin()), autoTrade.getExchange());
    }

    @Override
    public void initClass(Liquidity liquidity) throws Exception {
        super.liquidity = liquidity;
        setCoinToken(Utils.splitCoinWithId(liquidity.getCoin()), liquidity.getExchange());
    }

    @Override
    public void initClass(RealtimeSync realtimeSync, CoinService coinService) throws Exception {
        super.realtimeSync = realtimeSync;
        super.coinService = coinService;
        setCoinToken(Utils.splitCoinWithId(realtimeSync.getCoin()), realtimeSync.getExchange());
    }

    @Override
    public void initClass(Fishing fishing, CoinService coinService) throws Exception {
        super.fishing = fishing;
        super.coinService = coinService;
        setCoinToken(Utils.splitCoinWithId(fishing.getCoin()), fishing.getExchange());
    }

    /** ?????? ?????? ?????? ?????? **/
    private void setCoinToken(String[] coinData, Exchange exchange) throws Exception {
        // Set token key
        if(keyList.isEmpty()){
            for (ExchangeCoin exCoin : exchange.getExchangeCoin()) {
                if (exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1])) {
                    keyList.put(PUBLIC_KEY, exCoin.getPublicKey());
                    keyList.put(SECRET_KEY, exCoin.getPrivateKey());
                }
            }

            if (keyList.isEmpty()) {
                String msg = "There is no match coin. " + Arrays.toString(coinData) + " " + exchange.getExchangeCode();
                throw new Exception(msg);
            }
            log.info("[MEXC][SET API KEY] First Key setting in instance API:{}, secret:{}", keyList.get(PUBLIC_KEY), keyList.get(SECRET_KEY));
        }
    }


    @Override
    public int startAutoTrade(String price, String cnt) {
        log.info("[MEXC][AUTOTRADE] START");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try {
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
                cancelOrder(firstOrderId);                      // ?????? ?????? ???, ?????? ??????
                if(Utils.isSuccessOrder(secondOrderId)){
                    cancelOrder(secondOrderId);                 // ?????? ?????? ???, ?????? ??????
                }
            }
        } catch (Exception e) {
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[MEXC][AUTOTRADE] Error : {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[MEXC][AUTOTRADE] END");
        return returnCode;
    }

    /**
     * ??????????????? function
     */
    @Override
    public int startLiquidity(Map list) {
        int returnCode = ReturnCode.SUCCESS.getCode();

        Queue<String> sellQueue   = (LinkedList) list.get("sell");
        Queue<String> buyQueue    = (LinkedList) list.get("buy");
        Queue<String> cancelList = new LinkedList<>();

        try {
            log.info("[MEXC][LIQUIDITY] START");
            String[] coinWithId = Utils.splitCoinWithId(liquidity.getCoin());
            Exchange exchange   = liquidity.getExchange();
            String symbol       = getSymbol(coinWithId, exchange);

            // ?????? ?????????, ?????? ????????? , ?????? ???????????? ?????? ?????? ????????? break;
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
                    cancelOrder(cancelId);
                    Thread.sleep(500);
                }
            }
        } catch (Exception e) {
            returnCode = ReturnCode.SUCCESS.getCode();
            log.error("[MEXC][LIQUIDITY] Error {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[MEXC][LIQUIDITY] END");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String, List> list, int intervalTime) {
        log.info("[MEXC][FISHINGTRADE] START");

        int returnCode = ReturnCode.SUCCESS.getCode();

        try {
            String[] coinWithId = Utils.splitCoinWithId(fishing.getCoin());
            Exchange exchange   = fishing.getExchange();
            String symbol       = getSymbol(coinWithId, exchange);

            // mode ??????
            Trade mode = Trade.valueOf(String.valueOf(list.keySet().toArray()[0]));
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode.getVal());
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            /* Start */
            log.info("[MEXC][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt     = Utils.getRandomString(fishing.getMinContractCnt(), fishing.getMaxContractCnt());
                String orderId = (mode == Trade.BUY) ?
                        createOrder(BUY,  tickPriceList.get(i), cnt, coinWithId, exchange) :
                        createOrder(SELL, tickPriceList.get(i), cnt, coinWithId, exchange);

                if(Utils.isSuccessOrder(orderId)){
                    Map<String, String> orderMap = new HashMap<>();
                    orderMap.put("price", tickPriceList.get(i));
                    orderMap.put("cnt", cnt);
                    orderMap.put("order_id", orderId);
                    orderList.add(orderMap);
                }
            }
            log.info("[MEXC][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");

            /* Sell Start */
            log.info("[MEXC][FISHINGTRADE][START BUY OR SELL TARGET PIECE COIN ]");
            boolean isSameFirstTick = true;    // ?????? ???????????? ????????? ??????/????????? ?????? ????????? ?????? ????????? ?????? ????????? ?????? ????????? ????????? ?????? ?????? ??????
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = Utils.deepCopy(orderList.get(i));
                BigDecimal cnt = new BigDecimal(copiedOrderMap.get("cnt"));

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
                        log.info("[MEXC][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        isSameFirstTick = false;
                        break;
                    }

                    String orderId = (mode == Trade.BUY) ?
                            createOrder(SELL, copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange) :
                            createOrder(BUY,  copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange);

                    if(Utils.isSuccessOrder(orderId)){
                        cnt = cnt.subtract(executionCnt);
                    } else {
                        log.error("[MEXC][FISHINGTRADE] While loop is broken, Because create order is failed");
                        break;
                    }
                }
                // ????????? ?????? ??????
                Thread.sleep(500);
                cancelOrder(orderList.get(i).get("order_id"));
            }
            log.info("[MEXC][FISHINGTRADE][END BUY OR SELL TARGET PIECE COIN ]");
        } catch (Exception e) {
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[MEXC][FISHINGTRADE] Error {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[MEXC][FISHINGTRADE] END");
        return returnCode;
    }

    @Override
    public int startRealtimeTrade(JsonObject realtime, boolean resetFlag) {
        log.info("[MEXC][REALTIME SYNC TRADE] START");
        int returnCode = ReturnCode.SUCCESS.getCode();
        String realtimeChangeRate = "signed_change_rate";

        try {
            boolean isStart = false;
            String[] coinWithId  = Utils.splitCoinWithId(realtimeSync.getCoin());
            Exchange exchange    = realtimeSync.getExchange();
            String symbol        = getSymbol(coinWithId, realtimeSync.getExchange());
            String[] currentTick = getTodayTick(exchange, coinWithId);
            //            String openingPrice  = currentTick[0];
            if(resetFlag){
                realtimeTargetInitRate = currentTick[1];
                log.info("[MEXC][REALTIME SYNC TRADE] Set init open rate : {} ", realtimeTargetInitRate);
            }
            String openingPrice  = realtimeTargetInitRate;
            String currentPrice = currentTick[1];
            log.info("[MEXC][REALTIME SYNC TRADE] open:{}, current:{} ", openingPrice, currentPrice);

            String targetPrice = "";
            String action = "";
            String mode = "";
            String cnt = Utils.getRandomString(realtimeSync.getMinTradeCnt(), realtimeSync.getMaxTradeCnt());

            // 1. ??????/?????? ?????? ????????? ????????? ??????
            int isInRange = isMoreOrLessPrice(currentPrice);
            if (isInRange != 0) {              // ?????? ?????? ??????
                if (isInRange == -1) {         // ??????????????? ?????? ??????
                    mode = UtilsData.MODE_BUY;
                    action = BUY;
                    targetPrice = realtimeSync.getMinPrice();
                } else if (isInRange == 1) {    // ??????????????? ?????? ??????
                    mode = UtilsData.MODE_SELL;
                    action = SELL;
                    targetPrice = realtimeSync.getMaxPrice();
                }
                isStart = true;
            } else {
                // ????????? ?????? ?????? ?????? ?????? ?????? ?????? ????????? ????????????.
                Map<String, String> tradeInfo = getTargetTick(openingPrice, currentPrice, realtime.get(realtimeChangeRate).getAsString());
                if (!tradeInfo.isEmpty()) {
                    targetPrice = tradeInfo.get("price");
                    mode = tradeInfo.get("mode");
                    action = (mode.equals(UtilsData.MODE_BUY)) ? BUY : SELL;
                    isStart = true;
                }
            }

            // 2. %??? ????????? ?????? ??????/?????? ??????
            if (isStart) {
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
                            log.info("[MEXC][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                        Thread.sleep(500);
                    }
                    Thread.sleep(500);
                    // ????????? ?????? ?????? ?????? ?????? ????????? ????????? ????????? ?????? ??????
                    cancelOrder(orderId);
                }
            }
        } catch (Exception e) {
            log.error("[MEXC][REALTIME SYNC TRADE] ERROR :{} ", e.getMessage());
            e.printStackTrace();
        }
        log.info("[MEXC][REALTIME SYNC TRADE] END");
        return returnCode;
    }


    /**
     * ?????? Tick ????????????
     * @param exchange
     * @param coinWithId
     * @return [ ?????? , ?????? ] String Array
     */
    private String[] getTodayTick(Exchange exchange, String[] coinWithId) throws Exception {

        String[] returnRes = new String[2];
        String url = UtilsData.MEXC_TICK+ "&symbol=" + getSymbol(coinWithId, exchange);
        JsonObject response = gson.fromJson(getHttpMethod(url), JsonObject.class);
        JsonArray obj       = response.getAsJsonArray("data").get(0).getAsJsonArray();

        returnRes[0] = obj.get(1).getAsString();
        returnRes[1] = obj.get(2).getAsString();

        return returnRes;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = "";
        try {
            log.info("[MEXC][ORDER BOOK] START");
            String url = UtilsData.MEXC_URL + UtilsData.MEXC_ORDERBOOK + "?symbol=" + getSymbol(coinWithId, exchange) + "&depth=10"; //?symbol=btc_usdt&depth=10
            returnRes = getHttpMethod(url);
            log.info("[MEXC][ORDER BOOK] END");
        } catch (Exception e) {
            log.error("[MEXC][ORDER BOOK] Error {}", e.getMessage());
            e.printStackTrace();
        }
        return returnRes;
    }


    @Override
    public String getBalance(String[] coinData, Exchange exchange) throws Exception{
        String returnValue = ReturnCode.NO_DATA.getValue();;
        setCoinToken(coinData, exchange);


        JsonObject object = new JsonObject();
        String  result = call(UtilsData.MEXC_BALANCE, object, new HashMap<>());
        JsonObject resultJson = gson.fromJson(result, JsonObject.class);
        if(resultJson.get("code").getAsString().equals("200")){
            returnValue = gson.toJson(resultJson.get("data"));
            log.info("[MEXC][GET BALANCE] Success response");
        }else{
            log.error("[MEXC][GET BALANCE] Fail response : {}", result);
        }
        return returnValue;
    }

    @Override
    public String createOrder(String type, String price, String cnt, String[] coinData, Exchange exchange){
        String orderId = ReturnCode.FAIL_CREATE.getValue();
        try {
            setCoinToken(coinData, exchange);
            String symbol = getSymbol(coinData, exchange);
            String action = parseAction(type);

            JsonObject object = new JsonObject();
            object.addProperty("symbol",     symbol);
            object.addProperty("price",      price);
            object.addProperty("quantity",   cnt);
            object.addProperty("order_type", "LIMIT_ORDER");
            object.addProperty("trade_type", action);
            log.info("[MEXC][CREATE ORDER] create order param : {}", gson.toJson(object));

            MexcResult<String> result = call("POST", UtilsData.MEXC_CREATE_ORDER, object, new HashMap<>(),  new TypeReference<MexcResult<String>>() {});
            if (result.getCode() == 200) {
                orderId = result.getData();
                log.info("[MEXC][CREATE ORDER] Success response : {}", objectMapper.writeValueAsString(result));
            } else {
                log.error("[MEXC][CREATE ORDER] Fail response : {}", objectMapper.writeValueAsString(result));
            }
        } catch (Exception e) {
            log.error("[MEXC][CREATE ORDER] Error : {}", e.getMessage());
            e.printStackTrace();
        }
        return orderId;
    }

    private int cancelOrder(String orderId) {
        int returnValue = ReturnCode.FAIL.getCode();
        try {
            Map<String, String> params = new HashMap<>(1);
            params.put("order_ids", orderId);
            MexcResult<Map<String, String>> cancelResult = call("DELETE", UtilsData.MEXC_CANCEL_ORDER, null, params,  new TypeReference<MexcResult<Map<String, String>>>() {});
            if (cancelResult.getCode() == 200) {
                returnValue = ReturnCode.SUCCESS.getCode();
                log.info("[MEXC][CANCEL ORDER] Success cancel response : {}", objectMapper.writeValueAsString(cancelResult));
            }else{
                log.error("[MEXC][CANCEL ORDER] Fail response : {}", objectMapper.writeValueAsString(cancelResult));
            }
        }catch (Exception e){
            log.error("[MEXC][CANCEL ORDER] Error orderId:{}, response:{}",orderId, e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }

    private String call(String uri, Object object, Map<String, String> params) throws Exception{

        params.put("api_key",     keyList.get(PUBLIC_KEY));
        params.put("req_time",    String.valueOf(Instant.now().getEpochSecond()));
        params.put("recv_window", "60");
        params.put("sign",        createSignature("GET", uri, params));

        Request.Builder builder = new Request.Builder().url(UtilsData.MEXC_URL + uri + "?" + toQueryString(params));
        builder.get();

        Request  request  = builder.build();
        Response response = OK_HTTP_CLIENT.newCall(request).execute();
        return response.body().string();
    }

    private <T> T call(String method, String uri, Object object, Map<String, String> params, TypeReference<T> ref) throws Exception{

        params.put("api_key",     keyList.get(PUBLIC_KEY));
        params.put("req_time",    String.valueOf(Instant.now().getEpochSecond()));
        params.put("recv_window", "60");
        params.put("sign",        createSignature(method, uri, params));

        Request.Builder builder = new Request.Builder().url(UtilsData.MEXC_URL + uri + "?" + toQueryString(params));
        if ("POST".equals(method)) {
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), gson.toJson(object));
            builder.post(body);
        } else {
            builder.delete();
        }
        Request  request  = builder.build();
        Response response = OK_HTTP_CLIENT.newCall(request).execute();

        return objectMapper.readValue(response.body().string(), ref);
    }

    private String createSignature(String method, String uri, Map<String, String> params) throws Exception{
        StringBuilder sb = new StringBuilder(1024);
        sb.append(method.toUpperCase()).append('\n')
                .append(uri).append('\n');
        SortedMap<String, String> map = new TreeMap<>(params);
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            sb.append(key).append('=').append(urlEncode(value)).append('&');
        }
        sb.deleteCharAt(sb.length() - 1);

        return actualSignature(sb.toString());
    }

    private String urlEncode(String s) {
        try{
            return URLEncoder.encode(s, "UTF-8").replaceAll("\\+", "%20");
        }catch (Exception e){
            e.printStackTrace();
            return "";
        }
    }

    private String actualSignature(String inputStr) throws Exception{
        Mac hmacSha256 = Mac.getInstance("HmacSHA256");
        SecretKeySpec secKey = new SecretKeySpec(keyList.get(SECRET_KEY).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        hmacSha256.init(secKey);
        byte[] hash = hmacSha256.doFinal(inputStr.getBytes(StandardCharsets.UTF_8));
        return byte2hex(hash);
    }

    private String byte2hex(byte[] b) throws Exception{
        StringBuilder hs = new StringBuilder();
        String temp;
        for (int n = 0; b != null && n < b.length; n++) {
            temp = Integer.toHexString(b[n] & 0XFF);
            if (temp.length() == 1) {
                hs.append('0');
            }
            hs.append(temp);
        }
        return hs.toString();
    }

    private String toQueryString(Map<String, String> params) throws Exception{
        return params.entrySet().stream().map((entry) -> entry.getKey() + "=" + urlEncode(entry.getValue())).collect(Collectors.joining("&"));
    }

    // ???????????? ?????? ?????? ??????
    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0].toUpperCase() + "_" + getCurrency(exchange, coinData[0], coinData[1]).toUpperCase();
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
