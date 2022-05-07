package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.common.enumeration.Trade;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kucoin.sdk.KucoinClientBuilder;
import com.kucoin.sdk.KucoinRestClient;
import com.kucoin.sdk.exception.KucoinApiException;
import com.kucoin.sdk.model.enums.ApiKeyVersionEnum;
import com.kucoin.sdk.rest.request.OrderCreateApiRequest;
import com.kucoin.sdk.rest.response.OrderCancelResponse;
import com.kucoin.sdk.rest.response.OrderCreateResponse;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
public class KucoinImp extends AbstractExchange {
    final private String BUY                  = "buy";
    final private String SELL                 = "sell";
    final private String ALREADY_TRADE        = "400100";

    /** Kucoin libirary **/
    private static KucoinRestClient kucoinRestClient;

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
        super.coinService  = coinService;
        setCoinToken(Utils.splitCoinWithId(realtimeSync.getCoin()), realtimeSync.getExchange());
    }

    @Override
    public void initClass(Fishing fishing, CoinService coinService)throws Exception {
        super.fishing     = fishing;
        super.coinService = coinService;
        setCoinToken(Utils.splitCoinWithId(fishing.getCoin()), fishing.getExchange());
    }

    private void setCoinToken(String[] coinData, Exchange exchange) throws Exception {

        // Set token key
        if(keyList.isEmpty()){
            for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
                if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1]) ){
                    keyList.put(PUBLIC_KEY, exCoin.getPublicKey());
                    keyList.put(SECRET_KEY,   exCoin.getPrivateKey());
                    keyList.put(API_PASSWORD, exCoin.getApiPassword());
                }
            }
            log.info("[GATEIO][SET API KEY] First Key setting in instance API:{}, secret:{}, password:{}",keyList.get(PUBLIC_KEY), keyList.get(SECRET_KEY), keyList.get(API_PASSWORD));
            if(keyList.isEmpty()){
                String msg = "There is no match coin. " + Arrays.toString(coinData) + " " + exchange.getExchangeCode();
                throw new Exception(msg);
            }else{
                setKucoinRestClient();
            }
        }
    }

    private void setKucoinRestClient() throws Exception{
        kucoinRestClient = new KucoinClientBuilder()
                .withBaseUrl(UtilsData.KUCOIN_URL)
                .withApiKey(keyList.get(PUBLIC_KEY), keyList.get(SECRET_KEY), keyList.get(API_PASSWORD))
                .withApiKeyVersion(ApiKeyVersionEnum.V2.getVersion())
                .buildRestClient();
    }

    /**
     * Auto Trade Start
     * @param symbol - coin + currency
     */
    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[KUCOIN][AUTOTRADE] START");
        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String[] coinWithId = Utils.splitCoinWithId(autoTrade.getCoin());
            Exchange exchange   = autoTrade.getExchange();
            String symbol       = getSymbol(coinWithId, exchange);
            Trade mode          = getMode(autoTrade.getMode());
            String firstAction  = (mode == Trade.BUY) ? BUY : SELL;
            String secondAction = (mode == Trade.BUY) ? SELL : BUY;

            String firstOrderId = createOrder(firstAction, price, cnt, coinWithId, exchange);
            if(Utils.isSuccessOrder(firstOrderId)){
                Thread.sleep(500);
                String secondOrderId = createOrder(secondAction, price, cnt, coinWithId, exchange);

                cancelOrder(firstOrderId);
                if(Utils.isSuccessOrder(secondOrderId)){
                    Thread.sleep(500);
                    cancelOrder(secondOrderId);                      // 매도 실패 시, 매수 취소
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[KUCOIN][AUTOTRADE] ERROR : {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[KUCOIN][AUTOTRADE] END");

        return returnCode;
    }

    /* 호가유동성 메서드 */
    @Override
    public int startLiquidity(Map list){
        int returnCode = ReturnCode.SUCCESS.getCode();

        Queue<String> sellQueue  = (LinkedList) list.get("sell");
        Queue<String> buyQueue   = (LinkedList) list.get("buy");
        Queue<String> cancelList = new LinkedList<>();

        try{
            log.info("[KUCOIN][LIQUIDITY] START");
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

                // price == "" 면 buy도 sell도 진행할 수 없는 상태기에, order를 하지 않는다.
                if(price != null){
                    orderId = createOrder(action, price, cnt, coinWithId, exchange);
                    if(Utils.isSuccessOrder(orderId)){
                        cancelList.add(orderId);
                    }
                    Thread.sleep(1000);
                }

                // 취소 로직
                if(!cancelList.isEmpty() && cancelFlag){
                    String cancelId = cancelList.poll();
                    cancelOrder(cancelId);
                    Thread.sleep(500);
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[KUCOIN][LIQUIDITY] ERROR {}", e.getMessage());
            e.printStackTrace();
        }
        log.info("[KUCOIN][LIQUIDITY] END");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[KUCOIN][FISHINGTRADE] START");

        int returnCode = ReturnCode.SUCCESS.getCode();

        try{
            String[] coinWithId = Utils.splitCoinWithId(fishing.getCoin());
            Exchange exchange   = fishing.getExchange();
            String symbol       = getSymbol(coinWithId, exchange);

            // mode 처리
            Trade mode = Trade.valueOf(String.valueOf(list.keySet().toArray()[0]));
            ArrayList<String> tickPriceList = (ArrayList) list.get(mode.getVal());
            ArrayList<Map<String, String>> orderList = new ArrayList<>();

            /* Start */
            log.info("[KUCOIN][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt     = Utils.getRandomString(fishing.getMinContractCnt(), fishing.getMaxContractCnt());
                String orderId = (mode == Trade.BUY) ?
                        createOrder(BUY,  tickPriceList.get(i), cnt, coinWithId, exchange) :
                        createOrder(SELL, tickPriceList.get(i), cnt, coinWithId, exchange);

                if(Utils.isSuccessOrder(orderId)){
                    Map<String, String> orderMap = new HashMap<>();
                    orderMap.put("price" , tickPriceList.get(i));
                    orderMap.put("cnt"   , cnt);
                    orderMap.put("order_id" ,orderId);
                    orderList.add(orderMap);
                }
            }
            log.info("[KUCOIN][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");


            /* Sell Start */
            log.info("[KUCOIN][FISHINGTRADE][START BUY OR SELL TARGET PIECE COIN ]");
            boolean isSameFirstTick = true;    // 해당 플래그를 이용해 매수/매도를 올린 가격이 현재 최상위 값이 맞는지 다른 사람의 코인을 사지 않게 방지
            for (int i = orderList.size() - 1; i >= 0; i--) {
                Map<String, String> copiedOrderMap = Utils.deepCopy(orderList.get(i));
                BigDecimal cnt                     = new BigDecimal(copiedOrderMap.get("cnt"));

                while (cnt.compareTo(new BigDecimal("0")) > 0) {
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

                    String orderPrice = copiedOrderMap.get("price");
                    if (!orderPrice.equals(nowFirstTick)) {
                        log.info("[KUCOIN][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        isSameFirstTick = false;
                        break;
                    }

                    String orderId = (mode == Trade.BUY) ?
                            createOrder(SELL, copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange) :
                            createOrder(BUY,  copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange);

                    if(Utils.isSuccessOrder(orderId)){
                        cnt = cnt.subtract(executionCnt);
                    }else{
                        break;
                    }
                }

                Thread.sleep(500);
                cancelOrder(orderList.get(i).get("order_id"));
                Thread.sleep(2000);
            }
            log.info("[KUCOIN][FISHINGTRADE][END BUY OR SELL TARGET PIECE COIN ]");
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[KUCOIN][FISHINGTRADE] {}", e.getMessage());
            e.printStackTrace();
        }

        log.info("[KUCOIN][FISHINGTRADE] END");
        return returnCode;
    }

    @Override
    public int startRealtimeTrade(JsonObject realtime, boolean resetFlag) {
        log.info("[KUCOIN][REALTIME SYNC TRADE] START");
        int returnCode   = ReturnCode.SUCCESS.getCode();
        String realtimeChangeRate = "signed_change_rate";

        try {
            boolean isStart      = false;
            String[] coinWithId  = Utils.splitCoinWithId(realtimeSync.getCoin());
            Exchange exchange    = realtimeSync.getExchange();
            String[] currentTick = getTodayTick(exchange, coinWithId);
            //            String openingPrice  = currentTick[0];
            if(resetFlag){
                realtimeTargetInitRate = currentTick[1];
                log.info("[KUCOIN][REALTIME SYNC TRADE] Set init open rate : {} ", realtimeTargetInitRate);
            }
            String openingPrice  = realtimeTargetInitRate;
            String currentPrice  = currentTick[1];
            log.info("[KUCOIN][REALTIME SYNC TRADE] open:{}, current:{} ", openingPrice, currentPrice);

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
                            log.info("[KUCOIN][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                    }
                    // 베스트 오퍼 체크 작업 이후 기존에 걸었던 매수에 대해 캔슬
                    cancelOrder(orderId);
                }
            }

        }catch (Exception e){
            log.error("[KUCOIN][REALTIME SYNC TRADE] ERROR :{} ", e.getMessage());
            e.printStackTrace();
        }
        log.info("[KUCOIN][REALTIME SYNC TRADE] END");
        return returnCode;
    }


    /**
     * 현재 Tick 가져오기
     * @param exchange
     * @param coinWithId
     * @return [ 시가 , 종가 ] String Array
     */
    private String[] getTodayTick(Exchange exchange, String[] coinWithId) throws Exception{

        String[] returnRes   = new String[2];
        String symbol        = getSymbol(coinWithId, exchange);

        String request       = UtilsData.KUCOIN_TICK;
        String response      = getHttpMethod(request);
        JsonObject lowData   = gson.fromJson(response, JsonObject.class);
        JsonArray jsonArray  = lowData.get("data").getAsJsonObject().get("ticker").getAsJsonArray();

        JsonObject resObj    = getJsonObjectBySymbol(jsonArray,symbol);

        BigDecimal current   = resObj.get("last").getAsBigDecimal();    // 현재 값
        BigDecimal percent   = resObj.get("changeRate").getAsBigDecimal().divide(new BigDecimal(100),10, BigDecimal.ROUND_UP);
        BigDecimal open      = current.divide(new BigDecimal(1).add(percent),10, BigDecimal.ROUND_UP);  // 소수점 11번째에서 반올림

        returnRes[0] = open.toPlainString();
        returnRes[1] = current.toPlainString();

        return returnRes;
    }

    private JsonObject getJsonObjectBySymbol(JsonArray jsonArray, String symbol) throws NullPointerException{
        JsonObject returnObj = null;
        for(JsonElement element : jsonArray){
            JsonObject object = element.getAsJsonObject();
            if(object.get("symbol").getAsString().equals(symbol)){
                returnObj = object;
                break;
            }
        }

        if(returnObj == null){
            log.error("[KUCOIN][ORDER BOOK] Symbol is not matched. request symbol : {}", symbol);
            throw new NullPointerException();
        }

        return returnObj;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = "";
        try{
            String request = UtilsData.KUCOIN_ORDERBOOK + "?symbol=" + getSymbol(coinWithId, exchange);
            returnRes      = getHttpMethod(request);
        }catch (Exception e){
            log.error("[KUCOIN][ORDER BOOK] ERROR : {}",e.getMessage());
            e.printStackTrace();
        }
        return returnRes;
    }


    //String type, String price, String cnt, String[] coinData, Exchange exchange
    @Override
    public String createOrder(String type, String price, String cnt, String[] coinData, Exchange exchange) {
        String orderId = ReturnCode.FAIL_CREATE.getValue();

        try {
            setCoinToken(coinData, exchange);
            String action = parseAction(type);
            String symbol = getSymbol(coinData, exchange);

            log.info("[KUCOIN][CREATE ORDER] mode:{},price:{},cnt:{},symbol:{}", action,price,cnt,symbol);
            OrderCreateApiRequest request = OrderCreateApiRequest.builder()
                    .price(new BigDecimal(price)).size(new BigDecimal(cnt)).side(action).tradeType("TRADE")
                    .symbol(symbol).type("limit").clientOid(String.valueOf(System.currentTimeMillis())).build();

            OrderCreateResponse order = kucoinRestClient.orderAPI().createOrder(request);
            orderId = order.getOrderId();
            log.info("[KUCOIN][CREATE ORDER] Response success orderId:{}", orderId);
        }catch(Exception e){
            orderId = ReturnCode.NO_DATA.getValue();
            log.error("[KUCOIN][CREATE ORDER] ERROR : {}", e.getMessage());
        }
        return orderId;
    }

    /** 매도/매수 거래 취소 로직 **/
    public int cancelOrder(String orderId) {
        int returnValue = ReturnCode.FAIL.getCode();
        try {
            log.info("[KUCOIN][CANCEL ORDER] orderId:{}", orderId);
            OrderCancelResponse orderCancelResponse = kucoinRestClient.orderAPI().cancelOrder(orderId);
            if(orderCancelResponse.getCancelledOrderIds().size() > 0){
                returnValue = ReturnCode.SUCCESS.getCode();
            }
            log.info("[KUCOIN][CANCEL ORDER] Success Response orderId:{}", orderId);
        }catch(KucoinApiException e){
            if(ALREADY_TRADE.equals(e.getCode())){
                log.info("[KUCOIN][CANCEL ORDER] Already trade orderId:{}", orderId);
            }else{
                log.error("[KUCOIN][CANCEL ORDER] {}", e.getMessage());
                e.printStackTrace();
            }
        }catch (Exception e){
            log.error("[KUCOIN][CANCEL ORDER] {}", e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }

    // 거래소에 맞춰 심볼 반환
    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0] + "-" + getCurrency(exchange, coinData[0], coinData[1]); // ex) ADA-USDT
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
