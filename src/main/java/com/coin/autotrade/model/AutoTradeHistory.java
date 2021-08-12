package com.coin.autotrade.model;

import lombok.Data;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "autotrade_history")
@DynamicUpdate
public class AutoTradeHistory {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "actual_price")
    private Integer price;

    @Column(name = "min_cnt")
    private Integer minCnt;

    @Column(name = "max_cnt")
    private Integer maxCnt;

    @Column(name = "actual_cnt")
    private Integer actualCnt;

    @Column(name = "min_seconds")
    private Integer minSeconds;

    @Column(name = "max_seconds")
    private Integer maxSeconds;

    @Column(name = "actual_seconds")
    private Integer actualSeconds;

//    @Column(name = "min_use")
//    private Integer minUse;
//
//    @Column(name = "max_use")
//    private Integer maxUse;
//
//    @Column(name = "actual_use")
//    private Integer actualUse;

    @Column
    private String mode;

    @Column
    private String exchange;

    @Column
    private String status;

    @Column
    private String userId;
}
