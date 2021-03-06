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
import retrofit2.http.HTTP;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
public class FoblGateImp extends AbstractExchange {
    private String USER_ID               = "userId";
    private String SELL                  = "ask";
    private String BUY                   = "bid";
    private String SUCCESS               = "0";
    private String ALREADY_TRADED        = "5004";

    /* Foblgate Function initialize for autotrade */
    @Override
    public void initClass(AutoTrade autoTrade) throws Exception{
        super.autoTrade = autoTrade;
        setApiKey(Utils.splitCoinWithId(autoTrade.getCoin()), autoTrade.getExchange());
    }

    /* Foblgate Function initialize for liquidity */
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

    /* Foblgate Function initialize for fishing */
    @Override
    public void initClass(Fishing fishing, CoinService coinService) throws Exception{
        super.fishing     = fishing;
        super.coinService = coinService;
        setApiKey(Utils.splitCoinWithId(fishing.getCoin()), fishing.getExchange());
    }

    /** ?????? ????????? ????????? API ?????? ???????????? */
    private void setApiKey(String[] coinData, Exchange exchange) throws Exception{

        if(keyList.isEmpty()){
            for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
                if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1])){
                    keyList.put(USER_ID,     exCoin.getExchangeUserId());
                    keyList.put(PUBLIC_KEY,     exCoin.getPublicKey());
                    keyList.put(SECRET_KEY,  exCoin.getPrivateKey());
                }
            }
            log.info("[FOBLGATE][SET API KEY] First Key setting in instance API:{}, secret:{}",keyList.get(PUBLIC_KEY), keyList.get(SECRET_KEY));

            if(keyList.isEmpty()){
                String msg = "There is no match coin. " + Arrays.toString(coinData) + " " + exchange.getExchangeCode();
                throw new Exception(msg);
            }
        }
    }

    /* ??????????????? ???????????? ?????? */
    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[FOBLGATE][AUTOTRADE START]");
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
                Thread.sleep(300);
                String secondOrderId = createOrder(secondAction, price, cnt, coinWithId, exchange);
                Thread.sleep(300);
                cancelOrder(firstOrderId, firstAction, price, symbol);
                if(Utils.isSuccessOrder(secondOrderId)){
                    cancelOrder(secondOrderId, secondAction, price, symbol);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[FOBLGATE][AUTOTRADE] Error : {}", e.getMessage());
        }
        log.info("[FOBLGATE][AUTOTRADE END]");
        return returnCode;
    }

    /** ??????????????? function */
    @Override
    public int startLiquidity(Map list){
        int returnCode = ReturnCode.SUCCESS.getCode();

        Queue<String> sellQueue              = (LinkedList) list.get("sell");
        Queue<String> buyQueue               = (LinkedList) list.get("buy");
        Queue<Map<String,String>> cancelList = new LinkedList<>();

        try{
            log.info("[FOBLGATE][LIQUIDITY] START");
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
                        Map<String, String> cancel = new HashMap<>();
                        cancel.put("orderId", orderId);
                        cancel.put("action",  action);
                        cancel.put("price",   price);
                        cancelList.add(cancel);
                    }
                    Thread.sleep(1000);
                }

                // ?????? ??????
                if(!cancelList.isEmpty() && cancelFlag){
                    Map<String, String> cancelMap = cancelList.poll();
                    String cancelId               = cancelMap.get("orderId");
                    String cancelAction           = cancelMap.get("action");
                    String cancelprice            = cancelMap.get("price");
                    cancelOrder(cancelId, cancelAction, cancelprice, symbol);
                    Thread.sleep(500);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[FOBLGATE][LIQUIDITY] Error {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[FOBLGATE][LIQUIDITY] END");
        return returnCode;
    }

    /* ?????? ?????? */
    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[FOBLGATE][FISHINGTRADE] START");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String[] coinWithId = Utils.splitCoinWithId(fishing.getCoin());
            Exchange exchange   = fishing.getExchange();
            String symbol       = getSymbol(coinWithId, exchange);

            // mode ??????
            Trade mode = Trade.valueOf(String.valueOf(list.keySet().toArray()[0]));
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode.getVal());
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            /* Buy Start */
            log.info("[FOBLGATE][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt     = Utils.getRandomString(fishing.getMinContractCnt(), fishing.getMaxContractCnt());
                String orderId = (mode == Trade.BUY) ?
                        createOrder(BUY,  tickPriceList.get(i), cnt, coinWithId, exchange) :
                        createOrder(SELL, tickPriceList.get(i), cnt, coinWithId, exchange);

                if(Utils.isSuccessOrder(orderId)){
                    Map<String, String> orderMap = new HashMap<>();
                    orderMap.put("order_id" ,orderId);
                    orderMap.put("price"    ,tickPriceList.get(i));
                    orderMap.put("symbol"   ,symbol);
                    orderMap.put("cnt"      ,cnt);
                    orderList.add(orderMap);
                }
                Thread.sleep(500);
            }
            log.info("[FOBLGATE][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");


            /* Sell Start */
            log.info("[FOBLGATE][FISHINGTRADE][START BUY OR SELL TARGET PIECE COIN ]");
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

                    if (!copiedOrderMap.get("price").equals(nowFirstTick)) {
                        log.info("[FOBLGATE][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        isSameFirstTick = false;
                        break;
                    }

                    String orderId = (mode == Trade.BUY) ?
                            createOrder(SELL, copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange) :
                            createOrder(BUY,  copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange);

                    if(Utils.isSuccessOrder(orderId)){
                        cnt = cnt.subtract(executionCnt);
                        Thread.sleep(500);
                        if(mode == Trade.BUY) {
                            cancelOrder(orderId, SELL, copiedOrderMap.get("price"),symbol );
                        }else{
                            cancelOrder(orderId, BUY, copiedOrderMap.get("price"),symbol );
                        }
                    }else{
                        break;
                    }
                }
                // ????????? ??????
                Thread.sleep(500);
                if(mode == Trade.BUY) {
                    cancelOrder(orderList.get(i).get("order_id"), BUY, orderList.get(i).get("price") ,symbol);
                }else{
                    cancelOrder(orderList.get(i).get("order_id"), SELL, orderList.get(i).get("price") ,symbol);
                }
            }
            log.info("[FOBLGATE][FISHINGTRADE][END BUY OR SELL TARGET PIECE COIN ]");
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[FOBLGATE][FISHINGTRADE] ERROR {}", e.getMessage());
            e.printStackTrace();
        }

        log.info("[FOBLGATE][FISHINGTRADE] END");
        return returnCode;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {

        String returnRes = ReturnCode.FAIL.getValue();
        String coin      = coinWithId[0];
        String coinId    = coinWithId[1];
        try{
            // SET API KEY
            setApiKey(coinWithId, exchange);
            String pairName = getSymbol(coinWithId, exchange);

            Map<String, String> header = new HashMap<>();
            header.put("apiKey",keyList.get(PUBLIC_KEY));
            header.put("pairName",pairName);

            String secretHash    = makeApiHash(keyList.get(PUBLIC_KEY) + pairName + keyList.get(SECRET_KEY));
            JsonObject returnVal = postHttpMethod(UtilsData.FOBLGATE_ORDERBOOK, secretHash, header);
            String status        = returnVal.get("status").getAsString();
            if(status.equals(SUCCESS)){
                returnRes = gson.toJson(returnVal);
                log.info("[FOBLGATE][GET ORDER BOOK] SUCCESS");
            }else{
                log.error("[FOBLGATE][GET ORDER BOOK] Fail Response:{}", gson.toJson(returnVal));
            }
        }catch (Exception e){
            log.error("[FOBLGATE][GET ORDER BOOK] Error : {}",e.getMessage());
            e.printStackTrace();
        }
        return returnRes;
    }

    @Override
    public int startRealtimeTrade(JsonObject realtime, boolean resetFlag) {
        log.info("[FOBLGATE][REALTIME SYNC TRADE START]");
        int returnCode   = ReturnCode.SUCCESS.getCode();
        String realtimeChangeRate = "signed_change_rate";

        try {
            boolean isStart      = false;
            String[] coinWithId  = Utils.splitCoinWithId(realtimeSync.getCoin());
            Exchange exchange    = realtimeSync.getExchange();
            String symbol        = getSymbol(coinWithId, exchange);
            String[] currentTick = getTodayTick(symbol);
            //            String openingPrice  = currentTick[0];
            if(resetFlag){
                realtimeTargetInitRate = currentTick[1];
                log.info("[FOBLGATE][REALTIME SYNC TRADE] Set init open rate : {} ", realtimeTargetInitRate);
            }
            String openingPrice  = realtimeTargetInitRate;
            String currentPrice  = currentTick[1];
            log.info("[FOBLGATE][REALTIME SYNC TRADE] open:{}, current:{} ", openingPrice, currentPrice);

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
                            log.info("[FOBLGATE][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                    }

                    // ????????? ?????? ?????? ?????? ?????? ????????? ????????? ????????? ?????? ??????
                    cancelOrder(orderId, action, targetPrice, symbol);
                }
            }
        }catch (Exception e){
            log.error("[FOBLGATE][REALTIME SYNC TRADE] Error :{} ", e.getMessage());
            e.printStackTrace();
        }
        log.info("[FOBLGATE][REALTIME SYNC TRADE END]");
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
        String typeDay       = "1";     // 1??? ?????? ???????????? ????????? ??????
        String min           = "240";   // typeDay ??? 0??? ???????????? ????????????.
        String startDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String dateCount     = "2";     // ?????????(2??? ?????? ??????, 3????????? ???????????? ??????)

        Map<String, String> header = new HashMap<>();
        header.put("apiKey",keyList.get(PUBLIC_KEY));
        header.put("pairName",symbol);
        header.put("type",typeDay);
        header.put("min",min);
        header.put("startDateTime",startDateTime);
        header.put("cnt", dateCount);
        String secretHash    = makeApiHash(keyList.get(PUBLIC_KEY) + symbol + typeDay + min + startDateTime + dateCount + keyList.get(SECRET_KEY));
        JsonObject returnVal = postHttpMethod(UtilsData.FOBLGATE_TICK, secretHash, header);
        String status        = returnVal.get("status").getAsString();
        if(status.equals(SUCCESS)){
            // ????????? ????????? ????????? %??? ???????????????, ?????? ?????? ?????? ???????????? ???.
            // [ ???????????? | ?????? | ?????? | ?????? | ??????(?????????) | volume? ] String Array
            JsonObject object = returnVal.get("data").getAsJsonObject();
            JsonArray array   = object.get("series").getAsJsonArray();
            returnRes[0]      = array.get(0).getAsString().split("\\|")[4];  // ????????? ????????? ????????? ??????
            returnRes[1]      = array.get(1).getAsString().split("\\|")[4];  // ????????? ??????(?????????)
        }else{
            log.error("[FOBLGATE][GET TODAY TICK] Response:{}", gson.toJson(returnVal));
            throw new Exception(gson.toJson(returnVal));
        }

        return returnRes;
    }


    @Override
    public String getBalance(String[] coinData, Exchange exchange) throws Exception{
        String returnValue = ReturnCode.NO_DATA.getValue();;
        setApiKey(coinData, exchange);

        Map<String, String> header = new HashMap<>();
        header.put("mbId",keyList.get(USER_ID));
        header.put("apiKey", keyList.get(PUBLIC_KEY));
        String secretHash = makeApiHash(keyList.get(PUBLIC_KEY) + keyList.get(USER_ID) + keyList.get(SECRET_KEY));

        JsonObject returnVal = postHttpMethod(UtilsData.FOBLGATE_BALANCE, secretHash, header);
        String status        = gson.fromJson(returnVal.get("status"), String.class);
        if(status.equals(SUCCESS)){
            returnValue = gson.toJson(returnVal.get("data"));
            log.info("[FOBLGATE][GET BALANCE] Success response");
        }else{
            log.error("[FOBLGATE][GET BALANCE] Fail response : {}", gson.toJson(returnVal));
        }
        return returnValue;
    }



    @Override
    public String createOrder(String type, String price, String cnt, String[] coinData, Exchange exchange){

        String orderId = ReturnCode.FAIL_CREATE.getValue();
        try{
            setApiKey(coinData, exchange);
            String action = parseAction(type);
            String symbol = getSymbol(coinData, exchange);

            Map<String, String> header = setDefaultRequest(keyList.get(USER_ID), symbol, action, keyList.get(PUBLIC_KEY));
            header.put("price",  price);   // price
            header.put("amount", cnt);     // cnt
            String secretHash = makeApiHash(keyList.get(PUBLIC_KEY) + keyList.get(USER_ID) + symbol + action + price+ cnt+ keyList.get(SECRET_KEY));

            JsonObject returnVal = postHttpMethod(UtilsData.FOBLGATE_CREATE_ORDER, secretHash, header);
            String status        = gson.fromJson(returnVal.get("status"), String.class);
            if(status.equals(SUCCESS)){
                orderId = gson.fromJson(returnVal.get("data"), String.class);
                log.info("[FOBLGATE][CREATE ORDER] Success response : {}", gson.toJson(returnVal));
            }else{
                log.error("[FOBLGATE][CREATE ORDER] Fail response :{}", gson.toJson(returnVal));
            }
        }catch (Exception e){
            log.error("[FOBLGATE][CREATE ORDER] Occur error :{}",e.getMessage());
            e.printStackTrace();
        }
        return orderId;
    }

    /**
     * ?????? ??????
     * @return ?????? ???, ReturnCode.Success. ?????? ???, ReturnCode.Fail
     */
    public int cancelOrder(String ordNo, String type, String price, String symbol){

        int returnCode = ReturnCode.FAIL.getCode();
        try{

            Map<String, String> header = setDefaultRequest(keyList.get(USER_ID), symbol, type, keyList.get(PUBLIC_KEY));
            header.put("ordNo",     ordNo);                 // order Id
            header.put("ordPrice",  price);                 // price
            String secretHash = makeApiHash(keyList.get(PUBLIC_KEY) + keyList.get(USER_ID) + symbol + ordNo + type + price+ keyList.get(SECRET_KEY));

            JsonObject returnVal = postHttpMethod(UtilsData.FOBLGATE_CANCEL_ORDER, secretHash, header);
            String status        = returnVal.get("status").getAsString();
            if(status.equals(SUCCESS) || status.equals(ALREADY_TRADED)){
                returnCode = ReturnCode.SUCCESS.getCode();
                log.info("[FOBLGATE][CANCEL ORDER] Success response:{}", gson.toJson(returnVal));
            }else{
                log.error("[FOBLGATE][CANCEL ORDER] Fail response:{}", gson.toJson(returnVal));
            }
        }catch (Exception e){
            log.error("[FOBLGATE][CANCEL ORDER] Error {}",e.getMessage());
            e.printStackTrace();
        }
        return returnCode;
    }


    /**
     * API ?????? ??? Hash ?????? ????????? ??????
     * @param targetStr
     * @throws Exception ????????? ????????? ????????? ??????????????? ??????.
     */
    private String makeApiHash(String targetStr) throws Exception{
        StringBuffer sb  = new StringBuffer();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(targetStr.getBytes());
        byte byteData[] = md.digest();
        for (int i = 0; i < byteData.length; i++) {
            sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
        }
        //convert the byte to hex format method 2
        StringBuffer hexString = new StringBuffer();
        for (int i=0;i<byteData.length;i++) {
            String hex=Integer.toHexString(0xff & byteData[i]);
            if(hex.length()==1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * HTTP POST Method for Foblgate
     * @param targetUrl  - target url
     * @param secretHash - ???????????? ???
     * @param formData   - post??? ???????????? body ?????????
     * @throws HTTP ?????? ?????????, ?????? ???????????? ?????? ????????? ????????? ?????? ?????????????????? ??????????????? ???????????????.
     */
    private JsonObject postHttpMethod(String targetUrl, String secretHash,  Map<String, String> datas ) throws Exception{

        String twoHyphens    = "--";
        String boundary      = "*******";
        String lineEnd       = "\r\n";
        String delimiter     = twoHyphens + boundary + lineEnd;

        log.info("[FOBLGATE][POST HTTP] Url :{} Request:{}, Secret-Hash:{}",targetUrl, datas.toString(), secretHash);
        URL url = new URL(targetUrl);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestProperty("secretheader", secretHash);
        connection.setRequestProperty("Content-Type","multipart/form-data;boundary="+boundary);
        connection.setRequestProperty("Accept"      , "*/*");
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(UtilsData.TIMEOUT_VALUE);
        connection.setReadTimeout(UtilsData.TIMEOUT_VALUE);
        connection.setDoOutput(true);
        connection.setDoInput(true);
        DataOutputStream dos = new DataOutputStream(connection.getOutputStream());
        for(String key : datas.keySet()){
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\""+ key +"\"" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.writeBytes(datas.get(key));
            dos.writeBytes(lineEnd);
        }
        dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
        dos.flush();
        dos.close();

        StringBuffer response = new StringBuffer();
        if(connection.getResponseCode() == HttpsURLConnection.HTTP_OK){
            BufferedReader br = null;
            if(connection.getInputStream() != null){
                br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            }else if(connection.getErrorStream() != null){
                br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            }else{
                log.error("[FOBLGATE][POST HTTP] Return Code is 200. But inputstream and errorstream is null");
                throw new Exception();
            }
            String inputLine = "";
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();
        }else{
            log.error("[FOBLGATE][POST HTTP] Return code : {}, msg : {}",connection.getResponseCode(), connection.getResponseMessage());
            throw new Exception();
        }

        return gson.fromJson(response.toString(), JsonObject.class);
    }

    /**
     * request??? ????????? Map??? ???????????? ????????? ?????????????????? ???????????? ?????????, ?????? ????????? ???????????? ???????????? ??????
     * @param symbol symbol is pairName coin/currency
     * @param type type is action
     */
    private Map<String, String> setDefaultRequest(String userId, String symbol, String type, String apiKey){
        Map<String, String> mapForRequest = new HashMap<>();
        mapForRequest.put("mbId",userId);
        mapForRequest.put("pairName", symbol);
        mapForRequest.put("action", type);
        mapForRequest.put("apiKey", apiKey);

        return mapForRequest;
    }

    // ???????????? ?????? ?????? ??????
    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0] + "/" + getCurrency(exchange, coinData[0], coinData[1]);
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
