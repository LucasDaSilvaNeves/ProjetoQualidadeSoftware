package com.example.alocacao.service;

import com.example.alocacao.domain.AuditEntry;
import com.example.alocacao.repository.AuditEntryRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/** Registro de auditoria: cada mudança de estado gera uma entrada com autor, estados e horário. */
@Service
public class AuditService {

    private final AuditEntryRepository repository;
    private final Clock clock;

    public AuditService(AuditEntryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void record(String entityType, String entityId, String action, String fromStatus, String toStatus,
                       String actor, String details, LocalDateTime referenceTime) {
        AuditEntry entry = new AuditEntry();
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setFromStatus(fromStatus);
        entry.setToStatus(toStatus);
        entry.setActor(actor);
        entry.setDetails(details == null ? null : truncate(details, 500));
        entry.setReferenceTime(referenceTime);
        entry.setCreatedAt(LocalDateTime.now(clock));
        repository.save(entry);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
