package com.coin.autotrade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;


@SpringBootApplication
@EnableAsync
@ServletComponentScan
public class AutoTradeApplication {
    // Main starter class
    public static void main(String[] args) {
        SpringApplication.run(AutoTradeApplication.class, args);
    }

}
