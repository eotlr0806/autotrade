package com.coin.autotrade.service.function;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.kucoin.sdk.KucoinClientBuilder;
import com.kucoin.sdk.KucoinRestClient;
import com.kucoin.sdk.exception.KucoinApiException;
import com.kucoin.sdk.model.enums.ApiKeyVersionEnum;
import com.kucoin.sdk.rest.request.OrderCreateApiRequest;
import com.kucoin.sdk.rest.response.OrderCancelResponse;
import com.kucoin.sdk.rest.response.OrderCreateResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

@Slf4j
public class KucoinFunction extends ExchangeFunction{

    final private String ACCESS_TOKEN         = "apiToken";
    final private String SECRET_KEY           = "secretKey";
    final private String API_PASSWORD         = "apiPassword";
    final private String BUY                  = "buy";
    final private String SELL                 = "sell";
    final private String ALREADY_TRADE        = "400100";
    private Map<String, String> keyList       = new HashMap<>();

    /** Kucoin libirary **/
    private static KucoinRestClient kucoinRestClient;

    @Override
    public void initClass(AutoTrade autoTrade, User user, Exchange exchange){
        super.autoTrade = autoTrade;

        setCommonValue(user, exchange);
        setCoinToken(ServiceCommon.splitCoinWithId(autoTrade.getCoin()));
        setKucoinRestClient();
    }

    @Override
    public void initClass(Liquidity liquidity, User user, Exchange exchange){
        super.liquidity = liquidity;

        setCommonValue(user, exchange);
        setCoinToken(ServiceCommon.splitCoinWithId(liquidity.getCoin()));
        setKucoinRestClient();
    }

