package com.coin.autotrade.repository;

import com.coin.autotrade.model.ExchangeCoin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ExchangeCoinRepository extends JpaRepository<ExchangeCoin, Long> {

    @Query(value = "SELECT coalesce(max(s.id),0)+1 FROM exchange_coin s", nativeQuery = true)
    Long selectMaxId();
}
