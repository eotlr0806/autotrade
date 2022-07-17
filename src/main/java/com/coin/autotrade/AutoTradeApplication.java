package com.coin.autotrade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableAsync
@ServletComponentScan
@EnableScheduling
public class AutoTradeApplication {
    // Main starter class
    public static void main(String[] args) {
        SpringApplication.run(AutoTradeApplication.class, args);
//
//
//        Map<String, Object> header = new LinkedHashMap<>();
//        header.put("alg", "HS256");
//        header.put("typ", "JWT");
//
//
//        Map<String, String> map = new HashMap<>();
//        map.put("type", "OpenAPI");
//        map.put("sub", "cee88ab0bc69435784b7db0545e85647");
//        map.put("nonce", String.valueOf(System.currentTimeMillis()));
//
//        System.out.println(">>>>>>>>>>>>>>>> "+ Utils.getJwtToken("key",
//                header,
//                map));
    }

}
