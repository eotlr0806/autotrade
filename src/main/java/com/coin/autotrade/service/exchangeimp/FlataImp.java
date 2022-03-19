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
            String[] coinData   = Utils.splitCoinWithId(autoTrade.getCoin());
            String sessionKey   = getSessionKey(coinData, autoTrade.getExchange());
            String symbol       = getSymbol(coinData, autoTrade.getExchange());
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
            if(!(orderId = createOrder(firstAction,price, cnt, symbol, sessionKey)).equals(ReturnCode.NO_DATA.getValue())){
                if(createOrder(secondAction,price, cnt, symbol, sessionKey).equals(ReturnCode.NO_DATA.getValue())){          // SELL 모드가 실패 시,
                    cancelOrder(orderId, sessionKey);
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
            String[] coinData = Utils.splitCoinWithId(liquidity.getCoin());
            String sessionKey = getSessionKey(coinData, liquidity.getExchange());
            String symbol     = getSymbol(coinData, liquidity.getExchange());

            while (!sellQueue.isEmpty() || !buyQueue.isEmpty() || !cancelList.isEmpty()) {
                String mode           = (Utils.getRandomInt(1, 2) == 1) ? UtilsData.MODE_BUY : UtilsData.MODE_SELL;
                boolean cancelFlag    = (Utils.getRandomInt(1, 2) == 1) ? true : false;
                String orderId        = ReturnCode.NO_DATA.getValue();
                String price          = "";
                String action         = "";
                String cnt            = Utils.getRandomString(liquidity.getMinCnt(), liquidity.getMaxCnt());

                if (!buyQueue.isEmpty() && mode.equals(UtilsData.MODE_BUY)) {
                    price   = buyQueue.poll();
                    action  = BUY;
                } else if (!sellQueue.isEmpty() && mode.equals(UtilsData.MODE_SELL)) {
                    price   = sellQueue.poll();
                    action  = SELL;
                }
                // 매수 로직
                if(!action.equals("")){
                    orderId = createOrder(action, price, cnt, symbol, sessionKey);
                    if(!orderId.equals(ReturnCode.NO_DATA.getValue())){
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
            String[] coinData = Utils.splitCoinWithId(fishing.getCoin());
            String sessionKey = getSessionKey(coinData, fishing.getExchange());
            String symbol     = getSymbol(coinData, fishing.getExchange());

            // mode 처리
            String mode = fishing.getMode();
            if(UtilsData.MODE_RANDOM.equals(mode)){
                mode = (Utils.getRandomInt(0,1) == 0) ? UtilsData.MODE_BUY : UtilsData.MODE_SELL;
            }

            boolean noIntervalFlag   = true;    // 해당 플래그를 이용해 마지막 매도/매수 후 바로 intervalTime 없이 바로 다음 매수/매도 진행
            boolean noMatchFirstTick = true;    // 해당 플래그를 이용해 매수/매도를 올린 가격이 현재 최상위 값이 맞는지 다른 사람의 코인을 사지 않게 방지

            for(String temp : list.keySet()){  mode = temp; }
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode);
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            /* Start */
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt     = Utils.getRandomString(fishing.getMinContractCnt(), fishing.getMaxContractCnt());
                String orderId = ReturnCode.NO_DATA.getValue();
                if(UtilsData.MODE_BUY.equals(mode)) {
                    orderId = createOrder(BUY, tickPriceList.get(i), cnt, symbol, sessionKey);
                }else{
                    orderId = createOrder(SELL, tickPriceList.get(i), cnt, symbol, sessionKey);
                }
                if(!orderId.equals(ReturnCode.NO_DATA.getValue())){                                                // 매수/매도가 정상적으로 이뤄졌을 경우 데이터를 list에 담는다
                    Map<String, String> orderMap = new HashMap<>();
                    orderMap.put("order_id" ,orderId);
                    orderMap.put("price"    ,tickPriceList.get(i));
                    orderMap.put("symbol"   ,symbol);
                    orderMap.put("cnt"      ,cnt);
                    orderList.add(orderMap);
                }
            }

            /* Sell Start */
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = Utils.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("cnt"));

                while (cnt.compareTo(new BigDecimal("0")) > 0) {
                    if (!noMatchFirstTick) break;                   // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                    if (noIntervalFlag) Thread.sleep(intervalTime); // intervalTime 만큼 휴식 후 매수 시작
                    String orderId            = ReturnCode.NO_DATA.getValue();
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

                    if (!copiedOrderMap.get("price").equals(nowFirstTick)) {
                        log.info("[FLATA][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        noMatchFirstTick = false;
                        break;
                    }

                    if(UtilsData.MODE_BUY.equals(mode)) {
                        orderId = createOrder(SELL, copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol, sessionKey);
                    }else{
                        orderId = createOrder(BUY, copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol, sessionKey);
                    }

                    if(!orderId.equals(ReturnCode.NO_DATA.getValue())){
                        cnt = cnt.subtract(cntForExcution);
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
            String sessionKey    = getSessionKey(coinWithId, fishing.getExchange());
            String symbol        = getSymbol(coinWithId, realtimeSync.getExchange());
            String[] currentTick = getTodayTick(symbol);
            //            String openingPrice  = currentTick[0];
            if(resetFlag){
                realtimeTargetInitRate = currentTick[1];
                log.info("[FLATA][REALTIME SYNC TRADE] Set init open rate : {} ", realtimeTargetInitRate);
            }
            String openingPrice  = realtimeTargetInitRate;
            String currentPrice  = currentTick[1];
            log.info("[FLATA][REALTIME SYNC TRADE] open:{}, current:{} ", openingPrice, currentPrice);

            String orderId       = ReturnCode.NO_DATA.getValue();
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
                if( !(orderId = createOrder(action, targetPrice, cnt, symbol, sessionKey)).equals(ReturnCode.NO_DATA.getValue())){    // 매수/OrderId가 있으면 성공

                    Thread.sleep(300);

                    // 3. bestoffer set 로직
                    JsonArray array = makeBestofferAfterRealtimeSync(targetPrice, mode);
                    for (int i = 0; i < array.size(); i++) {
                        JsonObject object       = array.get(i).getAsJsonObject();
                        String bestofferPrice   = object.get("price").getAsString();
                        String bestofferCnt     = object.get("cnt").getAsString();
                        String bestofferOrderId = ReturnCode.NO_DATA.getValue();

                        if( !(bestofferOrderId = createOrder(action, bestofferPrice, bestofferCnt, symbol, sessionKey)).equals(ReturnCode.NO_DATA.getValue())){
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




    /* 최초에 등록한 세션키를 가져옴. */
    /**
     * 매도/매수 function
     * @param type   - 1: 매수 / 2 : 매도
     * @param symbol - coin + / + currency
     */
    public String createOrder(String type, String price, String cnt, String symbol, String sessionKey){

        String orderId = ReturnCode.NO_DATA.getValue();
        try{
            BigDecimal decimalPrice = new BigDecimal(price);
            BigDecimal decimalCnt   = new BigDecimal(cnt);
            BigDecimal tax          = new BigDecimal("1000");
            BigDecimal total        = decimalPrice.multiply(decimalCnt);
            BigDecimal resultTax    = total.divide(tax);
            String minCntFix        = getCoinMinCount(symbol);

            int dotIdx = minCntFix.indexOf(".");
            int oneIdx = minCntFix.indexOf("1");
            int length = minCntFix.length();
            int primary = 0;
            double doubleCnt = Double.parseDouble(cnt);
            // 소수
            if(dotIdx < oneIdx){
                // 34.232
                // 0.01
                primary = oneIdx - dotIdx;  // 소수점 2째 자리.
                int num = (int) Math.pow(10,primary);
                doubleCnt = Math.floor(doubleCnt * num) / num;
            }else{
                // 34.232
                //  1.0
                primary = dotIdx - 1; // 해당 자리에서 버림
                int num = ((int) Math.pow(10,primary) == 0) ? 1 : (int) Math.pow(10,primary);
                doubleCnt = Math.floor(doubleCnt / num) * num;
            }

            JsonObject header = new JsonObject();
            header.addProperty("symbol",symbol);
            header.addProperty("buySellType",type);
            header.addProperty("ordPrcType", "2");  // 1은 시장가, 2는 지정가
            header.addProperty("ordPrc",Double.parseDouble(price));
            header.addProperty("ordQty",doubleCnt);
            header.addProperty("ordFee",resultTax.doubleValue());

            String modeKo = (type.equals("1")) ? "BUY":"SELL";
            log.info("[FLATA][CREATE ORDER START] mode {} , valeus {}", modeKo, gson.toJson(header));

            JsonObject response = postHttpMethodWithSession(UtilsData.FLATA_CREATE_ORDER,gson.toJson(header), sessionKey);
            JsonObject item     = response.get("item").getAsJsonObject();
            orderId             = item.get("ordNo").toString();

            // Order ID 가 0이면 에러
            if(!orderId.equals("0")){
                log.info("[FLATA][CREATE ORDER] response :{}", gson.toJson(response));
            }else{
                orderId = ReturnCode.NO_DATA.getValue();
                log.error("[FLATA][CREATE ORDER] response :{}", gson.toJson(response));
            }
        }catch (Exception e){
            orderId = ReturnCode.NO_DATA.getValue();
            log.error("[FLATA][CREATE ORDER] {}",e.getMessage());
            e.printStackTrace();
        }
        return orderId;
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
}
