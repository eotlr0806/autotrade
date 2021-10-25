package com.coin.autotrade.service.parser;

import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.Exchange;
import com.coin.autotrade.repository.ExchangeRepository;
import com.coin.autotrade.service.CoinService;
import com.coin.autotrade.service.function.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OrderBookParser {

    @Autowired
    ExchangeRepository exchangeRepository;

    /** Order book 의 Parser Layer **/
    public String getOrderBook(String exchange, String coinData, String userId){

        String returnVal ="";

        try{
            // 거래소 정보
            Exchange exchangeObj = exchangeRepository.findByexchangeCode(exchange);
            String[] coinWithId  = ServiceCommon.setCoinData(coinData);

            ExchangeFunction exchangeFunction = ServiceCommon.initExchange(exchange);
            returnVal = parseData(exchange, exchangeFunction.getOrderBook(exchangeObj, coinWithId));

        }catch(Exception e){
            log.error("[ERROR][API Get Order book] {}",e.getMessage());
        }
        return returnVal;
    }

    /** 거래소에서 받은 데이터를 기준에 맞춰 파싱해서 보내준다. **/
    public String parseData(String exchange, String data){
        String returnValue     = "";
        JsonArray parseAsk   = new JsonArray();
        JsonArray parseBid   = new JsonArray();

        try{
            Gson gson = new Gson();
            JsonObject object = gson.fromJson(data, JsonObject.class);

            // Coinone
            if(exchange.equals(DataCommon.COINONE)){
                returnValue = data;
            }
            // Flata
            else if(exchange.equals(DataCommon.FLATA)){
                JsonArray ask = gson.fromJson(object.get("ask"), JsonArray.class);
                JsonArray bid = gson.fromJson(object.get("bid"), JsonArray.class);

                for(int i=0; i < ask.size(); i++){
                    JsonObject askObj = (JsonObject) ask.get(i);

                    JsonObject newAsk = new JsonObject();
                    newAsk.addProperty("price", askObj.get("px").toString().replace("\"","") );
                    newAsk.addProperty("qty"  , askObj.get("qty").toString().replace("\"","") );
                    parseAsk.add(newAsk);
                }
                for(int i=0; i < bid.size(); i++){
                    JsonObject bidObj = (JsonObject) bid.get(i);

                    JsonObject newBid = new JsonObject();
                    newBid.addProperty("price", bidObj.get("px").toString().replace("\"",""));
                    newBid.addProperty("qty"  , bidObj.get("qty").toString().replace("\"",""));
                    parseBid.add(newBid);
                }
                JsonObject returnObj = new JsonObject();
                returnObj.add("ask",parseAsk);
                returnObj.add("bid",parseBid);

                returnValue = gson.toJson(returnObj);
            }
            // Foblgate
            else if(exchange.equals(DataCommon.FOBLGATE)){
                JsonObject objData  = gson.fromJson(object.get("data"), JsonObject.class);
                JsonArray ask       = gson.fromJson(objData.get("sellList"), JsonArray.class);
                JsonArray bid       = gson.fromJson(objData.get("buyList"), JsonArray.class);

                for(int i=0; i < ask.size(); i++){
                    JsonObject askObj = (JsonObject) ask.get(i);

                    JsonObject newAsk = new JsonObject();
                    newAsk.addProperty("price", askObj.get("price").toString().replace("\"","") );
                    newAsk.addProperty("qty"  , askObj.get("amount").toString().replace("\"","") );
                    parseAsk.add(newAsk);
                }
                for(int i=0; i < bid.size(); i++){
                    JsonObject bidObj = (JsonObject) bid.get(i);

                    JsonObject newBid = new JsonObject();
                    newBid.addProperty("price", bidObj.get("price").toString().replace("\"",""));
                    newBid.addProperty("qty"  , bidObj.get("amount").toString().replace("\"",""));
                    parseBid.add(newBid);
                }
                JsonObject returnObj = new JsonObject();
                returnObj.add("ask",parseAsk);
                returnObj.add("bid",parseBid);

                returnValue = gson.toJson(returnObj);
            }
            // BITHUMB
            else if(exchange.equals(DataCommon.BITHUMB)){
                JsonObject dataObj = object.getAsJsonObject("data");
                JsonArray ask       = gson.fromJson(dataObj.get("asks"), JsonArray.class);
                JsonArray bid       = gson.fromJson(dataObj.get("bids"), JsonArray.class);

                for(int i=0; i < ask.size(); i++){
                    JsonObject askObj = (JsonObject) ask.get(i);

                    JsonObject newAsk = new JsonObject();
                    newAsk.addProperty("price", askObj.get("price").toString().replace("\"","") );
                    newAsk.addProperty("qty"  , askObj.get("quantity").toString().replace("\"","") );
                    parseAsk.add(newAsk);
                }
                for(int i=0; i < bid.size(); i++){
                    JsonObject bidObj = (JsonObject) bid.get(i);

                    JsonObject newBid = new JsonObject();
                    newBid.addProperty("price", bidObj.get("price").toString().replace("\"",""));
                    newBid.addProperty("qty"  , bidObj.get("quantity").toString().replace("\"",""));
                    parseBid.add(newBid);
                }
                JsonObject returnObj = new JsonObject();
                returnObj.add("ask",parseAsk);
                returnObj.add("bid",parseBid);

                returnValue = gson.toJson(returnObj);
            }
            // Dcoin
            else if(exchange.equals(DataCommon.DCOIN)){
                JsonObject dataObj = object.getAsJsonObject("data");
                String[][] ask = gson.fromJson(dataObj.get("asks"),String[][].class);
                String[][] bid = gson.fromJson(dataObj.get("bids"),String[][].class);

                for(int i=0; i < ask.length; i++){
                    JsonObject askObj = new JsonObject();
                    askObj.addProperty("price", ask[i][0]);
                    askObj.addProperty("qty",   ask[i][1]);
                    parseAsk.add(askObj);
                }

                for(int i=0; i < bid.length; i++){
                    JsonObject bidObj = new JsonObject();
                    bidObj.addProperty("price", bid[i][0]);
                    bidObj.addProperty("qty",   bid[i][1]);
                    parseBid.add(bidObj);
                }
                JsonObject returnObj = new JsonObject();
                returnObj.add("ask",parseAsk);
                returnObj.add("bid",parseBid);

                returnValue = gson.toJson(returnObj);
            }
            // Bithumb Global
            else if(exchange.equals(DataCommon.BITHUMB_GLOBAL)){
                JsonObject dataObj = object.getAsJsonObject("data");
                String[][] ask = gson.fromJson(dataObj.get("s"),String[][].class);
                String[][] bid = gson.fromJson(dataObj.get("b"),String[][].class);

                for(int i=0; i < ask.length; i++){
                    JsonObject askObj = new JsonObject();
                    askObj.addProperty("price", ask[i][0]);
                    askObj.addProperty("qty",   ask[i][1]);
                    parseAsk.add(askObj);
                }

                for(int i=0; i < bid.length; i++){
                    JsonObject bidObj = new JsonObject();
                    bidObj.addProperty("price", bid[i][0]);
                    bidObj.addProperty("qty",   bid[i][1]);
                    parseBid.add(bidObj);
                }
                JsonObject returnObj = new JsonObject();
                returnObj.add("ask",parseAsk);
                returnObj.add("bid",parseBid);

                returnValue = gson.toJson(returnObj);
            }
            // KUCOIN
            else if(exchange.equals(DataCommon.KUCOIN) || exchange.equals(DataCommon.OKEX)){

                JsonObject dataObj = null;
                if(exchange.equals(DataCommon.KUCOIN)){
                    dataObj = object.getAsJsonObject("data");
                }else if(exchange.equals(DataCommon.OKEX)){
                    dataObj = object.getAsJsonArray("data").get(0).getAsJsonObject();
                }

                String[][] ask = gson.fromJson(dataObj.get("asks"),String[][].class);
                String[][] bid = gson.fromJson(dataObj.get("bids"),String[][].class);

                for(int i=0; i < ask.length; i++){
                    JsonObject askObj = new JsonObject();
                    askObj.addProperty("price", ask[i][0]);
                    askObj.addProperty("qty",   ask[i][1]);
                    parseAsk.add(askObj);
                }

                for(int i=0; i < bid.length; i++){
                    JsonObject bidObj = new JsonObject();
                    bidObj.addProperty("price", bid[i][0]);
                    bidObj.addProperty("qty",   bid[i][1]);
                    parseBid.add(bidObj);
                }
                JsonObject returnObj = new JsonObject();
                returnObj.add("ask",parseAsk);
                returnObj.add("bid",parseBid);

                returnValue = gson.toJson(returnObj);
            }

        }catch (Exception e){
            log.error("[ERROR][Parse Order book] {}",e.getMessage());
        }

        return returnValue;
    }
}
