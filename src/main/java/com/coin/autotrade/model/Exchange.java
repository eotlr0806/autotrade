package com.coin.autotrade.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "exchange")
@DynamicUpdate
public class Exchange {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "exchange_code")
    private String exchangeCode;

    @Column(name = "exchange_name")
    private String exchangeName;

    @OneToMany(mappedBy = "exchange", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<ExchangeCoin> exchangeCoin = new HashSet<>();

}
