package com.coin.autotrade.service;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.UtilsData;
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
import java.util.*;

@org.springframework.stereotype.Service
@Slf4j
public class AddTickService {

    @Autowired
    ExchangeRepository exchangeRepository;

    @Autowired
    OrderBookService orderBookService;

    private final String BUY   = "BUY";
    private final String SELL  = "SELL";
    private final String START = "START";
    private final String END   = "END";

    // Start autoTrade
    public Response addTick(AddTick params){
        Response response                        = new Response(ReturnCode.FAIL);
        Map<String,Map<String, String>> msgList  = new HashMap<>();
        boolean success                          = true;

        try{
            Optional<Exchange> exchangeObj = exchangeRepository.findById(Long.parseLong(params.getExchangeId()));
            if(exchangeObj.isPresent()) {
                Exchange exchange = exchangeObj.get();
                AbstractExchange abstractExchange = Utils.getInstance(exchange.getExchangeCode());
                String[] coinWithId               = Utils.splitCoinWithId(params.getCoinWithId());
                String orderBook                  = getOrderBook(abstractExchange, exchange, coinWithId);
                Map<String, BigDecimal> bookPrice = getBookPrice(orderBook, params.getMode());
                BigDecimal startPrice             = bookPrice.get(START);
                BigDecimal endPrice               = bookPrice.get(END);

                log.info("[ADD TICK SERVICE] START ADD TICK.");
                log.info("[ADD TICK SERVICE] MODE: {}",  params.getMode());
                log.info("[ADD TICK SERVICE] START: {}", startPrice);
                log.info("[ADD TICK SERVICE] END: {}",   endPrice);
                for (int i = 1; i <= Integer.parseInt(params.getTickCnt()); i++){
                    BigDecimal bTick = new BigDecimal(params.getTick());
                    BigDecimal price = (params.getMode() == Trade.BUY) ?
                                                    startPrice.add(bTick.multiply(new BigDecimal(i))) :
                                                    startPrice.subtract(bTick.multiply(new BigDecimal(i)));

                    if(isStop(params.getMode(), price, endPrice)){
                        log.info("[ADD TICK SERVICE] ADD TICK IS STOP.");
                        log.info("[ADD TICK SERVICE] NOW: {}", price);
                        log.info("[ADD TICK SERVICE] END: {}", endPrice);
                        break;
                    }

                    String cnt     = getCnt(params);
                    String orderId = abstractExchange.createOrder(params.getMode().getVal(),
                            price.toPlainString(),
                            cnt,
                            coinWithId,
                            exchange);

                    // 20220510 - coinone에 대해서만
                    if(UtilsData.COINONE.equals(exchange.getExchangeCode())){
                        Thread.sleep(150);
                    }
                    msgList.put(price.toPlainString(),makeResponse(params.getMode().getVal(), cnt, orderId));

                    if(Utils.isSuccessOrder(orderId)){
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
    private Map<String,BigDecimal> getBookPrice(String orderBook, Trade mode) throws Exception{
        Gson gson = Utils.getGson();
        Map<String, BigDecimal> returnMap = new HashMap<>();
        JsonObject orderJson = gson.fromJson(orderBook, JsonObject.class);
        if(mode == Trade.BUY){
            returnMap.put(START, orderJson.getAsJsonArray("bid").get(0).getAsJsonObject().get("price").getAsBigDecimal());
            returnMap.put(END, orderJson.getAsJsonArray("ask").get(0).getAsJsonObject().get("price").getAsBigDecimal());
        }else{
            returnMap.put(START, orderJson.getAsJsonArray("ask").get(0).getAsJsonObject().get("price").getAsBigDecimal());
            returnMap.put(END, orderJson.getAsJsonArray("bid").get(0).getAsJsonObject().get("price").getAsBigDecimal());

        }
        return returnMap;
    }
    
    private boolean isStop(Trade type, BigDecimal start, BigDecimal end){
        if((type == Trade.BUY) && start.compareTo(end) >= 0) {
            return true;
        }else if((type == Trade.SELL) && start.compareTo(end) <= 0){
            return true;
        }else{
            return false;
        }
    }

    private String getCnt(AddTick addTick) throws Exception{
        return Utils.getRandomString(addTick.getMinCnt(), addTick.getMaxCnt());
    }

    private Map<String, String> makeResponse(String mode, String cnt, String orderId){
        Map<String, String > map = new HashMap<>();
        map.put("mode",mode);
        map.put("cnt",cnt);
        map.put("orderId",orderId);
        return map;
    }

}
