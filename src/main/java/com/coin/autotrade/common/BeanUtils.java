package com.coin.autotrade.common;

import lombok.experimental.UtilityClass;
import org.springframework.context.ApplicationContext;

@UtilityClass
public class BeanUtils {
    public static Object getBean(Class<?> classType) {
        ApplicationContext applicationContext = ApplicationContextProvider.getApplicationContext(); // Container 받아오기
        return applicationContext.getBean(classType); // Container에서 param의 class에 해당하는 bean 가져오고 return
    }
}
