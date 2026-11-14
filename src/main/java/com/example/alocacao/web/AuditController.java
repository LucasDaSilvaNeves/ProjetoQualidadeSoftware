package com.example.alocacao.web;

import com.example.alocacao.repository.AuditEntryRepository;
import com.example.alocacao.web.dto.Dtos.AuditView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Auditoria")
public class AuditController {

    private final AuditEntryRepository repository;

    public AuditController(AuditEntryRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Operation(summary = "Histórico de mudanças; informe entityType e entityId para ver o de uma reserva")
    public List<AuditView> history(@RequestParam(required = false) String entityType,
                                   @RequestParam(required = false) String entityId) {
        if (entityType != null && entityId != null) {
            return repository.findByEntityTypeAndEntityIdOrderByIdAsc(entityType, entityId)
                    .stream().map(AuditView::from).toList();
        }
        return repository.findAllByOrderByIdDesc(PageRequest.of(0, 200)).stream().map(AuditView::from).toList();
    }
}
