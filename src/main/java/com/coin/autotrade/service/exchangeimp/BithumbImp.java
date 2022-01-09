package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.TradeData;
import com.coin.autotrade.common.HttpRequest;
import com.coin.autotrade.common.TradeService;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;


@Slf4j
public class BithumbImp extends AbstractExchange {

    private final String DEFAULT_ENCODING = "UTF-8";
    private final String HMAC_SHA512      = "HmacSHA512";

    final private String ACCESS_TOKEN  = "access_token";
    final private String SECRET_KEY    = "secret_key";
    final private String ALREADY_TRADED= "3000";
    final private String BUY           = "bid";
    final private String SELL          = "ask";
    Map<String, String> keyList        = new HashMap<>();



    @Override
    public void initClass(AutoTrade autoTrade){
        super.autoTrade = autoTrade;
        setCoinToken(TradeService.splitCoinWithId(autoTrade.getCoin()), autoTrade.getExchange());
    }

    @Override
    public void initClass(Liquidity liquidity){
        super.liquidity = liquidity;
        setCoinToken(TradeService.splitCoinWithId(liquidity.getCoin()), liquidity.getExchange());
    }

    @Override
    public void initClass(RealtimeSync realtimeSync){
        super.realtimeSync = realtimeSync;
        setCoinToken(TradeService.splitCoinWithId(realtimeSync.getCoin()), realtimeSync.getExchange());
    }

    @Override
    public void initClass(Fishing fishing, CoinService coinService){
        super.fishing     = fishing;
        super.coinService = coinService;
        setCoinToken(TradeService.splitCoinWithId(fishing.getCoin()), fishing.getExchange());
    }

