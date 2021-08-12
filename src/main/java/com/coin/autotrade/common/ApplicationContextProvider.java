package com.coin.autotrade.common;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component // Bean으로 등록
public class ApplicationContextProvider implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    // 인터페이스 구현부
    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        applicationContext = ctx;
    }
    // 이 메서드를 통해 ApplicationContext(Spring IoC Container) 접근
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}