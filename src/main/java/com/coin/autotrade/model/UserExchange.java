package com.coin.autotrade.model;

import com.google.gson.annotations.Expose;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;

@Data
@Entity
@Table(name = "user_exchange")
@DynamicUpdate
@EqualsAndHashCode(exclude = {"user"})
@ToString(exclude = {"user"})
public class UserExchange {

    @Id
    @Column(name = "id")
    @Expose
    private Long id;

    @Column(name = "exchange_nm", length = 128)
    @Expose
    private String exchangeNm;

    @Column(name = "key_type", length = 64)
    @Expose
    private String keyType;

    @Column(name = "key_value", length = 1024)
    @Expose
    private String keyValue;

    @Column(name = "description", length = 512)
    @Expose
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

}
