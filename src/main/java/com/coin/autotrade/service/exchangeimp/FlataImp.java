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

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

@Slf4j
public class FlataImp extends AbstractExchange {

    String coinMinCnt                          = "";
    final private String BUY                   = "1";
    final private String SELL                  = "2";
    final private String ALREADY_TRADED        = "30044";

    @Override
    public void initClass(AutoTrade autoTrade) throws Exception{
        super.autoTrade = autoTrade;
    }

    @Override
    public void initClass(Liquidity liquidity) throws Exception{
        super.liquidity = liquidity;
    }

    @Override
    public void initClass(RealtimeSync realtimeSync, CoinService coinService) throws Exception{
        super.realtimeSync = realtimeSync;
        super.coinService  = coinService;
    }

    @Override
    public void initClass(Fishing fishing, CoinService coinService) throws Exception{
        super.fishing     = fishing;
        super.coinService = coinService;
    }

    /**
     * Session key 생성
     * 해당 코인에 등록된 key를 가져와 그 코인에 맞는 session key 생성
     * 하나의 계정에 세션키를 하나만 생성가능하기에, 아래와 같이 static 에 저장된 맵에 저장 함;;
     */
    private String setSessionKey(String userPublicKey, String coinCode, String coinId, Exchange exchange) throws Exception{

        // 해당 계정에 대해 세션 키가 있을 경우 반환
        if(UtilsData.FLATA_SESSION_KEY.get(userPublicKey) != null){
            return UtilsData.FLATA_SESSION_KEY.get(userPublicKey);
        }

        String publicKey     = "";
        String secretKey     = "";
        String returnValue   = "";
        JsonObject header    = new JsonObject();

        if(exchange.getExchangeCoin().size() > 0){
            for(ExchangeCoin coin : exchange.getExchangeCoin()){
                if(coin.getCoinCode().equals(coinCode) && coin.getId() == Long.parseLong(coinId)){
                    publicKey = coin.getPublicKey();
                    secretKey = coin.getPrivateKey();
                    break;
                }
            }
        }
        header.addProperty("acctid",publicKey);
        header.addProperty("acckey",secretKey);
        JsonObject response =  postHttpMethod(UtilsData.FLATA_MAKE_SESSION, gson.toJson(header));
        JsonObject item     =  response.getAsJsonObject("item");
        returnValue         =  item.get("sessionId").getAsString();


        if(header.isJsonNull()){
            log.info("[FLATA][SET SESSION KEY] Error Set session. coin {}: coin id : {}", coinCode, coinId );
            throw new Exception("Key is null");
        }else{
            // 메모리에 저장
            UtilsData.FLATA_SESSION_KEY.put(userPublicKey, returnValue);
            log.info("[FLATA][SET SESSION KEY] First session key {}: mapperedPublicKey : {}", returnValue, userPublicKey );
            return UtilsData.FLATA_SESSION_KEY.get(userPublicKey);
        }
    }

    /**
     * Get Session key
     */
    private String getSessionKey(String[] CoinWithId, Exchange exchange) throws Exception{
        String publicKey  = "";
        String sessionKey = "";

        String coin   = CoinWithId[0];
        String coinId = CoinWithId[1];
        for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
            if(exCoin.getCoinCode().equals(coin) && exCoin.getId() == Long.parseLong(coinId)){
                publicKey = exCoin.getPublicKey();
                break;
            }
        }

        if(publicKey.equals("")){
            log.error("[FLATA][GET SESSION] Public key is null. Coin data : {}", Arrays.toString(CoinWithId));
            throw new Exception("Public Key is null");
        }else{
            sessionKey = setSessionKey(publicKey, coin, coinId, exchange);
        }

