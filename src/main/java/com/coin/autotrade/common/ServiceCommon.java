package com.coin.autotrade.common;

import com.coin.autotrade.service.function.*;
import com.coin.autotrade.service.thread.AutoTradeThread;
import com.coin.autotrade.service.thread.FishingTradeThread;
import com.coin.autotrade.service.thread.LiquidityTradeThread;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

import javax.crypto.Cipher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/*** 공통으로 쓰이는 서비스 로직을 담은 클래스 */
@Slf4j
public class ServiceCommon {

    public static Random random          = null;
    public static Object synchronizedObj = new Object();


    /**
     * min 과 max 사이의 값 반환
     * @Param min - minial value
     * @Param max - maxial value
     */
    public static int getRandomInt (int min, int max){
        int value = 2100000000;
        try{
            if(random == null){
                random = new Random();
            }
            value = random.nextInt(max-min+1)+min;
        }catch(Exception e){
            log.error("[ServiceCommon.java - getRandomInt] {}", e.getMessage());
        }
        return value;
    }

    /**
     * min 과 max 사이의 값 반환
     * @Param min - minial value
     * @Param max - maxial value
     */
    public static double getRandomDouble (double min, double max){
        double value = 0.0;
        try{
            if(random == null){
                random = new Random();
            }
            value = min + (random.nextDouble() * (max - min));
        }catch(Exception e){
            log.error("[ServiceCommon.java - getRandomDouble] {}", e.getMessage());
        }
        return value;
    }

    public static BigDecimal getRandomDecimal (BigDecimal min, BigDecimal max){
        BigDecimal value = null;
        try{
            if(random == null){ random = new Random(); }

            BigDecimal randomVal = new BigDecimal(String.valueOf(random.nextDouble()));
            value = min.add(randomVal.multiply( max.subtract(min) ));
        }catch(Exception e){
            log.error("[ServiceCommon.java - getRandomDecimal] {}", e.getMessage());
        }
        return value;
    }

    @Async
    public static int startThread (Thread thread) throws Exception {
        thread.start();
        return DataCommon.CODE_SUCCESS;
    }

