package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.common.enumeration.Trade;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Abstract class for common function and variable
@Slf4j
public abstract class AbstractExchange {

    /**##############################################################
     * #################### Declaration variable ####################
     * ############################################################## */
     final protected String PUBLIC_KEY      = "public_key";
     final protected String SECRET_KEY      = "secret_key";
     final protected String MIN_AMOUNT      = "min_amount";
     final protected String API_PASSWORD    = "apiPassword";
     protected Map<String, String> keyList  = new HashMap<>();
     AutoTrade autoTrade                    = null;
     Liquidity liquidity                    = null;
     Fishing fishing                        = null;
     RealtimeSync realtimeSync              = null;
     CoinService coinService                = null; // Fishing 시, 사용하기 위한 coin Service class
     String realtimeTargetInitRate          = null; // realtime 에서 사용하는 실시간 동기화 타겟의 최초 현재 값
     Gson gson                              = new Gson();




    /**#####################################################################
     * #################### Declaration abstract method ####################
     * ##################################################################### */
    /** 자전 거래를 이용하기위한 초기값 설정 */
    public abstract void initClass(AutoTrade autoTrade) throws Exception;
    /** 호가 유동성을 이용하기 위한 초기값 설정 */
    public abstract void initClass(Liquidity liquidity) throws Exception;
    /** 매매 긁기를 이용하기 위한 초기값 설정 */
    public abstract void initClass(Fishing fishing, CoinService coinService) throws Exception;
    /** 실시간 동기화를 이용하기 위한 초기값 설정 */
    public abstract void initClass(RealtimeSync realtimeSync, CoinService coinService) throws Exception;

    public abstract int startAutoTrade(String price, String cnt);
    public abstract int startLiquidity(Map list);
    public abstract int startFishingTrade(Map<String, List> list, int intervalTime);
    public abstract int startRealtimeTrade(JsonObject realtime, boolean resetFlag);
    public abstract String getOrderBook(Exchange exchange, String[] coinWithId);
    public abstract String createOrder(String type, String price, String cnt, String[] coinData, Exchange exchange) throws Exception;

    public String getBalance(String[] coinData, Exchange exchange) throws Exception {
        throw new Exception("Not supported");
    }




    /**#####################################################################
     * #################### Declaration common method ######################
     * ##################################################################### */

    /**
     * 실시간 동기화에서 사용하며, 현재 가격이 설정한 minPrice or maxPrice 보다 낮거나 높으면 매수
     * @param currentPriceStr 현재 가격
     * @return 해당 구간에 있을 경우 0, 지지선보다 낮을 경우 -1, 저항선보다 높을 경우 1
     */
    protected int isMoreOrLessPrice(String currentPriceStr) throws Exception {
        BigDecimal minPrice     = new BigDecimal(realtimeSync.getMinPrice());
        BigDecimal maxPrice     = new BigDecimal(realtimeSync.getMaxPrice());
        BigDecimal currentPrice = new BigDecimal(currentPriceStr);

        if(currentPrice.compareTo(minPrice) < 0){
            return -1;
        }else if(currentPrice.compareTo(maxPrice) > 0){
            return 1;
        }else{
            return 0;
        }
    }

