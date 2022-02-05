package com.coin.autotrade.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "user_exchange")
@DynamicUpdate
public class UserExchange {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "exchange_nm", length = 128)
    private String exchangeNm;

    @Column(name = "key_type", length = 64)
    private String keyType;

    @Column(name = "key_value", length = 1024)
    private String keyValue;

    @Column(name = "description", length = 512)
    private String description;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

}
