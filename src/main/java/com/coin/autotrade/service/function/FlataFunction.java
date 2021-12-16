package com.coin.autotrade.service.function;

import com.coin.autotrade.common.BeanUtils;
import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.*;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.service.CoinService;
import com.google.gson.Gson;
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
public class FlataFunction extends ExchangeFunction{

    String coinMinCnt                          = "";
    final private String BUY                   = "1";
    final private String SELL                  = "2";
    final private String ALREADY_TRADED        = "30044";

    private ExchangeRepository exchageRepository;

    @Override
    public void initClass(AutoTrade autoTrade, User user, Exchange exchange){
        super.autoTrade = autoTrade;
        setCommonValue(user, exchange);
    }

    @Override
    public void initClass(Liquidity liquidity, User user, Exchange exchange){
        super.liquidity = liquidity;
        setCommonValue(user, exchange);
    }

    @Override
    public void initClass(Fishing fishing, User user, Exchange exchange, CoinService coinService){
        super.fishing     = fishing;
        super.coinService = coinService;
        setCommonValue(user, exchange);
    }

    private void setCommonValue(User user,  Exchange exchange){
        super.user     = user;
        super.exchange = exchange;
    }


    /**
     * Start auto trade function
     * @param symbol - coin / currency
     */
    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[FLATA][AUTOTRADE START]");

