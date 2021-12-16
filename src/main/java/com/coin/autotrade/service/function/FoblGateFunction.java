package com.coin.autotrade.service.function;

import com.coin.autotrade.common.BeanUtils;
import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.*;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.service.CoinService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
public class FoblGateFunction extends ExchangeFunction{

    private String API_KEY               = "apiKey";
    private String SECRET_KEY            = "secretKey";
    private String USER_ID               = "userId";
    private String SELL                  = "ask";
    private String BUY                   = "bid";
    private String ALREADY_TRADED        = "5004";
    private Map<String, String> keyList  = new HashMap<>();

    ExchangeRepository exchageRepository;

    /* Foblgate Function initialize for autotrade */
    @Override
    public void initClass(AutoTrade autoTrade, User user, Exchange exchange){
        super.autoTrade = autoTrade;
        setCommonValue(user, exchange);
    }

    /* Foblgate Function initialize for liquidity */
    @Override
    public void initClass(Liquidity liquidity, User user, Exchange exchange){
        super.liquidity = liquidity;
        setCommonValue(user, exchange);
    }

    /* Foblgate Function initialize for fishing */
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

    /* 포블게이트 자전거래 로직 */
    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[FOBLGATE][AUTOTRADE START]");
        int returnCode    = DataCommon.CODE_SUCCESS;

        try{
            String[] coinData = ServiceCommon.splitCoinWithId(autoTrade.getCoin());
            String coin       = coinData[0];
            String coinId     = coinData[1];
            String symbol     = coinData[0] + "/" + getCurrency(exchange, coinData[0], coinData[1]);
            setApiKey(coin, coinId);    // Key 값 셋팅

            // mode 처리
            String mode = autoTrade.getMode();
            if(DataCommon.MODE_RANDOM.equals(mode)){
                mode = (ServiceCommon.getRandomInt(0,1) == 0) ? DataCommon.MODE_BUY : DataCommon.MODE_SELL;
            }

            // 1 : 매수 , 2 : 매도
            if(DataCommon.MODE_BUY.equals(mode)){
                String buyOrderId  = "";
                if( !(buyOrderId = createOrder(BUY, price, cnt, symbol)).equals("")){    // 매수/OrderId가 있으면 성공
                    Thread.sleep(300);
                    if(createOrder(SELL,price,cnt,symbol).equals("")){                   // 매도/OrderId가 없으면 실패
                        Thread.sleep(300);
                        cancelOrder(buyOrderId,BUY, price, symbol);                      // 매도 실패 시, 매수 취소
                    }
                }
            }else if(DataCommon.MODE_SELL.equals(mode)){
                String sellOrderId  = "";
                if( !(sellOrderId = createOrder(SELL,price, cnt, symbol)).equals("")){
                    Thread.sleep(300);
                    if(createOrder(BUY,price, cnt, symbol).equals("")){
                        Thread.sleep(300);
                        cancelOrder(sellOrderId,SELL, price, symbol);
                    }
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[FOBLGATE][ERROR][AUTOTRADE] {}", e.getMessage());
        }

        log.info("[FOBLGATE][AUTOTRADE END]");
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
            log.info("[FOBLGATE][LIQUIDITY] Start");
            String[] coinData = ServiceCommon.splitCoinWithId(liquidity.getCoin());
            String coin       = coinData[0];
            String coinId     = coinData[1];
            setApiKey(coin, coinId);    // Key 값 셋팅
            String symbol = coinData[0] + "/" + getCurrency(exchange, coinData[0], coinData[1]);
            int minCnt = liquidity.getMinCnt();
            int maxCnt = liquidity.getMaxCnt();


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
                    if(randomMode.equals(BUY)){
                        if(!firstOrderId.equals("")){
                            cancelOrder(firstOrderId,   BUY, firstPrice, symbol);
                        }
                        Thread.sleep(300);
                        if(!secondsOrderId.equals("")){
                            cancelOrder(secondsOrderId, SELL, secondsPrice, symbol);
                        }
                    }else if(randomMode.equals(SELL)){
                        if(!firstOrderId.equals("")){
                            cancelOrder(firstOrderId,   SELL, firstPrice, symbol);
                        }
                        Thread.sleep(300);
                        if(!secondsOrderId.equals("")){
                            cancelOrder(secondsOrderId, BUY, secondsPrice, symbol);
                        }
                    }
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[FOBLGATE][ERROR][LIQUIDITY] {}", e.getMessage());
        }
        log.info("[FOBLGATE][LIQUIDITY] End");
        return returnCode;
    }

    /* 매매 긁기 */
    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[FOBLGATE][FISHINGTRADE START]");

        int returnCode    = DataCommon.CODE_SUCCESS;

