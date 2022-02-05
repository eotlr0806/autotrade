package com.coin.autotrade.service;

import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.model.ExchangeCoin;
import com.coin.autotrade.repository.ExchangeCoinRepository;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ExchangeCoinService {

    @Autowired
    ExchangeCoinRepository exchangeCoinRepository;

    /**
     * @param coin coin id 가 없을 경우 insert, 존재할 경우 update
     * @return ReturnCode.FAIL, ReturnCode.SUCCESS
     */
    public ReturnCode insertUpdateCoin(ExchangeCoin coin){
        ReturnCode returnCode = ReturnCode.FAIL;
        Gson gson             = Utils.getGson();
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
            returnCode = ReturnCode.SUCCESS;
        }catch(Exception e){
            log.error("[INSERT UPDATE COIN] Occur error :{}", e.getMessage());
            e.printStackTrace();
        }

        return returnCode;
    }


    /* Delete Coin method */
    public ReturnCode deleteCoin(Long id){
        ReturnCode returnCode = ReturnCode.FAIL;
        try{
            exchangeCoinRepository.deleteById(id);
            returnCode = ReturnCode.SUCCESS;
            log.info("[DELETE COIN] Delete coin. Coin ID {}", String.valueOf(id));
        }catch(Exception e){
            log.error("[DELETE COIN] Occur error: {}", e.getMessage());
            e.printStackTrace();
        }
        return returnCode;
    }
}
