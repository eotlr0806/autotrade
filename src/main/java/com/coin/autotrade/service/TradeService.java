package com.coin.autotrade.service;

import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.model.AutoTrade;
import com.coin.autotrade.model.Fishing;
import com.coin.autotrade.model.Liquidity;
import com.coin.autotrade.repository.AutoTradeRepository;
import com.coin.autotrade.repository.FishingRepository;
import com.coin.autotrade.repository.LiquidityRepository;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TradeService {

    @Autowired
    AutoTradeRepository autotradeRepository;

    @Autowired
    LiquidityRepository liquidityRepository;

    @Autowired
    FishingRepository fishingRepository;

    private final String AUTOTRADE = "AUTOTRADE";
    private final String LIQUIDITY = "LIQUIDITY";
    private final String FISHING   = "FISHING";

    // Start autoTrade
    public ReturnCode saveTrade(String trade){

        ReturnCode returnValue = ReturnCode.SUCCESS;

        try{
            Gson gson = new Gson();
            JsonObject tradeJson = gson.fromJson(trade, JsonObject.class);
            String type = tradeJson.get("tradeType").getAsString();

            if(type.equals(AUTOTRADE)){
                AutoTrade autoTrade = gson.fromJson(trade , AutoTrade.class);
                autoTrade.setId(autotradeRepository.selectMaxId());
                autoTrade.setDate(ServiceCommon.getNowData());
                autotradeRepository.save(autoTrade);                // DB에 해당 autotrade 저장
            }else if(type.equals(LIQUIDITY)){
                Liquidity liquidity = gson.fromJson(trade , Liquidity.class);
                liquidity.setId(liquidityRepository.selectMaxId());
                liquidity.setDate(ServiceCommon.getNowData());
                liquidityRepository.save(liquidity);                // DB에 해당 liquiditiy 저장
            }else if(type.equals(FISHING)){
                Fishing fishing = gson.fromJson(trade , Fishing.class);
                fishing.setId(fishingRepository.selectMaxId());
                fishing.setDate(ServiceCommon.getNowData());
                fishingRepository.save(fishing);                    // DB에 해당 liquiditiy 저장
            }else{
                log.error("[SAVE TRADE] Saving type is not correct : {}", trade);
                returnValue = ReturnCode.FAIL;
            }
        }catch(Exception e){
            log.error("[SAVE TRADE] Occur error : {}",e.getMessage());
            e.printStackTrace();
        }
        return returnValue;
    }
}
