package com.coin.autotrade.service.schedule;

import com.coin.autotrade.service.ExceptionLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ExceptionLogSchedule {
    @Autowired
    ExceptionLogService exceptionLogService;

    @Scheduled(cron = "0 0 12 * * ?")
    public void deleteSchedule(){
        log.info("[ExceptionLogSchedule][deleteSchedule] Delete exception log start");
        exceptionLogService.deleteByTimeBefore(1L);
        log.info("[ExceptionLogSchedule][deleteSchedule] Delete exception log end");
    }
}
