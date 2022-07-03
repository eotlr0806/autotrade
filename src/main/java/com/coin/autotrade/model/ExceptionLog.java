package com.coin.autotrade.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "exception_log")
@DynamicUpdate
public class ExceptionLog {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @Column
    private long id;

    @Column(name = "date_time")
    private LocalDateTime dateTime;

    @Column(name = "exchange")
    private String exchange;

    @Column(name = "action")
    private String action;

    @Column(name = "request")
    @Lob
    private String request;

    @Column(name = "log")
    @Lob
    private String log;
}
