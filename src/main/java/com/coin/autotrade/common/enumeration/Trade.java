package com.coin.autotrade.common.enumeration;

import lombok.Getter;

@Getter
public enum Trade {
    BUY("BUY"),
    SELL("SELL");

    private String val;
    Trade(String val){
        this.val = val;
    }

    public boolean equals(String msg){
        if(this.getVal().equals(msg)){
            return true;
        }else{
            return false;
        }
    }
}
