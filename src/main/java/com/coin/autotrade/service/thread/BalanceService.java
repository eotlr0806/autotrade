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
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

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
            if(exchangeObj.isPresent()){
                Exchange exchange = exchangeObj.get();
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
        JsonObject result = gson.fromJson(data, JsonObject.class);

        if(exchange.equals(UtilsData.COINONE)){
            returnValue = gson.toJson(makeCoinoneArray(result));
        }else if(exchange.equals(UtilsData.BITHUMB)){
            returnValue = gson.toJson(makeBithumbArray(result));
        }
        if(returnValue.equals("[]")){
            throw new Exception(data);
        }
        return returnValue;
    }

    /**
     * 코인원 전용 parser
     * @param data
     * @return
     * @throws Exception
     */
    private JsonArray makeCoinoneArray(JsonObject data) throws Exception{
        JsonArray resultJson = new JsonArray();
        for (String key : data.keySet()){
            if(key.equals("result") || key.equals("errorCode") || key.equals("normalWallets")){
                continue;
            }
            JsonObject element = data.getAsJsonObject(key);
            BigDecimal avail   = new BigDecimal(element.get("avail").getAsString());
            BigDecimal balance = new BigDecimal(element.get("balance").getAsString());
            if(avail.compareTo(new BigDecimal(0)) == 0 && balance.compareTo(new BigDecimal(0)) == 0){
                continue;
            }else{
                JsonObject item     = new JsonObject();
                String commaAvail   = element.get("avail").getAsString();
                String commaBalance = element.get("balance").getAsString();
                item.add(key.toUpperCase(), makeBalance(commaAvail, commaBalance));
                resultJson.add(item);
            }
        }
        return resultJson;
    }

    /**
     * 빗썸 전용 parser
     * @param data
     * @return
     * @throws Exception
     */
    private JsonArray makeBithumbArray(JsonObject data) throws Exception{

        JsonArray jsonArray = new JsonArray();
        Set<String> keyList = new HashSet();
        for (String key : data.keySet()){

            String symbol = key.substring(key.lastIndexOf("_") + 1);
            if(keyList.contains(symbol)){
                continue;
            }
            keyList.add(symbol);

            String total = "0";
            String avail = "0";
            if(data.get("total_" + symbol) != null && data.get("available_" + symbol) != null){
                total = data.get("total_" + symbol).getAsString();
                avail = data.get("available_" + symbol).getAsString();
            }

            BigDecimal bigDecimalTotal = new BigDecimal(total);
            if(bigDecimalTotal.compareTo(BigDecimal.ZERO) == 0){    // 0인거 제외
                continue;
            }

            JsonObject symbolObject = new JsonObject();
            symbolObject.add(symbol.toUpperCase(), makeBalance(avail, total));
            jsonArray.add(symbolObject);
        }

        return jsonArray;
    }

    /**
     * client에 맞게 치환해주는 메서드
     * @param avail
     * @param balance
     * @return
     * @throws Exception
     */
    private JsonObject makeBalance(String avail, String balance) throws Exception {
        JsonObject object = new JsonObject();
        object.addProperty("avail", addCommaWithKrw(avail));
        object.addProperty("balance", addCommaWithKrw(balance));

        return object;
    }

    /**
     * 3자리마다 , 를 넣어주는 메서드
     * @param moeny
     * @return
     * @throws Exception
     */
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
