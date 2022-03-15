package com.coin.autotrade.common;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class MexcResult<T> {
    private T data;
    private int code;
    private String msg;
}
