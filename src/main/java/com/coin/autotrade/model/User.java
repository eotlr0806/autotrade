package com.coin.autotrade.model;

import com.google.gson.annotations.Expose;
import lombok.Data;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "user")
@DynamicUpdate
public class User {

    @Id
    @Column(name = "user_id")
    @Expose
    private String userId;

    @Column(name = "user_pw")
    @Expose
    private String userPw;

    @Column(name = "user_nm")
    @Expose
    private String userNm;

    @Column(name = "create_dt")
    @Expose
    private String createDt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Expose
    private Set<UserExchange> userExchanges = new HashSet<>();

}
