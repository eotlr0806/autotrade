package com.coin.autotrade.service;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.AutoTrade;
import com.coin.autotrade.model.Fishing;
import com.coin.autotrade.model.Liquidity;
import com.coin.autotrade.repository.*;
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

    @Autowired
    CoinService coinService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ExchangeRepository exchangeRepository;

    private String AUTOTRADE = "AUTOTRADE";
    private String LIQUIDITY = "LIQUIDITY";
    private String FISHING   = "FISHING";

    // Start autoTrade
    public int saveTrade(String trade){

        int returnValue = DataCommon.CODE_SUCCESS;

        try{
            log.info("[TRADE][SAVE] Data: {} ", trade);
            Gson gson = new Gson();

            JsonObject tradeJson = gson.fromJson(trade, JsonObject.class);
            String type = tradeJson.get("tradeType").getAsString();

            if(type.equals(AUTOTRADE)){
                AutoTrade autoTrade = gson.fromJson(trade , AutoTrade.class);
                Long id = autotradeRepository.selectMaxId();
                autoTrade.setId(id);
                autoTrade.setDate(ServiceCommon.getNowData());
                autotradeRepository.save(autoTrade);                // DB에 해당 autotrade 저장
            }else if(type.equals(LIQUIDITY)){
                Liquidity liquidity = gson.fromJson(trade , Liquidity.class);
                Long id = liquidityRepository.selectMaxId();
                liquidity.setId(id);
                liquidity.setDate(ServiceCommon.getNowData());
                liquidityRepository.save(liquidity);                // DB에 해당 liquiditiy 저장
            }else if(type.equals(FISHING)){
                Fishing fishing = gson.fromJson(trade , Fishing.class);
                Long id = fishingRepository.selectMaxId();
                fishing.setId(id);
                fishing.setDate(ServiceCommon.getNowData());
                fishingRepository.save(fishing);                    // DB에 해당 liquiditiy 저장
            }
        }catch(Exception e){
            returnValue = DataCommon.CODE_ERROR;
            log.error("[ERROR][TRADE][SAVE] {}",e.getMessage());
        }
        return returnValue;
    }
}
