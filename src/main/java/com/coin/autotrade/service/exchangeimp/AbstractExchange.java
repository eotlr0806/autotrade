package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.common.TradeData;
import com.coin.autotrade.common.TradeService;
import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Abstract class for common function and variable
@Slf4j
public abstract class AbstractExchange {
     AutoTrade autoTrade         = null;
     Liquidity liquidity         = null;
     Fishing fishing             = null;
     RealtimeSync realtimeSync   = null;
     CoinService coinService     = null; // Fishing 시, 사용하기 위한 coin Service class
     Gson gson                   = new Gson();

    /** 자전 거래를 이용하기위한 초기값 설정 */
    public abstract void initClass(AutoTrade autoTrade) throws Exception;
    /** 호가 유동성을 이용하기 위한 초기값 설정 */
    public abstract void initClass(Liquidity liquidity) throws Exception;
    /** 매매 긁기를 이용하기 위한 초기값 설정 */
    public abstract void initClass(Fishing fishing, CoinService coinService) throws Exception;
    /** 실시간 동기화를 이용하기 위한 초기값 설정 */
    public abstract void initClass(RealtimeSync realtimeSync) throws Exception;

    public abstract int startAutoTrade(String price, String cnt);
    public abstract int startLiquidity(Map list);
    public abstract int startFishingTrade(Map<String, List> list, int intervalTime);

    /**
     * Order book list 를 조회하는 메서드
     * @param exchange
     * @param coinWithId
     * @return 실패 시, ReturnCode.FAIL / 성공 시, 데이터
     */
    public abstract String getOrderBook(Exchange exchange, String[] coinWithId);

    // TODO : check
    public int startRealtimeTrade(JsonObject realtime) {

        return 200;
    }


    /**
     * 실시간 동기화에서 사용하며, 현재 가격이 설정한 minPrice or maxPrice 보다 낮거나 높으면 매수
     * @param currentPriceStr 현재 가격
     * @return 해당 구간에 있을 경우 0, 지지선보다 낮을 경우 -1, 저항선보다 높을 경우 1
     */
    public int isMoreOrLessPrice(String currentPriceStr) throws Exception {
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
    public Map<String, String> getTargetTick(String openingPrice, String currentPrice, String realtimePercent) throws Exception{
        int roundUpScale   = 15;     // 반올림 소수점
        Map<String,String> returnMap = new HashMap<>();

        BigDecimal openingDecimalPrice = new BigDecimal(openingPrice);                          // 시가
        BigDecimal currentDecimalPrice = new BigDecimal(currentPrice);                          // 현재가
        BigDecimal differencePrice     = currentDecimalPrice.subtract(openingDecimalPrice);     // 현재가 - 시가
        BigDecimal differencePercent   = differencePrice.divide(openingDecimalPrice, roundUpScale, BigDecimal.ROUND_CEILING); // 시가 대비 현재가 증가 및 감소율

        BigDecimal realtimeDecimalPercent  = new BigDecimal(realtimePercent);                // 실시간 연동하는 코인의 증감률
        BigDecimal syncPercent             = new BigDecimal(realtimeSync.getPricePercent()); // 1~100 까지의 %값
        BigDecimal realtimeTargetPercent   = realtimeDecimalPercent.multiply(syncPercent).divide(new BigDecimal(100),roundUpScale, BigDecimal.ROUND_CEILING); // 실시간 연동 코인 증감률 * 설정 값

        // 1의 자리가 같을 경우 패스 ex) 1.9 == 1.1 같은 선상이라고 보고, 패스함.
        // 추가적으로 -0.3은 -1 로 취급
        BigDecimal realtimeTargetPercentFloor = realtimeTargetPercent.setScale(2, BigDecimal.ROUND_FLOOR);
        BigDecimal differencePercentFloor     = differencePercent.setScale(2, BigDecimal.ROUND_FLOOR);
        if(realtimeTargetPercentFloor.compareTo(differencePercentFloor) != 0){  // 두개의 차이가 같은 구간이 아닐 경우
            if(realtimeTargetPercent.compareTo(differencePercent) < 0){         // 동기화 코인 %가 기준 코인 상승률보다 적으면 true
                returnMap.put("mode", TradeData.MODE_SELL);
            }else if(realtimeTargetPercent.compareTo(differencePercent) > 0){
                returnMap.put("mode",TradeData.MODE_BUY);
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
                    realtimeTargetPercent.multiply(new BigDecimal(100)), realtimeSync.getCoin(),
                    differencePercent.multiply(new BigDecimal(100)), targetPrice.toPlainString(), returnMap.get("mode"));
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
    public BigDecimal makeTargetPrice(BigDecimal openingPrice, BigDecimal targetPercent) throws Exception{
        String coinPriceStr   = "";
        String[] coinArr      = TradeService.splitCoinWithId(realtimeSync.getCoin());
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
}
