package com.coin.autotrade.service.function;

import com.coin.autotrade.common.BeanUtils;
import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.*;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.service.CoinService;
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
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
public class DcoinFunction extends ExchangeFunction{

    final private String ACCESS_TOKEN         = "apiToken";
    final private String SECRET_KEY           = "secretKey";
    final private String BUY                  = "BUY";
    final private String SELL                 = "SELL";
    private Map<String, String> keyList       = new HashMap<>();
    private ExchangeRepository exchageRepository;


    @Override
    public void initClass(AutoTrade autoTrade, User user, Exchange exchange){
        super.autoTrade = autoTrade;
        setCommonValue(user, exchange);
        setCoinToken(ServiceCommon.setCoinData(autoTrade.getCoin()));
    }

    @Override
    public void initClass(Liquidity liquidity, User user, Exchange exchange){
        super.liquidity = liquidity;
        setCommonValue(user, exchange);
        setCoinToken(ServiceCommon.setCoinData(liquidity.getCoin()));
    }

    @Override
    public void initClass(Fishing fishing , User user,  Exchange exchange,CoinService coinService){
        super.fishing     = fishing;
        super.coinService = coinService;
        setCommonValue(user, exchange);
        setCoinToken(ServiceCommon.setCoinData(fishing.getCoin()));
    }

    private void setCommonValue(User user,  Exchange exchange){
        super.user     = user;
        super.exchange = exchange;
    }

