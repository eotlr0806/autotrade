package com.coin.autotrade.repository;

import com.coin.autotrade.model.ExceptionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.time.LocalDateTime;

@Repository
public interface ExceptionLogRepository extends JpaRepository<ExceptionLog, Long> {

    @Modifying
    @Transactional
    public void deleteByDateTimeLessThan(LocalDateTime time);
}
