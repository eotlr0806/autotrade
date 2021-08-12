package com.coin.autotrade.service;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.model.ExchangeCoin;
import com.coin.autotrade.repository.ExchangeCoinRepository;
import com.coin.autotrade.repository.ExchangeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ExchangeCoinService {

    @Autowired
    ExchangeCoinRepository exchangeCoinRepository;

    /**
     * Add / Update Coin
     * @param coin
     * @return
     */
    public int insertCoin(ExchangeCoin coin){

        try{
            // coin id 가 넘어오면 Update로 변경
            if(coin.getId() == null){
                Long id = exchangeCoinRepository.selectMaxId();
                coin.setId(id+1);
                exchangeCoinRepository.save(coin);
                log.info("[Exchange Coin - API] Add coin. exchange:{} , coin:{}, coinName:{}",
                        coin.getExchange(), coin.getCoinCode(), coin.getCoinName());
            }else{
                exchangeCoinRepository.save(coin);
                log.info("[Exchange Coin - API] Update coin. exchange:{} , coin:{}, coinName:{}",
                        coin.getExchange(), coin.getCoinCode(), coin.getCoinName());
            }
        }catch(Exception e){
            log.error("[ERROR][Exchange Coin - API] {}", e.getMessage());
            return DataCommon.CODE_ERROR;
        }

        return DataCommon.CODE_SUCCESS;
    }


    /**
     * Delete Coin method
     * @param id
     * @return
     */
    public int deleteCoin(Long id){
        try{
            exchangeCoinRepository.deleteById(id);
            log.info("[Exchange Coin - API] Delete coin. Coin ID {}", String.valueOf(id));
        }catch(Exception e){
            log.error("[ERROR][Exchange Coin - API] {}", e.getMessage());
            return DataCommon.CODE_ERROR;
        }
        return DataCommon.CODE_SUCCESS;
    }
}
