package com.coin.autotrade.common;

import com.coin.autotrade.service.thread.AutoTradeThread;
import com.coin.autotrade.service.thread.FishingTradeThread;
import com.coin.autotrade.service.thread.LiquidityTradeThread;
import com.coin.autotrade.service.thread.RealtimeSyncThread;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*** 공통으로 쓰이는 데이터를 담은 클래스 */
public class UtilsData {
    /** Common value **/
    public static Map<String, String> FLATA_SESSION_KEY = new HashMap<String, String>();    // api user , Key

    /** Define exchange name */
    public static String COINONE        = "COINONE";
    public static String DCOIN          = "DCOIN";
    public static String FOBLGATE       = "FOBLGATE";
    public static String FLATA          = "FLATA";
    public static String BITHUMB_GLOBAL = "BITHUMBGLOBAL";
    public static String BITHUMB        = "BITHUMB";
    public static String KUCOIN         = "KUCOIN";
    public static String OKEX           = "OKEX";
    public static String GATEIO         = "GATEIO";
    public static String LBANK          = "LBANK";
    public static String DIGIFINEX      = "DIGIFINEX";
    public static String XTCOM          = "XTCOM";

    public static String MODE_RANDOM    = "RANDOM";
    public static String MODE_SELL      = "SELL";
    public static String MODE_BUY       = "BUY";
    public static String MODE_SELF_L    = "SELF_L";
    public static String STATUS_RUN     = "RUN";
    public static String STATUS_STOP    = "STOP";


    /************************* DEFINE API URL *************************/
    public static String UPBIT_REALTIME           = "https://api.upbit.com/v1/ticker";  // 실시간 연동 API

    public static String COINONE_ORDERBOOK        = "https://api.coinone.co.kr/orderbook";
    public static String COINONE_TICK             = "https://api.coinone.co.kr/ticker_utc";
    public static String COINONE_LIMIT_BUY        = "https://api.coinone.co.kr/v2/order/limit_buy/";
    public static String COINONE_LIMIT_SELL       = "https://api.coinone.co.kr/v2/order/limit_sell/";
    public static String COINONE_CANCEL           = "https://api.coinone.co.kr/v2/order/cancel/";

    public static String FOBLGATE_ORDERBOOK       = "https://api2.foblgate.com/api/ticker/orderBook";
    public static String FOBLGATE_TICK            = "https://api2.foblgate.com/api/chart/selectChart";
    public static String FOBLGATE_CREATE_ORDER    = "https://api2.foblgate.com/api/trade/orderPlace";
    public static String FOBLGATE_CANCEL_ORDER    = "https://api2.foblgate.com/api/trade/orderCancel";

    public static String DCOIN_ORDERBOOK          = "https://openapi.dcoin.com/open/api/market_dept";
    public static String DCOIN_TICK               = "https://openapi.dcoin.com/open/api/get_ticker";
    public static String DCOIN_CREATE_ORDER       = "https://openapi.dcoin.com/open/api/create_order";
    public static String DCOIN_CANCEL_ORDER       = "https://openapi.dcoin.com/open/api/cancel_order";

    public static String FLATA_ORDERBOOK          = "https://www.flata.exchange/out/api/getSnapshot";
    public static String FLATA_COININFO           = "https://www.flata.exchange/out/api/InstList";
    public static String FLATA_TICK               = "https://www.flata.exchange/out/api/getTicker";
    public static String FLATA_MAKE_SESSION       = "https://www.flata.exchange/out/api/confirm/check";
    public static String FLATA_CREATE_ORDER       = "https://www.flata.exchange/out/api/trading/newOrder";
    public static String FLATA_CANCEL_ORDER       = "https://www.flata.exchange/out/api/trading/cancelOrder";

    public static String KUCOIN_URL               = "https://api.kucoin.com";
    public static String KUCOIN_ORDERBOOK         = "https://api.kucoin.com/api/v1/market/orderbook/level2_100";
    public static String KUCOIN_TICK              = "https://api.kucoin.com/api/v1/market/allTickers";

    public static String GATEIO_URL               = "https://api.gateio.ws/api/v4";
    public static String GATEIO_ORDERBOOK         = "https://api.gateio.ws/api/v4/spot/order_book";
    public static String GATEIO_TICK              = "https://api.gateio.ws/api/v4/spot/tickers";

    public static String LBANK_ORDERBOOK          = "https://www.lbkex.net/v2/depth.do";
    public static String LBANK_TICK               = "https://www.lbkex.net/v2/ticker/24hr.do";

    public static String DIGIFINEX_ORDERBOOK      = "https://openapi.digifinex.com/v3/order_book";
    public static String DIGIFINEX_TICK           = "https://openapi.digifinex.com/v3/ticker";
    public static String DIGIFINEX_TIMESTAMP      = "https://openapi.digifinex.com/v3/time";
    public static String DIGIFINEX_CREATE_ORDER   = "https://openapi.digifinex.com/v3/spot/order/new";
    public static String DIGIFINEX_CANCEL_ORDER   = "https://openapi.digifinex.com/v3/spot/order/cancel";

    public static String XTCOM_ORDERBOOK          = "https://api.xt.com/data/api/v1/getDepth";
    public static String XTCOM_CREATE_ORDER       = "https://api.xt.com/trade/api/v1/order";
    public static String XTCOM_CANCEL_ORDER       = "https://api.xt.com/trade/api/v1/cancel";
    public static String XTCOM_TICK               = "https://api.xt.com/data/api/v1/getTickers";
    public static String XTCOM_GET_COININFO       = "https://api.xt.com/data/api/v1/getMarketConfig";

    public static String BITHUMB_URL                    = "https://api.bithumb.com";
    public static String BITHUMB_ORDERBOOK              = "https://api.bithumb.com/public/orderbook";
    public static String BITHUMB_TICK                   = "https://api.bithumb.com/public/ticker";
    public static String BITHUMB_ENDPOINT_CREATE_ORDER  = "/trade/place";
    public static String BITHUMB_ENDPOINT_CANCEL_ORDER  = "/trade/cancel";

    public static String BITHUMB_GLOBAL_ORDERBOOK       = "https://global-openapi.bithumb.pro/openapi/v1/spot/orderBook";
    public static String BITHUMB_GLOBAL_TICK            = "https://global-openapi.bithumb.pro/openapi/v1/spot/ticker";
    public static String BITHUMB_GLOBAL_CREATE_ORDER    = "https://global-openapi.bithumb.pro/openapi/v1/spot/placeOrder";
    public static String BITHUMB_GLOBAL_CANCEL_ORDER    = "https://global-openapi.bithumb.pro/openapi/v1/spot/cancelOrder";

    public static String OKEX_URL                       = "https://www.okex.com";
    public static String OKEX_ORDERBOOK                 = "https://www.okex.com/api/v5/market/books";
    public static String OKEX_TICK                      = "https://www.okex.com/api/v5/market/tickers";
    public static String OKEX_ENDPOINT_CREATE_ORDER     = "/api/v5/trade/order";
    public static String OKEX_ENDPOINT_CANCEL_ORDER     = "/api/v5/trade/cancel-order";


    /** Define Variable */
    public static int TIMEOUT_VALUE = 10000;

    /** Thread Concurrent Hashmap */
    public static ConcurrentHashMap<Long, AutoTradeThread> autoTradeThreadMap        = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Long, LiquidityTradeThread> liquidityThreadMap   = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Long, FishingTradeThread> fishingTradeThreadMap  = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Long, RealtimeSyncThread> realtimeSyncThreadMap  = new ConcurrentHashMap<>();
}
