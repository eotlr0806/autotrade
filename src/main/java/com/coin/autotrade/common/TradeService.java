package com.coin.autotrade.common;

import com.coin.autotrade.service.exchangeimp.*;
import com.coin.autotrade.service.thread.AutoTradeThread;
import com.coin.autotrade.service.thread.FishingTradeThread;
import com.coin.autotrade.service.thread.LiquidityTradeThread;
import com.coin.autotrade.service.thread.RealtimeSyncThread;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/*** 공통으로 쓰이는 서비스 로직을 담은 클래스 */
@Slf4j
public class TradeService {

    private static Random random     = null;
    private static Gson gson         = null;
    private static JsonMapper mapper = null;

    /**
     * 공통으로 사용하는 Gson 객체 반환
     * @return
     */
    public static Gson getGson() {
        if(gson == null){
            gson = new Gson();
        }
        return gson;
    }

    /**
     * 공통으로 사용하는 Gson 객체 반환
     * @return
     */
    public static JsonMapper getMapper() {
        if(mapper == null){
            mapper = new JsonMapper();
        }
        return mapper;
    }

    /**
     * min 과 max 사이의 값 반환
     * @Param min - minial value
     * @Param max - maxial value
     */
    public static int getRandomInt (int min, int max){
        int value = 2100000000;
        try{
            if(random == null){
                random = new Random();
            }
            value = random.nextInt(max-min+1)+min;
        }catch(Exception e){
            log.error("[ServiceCommon.java - getRandomInt] {}", e.getMessage());
        }
        return value;
    }

    /**
     * min 과 max 사이의 값 반환
     * @Param min - minial value
     * @Param max - maxial value
     */
    public static double getRandomDouble (double min, double max){
        double value = 0.0;
        try{
            if(random == null){
                random = new Random();
            }
            value = min + (random.nextDouble() * (max - min));
        }catch(Exception e){
            log.error("[ServiceCommon.java - getRandomDouble] {}", e.getMessage());
        }
        return value;
    }

    public static BigDecimal getRandomDecimal (BigDecimal min, BigDecimal max){
        BigDecimal value = null;
        try{
            if(random == null){ random = new Random(); }

            BigDecimal randomVal = new BigDecimal(String.valueOf(random.nextDouble()));
            value = min.add(randomVal.multiply( max.subtract(min) ));
        }catch(Exception e){
            log.error("[ServiceCommon.java - getRandomDecimal] {}", e.getMessage());
        }
        return value;
    }

    @Async
    public static int startThread (Thread thread) throws Exception {
        thread.start();
        return TradeData.CODE_SUCCESS;
    }

    /**
     * schedule thread를 공통 Concurrent Hashmap 에 담아 보관한다.
     * @param thread - Schedule thread object
     * @return - true : 성공 / false / 실패
     */
    public static boolean setAutoTradeThread (Long id, AutoTradeThread thread){
        try{
            TradeData.autoTradeThreadMap.put(id, thread);
            return true;
        }catch (Exception e){
            log.error("[SET AUTOTRADE THREAD] Fail saving thread Thread id: {}", id);
            e.printStackTrace();
        }
        return false;
    }

    /**
     * schedule thread에서 map 에 값을 remove 후 get해서 반환
     * @param key
     * @return
     */
    public static AutoTradeThread popAutoTradeThread (long id){
        AutoTradeThread thread = TradeData.autoTradeThreadMap.get(id);
        TradeData.autoTradeThreadMap.remove(id);
        return thread;
    }

    /**
     * @return id 가 있을 경우 true, 없을 경우 false
     * */
    public static boolean isAutoTradeThread(long id){
        if(TradeData.autoTradeThreadMap.containsKey(id)){
            return true;
        }else{
            return false;
        }
    }


    /**
     * liquidity thread를 공통 Concurrent Hashmap 에 담아 보관한다.
     * @param thread - Liquidity thread object
     * @return - true : 성공 / false / 실패
     */
    public static boolean setLiquidityThread (Long id, LiquidityTradeThread thread){
        try{
            TradeData.liquidityThreadMap.put(id, thread);
            return true;
        }catch (Exception e){
            log.error("[SET LIQUIDITY THREAD] Fail saving thread Thread id: {}", id);
            System.out.println(e.getMessage());
        }
        return false;
    }

    /**
     * liquidity thread에서 map 에 값을 remove 후 get해서 반환
     * @param key
     * @return
     */
    public static LiquidityTradeThread popLiquidityThread (long id){
        LiquidityTradeThread thread = TradeData.liquidityThreadMap.get(id);
        TradeData.liquidityThreadMap.remove(id);
        return thread;
    }