    /**
     * 설정한 구간안에 있는지 확인
     * @param openingPrice 시가
     * @param currentPrice 현재가
     * @param realtimePercent 동기화 코인에 대한 금일 퍼센트
     * @return empty 일 경우 거래할 필요 없음. 그게 아닐 경우 Map에 (mode, TradeData.BUY(SELL) , (price, 0.00025) 이 들어감.
     * @throws Exception
     */
    protected Map<String, String> getTargetTick(String openingPrice, String currentPrice, String realtimePercent) throws Exception{
        int roundUpScale   = 15;     // 반올림 소수점
        Map<String,String> returnMap = new HashMap<>();

        BigDecimal openingDecimalPrice = new BigDecimal(openingPrice);                          // 시가
        BigDecimal currentDecimalPrice = new BigDecimal(currentPrice);                          // 현재가
        BigDecimal differencePrice     = currentDecimalPrice.subtract(openingDecimalPrice);     // 현재가 - 시가
        BigDecimal differencePercent   = differencePrice.divide(openingDecimalPrice, roundUpScale, BigDecimal.ROUND_CEILING); // 시가 대비 현재가 증가 및 감소율

        BigDecimal realtimeDecimalPercent  = new BigDecimal(realtimePercent);                // 실시간 연동하는 코인의 증감률
        BigDecimal syncPercent             = new BigDecimal(realtimeSync.getPricePercent()); // 1~100 까지의 %값
        BigDecimal realtimeTargetPercent   = realtimeDecimalPercent.multiply(syncPercent).divide(new BigDecimal(100),roundUpScale, BigDecimal.ROUND_CEILING); // 실시간 연동 코인 증감률 * 설정 값

        // 소수점 2번째 자리가 같을 경우 패스 ex) 1.009 == 1.001 같은 선상이라고 보고, 패스함.
        BigDecimal realtimeTargetPercentFloor = realtimeTargetPercent.setScale(4, BigDecimal.ROUND_FLOOR);
        BigDecimal differencePercentFloor     = differencePercent.setScale(4, BigDecimal.ROUND_FLOOR);
        if(realtimeTargetPercentFloor.compareTo(differencePercentFloor) != 0){  // 두개의 차이가 같은 구간이 아닐 경우
            if(realtimeTargetPercent.compareTo(differencePercent) < 0){         // 동기화 코인 %가 기준 코인 상승률보다 적으면 true
                returnMap.put("mode", UtilsData.MODE_SELL);
            }else if(realtimeTargetPercent.compareTo(differencePercent) > 0){
                returnMap.put("mode", UtilsData.MODE_BUY);
            }
        }

        if(!returnMap.isEmpty()){
            // Set target price
            BigDecimal targetPrice = makeTargetPrice(openingDecimalPrice, realtimeTargetPercent);
            returnMap.put("price",targetPrice.toPlainString());

            log.info("[ABSTRACT EXCHANGE][REALTIME SYNC TRADE] Realtime target percent with sync: {}, " +
                            "Local target coin : {}, " +
                            "Local target percent : {} , " +
                            "Local target price : {}, " +
                            "Mode : {}",
                    realtimeTargetPercent.multiply(new BigDecimal(100)).toPlainString(), realtimeSync.getCoin(),
                    differencePercent.multiply(new BigDecimal(100)).toPlainString(), targetPrice.toPlainString(), returnMap.get("mode"));
        }else{
            log.info("[ABSTRACT EXCHANGE][REALTIME SYNC TRADE] target price within realtime target price.");
        }

        return returnMap;
    }

    /**
     * realtimeSync 에서 사용하며, 매수/매도해야 할 특정 가격을 구함.
     * @param openingPrice   시가
     * @param targetPercent  시가 기준으로 움직여야 되는 목표 퍼센트
     * @throws Exception
     */
    protected BigDecimal makeTargetPrice(BigDecimal openingPrice, BigDecimal targetPercent) throws Exception{
        String coinPriceStr   = "";
        String[] coinArr      = Utils.splitCoinWithId(realtimeSync.getCoin());
        for(ExchangeCoin coin : realtimeSync.getExchange().getExchangeCoin()){
            if(coin.getCoinCode().equals(coinArr[0]) && coin.getId() == Long.parseLong(coinArr[1])){
                coinPriceStr = coin.getCoinPrice();
                break;
            }
        }

        int scale = coinPriceStr.length() - (coinPriceStr.indexOf(".") + 1);    // 어디서 버림을 해야 하는지 값 체크
        BigDecimal coinPrice              = new BigDecimal(coinPriceStr);
        BigDecimal targetPriceWithoutDrop = openingPrice.add(openingPrice.multiply(targetPercent));
        BigDecimal returnTargetPrice      = null;
        if(coinPrice.compareTo(new BigDecimal("1.0")) < 0){
            returnTargetPrice = targetPriceWithoutDrop.setScale(scale, BigDecimal.ROUND_FLOOR);
        }else{
            returnTargetPrice = targetPriceWithoutDrop.subtract(targetPriceWithoutDrop.remainder(coinPrice));
        }
        return returnTargetPrice;
    }

