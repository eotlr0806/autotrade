package com.coin.autotrade.common;

import com.coin.autotrade.service.thread.AutoTradeThread;
import com.coin.autotrade.service.thread.FishingTradeThread;
import com.coin.autotrade.service.thread.LiquidityTradeThread;

import java.util.HashMap;
import java.util.Map;

/*** 공통으로 쓰이는 데이터를 담은 클래스 */
public class DataCommon {
    /** Common value **/
    public static Map<String, String> FLATA_SESSION_KEY = new HashMap<String, String>();    // api user , Key

    /** Define exchange name */
    public static String COINONE        = "COINONE";
    public static String DCOIN          = "DCOIN";
    public static String FOBLGATE       = "FOBLGATE";
    public static String FLATA          = "FLATA";
    public static String BITHUMB_GLOBAL = "BITHUMBGLOBAL";
    public static String BITHUMB        = "BITHUMB";

    public static String MODE_RANDOM    = "RANDOM";
    public static String MODE_SELL      = "SELL";
    public static String MODE_BUY       = "BUY";
    public static String MODE_RANDOM_L  = "RANDOM_L";
    public static String MODE_SELF_L    = "SELF_L";
    public static String STATUS_RUN     = "RUN";
    public static String STATUS_STOP    = "STOP";


    /** Define Public Api URL */
    public static String COINONE_ORDERBOOK        = "https://api.coinone.co.kr/orderbook";
    public static String FOBLGATE_ORDERBOOK       = "https://api2.foblgate.com/api/ticker/orderBook";
    public static String DCOIN_ORDERBOOK          = "https://openapi.dcoin.com/open/api/market_dept";
    public static String FLATA_ORDERBOOK          = "https://www.flata.exchange/out/api/getSnapshot";
    public static String FLATA_COININFO           = "https://www.flata.exchange/out/api/InstList";
    public static String BITHUMB_GLOBAL_ORDERBOOK = "https://global-openapi.bithumb.pro/openapi/v1/spot/orderBook";
    public static String BITHUMB_ORDERBOOK        = "https://api.bithumb.com/public/orderbook";

    /** Define Private Api URL */
    public static String COINONE_LIMIT_BUY           = "https://api.coinone.co.kr/v2/order/limit_buy/";
    public static String COINONE_LIMIT_SELL          = "https://api.coinone.co.kr/v2/order/limit_sell/";
    public static String COINONE_CANCEL              = "https://api.coinone.co.kr/v2/order/cancel/";
    public static String DCOIN_CREATE_ORDER          = "https://openapi.dcoin.com/open/api/create_order";
    public static String DCOIN_CANCEL_ORDER          = "https://openapi.dcoin.com/open/api/cancel_order";
    public static String FLATA_MAKE_SESSION          = "https://www.flata.exchange/out/api/confirm/check";
    public static String FLATA_CREATE_ORDER          = "https://www.flata.exchange/out/api/trading/newOrder";
    public static String FLATA_CANCEL_ORDER          = "https://www.flata.exchange/out/api/trading/cancelOrder";
    public static String FOBLGATE_CREATE_ORDER       = "https://api2.foblgate.com/api/trade/orderPlace";
    public static String FOBLGATE_CANCEL_ORDER       = "https://api2.foblgate.com/api/trade/orderCancel";
    public static String BITHUMB_GLOBAL_CREATE_ORDER = "https://global-openapi.bithumb.pro/openapi/v1/spot/placeOrder";
    public static String BITHUMB_GLOBAL_CANCEL_ORDER = "https://global-openapi.bithumb.pro/openapi/v1/spot/cancelOrder";

    public static String BITHUMB_URL                    = "https://api.bithumb.com";
    public static String BITHUMB_ENDPOINT_CREATE_ORDER  = "/trade/place";
    public static String BITHUMB_ENDPOINT_CANCEL_ORDER  = "/trade/cancel";

    /** Define Variable */
    public static int TICK_DECIMAL  = 1000;
    public static int TIMEOUT_VALUE = 10000;

    /** RSA Key */
    public static String RSA_WEB_KEY = "_RSA_WEB_KEY_"; // 개인키 session key
    public static String RSA_INSTANCE = "RSA";          // RSA transformation

    /** Thread List */
    public static Map<Long, AutoTradeThread> autoTradeThreadMap        = new HashMap<Long,AutoTradeThread>();
    public static Map<Long, LiquidityTradeThread> liquidityThreadMap   = new HashMap<Long,LiquidityTradeThread>();
    public static Map<Long, FishingTradeThread> fishingTradeThreadMap  = new HashMap<Long,FishingTradeThread>();

    /** ERROR CODE */
    public static Integer CODE_ERROR       = 400;
    public static Integer CODE_ERROR_LOGIN = 401;
    public static Integer CODE_SUCCESS     = 200;

    /** Thread pool size */
    public static Integer THREAD_MIN      = 10;
    public static Integer THREAD_MAX      = 100;
    public static Integer THREAD_CAPACITY = 200;

}
