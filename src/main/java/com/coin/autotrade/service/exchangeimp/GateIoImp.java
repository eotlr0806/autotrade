package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.TradeData;
import com.coin.autotrade.common.TradeService;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.gate.gateapi.ApiClient;
import io.gate.gateapi.ApiException;
import io.gate.gateapi.api.SpotApi;
import io.gate.gateapi.models.Order;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URL;
import java.util.*;

@Slf4j
public class GateIoImp extends AbstractExchange {

    final private String ACCESS_TOKEN   = "access_token";
    final private String SECRET_KEY     = "secret_key";
    final private String API_PASSWORD   = "apiPassword";
    final private String BUY            = "buy";
    final private String SELL           = "sell";
    final private String ORDERBOOK_SIZE = "100";
    final private String ALREADY_TRADED = "ORDER_NOT_FOUND";
    private ApiClient apiClient         = new ApiClient();
    Map<String, String> keyList         = new HashMap<>();
    private SpotApi spotApi             = null;

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
                    keyList.put(API_PASSWORD, exCoin.getApiPassword());

                    // Init Gateio API SDK
                    apiClient.setApiKeySecret(exCoin.getPublicKey(), exCoin.getPrivateKey());
                    apiClient.setBasePath(TradeData.GATEIO_URL);
                    spotApi = new SpotApi(apiClient);
                }
            }
        }catch (Exception e){
            log.error("[GATEIO][ERROR][SET COIN TOKEN] {}", e.getMessage());
        }
    }

    /**
     * gateio global 자전 거래
     * @param symbol coin + "_" + symbol
     */
    @Override
    public int startAutoTrade(String price, String cnt){
        log.info("[GATEIO][AUTOTRADE START]");
        int returnCode    = TradeData.CODE_SUCCESS;

        try{

            String[] coinData = TradeService.splitCoinWithId(autoTrade.getCoin());
            String symbol     = coinData[0] + "_" + getCurrency(autoTrade.getExchange(), coinData[0], coinData[1]);

            // mode 처리
            String mode = autoTrade.getMode();
            if(TradeData.MODE_RANDOM.equals(mode)){
                mode = (TradeService.getRandomInt(0,1) == 0) ? TradeData.MODE_BUY : TradeData.MODE_SELL;
            }

            // 1 : 매수 , 2 : 매도
            String firstOrderId  = "";
            String secondOrderId = "";
            if(TradeData.MODE_BUY.equals(mode)){
                if( !(firstOrderId = createOrder(BUY, price, cnt, symbol)).equals("")){   // 매수
                    if((secondOrderId = createOrder(SELL,price, cnt, symbol)).equals("")){               // 매도
                        Thread.sleep(3000);
                        cancelOrder(firstOrderId, symbol);                      // 매도 실패 시, 매수 취소
                    }
                }
            }else if(TradeData.MODE_SELL.equals(mode)){
                if( !(firstOrderId = createOrder(SELL,price, cnt, symbol)).equals("")){
                    if((secondOrderId = createOrder(BUY,price, cnt, symbol)).equals("")){
                        Thread.sleep(3000);
                        cancelOrder(firstOrderId, symbol);
                    }
                }
            }
            // 최초던진 값이 거래가 될 수 있기에 2번째 값은 무조건 취소진행
            if(!firstOrderId.equals("") || !secondOrderId.equals("")){
                Thread.sleep(3000);
                if(!firstOrderId.equals("")){
                    cancelOrder(firstOrderId, symbol);
                }
                if(!secondOrderId.equals("")){
                    cancelOrder(secondOrderId, symbol);
                }
            }
        }catch (Exception e){
            returnCode = TradeData.CODE_ERROR;
            log.error("[GATEIO][ERROR][AUTOTRADE] {}", e.getMessage());
        }

        log.info("[GATEIO][AUTOTRADE End]");
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
            log.info("[GATEIO][LIQUIDITY] Start");
            String[] coinData = TradeService.splitCoinWithId(liquidity.getCoin());
            String symbol     = coinData[0] + "_" + getCurrency(liquidity.getExchange(), coinData[0], coinData[1]);
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
                    Thread.sleep(2000);
                    if(!firstOrderId.equals("")){
                        cancelOrder(firstOrderId, symbol);
                    }
                    if(!secondsOrderId.equals("")){
                        Thread.sleep(500);
                        cancelOrder(secondsOrderId, symbol);
                    }
                }
            }
        }catch (Exception e){
            returnCode = TradeData.CODE_ERROR;
            log.error("[GATEIO][ERROR][LIQUIDITY] {}", e.getMessage());
        }
        log.info("[GATEIO][LIQUIDITY] End");
        return returnCode;
    }

    @Override
    public int startFishingTrade(Map<String,List> list, int intervalTime){
        log.info("[GATEIO][FISHINGTRADE START]");

        int returnCode    = TradeData.CODE_SUCCESS;

        try{
            String[] coinData = TradeService.splitCoinWithId(fishing.getCoin());
            String symbol     = coinData[0] + "_" + getCurrency(fishing.getExchange(), coinData[0], coinData[1]);

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
                    orderId = createOrder(BUY,  tickPriceList.get(i), cnt, symbol);
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
                        log.info("[GATEIO][FISHINGTRADE] Not Match First Tick. All Trade will be canceled RequestTick : {}, realTick : {}", copiedOrderMap.get("price"), nowFirstTick);
                        noMatchFirstTick = false;
                        break;
                    }

                    if(TradeData.MODE_BUY.equals(mode)) {
                        orderId = createOrder(SELL, copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol);
                    }else{
                        orderId = createOrder(BUY,  copiedOrderMap.get("price"), cntForExcution.toPlainString(), symbol);
                    }

                    if(!orderId.equals("")){
                        cnt = cnt.subtract(cntForExcution);
                    }else{
                        log.error("[GATEIO][FISHINGTRADE] While loop is broken, Because create order is failed");
                        break;
                    }
                }
                // 무조건 일단 취소
                Thread.sleep(1000);
                cancelOrder(orderList.get(i).get("order_id"), symbol);
            }
        }catch (Exception e){
            returnCode = TradeData.CODE_ERROR;
            log.error("[GATEIO][ERROR][FISHINGTRADE] {}", e.getMessage());
        }

        log.info("[GATEIO][FISHINGTRADE END]");
        return returnCode;
    }

    @Override
    public String getOrderBook(Exchange exchange, String[] coinWithId) {
        String returnRes = "";
        try{
            log.info("[GATEIO][ORDER BOOK START]");
            String coin   = coinWithId[0];
            String coinId = coinWithId[1];
            String inputLine;
            String symbol   = getCurrency(exchange, coin, coinId);

            String request  = TradeData.GATEIO_ORDERBOOK + "?currency_pair=" + coin + "_" + symbol + "&limit=" + ORDERBOOK_SIZE;
            URL url = new URL(request);

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            connection.setRequestMethod("GET");

            log.info("[GATEIO][ORDER BOOK - REQUEST] symbol:{}" ,  coin);

            int returnCode = connection.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer response = new StringBuffer();
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();
            returnRes = response.toString();

            log.info("[GATEIO][ORDER BOOK END]");

        }catch (Exception e){
            log.error("[GATEIO][ERROR][ORDER BOOK] {}",e.getMessage());
        }

        return returnRes;
    }


    /** Biyhumb global 매수/매도 로직 */
    public String createOrder(String type, String price, String cnt, String symbol){

        String orderId = "";

        try{

            Order order = new Order();

            order.setAccount(Order.AccountEnum.SPOT);
            order.setAutoBorrow(false);
            order.setTimeInForce(Order.TimeInForceEnum.GTC);
            order.setType(Order.TypeEnum.LIMIT);
            order.setAmount(cnt);
            order.setPrice(price);

            if(type.equals(BUY)){
                order.setSide(Order.SideEnum.BUY);
            }else{
                order.setSide(Order.SideEnum.SELL);
            }
            order.setCurrencyPair(symbol);

            log.info("[GATEIO][CREATE ORDER - request] mode:{}, currency:{}, cnt:{}, price:{}",  order.getSide(), order.getCurrencyPair(), order.getAmount(), order.getPrice());
            Order created = spotApi.createOrder(order);
            Order orderResult = spotApi.getOrder(created.getId(), symbol, String.valueOf(Order.AccountEnum.SPOT));
            orderId = orderResult.getId();

            log.info("[GATEIO][SUCCESS][CREATE ORDER - response] orderId:{}",  orderId);
        }catch (ApiException e){
            log.error("[GATEIO][ERROR][CREATE ORDER] {}",e.getMessage());
        }catch (Exception e){
            log.error("[GATEIO][ERROR][CREATE ORDER] {}",e.getMessage());
        }
        return orderId;
    }

    /* gateio global 거래 취소 */
    public int cancelOrder(String orderId, String symbol) {

        int returnValue = TradeData.CODE_ERROR;

        try {
            Order result = spotApi.cancelOrder(orderId, symbol, Order.AccountEnum.SPOT.toString());
            log.info("[GATEIO][SCUESS][CANCEL ORDER - response] orderId:{}", orderId);
        }catch(ApiException e){
            JsonObject object = gson.fromJson(e.getResponseBody(), JsonObject.class);
            String label      = gson.fromJson(object.get("label"),String.class);
            if(ALREADY_TRADED.equals(label)){
                log.info("[GATEIO][SCUESS][CANCEL ORDER - response] Already traded orderId:{}", orderId);
            }
        }catch (Exception e){
            log.error("[GATEIO][ERROR][CANCEL ORDER] orderId:{}, response:{}",orderId, e.getMessage());
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
            log.error("[GATEIO][ERROR][GET CURRENCY] {}",e.getMessage());
        }
        return returnVal;
    }

}
