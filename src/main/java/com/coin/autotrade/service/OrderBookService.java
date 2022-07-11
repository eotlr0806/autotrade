package com.coin.autotrade.service;

import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.Utils;
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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

@Service
@Slf4j
public class OrderBookService {

    @Autowired
    ExchangeRepository exchangeRepository;

    /**
     * Order book list 를 조회하는 메서드
     * @return NO_DATA / FAIL / order list
     */
    public String getOrderBook(Long exchangeId, String coinData){

        String returnVal = ReturnCode.NO_DATA.getValue();

        try{
            // 거래소 정보
            Optional<Exchange> exchangeObj = exchangeRepository.findById(exchangeId);
            Exchange exchange = null;
            if(exchangeObj.isPresent()){
                exchange = exchangeObj.get();
                String[] coinWithId  = Utils.splitCoinWithId(coinData);

                AbstractExchange abstractExchange = Utils.getInstance(exchange.getExchangeCode());
                String orderList                  = abstractExchange.getOrderBook(exchange, coinWithId);
                if(orderList.equals(ReturnCode.FAIL.getValue())){
                    returnVal = ReturnCode.FAIL.getValue();
                    log.error("[GET ORDER BOOK] API Order book Occur error");
                }else{
                    returnVal = parseOrderBook(exchange.getExchangeCode(), orderList);
                }
            }
        }catch(Exception e){
            log.error("[GET ORDER BOOK] Occur error : {}",e.getMessage());
            e.printStackTrace();
        }
        return returnVal;
    }

    /** 거래소에서 받은 데이터를 기준에 맞춰 파싱해서 보내준다. **/
    public String parseOrderBook(String exchange, String data) throws Exception{
        String returnValue     = "";
        JsonArray parseAsk   = new JsonArray();
        JsonArray parseBid   = new JsonArray();

        Gson gson = Utils.getGson();
        JsonObject object = gson.fromJson(data, JsonObject.class);

        if(exchange.equals(UtilsData.COINONE)){          // Coinone
            returnValue = data;
        } else if(exchange.equals(UtilsData.FOBLGATE)){  // Foblgate
            JsonObject dataObj  = object.getAsJsonObject("data");
            JsonArray ask       = dataObj.get("sellList").getAsJsonArray();
            JsonArray bid       = dataObj.get("buyList").getAsJsonArray();
            returnValue = setOrderBookDataByJsonArray(ask, bid, "price","amount");
        } else if(exchange.equals(UtilsData.BITHUMB)
                || exchange.equals(UtilsData.COINSBIT)
                || exchange.equals(UtilsData.MEXC)
                || exchange.equals(UtilsData.BIGONE)){   // BITHUMB
            JsonObject dataObj  = object.getAsJsonObject("data");
            JsonArray ask       = dataObj.get("asks").getAsJsonArray();
            JsonArray bid       = dataObj.get("bids").getAsJsonArray();

            returnValue = setOrderBookDataByJsonArray(ask, bid, "price","quantity");
        } else if(exchange.equals(UtilsData.FLATA)) {  // Flata - 현재 운영X
            JsonArray ask = object.get("ask").getAsJsonArray();
            JsonArray bid = object.get("bid").getAsJsonArray();
            returnValue = setOrderBookDataByJsonArray(ask, bid, "px", "qty");
        } else if(exchange.equals(UtilsData.DCOIN) || exchange.equals(UtilsData.BITHUMB_GLOBAL) || exchange.equals(UtilsData.LBANK)){
            JsonObject dataObj = object.getAsJsonObject("data");
            String[][] ask = null;
            String[][] bid = null;

            if(exchange.equals(UtilsData.DCOIN) || exchange.equals(UtilsData.LBANK)){
                ask = gson.fromJson(dataObj.get("asks"),String[][].class);
                bid = gson.fromJson(dataObj.get("bids"),String[][].class);
            }else if(exchange.equals(UtilsData.BITHUMB_GLOBAL)){
                ask = gson.fromJson(dataObj.get("s"),String[][].class);
                bid = gson.fromJson(dataObj.get("b"),String[][].class);
            }
            returnValue = setOrderBookDataByArray(ask,bid,"price","qty");

        } else if(exchange.equals(UtilsData.KUCOIN)
                        || exchange.equals(UtilsData.OKEX)
                        || exchange.equals(UtilsData.GATEIO)
                        || exchange.equals(UtilsData.DIGIFINEX)
                        || exchange.equals(UtilsData.XTCOM)){

            JsonObject dataObj = null;
            if(exchange.equals(UtilsData.KUCOIN)){
                dataObj = object.getAsJsonObject("data");
            }else if(exchange.equals(UtilsData.OKEX)){
                dataObj = object.getAsJsonArray("data").get(0).getAsJsonObject();
            }else if(exchange.equals(UtilsData.GATEIO)
                    || exchange.equals(UtilsData.DIGIFINEX)
                    || exchange.equals(UtilsData.XTCOM)){
                dataObj = object;
            }

            String[][] ask = gson.fromJson(dataObj.get("asks"),String[][].class);
            String[][] bid = gson.fromJson(dataObj.get("bids"),String[][].class);

            if(exchange.equals(UtilsData.DIGIFINEX)){
                Arrays.sort(ask, (String[] o1, String[] o2) -> {
                    BigDecimal first  = new BigDecimal(o1[0]);
                    BigDecimal second = new BigDecimal(o2[0]);
                    return first.compareTo(second);
                });
            }

            returnValue = setOrderBookDataByArray(ask,bid,"price","qty");

        } else if(exchange.equals(UtilsData.DEXORCA)){

            JsonArray array   = object.getAsJsonArray("quotes");
            JsonArray sellArr = new JsonArray();
            JsonArray buyArr  = new JsonArray();
            JsonObject resObj = new JsonObject();

            for (JsonElement element : array){
                JsonObject obj = element.getAsJsonObject();
                JsonObject sell = new JsonObject();
                JsonObject buy  = new JsonObject();
                sell.addProperty("price", obj.get("sell_quote").getAsString());
                sell.addProperty("qty",   obj.get("sell_left").getAsString());
                buy.addProperty("price", obj.get("buy_quote").getAsString());
                buy.addProperty("qty",   obj.get("buy_left").getAsString());

                sellArr.add(sell);
                buyArr.add(buy);
            }
            resObj.add("ask", sellArr);
            resObj.add("bid", buyArr);

            returnValue = gson.toJson(resObj);
        }

        return returnValue;
    }


