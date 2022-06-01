package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.common.enumeration.Trade;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lbank.java.api.sdk.response.ResCancelOrderVo;
import com.lbank.java.api.sdk.response.ResCreateOrderVo;
import com.lbank.java.api.sdk.response.ResUserInfoVo;
import com.lbank.java.api.sdk.service.LBankServiceImpl;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.*;


@Slf4j
public class LbankImp extends AbstractExchange {
    final private String USER_ID          = "user_id";
    final private String ALREADY_TRADED   = "10025";
    final private String BUY              = "buy";
    final private String SELL             = "sell";
    private LBankServiceImpl service      = null;

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
        if(service == null){
            for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
                if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1]) ){
                    service = new LBankServiceImpl(exCoin.getPublicKey(), exCoin.getPrivateKey(), "HmacSHA256");
                }
            }
            if(service == null){
                String msg = "There is no match coin. " + Arrays.toString(coinData) + " " + exchange.getExchangeCode();
                throw new Exception(msg);
            }
        }
    }

    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[LBANK][AUTOTRADE] START");
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
                String secondOrderId = createOrder(secondAction, price, cnt, coinWithId, exchange);

                Thread.sleep(500);
                cancelOrder(symbol, firstOrderId);                      // 매도 실패 시, 매수 취소
                if(Utils.isSuccessOrder(secondOrderId)){
                    cancelOrder(symbol, secondOrderId);                 // 매도 실패 시, 매수 취소
                }
            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[LBANK][AUTOTRADE] Error : {}", e.getMessage());
        }

        log.info("[LBANK][AUTOTRADE] END");
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
            log.info("[LBANK][LIQUIDITY] START");
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

                // 매수 로직
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
                    cancelOrder(symbol, cancelId);
                    Thread.sleep(500);
                }

            }
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[LBANK][LIQUIDITY] ERROR : {}", e.getMessage());
        }
        log.info("[LBANK][LIQUIDITY] END");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[LBANK][FISHINGTRADE] START");

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
            log.info("[LBANK][FISHINGTRADE][START BUY OR SELL TARGET ALL COIN]");
            for (int i = 0; i < tickPriceList.size(); i++) {
                String cnt     = Utils.getRandomString(fishing.getMinContractCnt(), fishing.getMaxContractCnt());
                String orderId = (mode == Trade.BUY) ?
                        createOrder(BUY,  tickPriceList.get(i), cnt, coinWithId, exchange) :
                        createOrder(SELL, tickPriceList.get(i), cnt, coinWithId, exchange);

                if(Utils.isSuccessOrder(orderId)){
                    Map<String, String> orderMap = new HashMap<>();
                    orderMap.put("price" ,tickPriceList.get(i));
                    orderMap.put("cnt" ,cnt);
                    orderMap.put("order_id" ,orderId);
                    if(mode == Trade.BUY){
                        orderMap.put("type", BUY);
                    }else{
                        orderMap.put("type", SELL);
                    }
                    orderList.add(orderMap);
                }
            }
            log.info("[LBANK][FISHINGTRADE][END BUY OR SELL TARGET ALL COIN]");

            /* Sell Start */
            log.info("[LBANK][FISHINGTRADE][START BUY OR SELL TARGET PIECE COIN ]");
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
                        log.info("[LBANK][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        isSameFirstTick = false;
                        break;
                    }

                    String orderId = (mode == Trade.BUY) ?
                            createOrder(SELL, copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange) :
                            createOrder(BUY,  copiedOrderMap.get("price"), executionCnt.toPlainString(), coinWithId, exchange);

                    if(Utils.isSuccessOrder(orderId)){
                        cnt = cnt.subtract(executionCnt);
                    }else{
                        log.error("[LBANK][FISHINGTRADE] While loop is broken, Because create order is failed");
                        break;
                    }
                }
                // 무조건 취소를 날려서 있던 없던 제거
                Thread.sleep(500);
                cancelOrder(symbol, orderList.get(i).get("order_id"));
                Thread.sleep(2000);
            }
            log.info("[LBANK][FISHINGTRADE][END BUY OR SELL TARGET PIECE COIN ]");
        }catch (Exception e){
            returnCode = ReturnCode.FAIL.getCode();
            log.error("[LBANK][FISHINGTRADE] ERROR {}", e.getMessage());
        }

        log.info("[LBANK][FISHINGTRADE] END");
        return returnCode;
    }


    @Override
    public int startRealtimeTrade(JsonObject realtime, boolean resetFlag) {
        log.info("[LBANK][REALTIME SYNC TRADE] START");
        int returnCode   = ReturnCode.SUCCESS.getCode();
        String realtimeChangeRate = "signed_change_rate";

        try {

            boolean isStart      = false;
            String[] coinWithId  = Utils.splitCoinWithId(realtimeSync.getCoin());
            Exchange exchange    = realtimeSync.getExchange();
            String symbol        = getSymbol(coinWithId, realtimeSync.getExchange());
            String[] currentTick = getTodayTick();
            //            String openingPrice  = currentTick[0];
            if(resetFlag){
                realtimeTargetInitRate = currentTick[1];
                log.info("[LBANK][REALTIME SYNC TRADE] Set init open rate : {} ", realtimeTargetInitRate);
            }
            String openingPrice  = realtimeTargetInitRate;
            String currentPrice  = currentTick[1];
            log.info("[LBANK][REALTIME SYNC TRADE] open:{}, current:{} ", openingPrice, currentPrice);

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
                            log.info("[LBANK][REALTIME SYNC] Bestoffer is setted. price:{}, cnt:{}", bestofferPrice, bestofferCnt);
                        }
                    }
                    cancelOrder(symbol, orderId);
                }
            }
        }catch (Exception e){
            log.error("[LBANK][REALTIME SYNC TRADE] Error :{} ", e.getMessage());
            e.printStackTrace();
        }
        log.info("[LBANK][REALTIME SYNC TRADE] END");
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
        String request       = UtilsData.LBANK_TICK + "?symbol=" + getSymbol(Utils.splitCoinWithId(realtimeSync.getCoin()),realtimeSync.getExchange());
        String response      = getHttpMethod(request);
        JsonObject resObject = gson.fromJson(response, JsonObject.class);
        String result        = resObject.get("result").getAsString();
        if(Boolean.parseBoolean(result)){
            JsonObject returnObj = resObject.get("data").getAsJsonArray().get(0).getAsJsonObject();
            JsonObject data      = returnObj.get("ticker").getAsJsonObject();
            BigDecimal percent = data.get("change").getAsBigDecimal().divide(new BigDecimal(100),10, BigDecimal.ROUND_UP);
            BigDecimal current = data.get("latest").getAsBigDecimal();
            BigDecimal open    = current.divide(new BigDecimal(1).add(percent),10, BigDecimal.ROUND_UP);  // 소수점 11번째에서 반올림

            returnRes[0] = open.toPlainString();
            returnRes[1] = current.toPlainString();

        }else{
            log.error("[LBANK][GET TODAY TICK] response : {}", response);
            throw new Exception(response);
        }
        return returnRes;
    }


    @Override
    public String getBalance(String[] coinData, Exchange exchange) throws Exception{
        String returnValue = ReturnCode.NO_DATA.getValue();;
        setCoinToken(coinData, exchange);
        log.info("[LBANK][GET BALANCE] START");
        String customId = UUID.randomUUID().toString();
        ResUserInfoVo resUserInfoVo = service.getUserInfo();
        returnValue = gson.toJson(resUserInfoVo.getData());
        log.info("[LBANK][GET BALANCE] END");
        return returnValue;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = ReturnCode.FAIL.getValue();
        try{
            String request  = UtilsData.LBANK_ORDERBOOK + "?symbol=" + getSymbol(coinWithId,exchange) + "&size=10";
            returnRes       = getHttpMethod(request);
        }catch (Exception e){
            log.error("[LBANK][ORDER BOOK] ERROR : {}",e.getMessage());
            e.printStackTrace();
        }
        return returnRes;
    }

    /** lbank 매수/매도 로직 */
    @Override
    public String createOrder(String type, String price, String cnt, String[] coinData, Exchange exchange){
        String orderId = ReturnCode.FAIL_CREATE.getValue();

        try{
            String action = parseAction(type);
            String symbol = getSymbol(coinData, exchange);
            setCoinToken(coinData, exchange);

            String customId = UUID.randomUUID().toString();
            ResCreateOrderVo createOrder = service.createOrder(symbol, action, price, cnt, customId);

            if(createOrder.getResult()){
                orderId = createOrder.getData().get("order_id");
                log.info("[LBANK][CREATE ORDER] Success response : {}", createOrder.toString());
            }else{
                log.error("[LBANK][CREATE ORDER] Fail response :{}", createOrder.toString());
            }
        }catch (Exception e){
            log.error("[LBANK][CREATE ORDER] ERROR {}",e.getMessage());
            e.printStackTrace();
        }
        return orderId;
    }

    /* Bithumb global 거래 취소 */
    private int cancelOrder(String symbol, String orderId) {

        int returnValue = ReturnCode.NO_DATA.getCode();
        try {
            ResCancelOrderVo cancelOrder = service.cancelOrder(symbol, orderId);
            if(cancelOrder.getResult()){
                log.info("[LBANK][CANCEL ORDER] Success response : {}", cancelOrder.toString());
            }else if(ALREADY_TRADED.equals(cancelOrder.getError_code())){
                log.info("[LBANK][CANCEL ORDER] Already traded response : {}", cancelOrder.toString());
            }else{
                log.error("[LBANK][CANCEL ORDER] Fail response :{}", cancelOrder.toString());
            }
        }catch(Exception e){
            log.error("[LBANK][CANCEL ORDER] ERROR : {}", e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }


    // 거래소에 맞춰 심볼 반환
    private String getSymbol(String[] coinData, Exchange exchange) throws Exception {
        return coinData[0].toLowerCase() + "_" + getCurrency(exchange,coinData[0], coinData[1]).toLowerCase();
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
