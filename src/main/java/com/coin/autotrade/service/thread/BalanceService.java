package com.coin.autotrade.service.thread;

import com.coin.autotrade.common.Response;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.enumeration.ReturnCode;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.service.exchangeimp.AbstractExchange;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Optional;

@org.springframework.stereotype.Service
@Slf4j
public class BalanceService {

    @Autowired
    ExchangeRepository exchangeRepository;

    /**
     * Order book list 를 조회하는 메서드
     * @return NO_DATA / FAIL / order list
     */
    public Response getBalance(Long exchangeId, String coinData){

        Response response = new Response(ReturnCode.FAIL);

        try{
            // 거래소 정보
            Optional<Exchange> exchangeObj = exchangeRepository.findById(exchangeId);
            Exchange exchange = null;
            if(exchangeObj.isPresent()){
                exchange = exchangeObj.get();
                String[] coinWithId  = Utils.splitCoinWithId(coinData);

                AbstractExchange abstractExchange = Utils.getInstance(exchange.getExchangeCode());
                String balance = abstractExchange.getBalance(coinWithId,exchange);

                String parseBalance = parseBalance(exchange.getExchangeCode(), balance);
                response.setBody(ReturnCode.SUCCESS, parseBalance);

            }
        }catch(Exception e){
            log.error("[GET BALANCE] Occur error : {}",e.getMessage());
            e.printStackTrace();
            response.setResponse(ReturnCode.FAIL, e.getMessage());
        }

        return response;
    }

    /** 거래소에서 받은 데이터를 기준에 맞춰 파싱해서 보내준다.
     *  이후 거래소에 맞춰 진행할 예정**/
    public String parseBalance(String exchange, String data) throws Exception{
        String returnValue     = "";

        Gson gson = Utils.getGson();
        if(exchange.equals(UtilsData.COINONE)){
            JsonObject result     = gson.fromJson(data, JsonObject.class);
            JsonArray resultJson = new JsonArray();
            for (String key : result.keySet()){
                if(key.equals("result") || key.equals("errorCode") || key.equals("normalWallets")){
                    continue;
                }
                JsonObject element = result.getAsJsonObject(key);
                BigDecimal avail   = new BigDecimal(element.get("avail").getAsString());
                BigDecimal balance = new BigDecimal(element.get("balance").getAsString());
                if(avail.compareTo(new BigDecimal(0)) == 0 && balance.compareTo(new BigDecimal(0)) == 0){
                    continue;
                }else{
                    JsonObject item     = new JsonObject();
                    String commaAvail   = addCommaWithKrw(element.get("avail").getAsString());
                    String commaBalance = addCommaWithKrw(element.get("balance").getAsString());
                    item.add(key.toUpperCase(), makeBalance(commaAvail, commaBalance));
                    resultJson.add(item);
                }
            }

            returnValue = gson.toJson(resultJson);
        }
        return returnValue;
    }

    private JsonObject makeBalance(String avail, String balance) {
        JsonObject object = new JsonObject();
        object.addProperty("avail", avail);
        object.addProperty("balance", balance);

        return object;
    }

    private String addCommaWithKrw(String moeny) throws Exception {
        StringBuilder builder = new StringBuilder();
        int cnt = 1;

        for (int i = moeny.length() -1; i >= 0; i--) {

            char c = moeny.charAt(i);
            if(moeny.indexOf('.') <= i){
                builder.insert(0,c);
            }else{
                // 3번째 자리일 경우(한국기준에 맞춤)
                if((cnt) % 3 == 0 && i > 0){
                    builder.insert(0,c);
                    builder.insert(0,',');
                }else{
                    builder.insert(0,c);
                }
                cnt++;
            }
        }
        return builder.toString();
    }
}