    private String setOrderBookDataByJsonArray(JsonArray asks, JsonArray bids, String priceKey, String qtyKey) throws Exception{

        JsonArray parseAsk   = new JsonArray();
        JsonArray parseBid   = new JsonArray();
        Gson gson            = Utils.getGson();

        for(int i=0; i < asks.size(); i++){
            JsonObject askObj = (JsonObject) asks.get(i);
            JsonObject newAsk = new JsonObject();
            newAsk.addProperty("price", askObj.get(priceKey).getAsString() );
            newAsk.addProperty("qty"  , askObj.get(qtyKey).getAsString() );
            parseAsk.add(newAsk);
        }
        for(int i=0; i < bids.size(); i++){
            JsonObject bidObj = (JsonObject) bids.get(i);
            JsonObject newBid = new JsonObject();
            newBid.addProperty("price", bidObj.get(priceKey).getAsString());
            newBid.addProperty("qty"  , bidObj.get(qtyKey).getAsString());
            parseBid.add(newBid);
        }
        JsonObject returnObj = new JsonObject();
        returnObj.add("ask",parseAsk);
        returnObj.add("bid",parseBid);

        return gson.toJson(returnObj);
    }

    private String setOrderBookDataByArray(String[][] asks, String[][] bids, String priceKey, String qtyKey) throws Exception{

        JsonArray parseAsk   = new JsonArray();
        JsonArray parseBid   = new JsonArray();
        Gson gson            = Utils.getGson();

        for(int i=0; i < asks.length; i++){
            JsonObject askObj = new JsonObject();
            askObj.addProperty(priceKey, asks[i][0]);
            askObj.addProperty(qtyKey,   asks[i][1]);
            parseAsk.add(askObj);
        }

        for(int i=0; i < bids.length; i++){
            JsonObject bidObj = new JsonObject();
            bidObj.addProperty(priceKey, bids[i][0]);
            bidObj.addProperty(qtyKey,   bids[i][1]);
            parseBid.add(bidObj);
        }
        JsonObject returnObj = new JsonObject();
        returnObj.add("ask",parseAsk);
        returnObj.add("bid",parseBid);

        return gson.toJson(returnObj);
    }
}
