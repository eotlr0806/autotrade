package com.coin.autotrade.service.function;

import com.coin.autotrade.model.*;
import com.coin.autotrade.service.CoinService;
import com.google.gson.Gson;

import java.util.List;
import java.util.Map;

// Abstract class for common function and variable
public abstract class ExchangeFunction {
     User user                   = null;
     AutoTrade autoTrade         = null;
     Exchange exchange           = null;
     Liquidity liquidity         = null;
     Fishing fishing             = null;
     CoinService coinService     = null; // Fishing 시, 사용하기 위한 coin Service class
     Gson gson                   = new Gson();

    /** 자전 거래를 이용하기위한 초기값 설정 */
    public abstract void initClass(AutoTrade autoTrade, User user, Exchange exchange);
    /** 호가 유동성을 이용하기 위한 초기값 설정 */
    public abstract void initClass(Liquidity liquidity, User user, Exchange exchange);
    /** 매매 긁기를 이용하기 위한 초기값 설정 */
    public abstract void initClass(Fishing fishing, User user, Exchange exchange, CoinService coinService);

    public abstract int startAutoTrade(String price, String cnt);
    public abstract int startLiquidity(Map list);
    public abstract int startFishingTrade(Map<String, List> list, int intervalTime);
    public abstract String getOrderBook(Exchange exchange, String[] coinWithId);

}
