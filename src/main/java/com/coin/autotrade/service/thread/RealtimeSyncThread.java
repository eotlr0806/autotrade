package com.coin.autotrade.service.thread;

import com.coin.autotrade.common.BeanUtils;
import com.coin.autotrade.common.TradeData;
import com.coin.autotrade.common.TradeService;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.model.RealtimeSync;
import com.coin.autotrade.model.User;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.repository.RealtimeSyncRepository;
import com.coin.autotrade.service.CoinService;
import com.coin.autotrade.service.exchangeimp.AbstractExchange;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

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
    boolean run                 = true;
    RealtimeSync realtimeSync   = null;

    /**
     * 해당 쓰레드에 필요한 초기값 셋팅
     */
    public void setTrade(RealtimeSync inputRealtimeSync) throws Exception{
        coinService            = (CoinService) BeanUtils.getBean(CoinService.class);
        realtimeSyncRepository = (RealtimeSyncRepository) BeanUtils.getBean(RealtimeSyncRepository.class);

        realtimeSync           = inputRealtimeSync;
        abstractExchange       = TradeService.getInstance(realtimeSync.getExchange().getExchangeCode());
        abstractExchange.initClass(realtimeSync);
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

                JsonObject realtime = getRealtimeData(realtimeSync.getSyncCoin());
                abstractExchange.startRealtimeTrade(realtime);

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

    // 업비트쪽으로 Get 을 날려 실시간 데이터를 받아 옴.
    private JsonObject getRealtimeData(String coin) throws Exception{
        String url = TradeData.UPBIT_REALTIME + "?markets=" + coin;
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

        JsonArray returnArr = TradeService.getGson().fromJson(response.toString(), JsonArray.class);
        return returnArr.get(0).getAsJsonObject();  // [{ ... }] 형식으로, Arr 안에 1개의 Json만있음.
    }

}
