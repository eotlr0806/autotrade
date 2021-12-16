package com.coin.autotrade.service;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.model.ExchangeCoin;
import com.coin.autotrade.repository.ExchangeCoinRepository;
import com.coin.autotrade.repository.ExchangeRepository;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ExchangeCoinService {

    @Autowired
    ExchangeCoinRepository exchangeCoinRepository;

    Gson gson = new Gson();

    /**
     * @param coin coin id 가 없을 경우 insert, 존재할 경우 update
     * @return ReturnCode.FAIL, ReturnCode.SUCCESS
     */
    public String insertUpdateCoin(ExchangeCoin coin){
        String returnVal = ReturnCode.FAIL.getValue();

        try{
            // coin id 가 넘어오면 Update로 변경
            if(coin.getId() == null){
                Long nextId = exchangeCoinRepository.selectMaxId() + 1;
                coin.setId(nextId);
                exchangeCoinRepository.save(coin);
                log.info("[INSERT COIN] Add coin. coin:{}", gson.toJson(coin));
            }else{
                exchangeCoinRepository.save(coin);
                log.info("[UPDATE COIN] Update coin. coin:{} ", gson.toJson(coin));
            }
            returnVal = ReturnCode.SUCCESS.getValue();
        }catch(Exception e){
            log.error("[INSERT UPDATE COIN] Occur error :{}", e.getMessage());
            e.printStackTrace();
        }

        return returnVal;
    }


    /* Delete Coin method */
    public String deleteCoin(Long id){
        String returnVal = ReturnCode.FAIL.getValue();
        try{
            exchangeCoinRepository.deleteById(id);
            returnVal = ReturnCode.SUCCESS.getValue();
            log.info("[DELETE COIN] Delete coin. Coin ID {}", String.valueOf(id));
        }catch(Exception e){
            log.error("[DELETE COIN] Occur error: {}", e.getMessage());
            e.printStackTrace();
        }
        return returnVal;
    }
}
