package com.coin.autotrade.service;

import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.repository.ExchangeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExchangeService {

    @Autowired
    ExchangeRepository exchangeRepository;

    /* Coin List를 가져오는 서비스 */
    public List<Exchange> getExchanges() throws Exception{
        return exchangeRepository.findAll();
    }

}
