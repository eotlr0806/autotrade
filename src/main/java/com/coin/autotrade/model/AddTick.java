package com.coin.autotrade.model;

import com.coin.autotrade.common.enumeration.Trade;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


@Getter
@Setter
@ToString
public class AddTick {
    private Integer  minCnt;        // 최소 개수
    private Integer  maxCnt;        // 최대 개수
    private String   tick;          // 틱 단위
    private Trade    mode;          // 매도,매수 채우기 모드
    private String   tickCnt;       // Tick 개수
    private String   exchangeId;    // 거래소 id
    private String   coinWithId;    // 코인;id
    private String   userId;
}
