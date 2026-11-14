package com.example.alocacao.repository;

import com.example.alocacao.domain.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditEntryRepository extends MongoRepository<AuditEntry, String> {

    List<AuditEntry> findByEntityTypeAndEntityIdOrderByIdAsc(String entityType, String entityId);

    Page<AuditEntry> findAllByOrderByIdDesc(Pageable pageable);

    long countByAction(String action);

    long countByActionAndReferenceTimeGreaterThanEqualAndReferenceTimeLessThan(
            String action, LocalDateTime from, LocalDateTime to);
}
