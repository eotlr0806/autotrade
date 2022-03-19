package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.service.BithumbHttpService;
import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.model.*;
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

    /** 코인 토큰 정보 셋팅 **/
    private void setCoinToken(String[] coinData, Exchange exchange) throws Exception{
        // Set token key
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

    /**
     * Bithumb global 자전 거래
     * @param symbol coin + "-" + symbol
     */
    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[BITHUMB][AUTOTRADE] START");
        int returnCode = ReturnCode.SUCCESS.getCode();
        String firstAction  = "";
        String secondAction = "";
        try{

            String[] coinData = Utils.splitCoinWithId(autoTrade.getCoin());
            String currency   = getCurrency(autoTrade.getExchange(), coinData[0], coinData[1]);

            // mode 처리
            String mode = autoTrade.getMode();
            if(UtilsData.MODE_RANDOM.equals(mode)){
                mode = (Utils.getRandomInt(0,1) == 0) ? UtilsData.MODE_BUY : UtilsData.MODE_SELL;
            }
            if(UtilsData.MODE_BUY.equals(mode)){
                firstAction  = BUY;
                secondAction = SELL;
            }else{
                firstAction  = SELL;
                secondAction = BUY;
            }

            String orderId = ReturnCode.NO_DATA.getValue();
            if(!(orderId = createOrder(firstAction, price, cnt, coinData[0],currency)).equals(ReturnCode.NO_DATA.getValue())){   // 매수
                Thread.sleep(500);
                if(createOrder(secondAction,price, cnt, coinData[0],currency).equals(ReturnCode.NO_DATA.getValue())){               // 매도
                    cancelOrder(firstAction,orderId, coinData[0], currency);                      // 매도 실패 시, 매수 취소
                }
            }

        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[BITHUMB][AUTOTRADE] Error : {}", e.getMessage());
        }

        log.info("[BITHUMB][AUTOTRADE] END");
        return returnCode;
    }

    /** 호가유동성 function */
    @Override
    public int startLiquidity(Map list){
        int returnCode = ReturnCode.SUCCESS.getCode();

        Queue<String> sellQueue = (LinkedList) list.get("sell");
        Queue<String> buyQueue  = (LinkedList) list.get("buy");
        Queue<Map<String,String>> cancelList = new LinkedList<>();

        try{
            log.info("[BITHUMB][LIQUIDITY] START");
            String[] coinData = Utils.splitCoinWithId(liquidity.getCoin());
            String currency   = getCurrency(liquidity.getExchange(), coinData[0], coinData[1]);

            while (!sellQueue.isEmpty() || !buyQueue.isEmpty() || !cancelList.isEmpty()) {
                String mode           = (Utils.getRandomInt(1, 2) == 1) ? UtilsData.MODE_BUY : UtilsData.MODE_SELL;
                boolean cancelFlag    = (Utils.getRandomInt(1, 2) == 1) ? true : false;
                String orderId        = ReturnCode.NO_DATA.getValue();
                String price          = "";
                String action         = "";
                String cnt            = Utils.getRandomString(liquidity.getMinCnt(), liquidity.getMaxCnt());

                if(!buyQueue.isEmpty() && mode.equals(UtilsData.MODE_BUY)){
                    price   = buyQueue.poll();
                    action  = BUY;
                }else if(!sellQueue.isEmpty() && mode.equals(UtilsData.MODE_SELL)){
                    price   = sellQueue.poll();
                    action  = SELL;
                }

                // 매수 로직
                if(!action.equals("")){
                    orderId = createOrder(action, price, cnt, coinData[0], currency);
                    if(!orderId.equals(ReturnCode.NO_DATA.getValue())){
                        Map<String, String> cancel = new HashMap<>();
                        cancel.put("orderId", orderId);
                        cancel.put("action",  action);
                        cancelList.add(cancel);
                    }
                    Thread.sleep(1000);
                }
                // 취소 로직
                if(!cancelList.isEmpty() && cancelFlag){
                    Map<String, String> cancelMap = cancelList.poll();
                    String cancelId               = cancelMap.get("orderId");
                    String cancelAction           = cancelMap.get("action");
                    cancelOrder(cancelAction,  cancelId, coinData[0], currency);
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
            String[] coinData = Utils.splitCoinWithId(fishing.getCoin());
            String currency   = getCurrency(fishing.getExchange(), coinData[0], coinData[1]);

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
            log.info("[BITHUMB][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt     = Utils.getRandomString(fishing.getMinContractCnt(), fishing.getMaxContractCnt());
                String orderId = ReturnCode.NO_DATA.getValue();
                if(UtilsData.MODE_BUY.equals(mode)) {
                    orderId = createOrder(BUY,  tickPriceList.get(i), cnt,  coinData[0],currency);
                }else{
                    orderId = createOrder(SELL, tickPriceList.get(i), cnt,  coinData[0],currency);
                }
                if(!orderId.equals(ReturnCode.NO_DATA.getValue())){         // 매수/매도가 정상적으로 이뤄졌을 경우 데이터를 list에 담는다
                    Map<String, String> orderMap = new HashMap<>();
                    orderMap.put("price" ,tickPriceList.get(i));
                    orderMap.put("cnt" ,cnt);
                    orderMap.put("order_id" ,orderId);
                    if(UtilsData.MODE_BUY.equals(mode)){
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
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = Utils.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("cnt"));

                while (cnt.compareTo(new BigDecimal("0")) > 0) {
                    if (!noMatchFirstTick) break;                   // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                    if (noIntervalFlag) Thread.sleep(intervalTime); // intervalTime 만큼 휴식 후 매수 시작
                    String orderId            = "";
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
                    String orderPrice = copiedOrderMap.get("price");
                    if (!orderPrice.equals(nowFirstTick)) {
                        log.info("[BITHUMB][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        noMatchFirstTick = false;
                        break;
                    }

                    if(UtilsData.MODE_BUY.equals(mode)) {
                        orderId = createOrder(SELL, copiedOrderMap.get("price"), cntForExcution.toPlainString(),  coinData[0],currency);
                    }else{
                        orderId = createOrder(BUY,  copiedOrderMap.get("price"), cntForExcution.toPlainString(),  coinData[0],currency);
                    }

                    if(!orderId.equals(ReturnCode.NO_DATA.getValue())){
                        cnt = cnt.subtract(cntForExcution);
                    }else{
                        log.error("[BITHUMB][FISHINGTRADE] While loop is broken, Because create order is failed");
                        break;
                    }
                }
                // 무조건 취소를 날려서 있던 없던 제거
                Thread.sleep(500);
                cancelOrder(orderList.get(i).get("type"), orderList.get(i).get("order_id"), coinData[0], currency);
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
    public int startRealtimeTrade(JsonObject realtime) {
        log.info("[BITHUMB][REALTIME SYNC TRADE] START");
        int returnCode   = ReturnCode.SUCCESS.getCode();
        String realtimeChangeRate = "signed_change_rate";

        try {

            boolean isStart      = false;
            String[] coinWithId  = Utils.splitCoinWithId(realtimeSync.getCoin());
            String currency      = getCurrency(realtimeSync.getExchange(), coinWithId[0], coinWithId[1]);
            String symbol        = getSymbol(coinWithId, realtimeSync.getExchange());
            String[] currentTick = getTodayTick();
            String openingPrice  = currentTick[0];
            String currentPrice  = currentTick[1];
            String orderId       = ReturnCode.NO_DATA.getValue();
            String targetPrice   = "";
            String action        = "";
            String mode          = "";
            String cnt           = Utils.getRandomString(realtimeSync.getMinTradeCnt(), realtimeSync.getMaxTradeCnt());

            int isInRange = isMoreOrLessPrice(currentPrice);

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
                if( !(orderId = createOrder(action, targetPrice, cnt, coinWithId[0], currency)).equals(ReturnCode.NO_DATA.getValue())){    // 매수/OrderId가 있으면 성공

                    Thread.sleep(300);

                    // 3. bestoffer set 로직
                    JsonArray array = makeBestofferAfterRealtimeSync(targetPrice, mode);
                    for (int i = 0; i < array.size(); i++) {
                        JsonObject object       = array.get(i).getAsJsonObject();
                        String bestofferPrice   = object.get("price").getAsString();
                        String bestofferCnt     = object.get("cnt").getAsString();
                        String bestofferOrderId = ReturnCode.NO_DATA.getValue();

                        if( !(bestofferOrderId = createOrder(action, bestofferPrice, bestofferCnt, coinWithId[0], currency)).equals(ReturnCode.NO_DATA.getValue())){
                            log.info("[BITHUMB][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                    }
                    cancelOrder(action, orderId, coinWithId[0], currency);
                }
            }
        }catch (Exception e){
            log.error("[BITHUMB][REALTIME SYNC TRADE] Error :{} ", e.getMessage());
            e.printStackTrace();
        }
        log.info("[BITHUMB][REALTIME SYNC TRADE] END");
        return returnCode;
    }



    /**
     * 현재 Tick 가져오기
     * @param exchange
     * @param coinWithId
     * @return [ 시가 , 종가 ] String Array
     */
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
            log.info("[BITHUMB][GET TODAY TICK] response : {}", Arrays.toString(returnRes));
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
        }catch (Exception e){
            log.error("[BITHUMB][ORDER BOOK] ERROR : {}",e.getMessage());
            e.printStackTrace();
        }
        return returnRes;
    }



    /** Biyhumb global 매수/매도 로직 */
    private String createOrder(String type, String price, String cnt, String coin, String currency){
        String orderId = ReturnCode.NO_DATA.getValue();

        try{

            HashMap<String, String> rgParams = new HashMap<String, String>();
            rgParams.put("order_currency", coin);
            rgParams.put("payment_currency", currency);
            rgParams.put("units", cnt);
            rgParams.put("price", price);
            rgParams.put("type", type);

            String api_host                     = UtilsData.BITHUMB_URL + UtilsData.BITHUMB_ENDPOINT_CREATE_ORDER;
            HashMap<String, String> httpHeaders = getHttpHeaders(UtilsData.BITHUMB_ENDPOINT_CREATE_ORDER, rgParams);
            String rgResultDecode               = postHttpMethod(api_host,  rgParams, httpHeaders);

            JsonObject returnVal = gson.fromJson(rgResultDecode, JsonObject.class);
            String status        = returnVal.get("status").getAsString();
            if(status.equals(SUCCESS)){
                orderId = returnVal.get("order_id").getAsString();
                log.info("[BITHUMB][CREATE ORDER] response : {}", rgResultDecode);
            }else{
                log.error("[BITHUMB][CREATE ORDER] response :{}", rgResultDecode);
            }
        }catch (Exception e){
            log.error("[BITHUMB][CREATE ORDER] ERROR {}",e.getMessage());
            e.printStackTrace();
        }
        return orderId;
    }

    /* Bithumb global 거래 취소 */
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
            }
        }catch(Exception e){
            log.error("[BITHUMB][CANCEL ORDER] ERROR : {}", e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }


    /** Bithum 에서 제공하는 메서드를 이용한 http */
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

    /** Bithum 에서 제공하는 메서드를 이용한 http를 이용하기 위해 header를 만드는 작업 */
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

    // Map으로 받은 파라미터 값들을 쿼리 스트링 형식으로 변경
    private String mapToQueryString(Map<String, String> map) throws Exception{
        StringBuilder string = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            string.append(entry.getKey());
            string.append("=");
            string.append(entry.getValue());
            if(i < map.size() - 1){
                string.append("&");
            }
            i++;
        }
        return string.toString();
    }

    // Bithum 에서 암호화를 하기위한 메서드
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

    // Bithum 에서 암호화를 하기위한 메서드
    private byte[] hmacSha512(String value, String key) throws Exception {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA512");
            Mac mac               = Mac.getInstance("HmacSHA512");
            mac.init(keySpec);

            final byte[] macData  = mac.doFinal( value.getBytes( ) );
            byte[] hex            = new Hex().encode( macData );
            return hex;

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    // Bithumb 에서 제공하는 메서드
    private String asHex(byte[] bytes){
        return new String(Base64.encodeBase64(bytes));
    }

    // 거래소에 맞춰 심볼 반환
    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0]+ "_" + getCurrency(exchange,coinData[0], coinData[1]);
    }



}
