package com.coin.autotrade.service;

import com.coin.autotrade.common.UtilsData;
import com.coin.autotrade.common.Utils;
import com.coin.autotrade.common.code.ReturnCode;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.service.exchangeimp.AbstractExchange;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

@org.springframework.stereotype.Service
@Slf4j
public class OrderBookService {

    @Autowired
    ExchangeRepository exchangeRepository;

    /**
     * Order book list 를 조회하는 메서드
     * @return NO_DATA / FAIL / order list
     */
    public String getOrderBook(Long exchangeId, String coinData, String userId){

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
            JsonObject dataObj  = gson.fromJson(object.get("data"), JsonObject.class);
            JsonArray ask       = gson.fromJson(dataObj.get("sellList"), JsonArray.class);
            JsonArray bid       = gson.fromJson(dataObj.get("buyList"), JsonArray.class);
            returnValue = setOrderBookDataByJsonArray(ask, bid, "price","amount");

        } else if(exchange.equals(UtilsData.BITHUMB)){   // BITHUMB
            JsonObject dataObj = object.getAsJsonObject("data");
            JsonArray ask       = gson.fromJson(dataObj.get("asks"), JsonArray.class);
            JsonArray bid       = gson.fromJson(dataObj.get("bids"), JsonArray.class);
            returnValue = setOrderBookDataByJsonArray(ask, bid, "price","quantity");

        } else if(exchange.equals(UtilsData.FLATA)){  // Flata - 현재 운영X
            JsonArray ask = gson.fromJson(object.get("ask"), JsonArray.class);
            JsonArray bid = gson.fromJson(object.get("bid"), JsonArray.class);
            returnValue = setOrderBookDataByJsonArray(ask,bid, "px","qty");

        } else if(exchange.equals(UtilsData.DCOIN)  || exchange.equals(UtilsData.BITHUMB_GLOBAL) || exchange.equals(UtilsData.LBANK)){
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

        } else if(exchange.equals(UtilsData.KUCOIN) || exchange.equals(UtilsData.OKEX) || exchange.equals(UtilsData.GATEIO)){

            JsonObject dataObj = null;
            if(exchange.equals(UtilsData.KUCOIN)){
                dataObj = object.getAsJsonObject("data");
            }else if(exchange.equals(UtilsData.OKEX)){
                dataObj = object.getAsJsonArray("data").get(0).getAsJsonObject();
            }else if(exchange.equals(UtilsData.GATEIO)){
                dataObj = object;
            }

            String[][] ask = gson.fromJson(dataObj.get("asks"),String[][].class);
            String[][] bid = gson.fromJson(dataObj.get("bids"),String[][].class);
            returnValue = setOrderBookDataByArray(ask,bid,"price","qty");

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
            newAsk.addProperty("price", askObj.get(priceKey).toString().replace("\"","") );
            newAsk.addProperty("qty"  , askObj.get(qtyKey).toString().replace("\"","") );
            parseAsk.add(newAsk);
        }
        for(int i=0; i < bids.size(); i++){
            JsonObject bidObj = (JsonObject) bids.get(i);
            JsonObject newBid = new JsonObject();
            newBid.addProperty("price", bidObj.get(priceKey).toString().replace("\"",""));
            newBid.addProperty("qty"  , bidObj.get(qtyKey).toString().replace("\"",""));
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
