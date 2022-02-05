package com.coin.autotrade.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "exchange_coin")
@DynamicUpdate
public class ExchangeCoin {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "coin_code", length= 64)
    private String coinCode;

    @Column(name = "coin_name", length= 128)
    private String coinName;

    @Column(name = "coin_price", length= 128)
    private String coinPrice;

    @Column(name = "currency", length= 128)
    private String currency;

    @Column(name = "exchange_id", length= 128)
    private Long exchangeId;

    @Column(name = "public_key", length= 128)
    private String publicKey;


    @Column(name = "private_key", length= 128)
    private String privateKey;


    @Column(name = "exchange_user_id", length= 128)
    private String exchangeUserId;

    @Column(name = "api_password", length= 128)
    private String apiPassword;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_id" , updatable = false, insertable = false)
    private Exchange exchange;

}
