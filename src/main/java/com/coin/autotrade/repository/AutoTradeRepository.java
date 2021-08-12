package com.coin.autotrade.repository;

import com.coin.autotrade.model.AutoTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AutoTradeRepository extends JpaRepository<AutoTrade, Long> {

    @Query(value = "SELECT coalesce(max(s.id),0)+1 FROM autotrade s", nativeQuery = true)
    Long selectMaxId();
}
