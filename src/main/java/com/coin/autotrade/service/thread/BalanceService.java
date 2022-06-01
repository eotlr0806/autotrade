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
import com.google.gson.JsonElement;
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
        String NOT_SUPPORT     = "[]";
        Gson gson = Utils.getGson();

        if(exchange.equals(UtilsData.COINONE)){
            returnValue = gson.toJson(makeCoinoneArray(gson.fromJson(data, JsonObject.class)));
        }else if(exchange.equals(UtilsData.BITHUMB)){
            returnValue = gson.toJson(makeBithumbArray(gson.fromJson(data, JsonObject.class)));
        }else if(exchange.equals(UtilsData.BITHUMB_GLOBAL)){
            returnValue = gson.toJson(makeBithumbGlobalArray(gson.fromJson(data, JsonArray.class)));
        }else if(exchange.equals(UtilsData.COINSBIT)){
            returnValue = gson.toJson(makeCoinsBitArray(gson.fromJson(data, JsonObject.class)));
        }else if(exchange.equals(UtilsData.DCOIN)){
            returnValue = gson.toJson(makeDcoinArray(gson.fromJson(data, JsonArray.class)));
        }else if(exchange.equals(UtilsData.DEXORCA)){
            returnValue = NOT_SUPPORT;
        }else if(exchange.equals(UtilsData.FLATA)){
            returnValue = NOT_SUPPORT;
        }else if(exchange.equals(UtilsData.DIGIFINEX)){
            returnValue = NOT_SUPPORT;
        }else if(exchange.equals(UtilsData.FOBLGATE)){
            returnValue = gson.toJson(makeFoblGateArray(gson.fromJson(data, JsonObject.class)));
        }else if(exchange.equals(UtilsData.GATEIO)){
            returnValue = gson.toJson(makeGateIoArray(gson.fromJson(data, JsonArray.class)));
        }else if(exchange.equals(UtilsData.KUCOIN)){
            returnValue = NOT_SUPPORT;
        }else if(exchange.equals(UtilsData.LBANK)){
            returnValue = gson.toJson(makeLbankArray(gson.fromJson(data, JsonObject.class)));
        }else if(exchange.equals(UtilsData.MEXC)){
            returnValue = gson.toJson(makeMexcArray(gson.fromJson(data, JsonObject.class)));
        }else if(exchange.equals(UtilsData.OKEX)){
            returnValue = gson.toJson(makeOkexArray(gson.fromJson(data, JsonArray.class)));
        }


        if(returnValue.equals(NOT_SUPPORT)){
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
            }
            resultJson.add(makeJson(key,avail.toPlainString(), balance.toPlainString()));
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
            jsonArray.add(makeJson(key,avail, total));
        }

        return jsonArray;
    }

    /**
     * 빗썸글로벌전용 전용 parser
     * @param data
     * @return
     * @throws Exception
     */
    private JsonArray makeBithumbGlobalArray(JsonArray arrayData) throws Exception{
        JsonArray resultJson = new JsonArray();
        for(JsonElement arrayElement : arrayData){
            JsonObject data = arrayElement.getAsJsonObject();
            BigDecimal btc     = new BigDecimal(data.get("btcQuantity").getAsString());
            BigDecimal avail   = new BigDecimal(data.get("count").getAsString());
            BigDecimal balance = new BigDecimal(data.get("frozen").getAsString());

            if(btc.compareTo(BigDecimal.ZERO) == 0){
                continue;
            }
            resultJson.add(makeJson(data.get("coinType").getAsString(),avail.toPlainString(),avail.add(balance).toPlainString() ));
        }

        return resultJson;
    }


    /**
     * 코인스빗 전용 parser
     * @param data
     * @return
     * @throws Exception
     */
    private JsonArray makeCoinsBitArray(JsonObject data) throws Exception{
        JsonArray resultJson = new JsonArray();
        for (String key : data.keySet()){
            JsonObject element = data.getAsJsonObject(key);
            BigDecimal avail   = new BigDecimal(element.get("available").getAsString());
            BigDecimal balance = new BigDecimal(element.get("freeze").getAsString());
            if(avail.compareTo(new BigDecimal(0)) == 0 && balance.compareTo(new BigDecimal(0)) == 0){
                continue;
            }
            resultJson.add(makeJson(key,avail.toPlainString(), balance.add(avail).toPlainString()));
        }
        return resultJson;
    }

    /**
     * 디코인 전용 parser
     * @param data
     * @return
     * @throws Exception
     */
    private JsonArray makeDcoinArray(JsonArray arrayData) throws Exception{
        JsonArray resultJson = new JsonArray();
        for(JsonElement arrayElement : arrayData){
            JsonObject data = arrayElement.getAsJsonObject();
            BigDecimal normal   = new BigDecimal(data.get("normal").getAsString()); // 사용가능
            BigDecimal locked   = new BigDecimal(data.get("locked").getAsString()); // 잠겨있는 금액 인듯

            if(normal.compareTo(BigDecimal.ZERO) == 0 && locked.compareTo(BigDecimal.ZERO) == 0){
                continue;
            }
            resultJson.add(makeJson(data.get("coin").getAsString(),normal.toPlainString(),normal.add(locked).toPlainString() ));
        }

        return resultJson;
    }

    /**
     * foblgate전용 parser
     * @param data
     * @return
     * @throws Exception
     */
    private JsonArray makeFoblGateArray(JsonObject data) throws Exception{
        JsonArray jsonArray = new JsonArray();
        Set<String> keyList = new HashSet();

        JsonObject total = data.getAsJsonObject("total");
        JsonObject avail = data.getAsJsonObject("avail");

        for(String coin : total.keySet()){
            BigDecimal totalVal = new BigDecimal(total.get(coin).getAsString());
            if(totalVal.compareTo(BigDecimal.ZERO) == 0){
                continue;
            }
            BigDecimal availVal = new BigDecimal(avail.get(coin).getAsString());
            jsonArray.add(makeJson(coin, availVal.toPlainString(), totalVal.toPlainString()));
        }

        return jsonArray;
    }

    /**
     * Gateio 전용 parser
     * @param arrayData
     * @return
     * @throws Exception
     */
    private JsonArray makeGateIoArray(JsonArray arrayData) throws Exception{
        JsonArray resultJson = new JsonArray();
        for(JsonElement arrayElement : arrayData){
            JsonObject data = arrayElement.getAsJsonObject();
            BigDecimal available = new BigDecimal(data.get("available").getAsString()); // 사용가능
            BigDecimal locked    = new BigDecimal(data.get("locked").getAsString());    // 잠겨있는 금액 인듯

            if(available.compareTo(BigDecimal.ZERO) == 0 && locked.compareTo(BigDecimal.ZERO) == 0){
                continue;
            }
            resultJson.add(makeJson(data.get("currency").getAsString() ,available.toPlainString(),available.add(locked).toPlainString() ));
        }

        return resultJson;
    }


    /**
     * Lbank 전용
     * @param data
     * @return
     * @throws Exception
     */
    private JsonArray makeLbankArray(JsonObject data) throws Exception{
        JsonArray jsonArray = new JsonArray();
        Set<String> keyList = new HashSet();

        JsonObject total = data.getAsJsonObject("asset");
        JsonObject avail = data.getAsJsonObject("free");

        for(String coin : total.keySet()){
            BigDecimal totalVal = new BigDecimal(total.get(coin).getAsString());
            if(totalVal.compareTo(BigDecimal.ZERO) == 0){
                continue;
            }
            BigDecimal availVal = new BigDecimal(avail.get(coin).getAsString());
            jsonArray.add(makeJson(coin, availVal.toPlainString(), totalVal.toPlainString()));
        }

        return jsonArray;
    }

    /**
     * Mexc 전용
     * @param data
     * @return
     * @throws Exception
     */
    private JsonArray makeMexcArray(JsonObject data) throws Exception{
        JsonArray jsonArray = new JsonArray();
        Set<String> keyList = new HashSet();
        for(String coin : data.keySet()){
            JsonObject item = data.get(coin).getAsJsonObject();
            BigDecimal frozen = new BigDecimal(item.get("frozen").getAsString());
            BigDecimal avail  = new BigDecimal(item.get("available").getAsString());

            jsonArray.add(makeJson(coin, avail.toPlainString(), frozen.add(avail).toPlainString()));
        }
        return jsonArray;
    }

    /**
     * okEx 거래소 전용
     * @param arrayData
     * @return
     * @throws Exception
     */
    private JsonArray makeOkexArray(JsonArray arrayData) throws Exception{
        JsonArray resultJson = new JsonArray();
        for(JsonElement arrayElement : arrayData){
            JsonObject data      = arrayElement.getAsJsonObject();
            BigDecimal available = new BigDecimal(data.get("availBal").getAsString()); // 사용가능
            BigDecimal locked    = new BigDecimal(data.get("frozenBal").getAsString());    // 잠겨있는 금액 인듯
            resultJson.add(makeJson(data.get("ccy").getAsString() ,available.toPlainString(),available.add(locked).toPlainString() ));
        }
        return resultJson;
    }

    /**
     * @param key 심볼
     * @param avail 사용 가능 금액
     * @param balacne 총 금액
     * @return
     * @throws Exception
     */
    private JsonObject makeJson(String key, String avail, String balacne) throws Exception{
        JsonObject item     = new JsonObject();
        item.add(key.toUpperCase(), makeBalance(avail, balacne));
        return item;
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