        try{
            String mode       = "";
            String[] coinData = ServiceCommon.splitCoinWithId(fishing.getCoin());
            String coin       = coinData[0];
            String coinId     = coinData[1];
            setApiKey(coin, coinId);    // Key 값 셋팅
            String symbol     = coinData[0] + "/" + getCurrency(exchange, coinData[0], coinData[1]);

            boolean noIntervalFlag   = true;    // 해당 플래그를 이용해 마지막 매도/매수 후 바로 intervalTime 없이 바로 다음 매수/매도 진행
            boolean noMatchFirstTick = true;    // 해당 플래그를 이용해 매수/매도를 올린 가격이 현재 최상위 값이 맞는지 다른 사람의 코인을 사지 않게 방지

            for(String temp : list.keySet()){  mode = temp; }
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode);


            /* 모드가 매수우선일 경우 */

            ArrayList<Map<String, String>> orderList = new ArrayList<>();
            /* Buy Start */
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double) fishing.getMinContractCnt(), (double) fishing.getMaxContractCnt()) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                String orderId = "";
                if(DataCommon.MODE_BUY.equals(mode)) {
                    orderId = createOrder(BUY, tickPriceList.get(i), cnt, symbol);      // 매수
                }else{
                    orderId = createOrder(SELL, tickPriceList.get(i), cnt, symbol);     // 매도
                }
                if(!orderId.equals("")){                                                // 매수/매도가 정상적으로 이뤄졌을 경우 데이터를 list에 담는다
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
                        orderId = createOrder(SELL, copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol);
                    }else{
                        orderId = createOrder(BUY, copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol);
                    }

                    if(!orderId.equals("")){
                        cnt = cnt.subtract(cntForExcution);
                        Thread.sleep(500);
                        if(DataCommon.MODE_BUY.equals(mode)) {
                            cancelOrder(orderId, SELL, copiedOrderMap.get("price"),symbol );
                        }else{
                            cancelOrder(orderId, BUY, copiedOrderMap.get("price"),symbol );
                        }
                    }else{
                        break;
                    }
                }
                // 무조건 취소
                Thread.sleep(500);
                if(DataCommon.MODE_BUY.equals(mode)) {
                    cancelOrder(orderList.get(i).get("order_id"), BUY, orderList.get(i).get("price") ,symbol);
                }else{
                    cancelOrder(orderList.get(i).get("order_id"), SELL, orderList.get(i).get("price") ,symbol);
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[FOBLGATE][ERROR][FISHINGTRADE] {}", e.getMessage());
        }

        log.info("[FOBLGATE][FISHINGTRADE END]");
        return returnCode;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        if(getExchange() == null){ setExchange(exchange); } // Exchange setting
        String coin   = coinWithId[0];
        String coinId = coinWithId[1];
        setApiKey(coin, coinId);

        String returnRes = "";
        try{
            log.info("[FOBLGATE][ORDER BOOK START]");
            String currency = getCurrency(exchange, coin, coinId);
            if(currency.equals("")){
                log.error("[FOBLGATE][ERROR][ORDER BOOK] There is no coin");
            }
            String pairName = coin+"/"+currency;

            Map<String, String> header = new HashMap<>();
            header.put("apiKey",keyList.get(API_KEY));
            header.put("pairName",pairName);
            String secretHash = makeApiHash(keyList.get(API_KEY) + pairName + keyList.get(SECRET_KEY));
            log.info("[FOBLGATE][ORDER BOOK - REQUEST] request:{}, hash:{}", header.toString(), secretHash);

            JsonObject returnVal = postHttpMethod(DataCommon.FOBLGATE_ORDERBOOK, secretHash, header);
            String status        = gson.fromJson(returnVal.get("status"), String.class);
            if(status.equals("0")){
                returnRes = gson.toJson(returnVal);
                log.info("[FOBLGATE][SUCCESS][ORDER BOOK]");
            }else{
                log.error("[FOBLGATE][ERROR][ORDER BOOK - RESPONSE] response:{}", gson.toJson(returnVal));
            }

            log.info("[FOBLGATE][ORDER BOOK END]");

        }catch (Exception e){
            log.error("[FOBLGATE][ERROR][ORDER BOOK] {}",e.getMessage());
        }

        return returnRes;
    }

    /** 생성자로서, 생성될 때, injection**/
    public FoblGateFunction(){
        exchageRepository   = (ExchangeRepository) BeanUtils.getBean(ExchangeRepository.class);
    }

    /** 해당 user 정보를 이용해 API 키를 셋팅한다 */
    public void setApiKey(String coin, String coinId){
        try{
            // 키 값이 셋팅되어 있지 않다면
            if(keyList.size() < 1){
                // Set token key
                for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
                    if(exCoin.getCoinCode().equals(coin) && exCoin.getId() == Long.parseLong(coinId)){
                        keyList.put(USER_ID,     exCoin.getExchangeUserId());
                        keyList.put(API_KEY,     exCoin.getPublicKey());
                        keyList.put(SECRET_KEY,  exCoin.getPrivateKey());
                    }
                }
                log.info("[FOBLGATE][Set Key] First Key setting in instance API:{}, secret:{}",keyList.get(API_KEY), keyList.get(SECRET_KEY));
            }
        }catch (Exception e){
            log.error("[FOBLGATE][ERROR][Set Key] {}",e.getMessage());
        }

    }


    /** 포블게이트 매수/매도 로직 */
    public String createOrder(String type, String price, String cnt, String symbol){

        String orderId = "";
        try{
            Map<String, String> header = setDefaultRequest(keyList.get(USER_ID), symbol,type,keyList.get(API_KEY));
            header.put("price",     price);                 // price
            header.put("amount",    cnt);                   // cnt
            String secretHash = makeApiHash(keyList.get(API_KEY) + keyList.get(USER_ID) + symbol + type + price+ cnt+ keyList.get(SECRET_KEY));

            JsonObject returnVal = postHttpMethod(DataCommon.FOBLGATE_CREATE_ORDER, secretHash, header);
            String status        = gson.fromJson(returnVal.get("status"), String.class);
            if(status.equals("0")){
                orderId = gson.fromJson(returnVal.get("data"), String.class);
                log.info("[FOBLEGATE][SUCCESS][CREATE ORDER - response] response : {}", gson.toJson(returnVal));
            }else{
                String errorMsg = returnVal.get("message").toString();
                log.error("[FOBLGATE][ERROR][CREATE ORDER - response] response :{}", gson.toJson(returnVal));
            }

        }catch (Exception e){
            log.error("[FOBLGATE][ERROR][CREATE ORDER] {}",e.getMessage());
        }
        return orderId;
    }

    /** 포블게이트 매수/매도 취소로직 */
    public int cancelOrder(String ordNo, String type, String price, String symbol){

        int returnCode = DataCommon.CODE_ERROR;
        try{

            Map<String, String> header = setDefaultRequest(keyList.get(USER_ID), symbol, type, keyList.get(API_KEY));
            header.put("ordNo",     ordNo);                 // order Id
            header.put("ordPrice",  price);                 // price
            String secretHash = makeApiHash(keyList.get(API_KEY) + keyList.get(USER_ID) + symbol + ordNo + type + price+ keyList.get(SECRET_KEY));

            JsonObject returnVal = postHttpMethod(DataCommon.FOBLGATE_CANCEL_ORDER, secretHash, header);
            String status        = gson.fromJson(returnVal.get("status"), String.class);
            if(status.equals("0") || status.equals(ALREADY_TRADED)){
                returnCode = DataCommon.CODE_SUCCESS;
                log.info("[FOBLGATE][SUCCESS][CANCEL ORDER - response] response:{}", gson.toJson(returnVal));
            }else{
                String errorMsg = returnVal.get("message").toString();
                log.error("[FOBLGATE][ERROR][CANCEL ORDER - response] response:{}", gson.toJson(returnVal));
            }

        }catch (Exception e){
            log.error("[FOBLGATE][ERROR][CANCEL ORDER] {}",e.getMessage());
        }
        return returnCode;
    }



    /* Fobl Gate 의 경우 통화 기준으로 필요함. */
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
            log.error("[FOBLGATE][ERROR][GET CURRENCY] {}",e.getMessage());
        }
        return returnVal;
    }


    /* API 이용하기 전, 해쉬값으로 변환하기 위한 메서드 */
    public String makeApiHash(String targetStr){
        StringBuffer sb = new StringBuffer();
        try{
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
        }catch (Exception e){
            log.error("[FOBLGATE][ERROR][API HASH] {}",e.getMessage());
        }

        return "";
    }

    /**
     * HTTP POST Method for Foblgate
     * @param targetUrl  - target url
     * @param secretHash - 암호화한 값
     * @param formData   - post에 들어가는 body 데이터
     */
    public JsonObject postHttpMethod(String targetUrl, String secretHash,  Map<String, String> datas ) {
        URL url;
        JsonObject returnObj = new JsonObject();
        String inputLine     = "";
        String twoHyphens    = "--";
        String boundary      = "*******";
        String lineEnd       = "\r\n";
        String delimiter     = twoHyphens + boundary + lineEnd;


        try{
            log.info("[FOBLGATE][POST REQUEST] request:{}, secretHash:{}", datas.toString(), secretHash);

            url = new URL(targetUrl);

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestProperty("secretheader", secretHash);
            connection.setRequestProperty("Content-Type","multipart/form-data;boundary="+boundary);
            connection.setRequestProperty("Accept"      , "*/*");
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setReadTimeout(DataCommon.TIMEOUT_VALUE);
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
            log.error("[FOBLGATE][ERROR][FOBLGATE POST HTTP] {}", e.getMessage());
        }

        return returnObj;
    }

    /**
     * request를 날릴때 Map에 데이터를 담아서 객체형식으로 보내줘야 하는데, 모든 요청에 공통으로 사용되는 값들
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


    public Exchange getExchange() { return super.exchange;  }
    public void setExchange(Exchange exchange) { super.exchange = exchange; }

}