    /** 코인 토큰 정보 셋팅 **/
    private void setCoinToken(String[] coinData, Exchange exchange){
        // Set token key
        try{
            for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
                if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1])){
                    keyList.put(ACCESS_TOKEN, exCoin.getPublicKey());
                    keyList.put(SECRET_KEY,   exCoin.getPrivateKey());
                }
            }
        }catch (Exception e){
            log.error("[BITHUMB][ERROR][SET COIN TOKEN] {}", e.getMessage());
        }
    }

    /**
     * Bithumb global 자전 거래
     * @param symbol coin + "-" + symbol
     */
    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[BITHUMB][AUTOTRADE START]");
        int returnCode    = TradeData.CODE_SUCCESS;

        try{

            String[] coinData = TradeService.splitCoinWithId(autoTrade.getCoin());
            String currency   = getCurrency(autoTrade.getExchange(), coinData[0], coinData[1]);

            // mode 처리
            String mode = autoTrade.getMode();
            if(TradeData.MODE_RANDOM.equals(mode)){
                mode = (TradeService.getRandomInt(0,1) == 0) ? TradeData.MODE_BUY : TradeData.MODE_SELL;
            }

            // 1 : 매수 , 2 : 매도
            if(TradeData.MODE_BUY.equals(mode)){
                String buyOrderId  = "";
                if( !(buyOrderId = createOrder(BUY, price, cnt, coinData[0],currency)).equals("")){   // 매수
                    Thread.sleep(300);
                    createOrder(SELL,price, cnt,  coinData[0],currency);
                    cancelOrder(BUY,buyOrderId, coinData[0], currency);                      // 매도 실패 시, 매수 취소
                }
            }else if(TradeData.MODE_SELL.equals(mode)){
                String sellOrderId  = "";
                if( !(sellOrderId = createOrder(SELL,price, cnt,  coinData[0],currency)).equals("")){
                    Thread.sleep(300);
                    createOrder(BUY,price, cnt,  coinData[0],currency);
                    cancelOrder(SELL, sellOrderId, coinData[0], currency);
                }
            }
        }catch (Exception e){
            returnCode = TradeData.CODE_ERROR;
            log.error("[BITHUMB][ERROR][AUTOTRADE] {}", e.getMessage());
        }

        log.info("[BITHUMB][AUTOTRADE End]");
        return returnCode;
    }

    /** 호가유동성 function */
    @Override
    public int startLiquidity(Map list){
        int returnCode = TradeData.CODE_SUCCESS;

        Queue<String> sellQueue = (LinkedList) list.get("sell");
        Queue<String> buyQueue  = (LinkedList) list.get("buy");
        List<Map<String,String>> CancelList = new ArrayList();

        try{
            log.info("[BITHUMB][LIQUIDITY] Start");
            String[] coinData = TradeService.splitCoinWithId(liquidity.getCoin());
            String currency   = getCurrency(liquidity.getExchange(), coinData[0], coinData[1]);
            int minCnt        = liquidity.getMinCnt();
            int maxCnt        = liquidity.getMaxCnt();


            while(sellQueue.size() > 0 || buyQueue.size() > 0){
                String randomMode = (TradeService.getRandomInt(1,2) == 1) ? BUY : SELL;
                String firstOrderId    = "";
                String secondsOrderId  = "";
                String firstPrice      = "";
                String secondsPrice    = "";
                String firstCnt        = String.valueOf(Math.floor(TradeService.getRandomDouble((double)minCnt, (double)maxCnt) * TradeData.TICK_DECIMAL) / TradeData.TICK_DECIMAL);
                String secondsCnt      = String.valueOf(Math.floor(TradeService.getRandomDouble((double)minCnt, (double)maxCnt) * TradeData.TICK_DECIMAL) / TradeData.TICK_DECIMAL);

                if(sellQueue.size() > 0 && buyQueue.size() > 0 && randomMode.equals(BUY)){
                    firstPrice   = buyQueue.poll();
                    firstOrderId = createOrder(BUY, firstPrice, firstCnt,  coinData[0],currency);

                    Thread.sleep(300);
                    secondsPrice   = sellQueue.poll();
                    secondsOrderId = createOrder(SELL, secondsPrice, secondsCnt,  coinData[0],currency);
                }else if(buyQueue.size() > 0 && sellQueue.size() > 0 && randomMode.equals(SELL)){
                    firstPrice   = sellQueue.poll();
                    firstOrderId = createOrder(SELL, firstPrice, firstCnt,  coinData[0],currency);

                    Thread.sleep(300);
                    secondsPrice   = buyQueue.poll();
                    secondsOrderId = createOrder(BUY, secondsPrice, secondsCnt,  coinData[0],currency);
                }

                Thread.sleep(1000);
                if(randomMode.equals(BUY)){
                    if(!firstOrderId.equals("")){
                        cancelOrder(BUY,firstOrderId,coinData[0],currency);
                    }
                    if(!secondsOrderId.equals("")){
                        Thread.sleep(300);
                        cancelOrder(SELL,secondsOrderId,coinData[0],currency);
                    }
                }else{
                    if(!firstOrderId.equals("")){
                        cancelOrder(SELL,firstOrderId,coinData[0],currency);
                    }
                    if(!secondsOrderId.equals("")){
                        Thread.sleep(300);
                        cancelOrder(BUY,secondsOrderId,coinData[0],currency);
                    }
                }

            }
        }catch (Exception e){
            returnCode = TradeData.CODE_ERROR;
            log.error("[BITHUMB][ERROR][LIQUIDITY] {}", e.getMessage());
        }
        log.info("[BITHUMB][LIQUIDITY] End");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[BITHUMB][FISHINGTRADE START]");

        int returnCode    = TradeData.CODE_SUCCESS;

        try{
            String[] coinData = TradeService.splitCoinWithId(fishing.getCoin());
            String currency   = getCurrency(fishing.getExchange(), coinData[0], coinData[1]);

            // mode 처리
            String mode = fishing.getMode();
            if(TradeData.MODE_RANDOM.equals(mode)){
                mode = (TradeService.getRandomInt(0,1) == 0) ? TradeData.MODE_BUY : TradeData.MODE_SELL;
            }

            boolean noIntervalFlag   = true;    // 해당 플래그를 이용해 마지막 매도/매수 후 바로 intervalTime 없이 바로 다음 매수/매도 진행
            boolean noMatchFirstTick = true;    // 해당 플래그를 이용해 매수/매도를 올린 가격이 현재 최상위 값이 맞는지 다른 사람의 코인을 사지 않게 방지

            for(String temp : list.keySet()){  mode = temp; }
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode);
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            /* Start */
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt = String.valueOf(Math.floor(TradeService.getRandomDouble((double) fishing.getMinContractCnt(), (double) fishing.getMaxContractCnt()) * TradeData.TICK_DECIMAL) / TradeData.TICK_DECIMAL);

                String orderId = "";
                if(TradeData.MODE_BUY.equals(mode)) {
                    orderId = createOrder(BUY,  tickPriceList.get(i), cnt,  coinData[0],currency);
                }else{
                    orderId = createOrder(SELL, tickPriceList.get(i), cnt,  coinData[0],currency);
                }
                if(!orderId.equals("")){                                                // 매수/매도가 정상적으로 이뤄졌을 경우 데이터를 list에 담는다
                    Map<String, String> orderMap = new HashMap<>();
                    orderMap.put("price" ,tickPriceList.get(i));
                    orderMap.put("cnt" ,cnt);
                    orderMap.put("order_id" ,orderId);
                    if(TradeData.MODE_BUY.equals(mode)){
                        orderMap.put("type", BUY);
                    }else{
                        orderMap.put("type", SELL);
                    }
                    orderList.add(orderMap);
                }
            }

            /* Sell Start */
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = TradeService.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("cnt"));

                while (cnt.compareTo(new BigDecimal("0")) > 0) {
                    if (!noMatchFirstTick) break;                   // 최신 매도/매수 건 값이 다를경우 돌 필요 없음.
                    if (noIntervalFlag) Thread.sleep(intervalTime); // intervalTime 만큼 휴식 후 매수 시작
                    String orderId            = "";
                    BigDecimal cntForExcution = new BigDecimal(String.valueOf(Math.floor(TradeService.getRandomDouble((double) fishing.getMinExecuteCnt(), (double) fishing.getMaxExecuteCnt()) * TradeData.TICK_DECIMAL) / TradeData.TICK_DECIMAL));

                    // 남은 코인 수와 매도/매수할 코인수를 비교했을 때, 남은 코인 수가 더 적다면.
                    if (cnt.compareTo(cntForExcution) < 0) {
                        cntForExcution = cnt;
                        noIntervalFlag = false;
                    } else {
                        noIntervalFlag = true;
                    }
                    // 매도/매수 날리기전에 최신 매도/매수값이 내가 건 값이 맞는지 확인
                    String nowFirstTick = "";
                    if(TradeData.MODE_BUY.equals(mode)) {
                        nowFirstTick = coinService.getFirstTick(fishing.getCoin(), fishing.getExchange()).get(TradeData.MODE_BUY);
                    }else{
                        nowFirstTick = coinService.getFirstTick(fishing.getCoin(), fishing.getExchange()).get(TradeData.MODE_SELL);
                    }
                    String orderPrice = copiedOrderMap.get("price");
                    if (!orderPrice.equals(nowFirstTick)) {
                        log.info("[BITHUMB][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        noMatchFirstTick = false;
                        break;
                    }

                    if(TradeData.MODE_BUY.equals(mode)) {
                        orderId = createOrder(SELL, copiedOrderMap.get("price"), cntForExcution.toPlainString(),  coinData[0],currency);
                    }else{
                        orderId = createOrder(BUY,  copiedOrderMap.get("price"), cntForExcution.toPlainString(),  coinData[0],currency);
                    }

                    if(!orderId.equals("")){
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
        }catch (Exception e){
            returnCode = TradeData.CODE_ERROR;
            log.error("[BITHUMB][ERROR][FISHINGTRADE] {}", e.getMessage());
        }

        log.info("[BITHUMB][FISHINGTRADE END]");
        return returnCode;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = "";
        try{
            log.info("[BITHUMB][ORDER BOOK START]");
            String coin = coinWithId[0];
            String coinId = coinWithId[1];
            String inputLine;
            String symbol   = getCurrency(exchange, coin, coinId);
            String request  = TradeData.BITHUMB_ORDERBOOK + "/" + coin + "_" + symbol;
            URL url = new URL(request);

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            connection.setRequestMethod("GET");

            log.info("[BITHUMB][ORDER BOOK - REQUEST] symbol:{}", "BITHUMB",  coin);

            int returnCode = connection.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer response = new StringBuffer();
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();
            returnRes = response.toString();

            log.info("[BITHUMB][ORDER BOOK END]");

        }catch (Exception e){
            log.error("[BITHUMB][ERROR][ORDER BOOK] {}",e.getMessage());
        }

        return returnRes;
    }



    /** Biyhumb global 매수/매도 로직 */
    public String createOrder(String type, String price, String cnt, String coin, String currency){

        String orderId = "";

        try{

            HashMap<String, String> rgParams = new HashMap<String, String>();
            rgParams.put("order_currency", coin);
            rgParams.put("payment_currency", currency);
            rgParams.put("units", cnt);
            rgParams.put("price", price);
            rgParams.put("type", type);

            String api_host = TradeData.BITHUMB_URL + TradeData.BITHUMB_ENDPOINT_CREATE_ORDER;
            HashMap<String, String> httpHeaders = getHttpHeaders(TradeData.BITHUMB_ENDPOINT_CREATE_ORDER, rgParams, keyList.get(ACCESS_TOKEN), keyList.get(SECRET_KEY));
            String rgResultDecode = postHttpMethod(api_host,  rgParams, httpHeaders);

            JsonObject returnVal = gson.fromJson(rgResultDecode, JsonObject.class);
            String status        = gson.fromJson(returnVal.get("status"), String.class);
            if(status.equals("0000")){
                orderId         = gson.fromJson(returnVal.get("order_id"), String.class);
                log.info("[BITHUMB][SUCCESS][CREATE ORDER - response] response : {}", gson.toJson(returnVal));
            }else{
                log.error("[BITHUMB][ERROR][CREATE ORDER - response] response :{}", gson.toJson(returnVal));
            }

        }catch (Exception e){
            log.error("[BITHUMB][ERROR][CREATE ORDER] {}",e.getMessage());
        }
        return orderId;
    }

    /* Bithumb global 거래 취소 */
    public int cancelOrder(String type, String orderId, String coin, String currency) {

        int returnValue = TradeData.CODE_ERROR;
        String errorCode = "";
        String errorMsg = "";

        try {
            HashMap<String, String> rgParams = new HashMap<String, String>();
            rgParams.put("type", type);
            rgParams.put("order_id", orderId);
            rgParams.put("order_currency", coin);
            rgParams.put("payment_currency", currency);

            String api_host = TradeData.BITHUMB_URL + TradeData.BITHUMB_ENDPOINT_CANCEL_ORDER;
            HashMap<String, String> httpHeaders = getHttpHeaders(TradeData.BITHUMB_ENDPOINT_CANCEL_ORDER, rgParams, keyList.get(ACCESS_TOKEN), keyList.get(SECRET_KEY));
            String rgResultDecode = postHttpMethod(api_host,  rgParams, httpHeaders);

            JsonObject returnVal = gson.fromJson(rgResultDecode, JsonObject.class);
            String status        = gson.fromJson(returnVal.get("status"), String.class);
            if(status.equals("0000") || status.equals(ALREADY_TRADED)){
                orderId         = gson.fromJson(returnVal.get("order_id"), String.class);
                log.info("[BITHUMB][SUCCESS][CANCEL ORDER - response] response : {}", gson.toJson(returnVal));
            }else{
                log.error("[BITHUMB][ERROR][CANCEL ORDER - response] response :{}", gson.toJson(returnVal));
            }

        }catch(Exception e){
            log.error("[BITHUMB][ERROR][CANCEL ORDER] {}", e.getMessage());
        }
        return returnValue;
    }

    /* Get 각 코인에 등록한 통화 */
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
            log.error("[BITHUMB][ERROR][GET CURRENCY] {}",e.getMessage());
        }
        return returnVal;
    }


    private String postHttpMethod(String targetUrl, HashMap<String, String> rgParams,  HashMap<String, String> httpHeaders) {
        String response = "";

        // SSL
        try{
            log.info("[BITHUMB][POST HTTP] url : {} , request : {}", targetUrl, rgParams.toString());

            if (targetUrl.startsWith("https://")) {
                HttpRequest request = HttpRequest.get(targetUrl);
                // Accept all certificates
                request.trustAllCerts();
                // Accept all hostnames
                request.trustAllHosts();
            }

            HttpRequest request = null;
            request = new HttpRequest(targetUrl, "POST");
            request.readTimeout(10000);

            if (httpHeaders != null && !httpHeaders.isEmpty()) {
                httpHeaders.put("api-client-type", "2");
                request.headers(httpHeaders);
            }
            if (rgParams != null && !rgParams.isEmpty()) {
                request.form(rgParams);
            }
            response = request.body();
            log.info("[BITTHUMB][POST HTTP] response {}", response);
            request.disconnect();
        }catch (Exception e){
            log.error("[BITTHUMB][ERROR][POST HTTP] {}", e.getMessage());
        }
        return response;
    }

    // Bithumb 에서 제공하는 메서드
    private HashMap<String, String> getHttpHeaders(String endpoint, HashMap<String, String> rgData, String apiKey, String apiSecret) {

        String strData = mapToQueryString(rgData).replace("?", "");
        String nNonce = usecTime();

        strData = strData.substring(0, strData.length()-1);
        strData = encodeURIComponent(strData);

        HashMap<String, String> array = new HashMap<String, String>();
        String str = endpoint + ";"	+ strData + ";" + nNonce;

        String encoded = asHex(hmacSha512(str, apiSecret));
        array.put("Api-Key", apiKey);
        array.put("Api-Sign", encoded);
        array.put("Api-Nonce", String.valueOf(nNonce));

        return array;

    }

    // Bithumb 에서 제공하는 메서드
    public static String mapToQueryString(Map<String, String> map) {
        StringBuilder string = new StringBuilder();

        if (map.size() > 0) {
            string.append("?");
        }

        for (Map.Entry<String, String> entry : map.entrySet()) {
            string.append(entry.getKey());
            string.append("=");
            string.append(entry.getValue());
            string.append("&");
        }

        return string.toString();
    }

    // Bithumb 에서 제공하는 메서드
    private String usecTime() {
        return String.valueOf(System.currentTimeMillis());
    }

    // Bithumb 에서 제공하는 메서드
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

    // Bithumb 에서 제공하는 메서드
    private byte[] hmacSha512(String value, String key){
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    key.getBytes(DEFAULT_ENCODING),
                    HMAC_SHA512);

            Mac mac = Mac.getInstance(HMAC_SHA512);
            mac.init(keySpec);

            final byte[] macData = mac.doFinal( value.getBytes( ) );

            byte[] hex = new Hex().encode( macData );

            //return mac.doFinal(value.getBytes(DEFAULT_ENCODING));
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
    public static String asHex(byte[] bytes){
        return new String(Base64.encodeBase64(bytes));
    }

}
