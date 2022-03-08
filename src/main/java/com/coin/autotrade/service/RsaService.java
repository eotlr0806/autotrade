package com.coin.autotrade.service;

import com.coin.autotrade.common.enumeration.SessionKey;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;


@Service
public class RsaService {

    private final String RSA = "RSA";

    /** RSA 공개키/개인키 생성 후 Request객체에 저장
     * @param request
     */
    public void makeRsaAndSaveSession(HttpServletRequest request) {

        HttpSession session = request.getSession();

        KeyPairGenerator generator;
        try {
            generator = KeyPairGenerator.getInstance(RSA);
            generator.initialize(1024);

            KeyPair keyPair         = generator.genKeyPair();
            KeyFactory keyFactory   = KeyFactory.getInstance(RSA);
            PublicKey publicKey     = keyPair.getPublic();
            PrivateKey privateKey   = keyPair.getPrivate();

            session.setAttribute(SessionKey.RSA.toString(), privateKey);   // session에 RSA 개인키를 세션에 저장

            RSAPublicKeySpec publicSpec = (RSAPublicKeySpec) keyFactory.getKeySpec(publicKey, RSAPublicKeySpec.class);
            String publicKeyModulus     = publicSpec.getModulus().toString(16);
            String publicKeyExponent    = publicSpec.getPublicExponent().toString(16);

            request.setAttribute("RSAModulus", publicKeyModulus);       // rsa modulus 를 request 에 추가
            request.setAttribute("RSAExponent", publicKeyExponent);     // rsa exponent 를 request 에 추가
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Request를 받는 RSA 복호화 메서드
     * @return
     * @throws Exception
     */
    public String decryptRsa(HttpServletRequest request, String securedName) {
        String decryptMsg = null;
        try{
            HttpSession session   = request.getSession();
            PrivateKey privateKey = (PrivateKey) session.getAttribute(SessionKey.RSA.toString());
            String securedValue   = request.getParameter(securedName);
            decryptMsg            = decryptRsaModule(privateKey, securedValue);
        }catch (Exception e){
            e.printStackTrace();
        }
        return decryptMsg;
    }

    /**
     * String 을 받는 RSA 복호화 메서드
     * @param privateKey
     * @param securedValue
     * @throws Exception
     */
    public String decryptRsaModule(PrivateKey privateKey, String securedValue) throws Exception{
        Cipher cipher         = Cipher.getInstance(RSA);
        byte[] encryptedBytes = hexToByteArray(securedValue);

        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

        return new String(decryptedBytes, "utf-8");
    }

    /* RSA private key 삭제 메서드 */
    public void removePrivateKey(HttpServletRequest request) throws Exception{
        request.getSession().removeAttribute(SessionKey.RSA.toString());    // RSA Key 삭제
    }

    /* hex 값을 byte로 변경 */
    private byte[] hexToByteArray(String hex) {
        if (hex == null || hex.length() % 2 != 0) { return new byte[] {}; }

        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            byte value = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
            bytes[(int) Math.floor(i / 2)] = value;
        }
        return bytes;
    }
}