    @Override
    public void initClass(Fishing fishing , User user,  Exchange exchange,CoinService coinService){
        super.fishing     = fishing;
        super.coinService = coinService;

        setCommonValue(user, exchange);
        setCoinToken(ServiceCommon.splitCoinWithId(fishing.getCoin()));
        setKucoinRestClient();
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
                    keyList.put(API_PASSWORD, exCoin.getApiPassword());
                }
            }
        }catch (Exception e){
            log.error("[KUCOIN][ERROR][SET COIN TOKEN] error : {} ", e.getMessage());
        }
    }

    private void setKucoinRestClient() {
        kucoinRestClient = new KucoinClientBuilder()
                .withBaseUrl(DataCommon.KUCOIN_URL)
                .withApiKey(keyList.get(ACCESS_TOKEN), keyList.get(SECRET_KEY), keyList.get(API_PASSWORD))
                .withApiKeyVersion(ApiKeyVersionEnum.V2.getVersion())
                .buildRestClient();
    }

    /**
     * Auto Trade Start
     * @param symbol - coin + currency
     */
    @Override
    public int startAutoTrade(String price, String cnt){

        log.info("[KUCOIN][AUTOTRADE] Start");

        int returnCode = DataCommon.CODE_SUCCESS;
        try{
            String[] coinData = ServiceCommon.splitCoinWithId(autoTrade.getCoin());
            String     symbol = coinData[0] + "-" + getCurrency(getExchange(), coinData[0], coinData[1]); // ex) ADA-USDT

            // mode 처리
            String mode = autoTrade.getMode();
            if(DataCommon.MODE_RANDOM.equals(mode)){
                mode = (ServiceCommon.getRandomInt(0,1) == 0) ? DataCommon.MODE_BUY : DataCommon.MODE_SELL;
            }

            String firstOrderId  = "";
            String secondOrderId = "";
            if(DataCommon.MODE_BUY.equals(mode)){
                if(!(firstOrderId = createOrder(BUY,price, cnt, symbol)).equals("")){
                    if((secondOrderId = createOrder(SELL,price, cnt, symbol)).equals("")){          // SELL 모드가 실패 시,
                        Thread.sleep(3000);
                        cancelOrder(firstOrderId);
                    }
                }
            }else if(DataCommon.MODE_SELL.equals(mode)){
                if(!(firstOrderId = createOrder(SELL,price, cnt, symbol)).equals("")){
                    if((secondOrderId = createOrder(BUY,price, cnt, symbol)).equals("")){           // BUY 모드가 실패 시,
                        Thread.sleep(3000);
                        cancelOrder(firstOrderId);
                    }
                }
            }
            // 최초 거래 시, 다른 값을 샀을 수 있기에, 2번째 값은 무조건 취소를 날린다.
            if(!firstOrderId.equals("") || !secondOrderId.equals("")){
                Thread.sleep(3000);
                if(!firstOrderId.equals("")){
                    cancelOrder(firstOrderId);
                }
                if(!secondOrderId.equals("")){
                    cancelOrder(secondOrderId);
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[KUCOIN][ERROR][AUTOTRADE] {}", e.getMessage());
        }

        log.info("[KUCOIN][AUTOTRADE] End");

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
            log.info("[KUCOIN][LIQUIDITY] Start");
            String[] coinData = ServiceCommon.splitCoinWithId(liquidity.getCoin());
            String symbol     = coinData[0] + "-" + getCurrency(getExchange(),coinData[0], coinData[1]);
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
                        cancelOrder(firstOrderId);
                    }
                    if(!secondsOrderId.equals("")){
                        Thread.sleep(300);
                        cancelOrder(secondsOrderId);
                    }
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[KUCOIN][ERROR][LIQUIDITY] {}", e.getMessage());
        }
        log.info("[KUCOIN][LIQUIDITY] End");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[KUCOIN][FISHINGTRADE START]");

        int returnCode    = DataCommon.CODE_SUCCESS;

        try{
            String[] coinData = ServiceCommon.splitCoinWithId(fishing.getCoin());
            String     symbol = coinData[0] + "-" + getCurrency(getExchange(), coinData[0], coinData[1]);

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
                    orderId = createOrder(BUY, tickPriceList.get(i), cnt, symbol);
                }else{
                    orderId = createOrder(SELL, tickPriceList.get(i), cnt, symbol);
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
                        log.info("[KUCOIN][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
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
                    }else{
                        break;
                    }
                }

                Thread.sleep(500);
                cancelOrder(orderList.get(i).get("order_id"));
                Thread.sleep(2000);
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[KUCOIN][ERROR][FISHINGTRADE] {}", e.getMessage());
        }

        log.info("[KUCOIN][FISHINGTRADE END]");
        return returnCode;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = "";
        try{
            String inputLine;
            String coin   = coinWithId[0];
            String coinId = coinWithId[1];
            String currency = getCurrency(exchange, coin, coinId);
            if(currency.equals("")){
                log.error("[KUCOIN][ERROR][ORDER BOOK] There is no coin");
                return "";
            }

            String params = "symbol=" + coin + "-" + currency;
            String request = DataCommon.KUCOIN_ORDERBOOK + "?" + params;
            URL url = new URL(request);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Context-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            connection.setConnectTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setReadTimeout(DataCommon.TIMEOUT_VALUE);

            log.info("[KUCOIN][ORDER BOOK - Request]  request : {}", request);

            int returnCode = connection.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer response = new StringBuffer();
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();

            returnRes = response.toString();

        }catch (Exception e){
            log.error("[KUCOIN][ERROR][ORDER BOOK] {}",e.getMessage());
        }

        return returnRes;
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
     * @param symbol - coin - currency
     */
    public String createOrder(String side, String price, String cnt, String symbol) {

        String orderId   = "";
        try {
            log.info("[KUCOIN][CREATE ORDER] mode:{},price:{},cnt:{},symbol:{}", side,price,cnt,symbol);
            OrderCreateApiRequest request = OrderCreateApiRequest.builder()
                    .price(new BigDecimal(price)).size(new BigDecimal(cnt)).side(side).tradeType("TRADE")
                    .symbol(symbol).type("limit").clientOid(String.valueOf(System.currentTimeMillis())).build();

            OrderCreateResponse order = kucoinRestClient.orderAPI().createOrder(request);
            orderId = order.getOrderId();
            log.info("[KUCOIN][SUCCESS][CREATE ORDER] orderId:{}", orderId);

        }catch(Exception e){
            orderId = "";
            log.error("[KUCOIN][ERROR][CREATE ORDER] {}", e.getMessage());
        }
        return orderId;
    }

    /** 매도/매수 거래 취소 로직 **/
    public int cancelOrder(String orderId) {
        int returnValue = DataCommon.CODE_ERROR;
        try {
            log.info("[KUCOIN][CANCEL ORDER] orderId:{}", orderId);
            OrderCancelResponse orderCancelResponse = kucoinRestClient.orderAPI().cancelOrder(orderId);
            if(orderCancelResponse.getCancelledOrderIds().size() > 0) returnValue = DataCommon.CODE_SUCCESS;
            log.info("[KUCOIN][SUCCESS][CANCEL ORDER] orderId:{}", orderId);
        }catch(KucoinApiException e){
            if(ALREADY_TRADE.equals(e.getCode())){
                log.info("[KUCOIN][SUCCESS][CANCEL ORDER] Already trade orderId:{}", orderId);
            }else{
                log.error("[KUCOIN][ERROR][CANCEL ORDER] {}", e.getMessage());
            }
        }catch (Exception e){
            log.error("[KUCOIN][ERROR][CANCEL ORDER] {}", e.getMessage());
        }
        return returnValue;
    }

    /* KUCOIN 의 경우 통화 기준으로 필요함.*/
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
            log.error("[KUCOIN][ERROR][Get Currency] {}",e.getMessage());
        }
        return returnVal;
    }
}