    /**
     * schedule thread를 공통 map 에 담아 보관한다.
     * @param thread - Schedule thread object
     * @return - true : 성공 / false / 실패
     */
    public static boolean setAutoTradeThread (Long id, AutoTradeThread thread){
        try{
            synchronized (synchronizedObj){
                DataCommon.autoTradeThreadMap.put(id, thread);
                return true;
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
        return false;
    }

    /**
     * schedule thread에서 map 에 값을 remove 후 get해서 반환
     * @param key
     * @return
     */
    public static AutoTradeThread getAutoTradeThread (long id){
        AutoTradeThread thread = DataCommon.autoTradeThreadMap.get(id);
        DataCommon.autoTradeThreadMap.remove(id);
        return thread;
    }


    /**
     * liquidity thread를 공통 map 에 담아 보관한다.
     * @param thread - Liquidity thread object
     * @return - true : 성공 / false / 실패
     */
    public static boolean setLiquidityThread (Long id, LiquidityTradeThread thread){
        try{
            synchronized (synchronizedObj){
                DataCommon.liquidityThreadMap.put(id, thread);
                return true;
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
        return false;
    }

    /**
     * liquidity thread에서 map 에 값을 remove 후 get해서 반환
     * @param key
     * @return
     */
    public static LiquidityTradeThread getLiquidityThread (long id){
        LiquidityTradeThread thread = DataCommon.liquidityThreadMap.get(id);
        DataCommon.liquidityThreadMap.remove(id);
        return thread;
    }

    /**
     * fishing thread를 공통 map 에 담아 보관한다.
     * @param thread - Fishing thread object
     * @return - true : 성공 / false / 실패
     */
    public static boolean setFishingThread (Long id, FishingTradeThread thread){
        try{
            synchronized (synchronizedObj){
                DataCommon.fishingTradeThreadMap.put(id, thread);
                return true;
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
        return false;
    }

    /**
     * liquidity thread에서 map 에 값을 remove 후 get해서 반환
     * @param key
     * @return
     */
    public static FishingTradeThread getFishingThread (long id){
        FishingTradeThread thread = DataCommon.fishingTradeThreadMap.get(id);
        DataCommon.fishingTradeThreadMap.remove(id);
        return thread;
    }


    /**
     * return value를 만들어주는 공통함수
     * @param code - code 값
     * @param message - 메세지.
     * @return
     */
    public static String makeReturnValue (Integer code, String data){
        JsonObject object = new JsonObject();
        object.addProperty("code",code);
        String[] dataList = data.split(",");
        if(dataList.length > 0){
            for(String token: dataList){
                String[] dataObject = token.split(":");
                if(dataObject.length > 1){
                    object.addProperty(dataObject[0].trim(),dataObject[1].trim());
                }
            }
        }
        return object.toString();
    }

    /**
     * make JSON DATA
     * @param data
     * @return
     */
    public static String MakeJsonData(HashMap<String,String> data){
        JsonObject object = new JsonObject();
        for(String key : data.keySet()){
            object.addProperty(key, data.get(key));
        }
        return object.toString();
    }

    /**
     * RSA 공개키/개인키 생성 후 Request객체에 저장
     * @param request
     */
    public static void initRsa(HttpServletRequest request) {
        HttpSession session = request.getSession();

        KeyPairGenerator generator;
        try {
            generator = KeyPairGenerator.getInstance(DataCommon.RSA_INSTANCE);
            generator.initialize(1024);

            KeyPair    keyPair      = generator.genKeyPair();
            KeyFactory keyFactory   = KeyFactory.getInstance(DataCommon.RSA_INSTANCE);
            PublicKey  publicKey    = keyPair.getPublic();
            PrivateKey privateKey   = keyPair.getPrivate();

            session.setAttribute(DataCommon.RSA_WEB_KEY, privateKey);   // session에 RSA 개인키를 세션에 저장

            RSAPublicKeySpec publicSpec = (RSAPublicKeySpec) keyFactory.getKeySpec(publicKey, RSAPublicKeySpec.class);
            String publicKeyModulus     = publicSpec.getModulus().toString(16);
            String publicKeyExponent    = publicSpec.getPublicExponent().toString(16);

            request.setAttribute("RSAModulus", publicKeyModulus);       // rsa modulus 를 request 에 추가
            request.setAttribute("RSAExponent", publicKeyExponent);     // rsa exponent 를 request 에 추가
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Request를 받는 RSA 복호화 메서드
     * @return
     * @throws Exception
     */
    public static String decryptRsa(HttpServletRequest request,
                                    String securedName) throws Exception {

        HttpSession session   = request.getSession();
        PrivateKey privateKey = (PrivateKey) session.getAttribute(DataCommon.RSA_WEB_KEY);
        String securedValue   = request.getParameter(securedName);

        return decryptRsaModule(privateKey, securedValue);
    }

    /**
     * String 을 받는 RSA 복호화 메서드
     * @param privateKey
     * @param securedValue
     * @return
     * @throws Exception
     */
    public static String decryptRsaModule(PrivateKey privateKey,
                                          String securedValue) throws Exception{
        Cipher cipher = Cipher.getInstance(DataCommon.RSA_INSTANCE);
        byte[] encryptedBytes = hexToByteArray(securedValue);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        String decryptedValue = new String(decryptedBytes, "utf-8"); // 문자 인코딩 주의.

        return decryptedValue;
    }

    /**
     * RSA private 삭제 메서드
     * @param request
     * @throws Exception
     */
    public static void removePrivateKey(HttpServletRequest request) throws Exception{

        HttpSession session = request.getSession();
        session.removeAttribute(DataCommon.RSA_WEB_KEY);    // RSA Key 삭제

        return;
    }

    /**
     * hex 값을 byte로 변경
     * @param hex
     * @return
     */
    public static byte[] hexToByteArray(String hex) {
        if (hex == null || hex.length() % 2 != 0) { return new byte[] {}; }

        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            byte value = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
            bytes[(int) Math.floor(i / 2)] = value;
        }
        return bytes;
    }

    /**
     * 현재 시간 반환
     * @return
     */
    public static String getNowData() {
        Date date = Calendar.getInstance().getTime();
        DateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");
        String strDate = dateFormat.format(date);

        return strDate;
    }

    public static String[] setCoinData(String coin){
        String[] coinData = null;
        try{
            coinData = coin.split(";");
        }catch (Exception e){
            log.error("[ERROR][SET COIN DATA] {}", e.getMessage());
        }

        return coinData;
    }

    public static String setFormatNum(String num) throws Exception{
        if(num.indexOf("E") != -1){

            BigDecimal decimalVal = new BigDecimal(Double.parseDouble(num));
        }
        return num;
    }

    public static Map deepCopy( Map<String,String> paramMap) throws Exception{
        Map<String, String> copy = new HashMap<String, String>();
        for (String key : paramMap.keySet()){
            copy.put(key, paramMap.get(key));
        }

        return copy;
    }

    public static String replaceLast(String string, String toReplace, String replacement) {
        int pos = string.lastIndexOf(toReplace);
        if (pos > -1) {
            return string.substring(0, pos)+ replacement + string.substring(pos +   toReplace.length(), string.length());
        } else {
            return string;
        }
    }

    /* 거래소 반환 */
    public static ExchangeFunction initExchange(String exchange) throws Exception{

        ExchangeFunction exchangeFunction = null;

        if(DataCommon.COINONE.equals(exchange)){
            exchangeFunction = new CoinOneFunction();
        } else if(DataCommon.FOBLGATE.equals(exchange)){
            exchangeFunction = new FoblGateFunction();
        } else if(DataCommon.FLATA.equals(exchange)){
            exchangeFunction = new FlataFunction();
        } else if(DataCommon.DCOIN.equals(exchange)){
            exchangeFunction = new DcoinFunction();
        } else if(DataCommon.BITHUMB_GLOBAL.equals(exchange)){
            exchangeFunction = new BithumbGlobalFunction();
        }

        return exchangeFunction;
    }

}