    private void setCoinToken(String[] coinData){
        // Set token key
        try{
            for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
                if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1]) ){
                    keyList.put(ACCESS_TOKEN, exCoin.getPublicKey());
                    keyList.put(SECRET_KEY,   exCoin.getPrivateKey());
                }
            }
        }catch (Exception e){
            log.error("[DCOIN][ERROR][SET COIN TOKEN] error : {} ", e.getMessage());
        }
    }


    /**
     * Auto Trade Start
     * @param symbol - coin + currency
     */
    @Override
    public int startAutoTrade(String price, String cnt){

        log.info("[DCOIN][AUTOTRADE] Start");

        int returnCode = DataCommon.CODE_SUCCESS;
        try{
            String[] coinData = ServiceCommon.setCoinData(autoTrade.getCoin());
            String     symbol = coinData[0] + "" + getCurrency(getExchange(), coinData[0], coinData[1]);

            // mode 처리
            String mode = autoTrade.getMode();
            if(DataCommon.MODE_RANDOM.equals(mode)){
                mode = (ServiceCommon.getRandomInt(0,1) == 0) ? DataCommon.MODE_BUY : DataCommon.MODE_SELL;
            }

            if(DataCommon.MODE_BUY.equals(mode)){
                String orderId = "";
                if(!(orderId = createOrder("BUY",price, cnt, symbol)).equals("")){
                    if(createOrder("SELL",price, cnt, symbol).equals("")){          // SELL 모드가 실패 시,
                        cancelOrder(symbol, orderId);
                    }
                }
            }else if(DataCommon.MODE_SELL.equals(mode)){
                String orderId = "";
                if(!(orderId = createOrder("SELL",price, cnt, symbol)).equals("")){
                    if(createOrder("BUY",price, cnt, symbol).equals("")){           // BUY 모드가 실패 시,
                        cancelOrder(symbol, orderId);
                    }
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[DCOIN][ERROR][AUTOTRADE] {}", e.getMessage());
        }

        log.info("[DCOIN][AUTOTRADE] End");

        return returnCode;
    }

    /* 호가유동성 메서드 */
    @Override
    public int startLiquidity(Map list){
        int returnCode = DataCommon.CODE_SUCCESS;

        Queue<String> sellQueue = (LinkedList) list.get("sell");
        Queue<String> buyQueue  = (LinkedList) list.get("buy");
        List<Map<String,String>> CancelList = new ArrayList();

        try{
            log.info("[DCOIN][LIQUIDITY] Start");
            String[] coinData = ServiceCommon.setCoinData(liquidity.getCoin());
            String symbol     = coinData[0] + "" + getCurrency(getExchange(),coinData[0], coinData[1]);
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
                    firstOrderId = createOrder(BUY, firstPrice, firstCnt, symbol);

                    Thread.sleep(300);
                    secondsPrice   = sellQueue.poll();
                    secondsOrderId = createOrder(SELL, secondsPrice, secondsCnt, symbol);
                }else if(buyQueue.size() > 0 && sellQueue.size() > 0 && randomMode.equals(SELL)){
                    firstPrice   = sellQueue.poll();
                    firstOrderId = createOrder(SELL, firstPrice, firstCnt, symbol);

                    Thread.sleep(300);
                    secondsPrice   = buyQueue.poll();
                    secondsOrderId = createOrder(BUY, secondsPrice, secondsCnt, symbol);
                }

                if(!firstOrderId.equals("") || !secondsOrderId.equals("")){
                    Thread.sleep(1000);
                    if(!firstOrderId.equals("")){
                        cancelOrder(symbol, firstOrderId);
                    }
                    if(!secondsOrderId.equals("")){
                        Thread.sleep(300);
                        cancelOrder(symbol, secondsOrderId);
                    }
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[DCOIN][ERROR][LIQUIDITY] {}", e.getMessage());
        }
        log.info("[DCOIN][LIQUIDITY] End");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[DCOIN][FISHINGTRADE START]");

        int returnCode    = DataCommon.CODE_SUCCESS;

        try{
            String[] coinData = ServiceCommon.setCoinData(fishing.getCoin());
            String     symbol = coinData[0] + "" + getCurrency(getExchange(), coinData[0], coinData[1]);

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
                    orderId = createOrder("BUY", tickPriceList.get(i), cnt, symbol);
                }else{
                    orderId = createOrder("SELL", tickPriceList.get(i), cnt, symbol);
                }
                if(!orderId.equals("")){                                                // 매수/매도가 정상적으로 이뤄졌을 경우 데이터를 list에 담는다
                    Map<String, String> orderMap = new HashMap<>();
                    orderMap.put("price" ,tickPriceList.get(i));
                    orderMap.put("cnt" ,cnt);
                    orderMap.put("order_id" ,orderId);
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
                    String orderPrice = copiedOrderMap.get("price");
                    if (!orderPrice.equals(nowFirstTick)) {
                        log.info("[DCOIN][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        noMatchFirstTick = false;
                        break;
                    }

                    if(DataCommon.MODE_BUY.equals(mode)) {
                        orderId = createOrder("SELL", copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol);
                    }else{
                        orderId = createOrder("BUY", copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol);
                    }

                    if(!orderId.equals("")){
                        cnt = cnt.subtract(cntForExcution);
                    }else{
                        break;
                    }
                }
                // 무조건 취소
                Thread.sleep(1000);
                cancelOrder(symbol, orderList.get(i).get("order_id"));

            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[DCOIN][ERROR][FISHINGTRADE] {}", e.getMessage());
        }

        log.info("[DCOIN][FISHINGTRADE END]");
        return returnCode;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = "";
        try{
            String coin = coinWithId[0];
            String coinId = coinWithId[1];
            String inputLine;
            String currency = getCurrency(exchange, coin, coinId);
            if(currency.equals("")){
                log.error("[DCOIN][ERROR][ORDER BOOK] There is no coin");
                return "";
            }
            String symbol = coin.toLowerCase().concat(currency);

            String encodedData = "symbol=" + URLEncoder.encode(symbol) + "&type=" + URLEncoder.encode("step0");

            String request = DataCommon.DCOIN_ORDERBOOK + "?" + encodedData;
            URL url = new URL(request);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Context-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            connection.setConnectTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setReadTimeout(DataCommon.TIMEOUT_VALUE);

            log.info("[DCOIN][ORDER BOOK - Request]  symbol:{}, type:{}", symbol, "step0");

            int returnCode = connection.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer response = new StringBuffer();
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();

            returnRes = response.toString();

        }catch (Exception e){
            log.error("[DCOIN][ERROR][ORDER BOOK] {}",e.getMessage());
        }

        return returnRes;
    }


    /** 생성자로서, 생성될 때, injection**/
    public DcoinFunction(){
        exchageRepository   = (ExchangeRepository) BeanUtils.getBean(ExchangeRepository.class);
    }

    /* 호가 조회 시, 사용하기위해 Set */
    public void setExchange(Exchange exchange){
        super.exchange = exchange;
    }

    /* 호가 조회 시, 사용하기위해 get */
    public Exchange getExchange(){
        return super.exchange;
    }

    /**
     * 매수 매도 로직
     * @param side   - SELL, BUY
     * @param symbol - coin + currency
     */
    public String createOrder(String side, String price, String cnt, String symbol) {

        String orderId   = "";
        String errorCode = "";
        String errorMsg  = "";

        try {
            // DCoin 의 경우, property 값들이 오름차순으로 입력되야 해서, 공통 함수로 빼기 어려움.
            JsonObject header = new JsonObject();
            header.addProperty("api_key", keyList.get(ACCESS_TOKEN));
            header.addProperty("price", Double.parseDouble(price));
            header.addProperty("side", side);
            header.addProperty("symbol", symbol.toLowerCase());
            header.addProperty("type", 1);
            header.addProperty("volume", Double.parseDouble(cnt));
            header.addProperty("sign",  createSign(gson.toJson(header)));

            String params = makeEncodedParas(header);
            JsonObject json = postHttpMethod(DataCommon.DCOIN_CREATE_ORDER, params);
            String result = json.get("code").toString().replace("\"", "");
            if ("0".equals(result)) {
                String data        = json.get("data").toString().replace("\"", "");
                JsonObject dataObj = gson.fromJson(data, JsonObject.class);
                orderId = dataObj.get("order_id").toString().replace("\"", "");
                log.info("[DCOIN][SUCCESS][CREATE ORDER - response] response :{}", gson.toJson(json));
            } else {
                log.error("[DCOIN][ERROR][CREATE ORDER - response] response {}", gson.toJson(json));
            }
        }catch(Exception e){
            log.error("[DCOIN][ERROR][CREATE ORDER] {}", e.getMessage());
        }
        return orderId;
    }

    /**
     * 매도/매수 거래 취소 로직
     * @param symbol   - coin + currency
     */
    public int cancelOrder(String symbol, String orderId) {

        int returnValue = DataCommon.CODE_ERROR;
        String errorCode = "";
        String errorMsg = "";

        try {
            JsonObject header = new JsonObject();
            header.addProperty("api_key", keyList.get(ACCESS_TOKEN));
            header.addProperty("order_id", orderId);
            header.addProperty("symbol", symbol.toLowerCase());
            header.addProperty("sign", createSign(gson.toJson(header)));

            JsonObject json = postHttpMethod(DataCommon.DCOIN_CANCEL_ORDER, makeEncodedParas(header));
            String result   = json.get("code").toString().replace("\"", "");
            if ("0".equals(result)) {
                returnValue = DataCommon.CODE_SUCCESS;
                log.info("[DCOIN][SUCCESS][CANCEL ORDER - response] response:{}", gson.toJson(json));
            } else {
                log.error("[DCOIN][ERROR][CANCEL ORDER - response] response:{}", gson.toJson(json));
            }
        }catch(Exception e){
            log.error("[DCOIN][ERROR][CANCEL ORDER] {}", e.getMessage());
        }
        return returnValue;
    }

    /** 암호화된 값 생성 */
    public String createSign(String params){
        String returnVal = "";
        String replaceParams = params.replace("\"","").replace("{","").replace("}","").replace(":","").replace(",","");
        String message = replaceParams.concat(keyList.get(SECRET_KEY));
        try {
            MessageDigest md5 = MessageDigest.getInstance("md5");
            byte[] code = md5.digest(message.getBytes());
            StringBuffer sb = new StringBuffer();
            for (byte b : code) {
                sb.append(String.format("%02x", b));
            }
            returnVal = sb.toString();

        } catch (Exception e) {
            e.printStackTrace();
            returnVal = null;
            log.error("[DCOIN][ERROR][Create Sign] {}", e.getMessage());
        }
        return returnVal;
    }

    public String makeEncodedParas(JsonObject header){
        String returnVal = "";
        int i =0;
        for(String key : header.keySet()){
            String value = header.get(key).toString().replace("\"","");
            if(i < header.size() -1){
                returnVal += (key + "=" + value + "&");
            }else{
                returnVal += (key + "=" + value);
            }
            i++;
        }

        return returnVal;
    }

    /* DCOIN 의 경우 통화 기준으로 필요함.*/
    public String getCurrency(Exchange exchange, String coin, String coinId){
        String returnVal = "";
        try {
            // 거래소를 체크하는 이유는 여러거래소에서 같은 코인을 할 수 있기에
            if(exchange.getExchangeCoin().size() > 0){
                for(ExchangeCoin data : exchange.getExchangeCoin()){
                    if(data.getCoinCode().equals(coin) && data.getId() == Long.parseLong(coinId)){
                        returnVal = data.getCurrency();
                    }
                }
            }
        }catch(Exception e){
            log.error("[DCOIN][ERROR][Get Currency] {}",e.getMessage());
        }
        return returnVal;
    }

    /* HTTP POST Method for coinone */
    public JsonObject postHttpMethod(String targetUrl, String payload) {
        URL url;
        String inputLine;
        JsonObject returnObj = null;
        try{

            log.info("[DCOIN][POST HTTP] request:{}", payload);

            url = new URL(targetUrl);
            HttpURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setReadTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setRequestProperty("Context-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");

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
            log.error("[DCOIN][ERROR][POST HTTP] {}", e.getMessage());
        }
        return returnObj;
    }
}
