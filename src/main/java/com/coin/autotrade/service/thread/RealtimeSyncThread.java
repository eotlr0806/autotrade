package com.coin.autotrade.service.thread;

import com.coin.autotrade.common.BeanUtils;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.enumeration.RealTimeSyncType;
import com.coin.autotrade.model.RealtimeSync;
import com.coin.autotrade.repository.RealtimeSyncRepository;
import com.coin.autotrade.service.BithumbHttpService;
import com.coin.autotrade.service.CoinService;
import com.coin.autotrade.service.exchangeimp.AbstractExchange;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Liquidity Trade Thread
 * DESC : 호가 유동성 쓰레드
 */
@Service
@Slf4j
public class RealtimeSyncThread implements Runnable{

    CoinService         coinService;
    RealtimeSyncRepository realtimeSyncRepository;
    AbstractExchange abstractExchange;
    boolean run                           = true;
    RealtimeSync realtimeSync             = null;
    private BigDecimal initRealTimeRate   = null;

    /**
     * 해당 쓰레드에 필요한 초기값 셋팅
     */
    public void setTrade(RealtimeSync inputRealtimeSync) throws Exception{
        coinService            = (CoinService) BeanUtils.getBean(CoinService.class);
        realtimeSyncRepository = (RealtimeSyncRepository) BeanUtils.getBean(RealtimeSyncRepository.class);

        realtimeSync           = inputRealtimeSync;
        abstractExchange       = Utils.getInstance(realtimeSync.getExchange().getExchangeCode());
        abstractExchange.initClass(realtimeSync, coinService);
    }

    // Stop thread
    public void setStop(){
        run = false;
    }

