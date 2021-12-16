package com.coin.autotrade.service;

import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.repository.ExchangeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ExchangeService {

    @Autowired
    ExchangeRepository exchangeRepository;

    /* Coin List를 가져오는 서비스 */
    public List<Exchange> getExchanges(){

        List<Exchange> returnEx = new ArrayList<>();
        try{
            returnEx = exchangeRepository.findAll();
        }catch (Exception e){
            log.error("[GET EXCHANGE] Occur error when finding exchange list {}", e.getMessage());
        }
        return returnEx;
    }

}
