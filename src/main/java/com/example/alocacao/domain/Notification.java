package com.example.alocacao.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document("notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    private String id;
    private String recipient;
    private String subject;
    private String message;

    /** SIMULADO (apenas registrada) ou WEBHOOK (enviada a uma API externa). */
    private String channel;
    private boolean delivered;
    private LocalDateTime createdAt;
}