    @Override
    public void run() {
        try{
            int intervalTime = 100;
            while(run){

                // 업비트 초기화시간인 9시에 맞춰, 8:50 ~ 9:10 까지는 배치가 돌지 않음.
                boolean sleepTime = isSleepTime();
                if(!sleepTime){
                    boolean resetTargetRate = getInitTargetRate();
                    JsonObject realtime     = getRealtimeData();
                    setChangeRate(realtime);    // 시작된 시점 기준으로 realtime 을 잡도록.

                    abstractExchange.startRealtimeTrade(realtime, resetTargetRate);  // 해당 데이터에서 사용하는 값은 signed_change_rate
                }
                intervalTime = realtimeSync.getSyncTime() * 1000;
                log.info("[REALTIME SYNC THREAD] Run thread , intervalTime : {} seconds", intervalTime/1000);
                Thread.sleep(intervalTime);
            }
        }catch (MalformedURLException e){
            log.error("[REALTIME SYNC THREAD] Get realtime error {}", e.getMessage());
            e.printStackTrace();
        }catch(Exception e){
            log.error("[REALTIME SYNC THREAD] Run is failed {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean getInitTargetRate() throws Exception {
        if(initRealTimeRate == null){
            return true;
        }else{
            return false;
        }
    }

    private boolean isSleepTime() throws Exception {
        LocalDateTime nowTime = LocalDateTime.now();
        log.info("[REALTIME SYNC THREAD] current Time : {} ", nowTime);
        int nowHour = nowTime.getHour();
        int nowMin  = nowTime.getMinute();
        if(nowHour == 8){       // 8:55 ~ 8:59
            if(nowMin >= 55 && nowMin <= 59){
                log.info("[REALTIME SYNC THREAD] This is stop time : {} ", nowTime);
                resetInitRealTimeRate();
                return true;
            }
        }else if(nowHour == 9){ // 9:00 ~ 9:05
            if(nowMin <= 5){
                log.info("[REALTIME SYNC THREAD] This is stop time : {} ", nowTime);
                resetInitRealTimeRate();
                return true;
            }
        }else if(nowHour == 23){    // 23:55 ~ 23:59
            if(nowMin >= 55 && nowMin <= 59){
                log.info("[REALTIME SYNC THREAD] This is stop time : {} ", nowTime);
                resetInitRealTimeRate();
                return true;
            }
        }else if(nowHour == 0){    // 00:00 ~ 00:05
            if(nowMin <= 5){
                log.info("[REALTIME SYNC THREAD] This is stop time : {} ", nowTime);
                resetInitRealTimeRate();
                return true;
            }
        }

        return false;
    }

    private void resetInitRealTimeRate() throws Exception {
        if(initRealTimeRate != null){
            initRealTimeRate = null;
        }
    }

    // 시작 한 시점을 시준으로 기준을 잡게 하기 위해 사용
    private void setChangeRate(JsonObject object) throws Exception {
        if(initRealTimeRate == null){
            initRealTimeRate = new BigDecimal(object.get("signed_change_rate").getAsString());
            if(object.has("signed_change_rate")){
                object.remove("signed_change_rate");
                object.addProperty("signed_change_rate","0.00");
            }
            log.info("[REALTIME SYNC THREAD] Set init realtime rate : {} ", initRealTimeRate);
        }else{
            // 시작 -3% 현재 -4%, 이동 -1% = current - start ( start > current )
            // 시작 -3% 현재 -2%, 이동 1%  = current - start ( start < current )
            // 시작 3%  현재 2% , 이동 -1% = current - start ( start > current )
            // 시작 3%  현재 4% , 이동 1%  = current - start
            BigDecimal nowRate  = new BigDecimal(object.get("signed_change_rate").getAsString());
            BigDecimal moveRate = nowRate.subtract(initRealTimeRate);
            if(object.has("signed_change_rate")){
                object.remove("signed_change_rate");
                object.addProperty("signed_change_rate",moveRate);
            }
            log.info("[REALTIME SYNC THREAD] realtime init:{}, now:{}, move:{} ",initRealTimeRate, nowRate, moveRate);
        }
    }

    // 업비트쪽으로 Get 을 날려 실시간 데이터를 받아 옴.
    private JsonObject getRealtimeData() throws Exception{

        String coin = realtimeSync.getSyncCoin();
        String url = "";
        if(realtimeSync.getType() == RealTimeSyncType.NOW){   // 실시간
            url = UtilsData.UPBIT_REALTIME + "?markets=" + coin;
        }else{
            url = UtilsData.UPBIT_REALTIME_BEFORE + "?market=" + coin + "&to=" + makeBeforeTime();
        }
        log.info("[REALTIME SYNC THREAD] Get realtime data on this url : {}" , url);

        URL urlWithParam = new URL(url);
        HttpURLConnection http = (HttpURLConnection) urlWithParam.openConnection();
        http.setRequestProperty("Accept", "application/json");
        http.setConnectTimeout(10000);
        BufferedReader bufferedReader = null;

        int resposeCode = http.getResponseCode();
        if(resposeCode == HttpURLConnection.HTTP_OK){
           bufferedReader = new BufferedReader(new InputStreamReader(http.getInputStream()));
        }else{
           bufferedReader = new BufferedReader(new InputStreamReader(http.getErrorStream()));
        }

        String inputLine;
        StringBuffer response = new StringBuffer();
        while ((inputLine = bufferedReader.readLine()) != null) {
            response.append(inputLine);
        }
        bufferedReader.close();

        if(resposeCode != HttpURLConnection.HTTP_OK){
            throw new MalformedURLException(response.toString());
        }

        JsonArray returnArr = Utils.getGson().fromJson(response.toString(), JsonArray.class);

        if(realtimeSync.getType() == RealTimeSyncType.NOW){   // 실시간
            return returnArr.get(0).getAsJsonObject();  // [{ ... }] 형식으로, Arr 안에 1개의 Json만있음.
        }else{
            return makeBeforeRealTime(returnArr.get(0).getAsJsonObject());
        }
    }

    private String makeBeforeTime() throws Exception {
        LocalDateTime target = LocalDateTime.now().minusHours(realtimeSync.getBeforeTime());
        String date          = target.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        log.info("[REALTIME SYNC THREAD] target before time : {}" , date);
        String url           = URLEncoder.encode(date,"UTF-8");
        return url;
    }

    private JsonObject makeBeforeRealTime(JsonObject object) throws Exception{
        JsonObject returnObj = new JsonObject();

        BigDecimal openingDecimalPrice = new BigDecimal(object.get("opening_price").getAsString());   // 시가
        BigDecimal currentDecimalPrice = new BigDecimal(object.get("trade_price").getAsString());     // 종가
        BigDecimal differencePrice     = currentDecimalPrice.subtract(openingDecimalPrice);     // 현재가 - 시가
        BigDecimal differencePercent   = differencePrice.divide(openingDecimalPrice, 15, BigDecimal.ROUND_CEILING); // 시가 대비 현재가 증가 및 감소율

        returnObj.addProperty("signed_change_rate", differencePercent.toPlainString());
        return returnObj;
    }

}
