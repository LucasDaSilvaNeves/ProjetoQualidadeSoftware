package com.example.alocacao.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document("audit_entries")
@CompoundIndex(name = "idx_audit_entity", def = "{'entityType': 1, 'entityId': 1}")
@CompoundIndex(name = "idx_audit_action", def = "{'action': 1, 'referenceTime': 1}")
@Getter
@Setter
@NoArgsConstructor
public class AuditEntry {

    @Id
    private String id;
    private String entityType;
    private String entityId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String actor;
    private String details;

    /** Para CONFLITO_EVITADO: início do horário que foi solicitado. */
    private LocalDateTime referenceTime;

    private LocalDateTime createdAt;
}
