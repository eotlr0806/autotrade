package com.coin.autotrade.model;

import com.coin.autotrade.common.enumeration.Trade;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "autotrade")
@DynamicUpdate
public class AutoTrade {

    @Id
    @Column(name = "id")
    private Long id;
    // 최소 자전거래 수량
    @Column(name = "min_cnt")
    private Integer minCnt;
    // 최대 자전거래 수량
    @Column(name = "max_cnt")
    private Integer maxCnt;
    // 최소 매수 시간
    @Column(name = "min_seconds")
    private Integer minSeconds;
    // 최대 매수 시간
    @Column(name = "max_seconds")
    private Integer maxSeconds;
    // 모드(무작위/매수우선/매도우선)
    @Column
    @Enumerated(EnumType.STRING)
    private Trade mode;
    // 거래소
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "exchange_id")
    private Exchange exchange;
    // 코인
    @Column
    private String coin;
    // 상태
    @Column
    private String status;
    // userId
    @Column
    private String userId;
    // 시작날짜
    @Column
    private String date;
}
