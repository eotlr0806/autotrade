package com.coin.autotrade.service;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.service.exchangeimp.AbstractExchange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Optional;

@org.springframework.stereotype.Service
@Slf4j
public class TradeActionService {

    @Autowired
    ExchangeRepository exchangeRepository;

    private final String BUY  = "BUY";
    private final String SELL = "SELL";

    // Start autoTrade
    public Response tradeAction(HashMap<String, String> params){
        Response response = new Response(ReturnCode.FAIL);

        try{
            Optional<Exchange> exchangeObj = exchangeRepository.findById(Long.parseLong(params.get("exchangeId")));
            Exchange exchange = null;
            if(exchangeObj.isPresent()) {
                exchange = exchangeObj.get();

                AbstractExchange abstractExchange = Utils.getInstance(exchange.getExchangeCode());
                String msg = abstractExchange.createOrder(params.get("type"),
                                                          params.get("price"),
                                                          params.get("cnt"),
                                                          Utils.splitCoinWithId(params.get("coinWithId")),
                                                          exchange);
                if(msg.equals(ReturnCode.NO_DATA.getValue()) || msg.equals(ReturnCode.FAIL.getValue()) ){
                    response.setBody(ReturnCode.FAIL, msg);
                }else{
                    response.setBody(ReturnCode.SUCCESS, msg);
                }
            }
        }catch(Exception e){
            log.error("[GET BALANCE] Occur error : {}",e.getMessage());
            e.printStackTrace();
            response.setResponse(ReturnCode.FAIL, e.getMessage());
        }
        return response;
    }
}
