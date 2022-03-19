package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.enumeration.ReturnCode;
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

    /** 해당 정보를 이용해 API 키를 셋팅한다 */
    private void setApiKey(String[] coinData, Exchange exchange) throws Exception{

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

    /* 포블게이트 자전거래 로직 */
    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[FOBLGATE][AUTOTRADE START]");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String symbol = getSymbol(Utils.splitCoinWithId(autoTrade.getCoin()), autoTrade.getExchange());
            // mode 처리
            String firstAction  = "";
            String secondAction = "";
            String mode         = autoTrade.getMode();
            if(UtilsData.MODE_RANDOM.equals(mode)){    // Trade Mode 가 랜덤일 경우 생성
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

            String orderId    = "";
            if( !(orderId  = createOrder(firstAction, price, cnt, symbol)).equals(ReturnCode.NO_DATA.getValue())){    // 매수/OrderId가 있으면 성공
                Thread.sleep(300);
                if(createOrder(secondAction,price,cnt,symbol).equals(ReturnCode.NO_DATA.getValue())){                   // 매도/OrderId가 없으면 실패
                    Thread.sleep(300);
                    cancelOrder(orderId,firstAction, price, symbol);                      // 매도 실패 시, 매수 취소
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[FOBLGATE][AUTOTRADE] Error : {}", e.getMessage());
        }
        log.info("[FOBLGATE][AUTOTRADE END]");
        return returnCode;
    }

    /** 호가유동성 function */
    @Override
    public int startLiquidity(Map list){
        int returnCode = ReturnCode.SUCCESS.getCode();

        Queue<String> sellQueue              = (LinkedList) list.get("sell");
        Queue<String> buyQueue               = (LinkedList) list.get("buy");
        Queue<Map<String,String>> cancelList = new LinkedList<>();

        try{
            log.info("[FOBLGATE][LIQUIDITY] START");
            String symbol = getSymbol(Utils.splitCoinWithId(liquidity.getCoin()), liquidity.getExchange());

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
                    orderId = createOrder(action, price, cnt, symbol);
                    if(!orderId.equals(ReturnCode.NO_DATA.getValue())){
                        Map<String, String> cancel = new HashMap<>();
                        cancel.put("orderId", orderId);
                        cancel.put("action",  action);
                        cancel.put("price",   price);
                        cancelList.add(cancel);
                    }
                    Thread.sleep(1000);
                }

                // 취소 로직
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

    /* 매매 긁기 */
    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[FOBLGATE][FISHINGTRADE] START");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String mode   = "";
            String symbol = getSymbol(Utils.splitCoinWithId(fishing.getCoin()), fishing.getExchange());

            boolean noIntervalFlag   = true;    // 해당 플래그를 이용해 마지막 매도/매수 후 바로 intervalTime 없이 바로 다음 매수/매도 진행
            boolean noMatchFirstTick = true;    // 해당 플래그를 이용해 매수/매도를 올린 가격이 현재 최상위 값이 맞는지 다른 사람의 코인을 사지 않게 방지

            for(String temp : list.keySet()){  mode = temp; }
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode);
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            /* Buy Start */
            log.info("[FOBLGATE][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt     = Utils.getRandomString(fishing.getMinContractCnt(), fishing.getMaxContractCnt());
                String orderId = ReturnCode.NO_DATA.getValue();
                if(UtilsData.MODE_BUY.equals(mode)) {
                    orderId = createOrder(BUY, tickPriceList.get(i), cnt, symbol);      // 매수
                }else{
                    orderId = createOrder(SELL, tickPriceList.get(i), cnt, symbol);     // 매도
                }
                if(!orderId.equals(ReturnCode.NO_DATA.getValue())){                                                // 매수/매도가 정상적으로 이뤄졌을 경우 데이터를 list에 담는다
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
                        log.info("[FOBLGATE][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        noMatchFirstTick = false;
                        break;
                    }

                    if(UtilsData.MODE_BUY.equals(mode)) {
                        orderId = createOrder(SELL, copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol);
                    }else{
                        orderId = createOrder(BUY, copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol);
                    }

                    if(!orderId.equals(ReturnCode.NO_DATA.getValue())){
                        cnt = cnt.subtract(cntForExcution);
                        Thread.sleep(500);
                        if(UtilsData.MODE_BUY.equals(mode)) {
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
                if(UtilsData.MODE_BUY.equals(mode)) {
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


    /**
     * Realtime Sync 거래
     * @param realtime
     * @return
     */
    public int startRealtimeTrade(JsonObject realtime, boolean resetFlag) {
        log.info("[FOBLGATE][REALTIME SYNC TRADE START]");
        int returnCode   = ReturnCode.SUCCESS.getCode();
        String realtimeChangeRate = "signed_change_rate";

        try {
            boolean isStart      = false;
            String symbol        = getSymbol(Utils.splitCoinWithId(realtimeSync.getCoin()), realtimeSync.getExchange());
            String[] currentTick = getTodayTick(symbol);
            //            String openingPrice  = currentTick[0];
            if(resetFlag){
                realtimeTargetInitRate = currentTick[1];
                log.info("[FOBLGATE][REALTIME SYNC TRADE] Set init open rate : {} ", realtimeTargetInitRate);
            }
            String openingPrice  = realtimeTargetInitRate;
            String currentPrice  = currentTick[1];
            log.info("[FOBLGATE][REALTIME SYNC TRADE] open:{}, current:{} ", openingPrice, currentPrice);

            String orderId       = ReturnCode.NO_DATA.getValue();
            String targetPrice   = "";
            String action        = "";
            String mode          = "";
            String cnt           = Utils.getRandomString(realtimeSync.getMinTradeCnt(), realtimeSync.getMaxTradeCnt());

            // 1. 최소/최대 매수 구간에 있는지 확인
            int isInRange        = isMoreOrLessPrice(currentPrice);
            if(isInRange != 0){              // 구간 밖일 경우
                if(isInRange == -1){         // 지지선보다 낮을 경우
                    mode         = UtilsData.MODE_BUY;
                    action       = BUY;
                    targetPrice  = realtimeSync.getMinPrice();
                }else if(isInRange == 1){    // 저항선보다 높을 경우
                    mode         = UtilsData.MODE_SELL;
                    action       = SELL;
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

            // 2. %를 맞추기 위한 매수/매도 로직
            if(isStart){
                if( !(orderId = createOrder(action, targetPrice, cnt, symbol)).equals(ReturnCode.NO_DATA.getValue())){    // 매수/OrderId가 있으면 성공
                    Thread.sleep(300);

                    // 3. bestoffer set 로직
                    JsonArray array = makeBestofferAfterRealtimeSync(targetPrice, mode);
                    for (int i = 0; i < array.size(); i++) {
                        JsonObject object       = array.get(i).getAsJsonObject();
                        String bestofferPrice   = object.get("price").getAsString();
                        String bestofferCnt     = object.get("cnt").getAsString();
                        String bestofferOrderId = ReturnCode.NO_DATA.getValue();

                        if( !(bestofferOrderId = createOrder(action, bestofferPrice, bestofferCnt, symbol)).equals(ReturnCode.NO_DATA.getValue())){
                            log.info("[FOBLGATE][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                    }

                    // 베스트 오퍼 체크 작업 이후 기존에 걸었던 매수에 대해 캔슬
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
     * 현재 Tick 가져오기
     * @param exchange
     * @param coinWithId
     * @return [ 시가 , 종가 ] String Array
     */
    private String[] getTodayTick(String symbol) throws Exception{
        String[] returnRes   = new String[2];
        String typeDay       = "1";     // 1일 경우 일단위로 데이터 반환
        String min           = "240";   // typeDay 가 0일 경우에만 의미있음.
        String startDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String dateCount     = "2";     // 하루치(2일 경우 어제, 3일경우 그제까지 반환)

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
            // 전날의 종가가 대비로 %가 움직이기에, 해당 값에 맞게 맞춰줘야 함.
            // [ 조회시간 | 시가 | 고가 | 저가 | 종가(현재가) | volume? ] String Array
            JsonObject object = returnVal.get("data").getAsJsonObject();
            JsonArray array   = object.get("series").getAsJsonArray();
            returnRes[0]      = array.get(0).getAsString().split("\\|")[4];  // 전날의 종가가 오늘의 시가
            returnRes[1]      = array.get(1).getAsString().split("\\|")[4];  // 현재의 종가(현재가)
        }else{
            log.error("[FOBLGATE][GET TODAY TICK] Response:{}", gson.toJson(returnVal));
            throw new Exception(gson.toJson(returnVal));
        }

        return returnRes;
    }



    /** 포블게이트 매수/매도 로직 */

    /**
     * 매수/매도 로직
     * @return 성공 시, order Id. 실패 시, ReturnCode.NO_DATA
     */
    public String createOrder(String type, String price, String cnt, String symbol){

        String orderId = ReturnCode.NO_DATA.getValue();
        try{
            Map<String, String> header = setDefaultRequest(keyList.get(USER_ID), symbol,type,keyList.get(PUBLIC_KEY));
            header.put("price",     price);   // price
            header.put("amount",    cnt);     // cnt
            String secretHash = makeApiHash(keyList.get(PUBLIC_KEY) + keyList.get(USER_ID) + symbol + type + price+ cnt+ keyList.get(SECRET_KEY));

            JsonObject returnVal = postHttpMethod(UtilsData.FOBLGATE_CREATE_ORDER, secretHash, header);
            String status        = gson.fromJson(returnVal.get("status"), String.class);
            if(status.equals(SUCCESS)){
                orderId = gson.fromJson(returnVal.get("data"), String.class);
                log.info("[FOBLEGATE][CREATE ORDER] Success response : {}", gson.toJson(returnVal));
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
     * 취소 로직
     * @return 성공 시, ReturnCode.Success. 실패 시, ReturnCode.Fail
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
     * API 이용 전 Hash 값을 만드는 작업
     * @param targetStr
     * @throws Exception 예외는 호출한 곳에서 처리하도록 진행.
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
     * @param secretHash - 암호화한 값
     * @param formData   - post에 들어가는 body 데이터
     * @throws HTTP 예외 외에도, 서버 에러코드 전송 시에도 예외를 던져 호출한곳에서 처리하도록 진행하였음.
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

    // 거래소에 맞춰 심볼 반환
    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0] + "/" + getCurrency(exchange, coinData[0], coinData[1]);
    }
}