        int returnCode    = DataCommon.CODE_SUCCESS;
        try{
            String[] coinData = ServiceCommon.splitCoinWithId(autoTrade.getCoin());
            String coin       = coinData[0];
            String coinId     = coinData[1];
            String sessionKey = getSessionKey(coin, coinId);
            String symbol     = coinData[0] + "/" + getCurrency(exchange, coinData[0], coinData[1]);

            // mode 처리
            String mode = autoTrade.getMode();
            if(DataCommon.MODE_RANDOM.equals(mode)){
                mode = (ServiceCommon.getRandomInt(0,1) == 0) ? DataCommon.MODE_BUY : DataCommon.MODE_SELL;
            }

            // 1 : 매수 , 2 : 매도
            if(DataCommon.MODE_BUY.equals(mode)){
                String buyOrderId  = "0";
                if( !(buyOrderId = createOrder(BUY,price, cnt, symbol, sessionKey)).equals("0")){   // 매수 성공
                    if(createOrder(SELL,price, cnt, symbol,sessionKey).equals("0")){                // 매도 실패
                        cancelOrder(buyOrderId, sessionKey);                                        // 매도 실패 시, 매수 취소
                    }
                }
            }else if(DataCommon.MODE_SELL.equals(mode)){
                String sellOrderId  = "0";
                if( !(sellOrderId = createOrder(SELL,price, cnt, symbol, sessionKey)).equals("0")){
                    if(createOrder(BUY,price, cnt, symbol, sessionKey).equals("0")){
                        cancelOrder(sellOrderId, sessionKey);
                    }
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[FLATA][ERROR][AUTOTRADE] {}", e.getMessage());
        }

        log.info("[FLATA][AUTOTRADE END]");
        return returnCode;
    }

    /** 호가유동성 function */
    @Override
    public int startLiquidity(Map list){
        int returnCode = DataCommon.CODE_SUCCESS;

        Queue<String> sellQueue = (LinkedList) list.get("sell");
        Queue<String> buyQueue  = (LinkedList) list.get("buy");
        List<Map<String,String>> CancelList = new ArrayList();

        try{
            log.info("[FLATA][LIQUIDITY] Start");
            String[] coinData = ServiceCommon.splitCoinWithId(liquidity.getCoin());
            String coin       = coinData[0];
            String coinId     = coinData[1];
            String sessionKey = getSessionKey(coin, coinId);
            String symbol     = coinData[0] + "/" + getCurrency(exchange, coinData[0], coinData[1]);
            int minCnt        = liquidity.getMinCnt();
            int maxCnt        = liquidity.getMaxCnt();


            while(sellQueue.size() > 0 || buyQueue.size() > 0){
                String randomMode = (ServiceCommon.getRandomInt(1,2) == 1) ? BUY : SELL;
                String firstOrderId    = "";
                String secondsOrderId  = "";
                String firstPrice      = "";
                String secondsPrice    = "";
                String firstCnt        = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                String secondsCnt      = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);

                if(sellQueue.size() > 0 && buyQueue.size() > 0 && randomMode.equals(BUY)){
                    firstPrice   = buyQueue.poll();
                    firstOrderId = createOrder(BUY,  firstPrice, firstCnt, symbol, sessionKey);

                    Thread.sleep(300);
                    secondsPrice   = sellQueue.poll();
                    secondsOrderId = createOrder(SELL,  secondsPrice, secondsCnt, symbol, sessionKey);
                }else if(buyQueue.size() > 0 && sellQueue.size() > 0 && randomMode.equals(SELL)){
                    firstPrice   = sellQueue.poll();
                    firstOrderId = createOrder(SELL,  firstPrice, firstCnt, symbol, sessionKey);

                    Thread.sleep(300);
                    secondsPrice   = buyQueue.poll();
                    secondsOrderId = createOrder(BUY,  secondsPrice, secondsCnt, symbol, sessionKey);
                }

                if(!firstOrderId.equals("") || !secondsOrderId.equals("")){
                    Thread.sleep(1000);
                    if(!firstOrderId.equals("")){
                        cancelOrder(firstOrderId, sessionKey);
                    }
                    if(!secondsOrderId.equals("")){
                        Thread.sleep(300);
                        cancelOrder(secondsOrderId, sessionKey);
                    }
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[FLATA][ERROR][LIQUIDITY] {}", e.getMessage());
        }
        log.info("[FLATA][LIQUIDITY] End");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[FLATA][FISHINGTRADE START]");

        int returnCode    = DataCommon.CODE_SUCCESS;

        try{
            String[] coinData = ServiceCommon.splitCoinWithId(fishing.getCoin());
            String coin       = coinData[0];
            String coinId     = coinData[1];
            String sessionKey = getSessionKey(coin, coinId);
            String symbol     = coinData[0] + "/" + getCurrency(exchange, coinData[0], coinData[1]);

            // mode 처리
            String mode = fishing.getMode();
            if(DataCommon.MODE_RANDOM.equals(mode)){
                mode = (ServiceCommon.getRandomInt(0,1) == 0) ? DataCommon.MODE_BUY : DataCommon.MODE_SELL;
            }

            boolean noIntervalFlag   = true;    // 해당 플래그를 이용해 마지막 매도/매수 후 바로 intervalTime 없이 바로 다음 매수/매도 진행
            boolean noMatchFirstTick = true;    // 해당 플래그를 이용해 매수/매도를 올린 가격이 현재 최상위 값이 맞는지 다른 사람의 코인을 사지 않게 방지

            for(String temp : list.keySet()){  mode = temp; }
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode);
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            /* Start */
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double) fishing.getMinContractCnt(), (double) fishing.getMaxContractCnt()) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                String orderId = "";
                if(DataCommon.MODE_BUY.equals(mode)) {
                    orderId = createOrder(BUY, tickPriceList.get(i), cnt, symbol, sessionKey);
                }else{
                    orderId = createOrder(SELL, tickPriceList.get(i), cnt, symbol, sessionKey);
                }
                if(!orderId.equals("0")){                                                // 매수/매도가 정상적으로 이뤄졌을 경우 데이터를 list에 담는다
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
                Map<String, String> copiedOrderMap = ServiceCommon.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("cnt"));

                while (cnt.compareTo(new BigDecimal("0")) > 0) {
                    if (!noMatchFirstTick) break;                   // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                    if (noIntervalFlag) Thread.sleep(intervalTime); // intervalTime 만큼 휴식 후 매수 시작
                    String orderId            = "";
                    BigDecimal cntForExcution = new BigDecimal(String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double) fishing.getMinExecuteCnt(), (double) fishing.getMaxExecuteCnt()) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL));
                    // 남은 코인 수와 매도/매수할 코인수를 비교했을 때, 남은 코인 수가 더 적다면.
                    if (cnt.compareTo(cntForExcution) < 0) {
                        cntForExcution = cnt;
                        noIntervalFlag = false;
                    } else {
                        noIntervalFlag = true;
                    }
                    // 매도/매수 날리기전에 최신 매도/매수값이 내가 건 값이 맞는지 확인
                    String nowFirstTick = "";
                    if(DataCommon.MODE_BUY.equals(mode)) {
                        nowFirstTick = coinService.getFirstTick(fishing.getCoin(), fishing.getExchange()).get(DataCommon.MODE_BUY);
                    }else{
                        nowFirstTick = coinService.getFirstTick(fishing.getCoin(), fishing.getExchange()).get(DataCommon.MODE_SELL);
                    }

                    if (!copiedOrderMap.get("price").equals(nowFirstTick)) {
                        log.info("[FOBLGATE][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        noMatchFirstTick = false;
                        break;
                    }

                    if(DataCommon.MODE_BUY.equals(mode)) {
                        orderId = createOrder(SELL, copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol, sessionKey);
                    }else{
                        orderId = createOrder(BUY, copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol, sessionKey);
                    }

                    if(!orderId.equals("0")){
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
            returnCode = DataCommon.CODE_ERROR;
            log.error("[FLATA][ERROR][FISHINGTRADE] {}", e.getMessage());
        }

        log.info("[FLATA][FISHINGTRADE END]");
        return returnCode;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = "";
        try{
            log.info("[FLATA][ORDER BOOK] START");
            String coin   = coinWithId[0];
            String coinId = coinWithId[1];
            String inputLine;
            String symbol = getCurrency(exchange, coin, coinId);
            String request  = DataCommon.FLATA_ORDERBOOK + "?symbol=" + coin + "/" + symbol + "&level=10";
            URL url = new URL(request);

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setReadTimeout(DataCommon.TIMEOUT_VALUE);

            log.info("[FLATA][ORDER BOOK - REQUEST] symbol:{}", coin);

            int returnCode = connection.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer response = new StringBuffer();
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();
            returnRes = response.toString();
            log.info("[FLATA][ORDER BOOK] End");

        }catch (Exception e){
            log.error("[FLATA][ERROR][ORDER BOOK] {}",e.getMessage());
        }

        return returnRes;
    }


    /** 생성자로서, 생성될 때, injection**/
    public FlataFunction(){
        exchageRepository   = (ExchangeRepository) BeanUtils.getBean(ExchangeRepository.class);
    }


    /**
     * Session key 생성
     * 해당 코인에 등록된 key를 가져와 그 코인에 맞는 session key 생성
     */
    public String setSessionKey(String userPublicKey, String coinCode, String coinId){

        // 해당 계정에 대해 세션 키가 있을 경우 반환
        if(DataCommon.FLATA_SESSION_KEY.get(userPublicKey) != null){
            return DataCommon.FLATA_SESSION_KEY.get(userPublicKey);
        }

        String publicKey     = "";
        String secretKey     = "";
        String returnValue   = "";
        JsonObject header    = new JsonObject();
        try{

            if(exchange.getExchangeCoin().size() > 0){
                for(ExchangeCoin coin : exchange.getExchangeCoin()){
                    if(coin.getCoinCode().equals(coinCode) && coin.getId() == Long.parseLong(coinId)){
                        publicKey   = coin.getPublicKey();
                        secretKey = coin.getPrivateKey();
                        break;
                    }
                }
            }
            header.addProperty("acctid",publicKey);
            header.addProperty("acckey",secretKey);

            JsonObject response =  postHttpMethod(DataCommon.FLATA_MAKE_SESSION, gson.toJson(header));
            JsonObject item     =  response.getAsJsonObject("item");
            returnValue         =  item.get("sessionId").toString().replace("\"","");

            // 메모리에 저장
            DataCommon.FLATA_SESSION_KEY.put(userPublicKey, returnValue);
            log.info("[SUCCESS][FLATA][SET SESSION KEY] First session key {}: mapperedPublicKey : {}", returnValue, userPublicKey );

        }catch (Exception e){
            log.error("[FLATA][ERROR][MAKE SESSION KEY] {}" , e.getMessage());
        }

        return DataCommon.FLATA_SESSION_KEY.get(userPublicKey);
    }

    /* 최초에 등록한 세션키를 가져옴. */
    public String getSessionKey(String coin, String coinId){
        String publicKey  = "";
        String sessionKey = "";
        try{
            for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
                if(exCoin.getCoinCode().equals(coin) && exCoin.getId() == Long.parseLong(coinId)){
                    publicKey = exCoin.getPublicKey();
                    break;
                }
            }

            if(!publicKey.equals("")){
                sessionKey = setSessionKey(publicKey, coin, coinId);
            }
        }catch (Exception e){
            log.error("[FLATA][ERROR][GET SESSION] {}", e.getMessage());
        }

        log.info("[FLATA][GET SESSION] Session key : {}", sessionKey );
        return sessionKey;
    }

    /**
     * 매도/매수 function
     * @param type   - 1: 매수 / 2 : 매도
     * @param symbol - coin + / + currency
     */
    public String createOrder(String type, String price, String cnt, String symbol, String sessionKey){

        String orderId = "";
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

            JsonObject response = postHttpMethodWithSession(DataCommon.FLATA_CREATE_ORDER,gson.toJson(header), sessionKey);
            JsonObject item     = gson.fromJson(response.get("item").toString(), JsonObject.class);
            orderId             = item.get("ordNo").toString();

            // Order ID 가 0이면 에러
            if(!orderId.equals("0")){
                log.info("[FLATA][SUCCESS][CREATE ORDER] response :{}", gson.toJson(response));
            }else{
                log.error("[FLATA][ERROR][CREATE ORDER] response :{}", gson.toJson(response));
            }
        }catch (Exception e){
            log.error("[FLATA][ERROR][CREATE ORDER] {}",e.getMessage());
        }
        return orderId;
    }

    /* 주문 취소 function */
    public int cancelOrder(String orderId, String sessionKey){
        int returnVal = DataCommon.CODE_ERROR;
        try{
            JsonObject header = new JsonObject();
            header.addProperty("orgOrdNo", orderId);

            JsonObject response    = postHttpMethodWithSession(DataCommon.FLATA_CANCEL_ORDER, gson.toJson(header), sessionKey);
            JsonObject item        = gson.fromJson(response.get("item").toString(), JsonObject.class);
            String returnOrderId   = item.get("ordNo").toString();

            String returnMeesageNo = "";
            if(item.has("messageNo")){
                returnMeesageNo = item.get("messageNo").toString().replace("\"","");
            }

            if( (!returnOrderId.equals("0") && !"".equals(returnOrderId)) || returnMeesageNo.equals(ALREADY_TRADED)){
                returnVal = DataCommon.CODE_SUCCESS;
                log.info("[FLATA][SUCCESS][CANCEL ORDER] response : {}",  gson.toJson(item));
            }else{
                log.error("[FLATA][ERROR][CANCEL ORDER] response : {}",  gson.toJson(item));
            }

        }catch (Exception e){
            log.error("[FLATA][ERROR][CANCEL ORDER] {}", e.getMessage());
        }

        return returnVal;
    }


    /* 해당 코인의 최소 매수/매도 단위 조회 */
    public String getCoinMinCount(String symbol) {
        String returnRes = "";

        // 과거에 값을 부여한 케이스가 있다면 그걸 쓰면됨
        if(!coinMinCnt.equals("")){
            return coinMinCnt;
        }

        try{
            log.info("[FLATA][GET COIN INFO] Start");
            String inputLine;
            String request  = DataCommon.FLATA_COININFO + "?symbol=" + symbol + "&lang=ko";
            URL url = new URL(request);

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            log.info("[FLATA][GET COIN INFO - REQUEST] symbol:{}", symbol);

            int returnCode = connection.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer response = new StringBuffer();
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();

            JsonObject object = gson.fromJson(response.toString(), JsonObject.class);
            JsonArray array   = gson.fromJson(object.get("record").toString(), JsonArray.class);
            JsonObject data   = gson.fromJson(array.get(0).toString(), JsonObject.class);


            returnRes = data.get("ordUnitQty").toString();
            coinMinCnt = returnRes;
            log.info("[SUCCESS][FLATA][GET COIN INFO] coinMinCnt {} ",  coinMinCnt);
            log.info("[FLATA][GET COIN INFO] End");

        }catch (Exception e){
            log.error("[FLATA][ERROR][GET COIN INFO] {}",e.getMessage());
        }

        return returnRes;
    }

    /* Coin의 등록된 화폐를 가져오는 로직 */
    public String getCurrency(Exchange exchange,String coin, String coinId){
        String returnVal = "";
        try {
            // Thread로 돌때는 최초에 셋팅을 해줘서 DB 조회가 필요 없음.
            if(exchange.getExchangeCoin().size() > 0){
                for(ExchangeCoin data : exchange.getExchangeCoin()){
                    if(data.getCoinCode().equals(coin) && data.getId() == Long.parseLong(coinId)){
                        returnVal = data.getCurrency();
                    }
                }
            }
        }catch(Exception e){
            log.error("[FLATA][ERROR][GET CUREENCY] {}",e.getMessage());
        }
        return returnVal;
    }

    /* HTTP POST Method for coinone */
    public JsonObject postHttpMethod(String targetUrl, String payload) {
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

            Gson gson = new Gson();
            returnObj = gson.fromJson(response.toString(), JsonObject.class);

        }catch(Exception e){
            log.error("[FLATA][ERROR][FLATA POST HTTP] {}", e.getMessage());
        }

        return returnObj;
    }


    public JsonObject postHttpMethodWithSession(String targetUrl, String payload, String SessionKey) {
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
            connection.setConnectTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setReadTimeout(DataCommon.TIMEOUT_VALUE);
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

            Gson gson = new Gson();
            returnObj = gson.fromJson(response.toString(), JsonObject.class);

        }catch(Exception e){
            log.error("[FLATA][ERROR][HTTP POST] {}", e.getMessage());
        }

        return returnObj;
    }

    public void setExchange(Exchange exchange){
        super.exchange = exchange;
    }
    public Exchange getExchange(){
        return super.exchange;
    }

}
