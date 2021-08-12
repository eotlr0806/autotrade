package com.coin.autotrade.model;

import com.google.gson.annotations.Expose;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;

@Data
@Entity
@Table(name = "exchange_coin")
@DynamicUpdate
@EqualsAndHashCode(exclude = {"exchange"})
@ToString(exclude = {"exchange"})
public class ExchangeCoin {

    @Id
    @Column(name = "id")
    @Expose
    private Long id;

    @Column(name = "coin_code", length= 64)
    @Expose
    private String coinCode;

    @Column(name = "coin_name", length= 128)
    @Expose
    private String coinName;

    @Column(name = "coin_price", length= 128)
    @Expose
    private String coinPrice;

    @Column(name = "currency", length= 128)
    @Expose
    private String currency;

    @Column(name = "exchange_id", length= 128)
    @Expose
    private Long exchangeId;

    @Column(name = "public_key", length= 128)
    @Expose
    private String publicKey;


    @Column(name = "private_key", length= 128)
    @Expose
    private String privateKey;


    @Column(name = "exchange_user_id", length= 128)
    @Expose
    private String exchangeUserId;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_id" , updatable = false, insertable = false)
    private Exchange exchange;

}
