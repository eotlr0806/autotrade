package com.coin.autotrade.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@Table(name = "liquidity")
@DynamicUpdate
public class Liquidity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "min_cnt")
    private Integer minCnt;

    @Column(name = "max_cnt")
    private Integer maxCnt;

    @Column(name = "min_seconds")
    private Integer minSeconds;

    @Column(name = "max_seconds")
    private Integer maxSeconds;

    @Column(name = "random_tick")
    private String randomTick;

    @Column(name = "range_tick")
    private String rangeTick;

    @Column(name = "self_tick")
    private String selfTick;

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
