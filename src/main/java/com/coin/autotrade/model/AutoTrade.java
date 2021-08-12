package com.coin.autotrade.model;

import lombok.Data;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "autotrade")
@DynamicUpdate
public class AutoTrade {

    @Id
    @Column(name = "id")
    private Long id;

//    @Column(name = "min_price")
//    private Integer minPrice;
//
//    @Column(name = "max_price")
//    private Integer maxPrice;

    @Column(name = "min_cnt")
    private Double minCnt;

    @Column(name = "max_cnt")
    private Integer maxCnt;

    @Column(name = "min_seconds")
    private Integer minSeconds;

    @Column(name = "max_seconds")
    private Integer maxSeconds;

//    @Column(name = "min_use")
//    private Integer minUse;
//
//    @Column(name = "max_use")
//    private Integer maxUse;

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
