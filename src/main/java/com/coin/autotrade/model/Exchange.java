package com.coin.autotrade.model;

import com.google.gson.annotations.Expose;
import lombok.Data;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "exchange")
@DynamicUpdate
public class Exchange {

    @Id
    @Column(name = "id")
    @Expose
    private Long id;

    @Column(name = "exchange_code")
    @Expose
    private String exchangeCode;

    @Column(name = "exchange_name")
    @Expose
    private String exchangeName;

    @OneToMany(mappedBy = "exchange", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Expose
    private Set<ExchangeCoin> exchangeCoin = new HashSet<>();

}
