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
    }

}
