package com.coin.autotrade.service.function;

import com.coin.autotrade.common.BeanUtils;
import com.coin.autotrade.common.DataCommon;
import com.coin.autotrade.common.ServiceCommon;
import com.coin.autotrade.model.*;
import com.coin.autotrade.repository.ExchangeRepository;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class DcoinFunction {

    private User user                   = null;
    private AutoTrade autoTrade         = null;
    private Exchange exchange           = null;
    private Liquidity liquidity         = null;
    private String ACCESS_TOKEN         = "apiToken";
    private String SECRET_KEY           = "secretKey";
    private Map<String, String> keyList = new HashMap<>();
    Gson gson                           = new Gson();
    private ExchangeRepository exchageRepository;

    /** 생성자로서, 생성될 때, injection**/
    public DcoinFunction(){
        exchageRepository   = (ExchangeRepository) BeanUtils.getBean(ExchangeRepository.class);
    }

    /**
     * 호가 조회 시, 사용하기위해 Set
     * @param exchange
     */
    public void setExchange(Exchange exchange){
        this.exchange = exchange;
    }

    /**
     * 호가 조회 시, 사용하기위해 get
     * @param exchange
     */
    public Exchange getExchange(){
        return this.exchange;
    }

    /**
     * Dcoin Function initialize
     * @param autoTrade
     * @param user
     */
    public void initDcoinAutoTrade(AutoTrade autoTrade, User user, Exchange exchange) throws Exception{
        this.user      = user;
        this.autoTrade = autoTrade;
        this.exchange  = exchange;
        // Set token key
        String[] coinData = ServiceCommon.setCoinData(autoTrade.getCoin());

        // Set token key
        for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
            if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1]) ){
                keyList.put(ACCESS_TOKEN, exCoin.getPublicKey());
                keyList.put(SECRET_KEY,   exCoin.getPrivateKey());
            }
        }
    }

    /**
     * 호가 유동성을 이용하기 위한 초기값 설정
     * @param liquidity
     * @param user
     */
    public void initDcoinLiquidity(Liquidity liquidity, User user, Exchange exchange) throws Exception{
        this.user       = user;
        this.liquidity  = liquidity;
        this.exchange   = exchange;

        // Set token key
        String[] coinData = ServiceCommon.setCoinData(liquidity.getCoin());

        // Set token key
        for(ExchangeCoin exCoin : exchange.getExchangeCoin()){
            if(exCoin.getCoinCode().equals(coinData[0]) && exCoin.getId() == Long.parseLong(coinData[1]) ){
                keyList.put(ACCESS_TOKEN, exCoin.getPublicKey());
                keyList.put(SECRET_KEY,   exCoin.getPrivateKey());
            }
        }
    }



    /**
     * Auto Trade Start
     * @param symbol - coin + currency
     */
    public int startAutoTrade(String price, String cnt, String symbol, String mode){

        log.info("[DCOIN][AutoTrade] Start");

        int returnCode = DataCommon.CODE_ERROR;
        try{
            // mode 처리
            if(DataCommon.MODE_RANDOM.equals(mode)){
                mode = (ServiceCommon.getRandomInt(0,1) == 0) ? DataCommon.MODE_BUY : DataCommon.MODE_SELL;
            }

            if(DataCommon.MODE_BUY.equals(mode)){
                String orderId = "";
                if(!(orderId = createOrder("BUY",price, cnt, symbol)).equals("")){
                    if(!createOrder("SELL",price, cnt, symbol).equals("")){
                        returnCode = DataCommon.CODE_SUCCESS;
                    }else{
                        cancelOrder(symbol, orderId);
                    }
                }
            }else if(DataCommon.MODE_SELL.equals(mode)){
                String orderId = "";
                if(!(orderId = createOrder("SELL",price, cnt, symbol)).equals("")){
                    if(!createOrder("BUY",price, cnt, symbol).equals("")){
                        returnCode = DataCommon.CODE_SUCCESS;
                    }else{
                        cancelOrder(symbol, orderId);
                    }
                }
            }
        }catch (Exception e){
            returnCode = DataCommon.CODE_ERROR;
            log.error("[ERROR][DCOIN][AutoTrade] {}", e.getMessage());
        }

        log.info("[DCOIN][AutoTrade] End");

        return returnCode;
    }

    /**
     * 호가유동성 메서드
     */
    public int startLiquidity(Map list, int minCnt, int maxCnt,String symbol){
        int returnCode = DataCommon.CODE_ERROR;

        List sellList = (ArrayList) list.get("sell");
        List buyList  = (ArrayList) list.get("buy");
        List<HashMap<String,String>> sellCancelList = new ArrayList();
        List<HashMap<String,String>> buyCancelList = new ArrayList();

        try{

            Thread.sleep(1000);
            /** 매도 **/
            log.info("[DCOIN][Liquidity-sell] Start");
            for(int i = 0; i < sellList.size(); i++){

                HashMap<String,String> sellValue = new HashMap<>();
                String price        = (String) sellList.get(i);
                String cnt          = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                String orderId      = createOrder("SELL",price, cnt, symbol);
                if(!orderId.equals("")){
                    sellValue.put("symbol", symbol);
                    sellValue.put("orderId",orderId);
                    sellCancelList.add(sellValue);
                }
                Thread.sleep(100);
            }
            log.info("[DCOIN][Liquidity-sell] End");
            Thread.sleep(500);

            /** 매도 취소 **/
            log.info("[DCOIN][Liquidity-sell-cancel] Start");
            for(int i=0; i < sellCancelList.size(); i++){
                Map<String,String> cancelData = (HashMap) sellCancelList.get(i);
                int returnStr = cancelOrder(cancelData.get("symbol"), cancelData.get("orderId"));
                // Coinone 방어로직
                Thread.sleep(100);
            }
            log.info("[DCOIN][Liquidity-sell-cancel] end");

            Thread.sleep(1000);

            /** 매수 **/
            log.info("[DCOIN][Liquidity-buy] Start");
            for(int i = 0; i < buyList.size(); i++){

                HashMap<String,String> buyValue = new HashMap<>();
                String price        = (String) buyList.get(i);
                String cnt          = String.valueOf(Math.floor(ServiceCommon.getRandomDouble((double)minCnt, (double)maxCnt) * DataCommon.TICK_DECIMAL) / DataCommon.TICK_DECIMAL);
                String orderId      = createOrder("BUY",price, cnt, symbol);
                if(!orderId.equals("")){
                    buyValue.put("symbol", symbol);
                    buyValue.put("orderId",orderId);
                    buyCancelList.add(buyValue);
                }
                // Coinone 방어로직
                Thread.sleep(100);
            }
            log.info("[DCOIN][Liquidity-buy] End");

            // sleep
            Thread.sleep(500);


            /** 매수 취소 **/
            log.info("[DCOIN][Liquidity-buy-cancel] Start");
            for(int i=0; i < buyCancelList.size(); i++){
                Map<String, String> cancelData = (HashMap) buyCancelList.get(i);
                int returnStr = cancelOrder(cancelData.get("symbol"), cancelData.get("orderId"));
                // Coinone 방어로직
                Thread.sleep(100);
            }
            log.info("[DCOIN][Liquidity-buy-cancel] end");

        }catch(Exception e){
            log.error("[ERROR][DCOIN] {}", e.getMessage());
        }
        return returnCode;
    }


    /**
     * 매수 매도 로직
     * @param side   - SELL, BUY
     * @param symbol - coin + currency
     * @return
     */
    public String createOrder(String side, String price, String cnt, String symbol) {

        String orderId = "";
        String errorCode = "";
        String errorMsg = "";

        try {
            JsonObject header = new JsonObject();
            header.addProperty("api_key", keyList.get(ACCESS_TOKEN));
            header.addProperty("price", Double.parseDouble(price));
            header.addProperty("side", side);
            header.addProperty("symbol", symbol.toLowerCase());
            header.addProperty("type", 1);
            header.addProperty("volume", Double.parseDouble(cnt));
            header.addProperty("sign",  createSign(gson.toJson(header)));

            log.info("[DCOIN][CREATE ORDER - request] userId:{}, value:{}",  user.getUserId(), gson.toJson(header));

            String params = makeEncodedParas(header);
            JsonObject json = postHttpMethod(DataCommon.DCOIN_CREATE_ORDER, params);
            String result = json.get("code").toString().replace("\"", "");
            if ("0".equals(result)) {
                String data        = json.get("data").toString().replace("\"", "");
                JsonObject dataObj = gson.fromJson(data, JsonObject.class);
                orderId = dataObj.get("order_id").toString().replace("\"", "");
                log.info("[SUCCESS][DCOIN][CREATE ORDER - response] response :{}", gson.toJson(json));
            } else {
                log.error("[ERROR][DCOIN][CREATE ORDER - response] response {}", gson.toJson(json));
            }
        }catch(Exception e){
            log.error("[ERROR][DCOIN][CREATE ORDER] {}", e.getMessage());
        }
        return orderId;
    }

    /**
     * 매도/매수 거래 취소 로직
     * @param symbol   - coin + currency
     * @param orderId  -
     * @return
     */
    public int cancelOrder(String symbol, String orderId) {

        int returnValue = DataCommon.CODE_ERROR;
        String errorCode = "";
        String errorMsg = "";

        try {
            JsonObject header = new JsonObject();
            header.addProperty("api_key", keyList.get(ACCESS_TOKEN));
            header.addProperty("order_id", orderId);
            header.addProperty("symbol", symbol.toLowerCase());
            header.addProperty("sign", createSign(gson.toJson(header)));

            log.info("[DCOIN][CANCEL ORDER - request] userId:{}, value:{}", user.getUserId(), gson.toJson(header));

            String params = makeEncodedParas(header);
            JsonObject json = postHttpMethod(DataCommon.DCOIN_CANCEL_ORDER, params);
            String result = json.get("code").toString().replace("\"", "");
            if ("0".equals(result)) {
                returnValue = DataCommon.CODE_SUCCESS;
                log.info("[SUCCESS][DCOIN][CANCEL ORDER - response] response:{}", gson.toJson(json));
            } else {
                log.error("[ERROR][DCOIN][CANCEL ORDER - response] response:{}", gson.toJson(json));
            }
        }catch(Exception e){
            log.error("[ERROR][DCOIN][CANCEL ORDER] {}", e.getMessage());
        }
        return returnValue;
    }




    /**
     * Dcoin Order book api
     * @param coin
     * @return
     */
    public String getOrderBook(Exchange exchange, String coin, String coinId){
        String returnRes = "";
        try{
            String inputLine;
            String currency = getCurrency(exchange, coin, coinId);
            if(currency.equals("")){
                log.error("[DCOIN][ERROR][Order book] There is no coin");
                return "";
            }
            String symbol = coin.toLowerCase().concat(currency);

            String encodedData = "symbol=" + URLEncoder.encode(symbol) + "&type=" + URLEncoder.encode("step0");

            String request = DataCommon.DCOIN_ORDERBOOK + "?" + encodedData;
            URL url = new URL(request);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Context-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            connection.setConnectTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setReadTimeout(DataCommon.TIMEOUT_VALUE);

            log.info("[DCOIN][Order book - Request]  symbol:{}, type:{}", symbol, "step0");

            int returnCode = connection.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer response = new StringBuffer();
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();

            returnRes = response.toString();

        }catch (Exception e){
            log.error("[ERROR][DCOIN][ORDER BOOK] {}",e.getMessage());
        }

        return returnRes;
    }

    /**
     * 암호화된 값 생성
     * @param params
     * @return
     */
    public String createSign(String params){
        String returnVal = "";
        String replaceParams = params.replace("\"","").replace("{","").replace("}","").replace(":","").replace(",","");
        String message = replaceParams.concat(keyList.get(SECRET_KEY));
        try {
            MessageDigest md5 = MessageDigest.getInstance("md5");
            byte[] code = md5.digest(message.getBytes());
            StringBuffer sb = new StringBuffer();
            for (byte b : code) {
                sb.append(String.format("%02x", b));
            }
            returnVal = sb.toString();

        } catch (Exception e) {
            e.printStackTrace();
            returnVal = null;
            log.error("[ERROR][Create Sign] {}", e.getMessage());
        }
        return returnVal;
    }

    public String makeEncodedParas(JsonObject header){
        String returnVal = "";
        int i =0;
        for(String key : header.keySet()){
            String value = header.get(key).toString().replace("\"","");
            if(i < header.size() -1){
                returnVal += (key + "=" + value + "&");
            }else{
                returnVal += (key + "=" + value);
            }
            i++;
        }

        return returnVal;
    }

    /**
     * DCOIN 의 경우 통화 기준으로 필요함.
     * @param coin
     * @return
     */
    public String getCurrency(Exchange exchange, String coin, String coinId){
        String returnVal = "";
        try {
            // 거래소를 체크하는 이유는 여러거래소에서 같은 코인을 할 수 있기에
            if(exchange.getExchangeCoin().size() > 0){
                for(ExchangeCoin data : exchange.getExchangeCoin()){
                    if(data.getCoinCode().equals(coin) && data.getId() == Long.parseLong(coinId)){
                        returnVal = data.getCurrency();
                    }
                }
            }
        }catch(Exception e){
            log.error("[ERROR][DCOIN][Get Currency] {}",e.getMessage());
        }
        return returnVal;
    }

    /**
     * HTTP POST Method for coinone
     * @param targetUrl
     * @param payload
     * @return
     */
    public JsonObject postHttpMethod(String targetUrl, String payload) {
        URL url;
        String inputLine;
        JsonObject returnObj = null;
        try{
            url = new URL(targetUrl);
            HttpURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setReadTimeout(DataCommon.TIMEOUT_VALUE);
            connection.setRequestProperty("Context-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");

            // Writing the post data to the HTTP request body
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
            bw.write(payload);
            bw.close();
            connection.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer response = new StringBuffer();
            while ((inputLine = br.readLine()) != null) {
                response.append(inputLine);
            }
            br.close();
            returnObj = gson.fromJson(response.toString(), JsonObject.class);
        }catch(Exception e){
            log.error("[COINONE][ERROR][CoinOne post http] {}", e.getMessage());
        }
        return returnObj;
    }
}
