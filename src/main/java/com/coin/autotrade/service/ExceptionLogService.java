package com.coin.autotrade.service;

import com.coin.autotrade.model.ExceptionLog;
import com.coin.autotrade.repository.ExceptionLogRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class ExceptionLogService {
    final private ExceptionLogRepository exceptionLogRepository;

    public List<ExceptionLog> findAll(){
        return exceptionLogRepository.findAll();
    }

    // 하루 전 데이터를 모두 삭제함.
    public void deleteByTimeBeforeOneDay(){
        LocalDateTime localDateTime = LocalDateTime.now();
        localDateTime = localDateTime.minusDays(1L);
        exceptionLogRepository.deleteByDateTimeLessThan(localDateTime);
    }

    /**
     * make and insert
     */
    public void makeLogAndInsert(String exchange, String request, String action, String logMsg){
        log.info("[ExceptionLogService][makeLogAndInsert] make and insert start");
        ExceptionLog exceptionLog = new ExceptionLog();
        exceptionLog.setAction(action);
        exceptionLog.setDateTime(LocalDateTime.now());
        exceptionLog.setRequest(request);
        exceptionLog.setExchange(exchange);
        exceptionLog.setLog(logMsg);

        insertLog(exceptionLog);
    }

    private void insertLog(ExceptionLog exceptionLog){
        log.info("[ExceptionLogService][insertLog] insert error log");
        try{
            exceptionLogRepository.save(exceptionLog);
        }catch (IllegalArgumentException e){
            log.error("[ExceptionLogService][insertLog] error: {}", e.getMessage());
        }
    }
}
