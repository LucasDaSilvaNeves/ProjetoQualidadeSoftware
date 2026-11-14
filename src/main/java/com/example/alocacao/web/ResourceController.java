package com.example.alocacao.web;

import com.example.alocacao.domain.ResourceType;
import com.example.alocacao.security.AppUserPrincipal;
import com.example.alocacao.service.ResourceService;
import com.example.alocacao.web.dto.Dtos.MaintenanceRequest;
import com.example.alocacao.web.dto.Dtos.MaintenanceView;
import com.example.alocacao.web.dto.Dtos.ResourceRequest;
import com.example.alocacao.web.dto.Dtos.ResourceView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/resources")
@Tag(name = "Recursos")
public class ResourceController {

    private final ResourceService service;

    public ResourceController(ResourceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Pesquisa por tipo, capacidade, localização, competência e disponibilidade")
    public List<ResourceView> search(
            @RequestParam(required = false) ResourceType type,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String competency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime availableFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime availableTo) {
        return service.search(type, minCapacity, location, competency, availableFrom, availableTo);
    }

    @GetMapping("/{id}")
    public ResourceView get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ResourceView> create(@AuthenticationPrincipal AppUserPrincipal me,
                                               @Valid @RequestBody ResourceRequest request) {
        ResourceView view = service.create(CurrentUserSupport.of(me), request);
        return ResponseEntity.created(URI.create("/api/resources/" + view.id())).body(view);
    }

    @PutMapping("/{id}")
    public ResourceView update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String id,
                               @Valid @RequestBody ResourceRequest request) {
        return service.update(CurrentUserSupport.of(me), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desativa o recurso (409 se houver reservas futuras)")
    public void deactivate(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String id) {
        service.deactivate(CurrentUserSupport.of(me), id);
    }

    @GetMapping("/{id}/maintenance")
    public List<MaintenanceView> listMaintenance(@PathVariable String id) {
        return service.listMaintenance(id);
    }

    @PostMapping("/{id}/maintenance")
    @Operation(summary = "Bloqueia o recurso por manutenção em um período")
    public ResponseEntity<MaintenanceView> addMaintenance(@AuthenticationPrincipal AppUserPrincipal me,
                                                          @PathVariable String id,
                                                          @Valid @RequestBody MaintenanceRequest request) {
        MaintenanceView view = service.addMaintenance(CurrentUserSupport.of(me), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @DeleteMapping("/{id}/maintenance/{maintenanceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMaintenance(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String id,
                                  @PathVariable String maintenanceId) {
        service.removeMaintenance(CurrentUserSupport.of(me), id, maintenanceId);
    }
}
