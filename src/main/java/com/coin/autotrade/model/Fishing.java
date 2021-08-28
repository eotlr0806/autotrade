package com.coin.autotrade.model;

import lombok.Data;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "fishing")
@DynamicUpdate
public class Fishing {

    @Id
    @Column(name = "id")
    private Long id;

    // 최소 주문 개수
    @Column(name = "min_contract_cnt")
    private Integer minContractCnt;

    // 최대 주문 개수
    @Column(name = "max_contract_cnt")
    private Integer maxContractCnt;

    // 최소 체결 개수
    @Column(name = "min_execute_cnt")
    private Integer minExecuteCnt;

    // 최대 체결 개수
    @Column(name = "max_execute_cnt")
    private Integer maxExecuteCnt;

    @Column(name = "min_seconds")
    private Integer minSeconds;

    @Column(name = "max_seconds")
    private Integer maxSeconds;

    @Column(name = "tick_cnt")
    private Integer tickCnt;

    @Column(name = "range_tick")
    private String rangeTick;

    @Column
    private String mode;

    @Column
    private String exchange;

    @Column
    private String coin;

    @Column
    private String status;

    @Column
    private String userId;

    @Column
    private String date;
}