    /**
     * @return id 가 있을 경우 true, 없을 경우 false
     * */
    public static boolean isLiquidityTradeThread(long id){
        if(TradeData.liquidityThreadMap.containsKey(id)){
            return true;
        }else{
            return false;
        }
    }

    /**
     * fishing thread를 공통 Concurrent Hashmap 에 담아 보관한다.
     * @param thread - Fishing thread object
     * @return - true : 성공 / false / 실패
     */
    public static boolean setFishingThread (Long id, FishingTradeThread thread){
        try{
            TradeData.fishingTradeThreadMap.put(id, thread);
            return true;
        }catch (Exception e){
            log.error("[SET FISHING THREAD] Fail saving thread Thread id: {}", id);
            e.printStackTrace();
        }
        return false;
    }

    /**
     * liquidity thread에서 map 에 값을 remove 후 get해서 반환
     * @param key
     * @return
     */
    public static FishingTradeThread popFishingThread (long id){
        FishingTradeThread thread = TradeData.fishingTradeThreadMap.get(id);
        TradeData.fishingTradeThreadMap.remove(id);
        return thread;
    }

    /**
     * @return id 가 있을 경우 true, 없을 경우 false
     * */
    public static boolean isFishingTradeThread(long id){
        if(TradeData.fishingTradeThreadMap.containsKey(id)){
            return true;
        }else{
            return false;
        }
    }


    /**
     * schedule thread를 공통 Concurrent Hashmap 에 담아 보관한다.
     * @param thread - Schedule thread object
     * @return - true : 성공 / false / 실패
     */
    public static boolean setRealtimeSyncThread (Long id, RealtimeSyncThread thread){
        try{
            TradeData.realtimeSyncThreadMap.put(id, thread);
            return true;
        }catch (Exception e){
            log.error("[SET REALTIMESYNC THREAD] Fail saving thread Thread id: {}", id);
            e.printStackTrace();
        }
        return false;
    }

    /**
     * schedule thread에서 map 에 값을 remove 후 get해서 반환
     * @param key
     * @return
     */
    public static RealtimeSyncThread popRealtimeSyncThread (long id){
        RealtimeSyncThread thread = TradeData.realtimeSyncThreadMap.get(id);
        TradeData.realtimeSyncThreadMap.remove(id);
        return thread;
    }

    /**
     * @return id 가 있을 경우 true, 없을 경우 false
     * */
    public static boolean isRealtimeSyncThread(long id){
        if(TradeData.realtimeSyncThreadMap.containsKey(id)){
            return true;
        }else{
            return false;
        }
    }

    /**
     * 현재 시간 반환
     * @return
     */
    public static String getNowData() {
        Date date = Calendar.getInstance().getTime();
        DateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");
        String strDate = dateFormat.format(date);

        return strDate;
    }

    /**
     * coin;id 로 온 값을 배열로 반환
     * @param coin coin;id
     * @return String[]에 index 0 에는 coin, 1에는 id를 넘겨준다. ex) [coin, id]
     */
    public static String[] splitCoinWithId(String coin){
        String[] coinData = null;
        try{
            coinData = coin.split(";");
        }catch (Exception e){
            log.error("[SET COIN DATA] {}", e.getMessage());
            e.printStackTrace();
        }
        return coinData;
    }

    public static Map deepCopy( Map<String,String> paramMap) throws Exception{
        Map<String, String> copy = new HashMap<String, String>();
        for (String key : paramMap.keySet()){
            copy.put(key, paramMap.get(key));
        }

        return copy;
    }


    /**
     * 해당 거래소에 맞게 인스턴스 반환
     * @param exchange
     */
    public static AbstractExchange getInstance(String exchange) throws Exception{

        AbstractExchange abstractExchange = null;

        if(TradeData.COINONE.equals(exchange)){
            abstractExchange = new CoinOneImp();
        } else if(TradeData.FOBLGATE.equals(exchange)){
            abstractExchange = new FoblGateImp();
        } else if(TradeData.FLATA.equals(exchange)){
            abstractExchange = new FlataImp();
        } else if(TradeData.DCOIN.equals(exchange)){
            abstractExchange = new DcoinImp();
        } else if(TradeData.BITHUMB_GLOBAL.equals(exchange)){
            abstractExchange = new BithumbGlobalImp();
        } else if(TradeData.BITHUMB.equals(exchange)){
            abstractExchange = new BithumbImp();
        } else if(TradeData.KUCOIN.equals(exchange)){
            abstractExchange = new KucoinImp();
        } else if(TradeData.OKEX.equals(exchange)){
            abstractExchange = new OkexImp();
        } else if(TradeData.GATEIO.equals(exchange)){
            abstractExchange = new GateIoImp();
        }

        return abstractExchange;
    }

}

