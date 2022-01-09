package com.coin.autotrade.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "realtime_sync")
@DynamicUpdate
public class RealtimeSync {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "sync_coin")
    private String syncCoin;

    @Column(name = "price_percent")
    private Integer pricePercent;

    @Column(name = "sync_time")
    private Integer syncTime;

    @Column(name = "min_price")
    private String minPrice;

    @Column(name = "max_price")
    private String maxPrice;

    @Column(name = "limit_trade_cnt")
    private Integer limitTradeCnt;

    // 거래소
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "exchange_id")
    private Exchange exchange;

    @Column
    private String coin;

    @Column
    private String status;

    @Column
    private String userId;

    @Column
    private String date;
}