    protected JsonArray makeBestofferAfterRealtimeSync(String targetPrice, String mode) throws Exception {
        JsonArray list                   = new JsonArray();
        JsonArray tickArray              = coinService.getTick(realtimeSync.getCoin(), realtimeSync.getExchange()).get(mode);
        BigDecimal expectedTargetTick    = new BigDecimal(tickArray.get(0).getAsJsonObject().get("price").getAsString());
        BigDecimal targetPriceBigDecimal = new BigDecimal(targetPrice);

        // 최대매수 호가 혹은 최대 매도 호가와 타겟 가격이 동일해졌다면 베스트 오퍼 예약
        if(expectedTargetTick.compareTo(targetPriceBigDecimal) == 0){
            log.info("[REALTIME SYNC] Target tick is the same with real tick. So make bestoffer. target:{}, real:{}", targetPriceBigDecimal, expectedTargetTick);
            int limitCheckTick               = realtimeSync.getTickCnt();
            BigDecimal tickRangeDecimal      = new BigDecimal(realtimeSync.getTickRange());
            List<String> tradePriceList      = new ArrayList<>();
            for (int i = 1; i <= limitCheckTick; i++) {
                BigDecimal bestofferForTrade = null;
                if(mode.equals(UtilsData.MODE_BUY)){    // 매수 시
                    bestofferForTrade = targetPriceBigDecimal.subtract(new BigDecimal(i).multiply(tickRangeDecimal));
                }else{                                  // 매도 시
                    bestofferForTrade = targetPriceBigDecimal.add(new BigDecimal(i).multiply(tickRangeDecimal));
                }
                boolean isTrade = true;
                // 이미 해당 가격에 물량이 있을 경우 pass
                for (int j = 0; j < tickArray.size(); j++) {
                    String tickPrice = tickArray.get(j).getAsJsonObject().get("price").getAsString();
                    BigDecimal existedBestofferInTrade = new BigDecimal(tickPrice);
                    if(existedBestofferInTrade.compareTo(bestofferForTrade) == 0){
                        isTrade = false;
                        break;
                    }
                }

                // 호가에 없을 경우 거래를 날려야 함
                if(isTrade){
                    String bestofferCnt = Utils.getRandomString(realtimeSync.getMinBestofferCnt(), realtimeSync.getMaxBestofferCnt());
                    JsonObject object   = new JsonObject();

                    object.add("price", gson.fromJson(bestofferForTrade.toPlainString(),JsonElement.class));
                    object.add("cnt",   gson.fromJson(bestofferCnt, JsonElement.class));
                    list.add(object);
                }
                isTrade = false;
            }
        }
        return list;
    }

    /**
     * 각 코인에 등록한 통화 반환
     * @param exchange
     * @param coin
     * @param coinId
     */
    public String getCurrency(Exchange exchange, String coin, String coinId) throws Exception{
        String returnVal = ReturnCode.FAIL.getValue();

        // 거래소를 체크하는 이유는 여러거래소에서 같은 코인을 할 수 있기에
        if(!exchange.getExchangeCoin().isEmpty()){
            for(ExchangeCoin data : exchange.getExchangeCoin()){
                if(data.getCoinCode().equals(coin) && data.getId() == Long.parseLong(coinId)){
                    returnVal = data.getCurrency();
                    break;
                }
            }
        }
        if(returnVal.equals(ReturnCode.FAIL.getValue())){
            String msg = "There is no currency with " + coin + " and " + coinId;
            throw new Exception(msg);
        }
        return returnVal;
    }

    // Http get method
    protected String getHttpMethod(String url) throws Exception{
        log.info("[ABSTRACT EXCHANGE][GET HTTP] url : {}", url);
        StringBuffer response = null;

        URL urlObject = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) urlObject.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
        connection.setConnectTimeout(UtilsData.TIMEOUT_VALUE);
        connection.setReadTimeout(UtilsData.TIMEOUT_VALUE);

        int returnCode    = connection.getResponseCode();
        String returnMsg  = connection.getResponseMessage();
        if(returnCode == HttpURLConnection.HTTP_OK){
            InputStreamReader reader = null;
            if(connection.getInputStream() != null){
                reader = new InputStreamReader(connection.getInputStream());
            }else if(connection.getErrorStream() != null){
                reader = new InputStreamReader(connection.getErrorStream());
            }else{
                log.error("[ABSTRACT EXCHANGE][GET HTTP] Http response is 200. But inputstream is null!!");
                throw new Exception();
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            response = new StringBuffer();
            String inputLine = "";
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();
        }else{
            log.error("[ABSTRACT EXCHANGE][GET HTTP] Error code : {}, Error msg : {}", returnCode, returnMsg);
            throw new Exception();
        }
        return response.toString();
    }

    /**
     * mode 값을 받아서 RANDOM일 경우 RANDOM값을 전달
     * @param mode
     * @return
     */
    protected Trade getMode(Trade mode){
        if(mode == Trade.RANDOM){
            return (Utils.getRandomInt(0,1) == 0) ? Trade.BUY : Trade.SELL;
        }else{
            return mode;
        }
    }

    /**
     * Random하게 BUY / SELL 중 하나의 모드를 반환.
     * @return
     */
    protected Trade getMode(){
        return (Utils.getRandomInt(0,1) == 0) ? Trade.BUY : Trade.SELL;
    }
}
