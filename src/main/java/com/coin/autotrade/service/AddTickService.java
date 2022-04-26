package com.coin.autotrade.service;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.common.enumeration.Trade;
import com.coin.autotrade.model.AddTick;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.service.exchangeimp.AbstractExchange;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Service
@Slf4j
public class AddTickService {

    @Autowired
    ExchangeRepository exchangeRepository;

    @Autowired
    OrderBookService orderBookService;

    private final String BUY  = "BUY";
    private final String SELL = "SELL";

    // Start autoTrade
    public Response addTick(AddTick params){
        Response response = new Response(ReturnCode.FAIL);

        try{
            Optional<Exchange> exchangeObj = exchangeRepository.findById(Long.parseLong(params.getExchangeId()));
            if(exchangeObj.isPresent()) {
                Exchange exchange = exchangeObj.get();
                AbstractExchange abstractExchange = Utils.getInstance(exchange.getExchangeCode());
                String[] coinWithId               = Utils.splitCoinWithId(params.getCoinWithId());
                String orderBook                  = getOrderBook(abstractExchange, exchange, coinWithId);
                String bookPrice                  = getBookPrice(orderBook, params.getMode());
                List<String> priceList            = makeTargetPrice(bookPrice, params);
                List<String> msgList              = new ArrayList<>();
                boolean success                   = true;
                for (String price : priceList){
                    String cnt     = getCnt(params);
                    String orderId = abstractExchange.createOrder(params.getMode().getVal(),
                                                              price,
                                                              cnt,
                                                              coinWithId,
                                                              exchange);
                    String msg = "price:" + price + " cnt:" + cnt + " orderId:" + orderId;
                    Thread.sleep(150);
                    msgList.add(msg);

                    if(orderId.equals(ReturnCode.NO_DATA.getValue())){
                        success = false;
                    }
                }
                if(success){
                    response.setBody(ReturnCode.SUCCESS, msgList);
                }else{
                    response.setBody(ReturnCode.FAIL, msgList);
                }
            }
        }catch(Exception e){
            log.error("[ADD TICK SERVICE] Occur error : {}",e.getMessage());
            e.printStackTrace();
            response.setResponse(ReturnCode.FAIL, e.getMessage());
        }
        return response;
    }

    // 해당 거래소의 해당 코인에 대해 호가를 가져옴.
    private String getOrderBook(AbstractExchange abstractExchange, Exchange exchange, String[] coinWithId) throws Exception{
        String orderBook = abstractExchange.getOrderBook(exchange, coinWithId);
        return orderBookService.parseOrderBook(exchange.getExchangeCode(), orderBook);
    }

    // 매도/매수에 맞게 book 가격을 가져온다.
    private String getBookPrice(String orderBook, Trade mode) throws Exception{
        Gson gson = Utils.getGson();
        JsonObject orderJson = gson.fromJson(orderBook, JsonObject.class);
        JsonObject targetObj = (mode == Trade.BUY) ?
                orderJson.getAsJsonArray("ask").get(0).getAsJsonObject() :
                orderJson.getAsJsonArray("bid").get(0).getAsJsonObject();
        String returnVal = targetObj.get("price").getAsString();
        log.info("[ADD TICK SERVICE] Book Price : {}", returnVal);

        return returnVal;
    }
    
    private List<String> makeTargetPrice(String bookPrice, AddTick addTick) throws Exception{
        Trade mode               = addTick.getMode();
        List<String> list        = new ArrayList<>();
        int tickCnt              = Integer.parseInt(addTick.getTickCnt()) - 1;
        BigDecimal price         = new BigDecimal(bookPrice);
        BigDecimal fixedPrice    = new BigDecimal(addTick.getTickStandard());
        BigDecimal standardPrice = (mode == Trade.BUY) ?
                                        price.subtract(fixedPrice) :
                                        price.add(fixedPrice);
        for (int i = tickCnt; i >= 0; i--) {
            BigDecimal value = (new BigDecimal(addTick.getTick())).multiply(new BigDecimal(i));
            BigDecimal target = (mode == Trade.BUY) ?
                                        standardPrice.subtract(value) :
                                        standardPrice.add(value);
            list.add(target.toPlainString());
        }

        return list;
    }

    private String getCnt(AddTick addTick) throws Exception{
        String cnt = Utils.getRandomString(addTick.getMinCnt(), addTick.getMaxCnt());
        //TODO : 거래소별 cut이 필요할 수 있음.
        return cnt;
    }


}
