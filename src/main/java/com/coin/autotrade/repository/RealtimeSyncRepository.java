package com.coin.autotrade.repository;

import com.coin.autotrade.model.RealtimeSync;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RealtimeSyncRepository extends JpaRepository<RealtimeSync, Long> {

    @Query(value = "SELECT coalesce(max(s.id),0)+1 FROM realtime_sync s", nativeQuery = true)
    Long selectMaxId();
}
