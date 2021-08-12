package com.coin.autotrade.repository;

import com.coin.autotrade.model.Liquidity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LiquidityRepository extends JpaRepository<Liquidity, Long> {

    @Query(value = "SELECT coalesce(max(s.id),0)+1 FROM liquidity s", nativeQuery = true)
    Long selectMaxId();
}
