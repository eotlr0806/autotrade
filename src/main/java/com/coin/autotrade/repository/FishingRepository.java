package com.coin.autotrade.repository;

import com.coin.autotrade.model.Fishing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FishingRepository extends JpaRepository<Fishing, Long> {

    @Query(value = "SELECT coalesce(max(s.id),0)+1 FROM fishing s", nativeQuery = true)
    Long selectMaxId();
}
