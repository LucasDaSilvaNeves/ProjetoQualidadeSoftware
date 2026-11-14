package com.example.alocacao.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document("reservations")
@CompoundIndex(name = "idx_reservation_requester_start", def = "{'requesterId': 1, 'startTime': -1}")
@Getter
@Setter
@NoArgsConstructor
public class Reservation {

    @Id
    private String id;

    private String requesterId;
    private String requesterName;
    private String requesterEmail;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String purpose;
    private ReservationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime checkedOutAt;
    private String checkedOutBy;
    private LocalDateTime returnedAt;
    private String returnedBy;
    private String decisionNote;

    /** Controle otimista: duas decisões simultâneas sobre a mesma reserva não se sobrescrevem. */
    @Version
    private Long version;

    private List<ReservationItem> items = new ArrayList<>();
}
