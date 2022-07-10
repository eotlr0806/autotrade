package com.coin.autotrade.common.enumeration;


import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum LogAction {
    ORDER_BOOK("호가 조회"),
    BALANCE("자산 조회"),
    CREATE_ORDER("주문"),
    CANCEL_ORDER("취소");

    private String korName;
}