        log.info("[FLATA][GET SESSION] Session key : {}", sessionKey );
        return sessionKey;
    }

    /**
     * Start auto trade function
     * @param symbol - coin / currency
     */
    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[FLATA][AUTOTRADE] START");

        int returnCode = ReturnCode.SUCCESS.getCode();
        try{
            String[] coinWithId = Utils.splitCoinWithId(autoTrade.getCoin());
            Exchange exchange   = autoTrade.getExchange();
            String symbol       = getSymbol(coinWithId, exchange);
            String sessionKey   = getSessionKey(coinWithId, exchange);
            Trade mode          = getMode(autoTrade.getMode());
            String firstAction  = (mode == Trade.BUY) ? BUY : SELL;
            String secondAction = (mode == Trade.BUY) ? SELL : BUY;

            String firstOrderId  = createOrder(firstAction,price, cnt, coinWithId, exchange);
            if(Utils.isSuccessOrder(firstOrderId)){
                String secondOrderId = createOrder(secondAction, price, cnt, coinWithId, exchange);
                cancelOrder(firstOrderId, sessionKey);
                if(Utils.isSuccessOrder(secondOrderId)){
                    cancelOrder(secondOrderId, sessionKey);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[FLATA][AUTOTRADE] Error {}", e.getMessage());
            e.printStackTrace();
        }

        log.info("[FLATA][AUTOTRADE] END");
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
            log.info("[FLATA][LIQUIDITY] Start");
            String[] coinWithId = Utils.splitCoinWithId(liquidity.getCoin());
            Exchange exchange   = liquidity.getExchange();
            String sessionKey = getSessionKey(coinWithId, exchange);
            String symbol     = getSymbol(coinWithId, exchange);

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

                // price == "" 면 buy도 sell도 진행할 수 없는 상태기에, order를 하지 않는다.
                if(price != null){
                    orderId = createOrder(action, price, cnt, coinWithId, exchange);
                    if(Utils.isSuccessOrder(orderId)){
                        cancelList.add(orderId);
                    }
                    Thread.sleep(1500);
                }

                // 취소 로직
                if(!cancelList.isEmpty() && cancelFlag){
                    String cancelId = cancelList.poll();
                    cancelOrder(cancelId, sessionKey);
                    Thread.sleep(500);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[FLATA][LIQUIDITY] Error {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[FLATA][LIQUIDITY] END");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[FLATA][FISHINGTRADE START]");

        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String[] coinWithId = Utils.splitCoinWithId(fishing.getCoin());
            Exchange exchange   = fishing.getExchange();
            String sessionKey   = getSessionKey(coinWithId, exchange);
            String symbol       = getSymbol(coinWithId, exchange);

            // mode 처리
            Trade mode = Trade.valueOf(String.valueOf(list.keySet().toArray()[0]));
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode.getVal());
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            /* Start */
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
            }

            /* Sell Start */
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

                    if (!copiedOrderMap.get("price").equals(nowFirstTick)) {
                        log.info("[FLATA][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        isSameFirstTick = false;
                        break;
                    }

                    String orderId = (mode == Trade.BUY) ?
                            createOrder(SELL, copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange) :
                            createOrder(BUY,  copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange);

                    if(Utils.isSuccessOrder(orderId)){
                        cnt = cnt.subtract(executionCnt);
                        Thread.sleep(500);
                        cancelOrder(orderId, sessionKey );
                    }else{
                        break;
                    }
                }
                // 무조건 취소
                Thread.sleep(500);
                cancelOrder(orderList.get(i).get("order_id"), sessionKey);
            }

        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[FLATA][FISHINGTRADE] Error {}", e.getMessage());
            e.printStackTrace();
        }

        log.info("[FLATA][FISHINGTRADE] END");
        return returnCode;
    }

    @Override
    public int startRealtimeTrade(JsonObject realtime, boolean resetFlag) {
        log.info("[FLATA][REALTIME SYNC TRADE] START");
        int returnCode   = ReturnCode.SUCCESS.getCode();
        String realtimeChangeRate = "signed_change_rate";

        try {

            boolean isStart      = false;
            String[] coinWithId  = Utils.splitCoinWithId(realtimeSync.getCoin());
            Exchange exchange    = realtimeSync.getExchange();
            String sessionKey    = getSessionKey(coinWithId, fishing.getExchange());
            String symbol        = getSymbol(coinWithId, exchange);
            String[] currentTick = getTodayTick(symbol);
            //            String openingPrice  = currentTick[0];
            if(resetFlag){
                realtimeTargetInitRate = currentTick[1];
                log.info("[FLATA][REALTIME SYNC TRADE] Set init open rate : {} ", realtimeTargetInitRate);
            }
            String openingPrice  = realtimeTargetInitRate;
            String currentPrice  = currentTick[1];
            log.info("[FLATA][REALTIME SYNC TRADE] open:{}, current:{} ", openingPrice, currentPrice);

            String targetPrice   = "";
            String action        = "";
            String mode          = "";
            String cnt           = Utils.getRandomString(realtimeSync.getMinTradeCnt(), realtimeSync.getMaxTradeCnt());
            int isInRange        = isMoreOrLessPrice(currentPrice);

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
                            log.info("[FLATA][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                    }
                    cancelOrder(orderId, sessionKey);
                }
            }
        }catch (Exception e){
            log.error("[FLATA][REALTIME SYNC TRADE] Error :{} ", e.getMessage());
            e.printStackTrace();
        }
        log.info("[FLATA][REALTIME SYNC TRADE] END");
        return returnCode;
    }


    private String[] getTodayTick(String symbol) throws Exception{

        String[] returnRes   = new String[2];
        String request       = UtilsData.FLATA_TICK + "?symbol=" + symbol;
        String response      = getHttpMethod(request);
        JsonObject resObject = gson.fromJson(response, JsonObject.class);
        // Is success
        if(resObject.has("item")){
            JsonObject data = resObject.get("item").getAsJsonObject();

            BigDecimal percent = data.get("chgRate").getAsBigDecimal().divide(new BigDecimal(100),15, BigDecimal.ROUND_UP);
            BigDecimal current = data.get("current").getAsBigDecimal();
            BigDecimal open    = current.divide(new BigDecimal(1).add(percent),15, BigDecimal.ROUND_UP);  // 소수점 11번째에서 반올림

            returnRes[0] = open.toPlainString();
            returnRes[1] = current.toPlainString();

        }else{
            log.error("[FLATA][GET TODAY TICK] response : {}", response);
            throw new Exception(response);
        }

        return returnRes;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = "";
        try{
            log.info("[FLATA][ORDER BOOK] START");
            String request  = UtilsData.FLATA_ORDERBOOK + "?symbol=" + getSymbol(coinWithId, exchange) + "&level=10";
            returnRes = getHttpMethod(request);
            log.info("[FLATA][ORDER BOOK] END");

        }catch (Exception e){
            log.error("[FLATA][ORDER BOOK] {}",e.getMessage());
            e.printStackTrace();
        }

        return returnRes;
    }

    @Override
    public String createOrder(String type, String price, String cnt, String[] coinData, Exchange exchange){

        String orderId = ReturnCode.FAIL_CREATE.getValue();
        try{
            String sessionKey       = getSessionKey(coinData, exchange);
            String symbol           = getSymbol(coinData, exchange);
            BigDecimal decimalPrice = new BigDecimal(price);
            BigDecimal decimalCnt   = new BigDecimal(cnt);
            BigDecimal total        = decimalPrice.multiply(decimalCnt);
            BigDecimal resultTax    = total.divide(BigDecimal.valueOf(1000));

            double doubleCnt = cutCnt(symbol, cnt);

            JsonObject header = new JsonObject();
            header.addProperty("symbol",symbol);
            header.addProperty("buySellType",parseAction(type));
            header.addProperty("ordPrcType", "2");              // 1은 시장가, 2는 지정가
            header.addProperty("ordPrc",Double.parseDouble(price));
            header.addProperty("ordQty",doubleCnt);
            header.addProperty("ordFee",resultTax.doubleValue());

            String modeKo = (type.equals("1")) ? "BUY":"SELL";
            log.info("[FLATA][CREATE ORDER START] mode {} , valeus {}", modeKo, gson.toJson(header));

            JsonObject response = postHttpMethodWithSession(UtilsData.FLATA_CREATE_ORDER,gson.toJson(header), sessionKey);
            JsonObject item     = response.get("item").getAsJsonObject();
            orderId             = item.get("ordNo").toString();

            if(!orderId.equals("0")){
                log.info("[FLATA][CREATE ORDER] response :{}", gson.toJson(response));
            }else{
                log.error("[FLATA][CREATE ORDER] response :{}", gson.toJson(response));
            }
        }catch (Exception e){
            orderId = ReturnCode.FAIL_CREATE.getValue();
            log.error("[FLATA][CREATE ORDER] {}",e.getMessage());
            e.printStackTrace();
        }
        return orderId;
    }

    private double cutCnt(String symbol, String cnt) throws Exception{
        String minCntFix = getCoinMinCount(symbol);

        int dotIdx = minCntFix.indexOf(".");
        int oneIdx = minCntFix.indexOf("1");
        int length = minCntFix.length();
        int primary = 0;
        double doubleCnt = Double.parseDouble(cnt);
        // 소수
        if(dotIdx < oneIdx){ // 34.232 < 0.01
            primary = oneIdx - dotIdx;  // 소수점 2째 자리.
            int num = (int) Math.pow(10,primary);
            doubleCnt = Math.floor(doubleCnt * num) / num;
        }else{
            primary = dotIdx - 1; // 해당 자리에서 버림
            int num = ((int) Math.pow(10,primary) == 0) ? 1 : (int) Math.pow(10,primary);
            doubleCnt = Math.floor(doubleCnt / num) * num;
        }
        return doubleCnt;
    }


    /* 주문 취소 function */
    public int cancelOrder(String orderId, String sessionKey){
        int returnVal = ReturnCode.FAIL.getCode();
        try{
            JsonObject header = new JsonObject();
            header.addProperty("orgOrdNo", orderId);

            JsonObject response    = postHttpMethodWithSession(UtilsData.FLATA_CANCEL_ORDER, gson.toJson(header), sessionKey);
            JsonObject item        = response.get("item").getAsJsonObject();
            String returnOrderId   = item.get("ordNo").getAsString();

            String returnMeesageNo = "";
            if(item.has("messageNo")){
                returnMeesageNo = item.get("messageNo").getAsString();
            }

            if( (!returnOrderId.equals("0") && !"".equals(returnOrderId)) || returnMeesageNo.equals(ALREADY_TRADED)){
                returnVal = ReturnCode.SUCCESS.getCode();
                log.info("[FLATA][CANCEL ORDER] response : {}",  gson.toJson(item));
            }else{
                log.error("[FLATA][CANCEL ORDER] response : {}",  gson.toJson(item));
            }

        }catch (Exception e){
            log.error("[FLATA][CANCEL ORDER] {}", e.getMessage());
            e.printStackTrace();
        }

        return returnVal;
    }


    /* 해당 코인의 최소 매수/매도 단위 조회 */
    private String getCoinMinCount(String symbol) throws Exception {

        // 과거에 값을 부여한 케이스가 있다면 그걸 쓰면됨
        if(!coinMinCnt.equals("")){
            log.info("[FLATA][GET COIN INFO] Coin is saved. coin cnt:{}", coinMinCnt);
        }else{
            log.info("[FLATA][GET COIN INFO] Coin is not saved. Start getting coin info. Symbol:{}", symbol);

            String request  = UtilsData.FLATA_COININFO + "?symbol=" + symbol + "&lang=ko";
            String res = getHttpMethod(request);

            JsonObject object = gson.fromJson(res, JsonObject.class);
            JsonArray array   = object.get("record").getAsJsonArray();
            JsonObject data   = array.get(0).getAsJsonObject();

            coinMinCnt = data.get("ordUnitQty").getAsString();
            log.info("[FLATA][GET COIN INFO] coinMinCnt {} ",  coinMinCnt);
        }
        return coinMinCnt;
    }


    /* HTTP POST Method for coinone */
    private JsonObject postHttpMethod(String targetUrl, String payload) {
        URL url;
        String inputLine;
        JsonObject returnObj = null;

        try{
            url = new URL(targetUrl);

            HttpURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");

            // Writing the post data to the HTTP request body
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
            bw.write(payload);
            bw.close();

            connection.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer response = new StringBuffer();
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();
            returnObj = gson.fromJson(response.toString(), JsonObject.class);

        }catch(Exception e){
            log.error("[FLATA][FLATA POST HTTP] Error {}", e.getMessage());
            e.printStackTrace();
        }

        return returnObj;
    }


    private JsonObject postHttpMethodWithSession(String targetUrl, String payload, String SessionKey) {
        URL url;
        String inputLine;
        JsonObject returnObj = null;

        try{
            log.info("[FLATA][HTTP POST] request {}", payload);

            url = new URL(targetUrl);

            HttpURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(UtilsData.TIMEOUT_VALUE);
            connection.setReadTimeout(UtilsData.TIMEOUT_VALUE);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Session", SessionKey);

            // Writing the post data to the HTTP request body
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
            bw.write(payload);
            bw.close();

            connection.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer response = new StringBuffer();
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();
            returnObj = gson.fromJson(response.toString(), JsonObject.class);

        }catch(Exception e){
            log.error("[FLATA][HTTP POST] Error {}", e.getMessage());
            e.printStackTrace();
        }

        return returnObj;
    }

    // 거래소에 맞춰 심볼 반환
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
