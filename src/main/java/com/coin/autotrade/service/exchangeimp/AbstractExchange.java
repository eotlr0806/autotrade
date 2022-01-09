package com.coin.autotrade.service.exchangeimp;

import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

// Abstract class for common function and variable
public abstract class AbstractExchange {
     AutoTrade autoTrade         = null;
     Liquidity liquidity         = null;
     Fishing fishing             = null;
     RealtimeSync realtimeSync   = null;
     CoinService coinService     = null; // Fishing 시, 사용하기 위한 coin Service class
     Gson gson                   = new Gson();

    /** 자전 거래를 이용하기위한 초기값 설정 */
    public abstract void initClass(AutoTrade autoTrade);
    /** 호가 유동성을 이용하기 위한 초기값 설정 */
    public abstract void initClass(Liquidity liquidity);
    /** 매매 긁기를 이용하기 위한 초기값 설정 */
    public abstract void initClass(Fishing fishing, CoinService coinService);
    /** 실시간 동기화를 이용하기 위한 초기값 설정 */
    public abstract void initClass(RealtimeSync realtimeSync);

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
}
