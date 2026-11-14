package com.example.alocacao.service;

import com.example.alocacao.domain.*;
import com.example.alocacao.repository.MaintenanceWindowRepository;
import com.example.alocacao.repository.ReservationRepository;
import com.example.alocacao.repository.ResourceRepository;
import com.example.alocacao.repository.SlotLockRepository;
import com.example.alocacao.web.dto.Dtos.MaintenanceRequest;
import com.example.alocacao.web.dto.Dtos.MaintenanceView;
import com.example.alocacao.web.dto.Dtos.ResourceRequest;
import com.example.alocacao.web.dto.Dtos.ResourceView;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ResourceService {

    private static final String ENTITY = "RESOURCE";

    private final ResourceRepository resources;
    private final MaintenanceWindowRepository maintenance;
    private final SlotLockRepository locks;
    private final ReservationRepository reservations;
    private final MongoTemplate mongo;
    private final AuditService audit;
    private final Clock clock;

    public ResourceService(ResourceRepository resources, MaintenanceWindowRepository maintenance,
                           SlotLockRepository locks, ReservationRepository reservations, MongoTemplate mongo,
                           AuditService audit, Clock clock) {
        this.resources = resources;
        this.maintenance = maintenance;
        this.locks = locks;
        this.reservations = reservations;
        this.mongo = mongo;
        this.audit = audit;
        this.clock = clock;
    }

    public List<ResourceView> search(ResourceType type, Integer minCapacity, String location, String competency,
                                     LocalDateTime availableFrom, LocalDateTime availableTo) {
        if ((availableFrom == null) != (availableTo == null)) {
            throw new BusinessRuleException("Para filtrar por disponibilidade informe início e fim");
        }
        if (availableFrom != null && !availableTo.isAfter(availableFrom)) {
            throw new BusinessRuleException("O término deve ser posterior ao início");
        }

        Criteria criteria = Criteria.where("active").is(true);
        if (type != null) {
            criteria.and("type").is(type);
        }
        if (minCapacity != null) {
            criteria.and("capacity").gte(minCapacity);
        }
        if (location != null && !location.isBlank()) {
            criteria.and("location").regex(contains(location));
        }
        if (competency != null && !competency.isBlank()) {
            criteria.and("competencies").regex(contains(competency));
        }
        List<Resource> candidates = mongo.find(new Query(criteria).with(Sort.by("name")), Resource.class);

        if (availableFrom != null && !candidates.isEmpty()) {
            Set<String> busy = busyResourceIds(candidates.stream().map(Resource::getId).toList(),
                    availableFrom, availableTo);
            candidates = candidates.stream().filter(r -> !busy.contains(r.getId())).toList();
        }
        return candidates.stream().map(ResourceView::from).toList();
    }

    public ResourceView get(String id) {
        return ResourceView.from(find(id));
    }

    public ResourceView create(CurrentUser actor, ResourceRequest req) {
        if (resources.existsByTypeAndName(req.type(), req.name().trim())) {
            throw new ConflictException("Já existe um recurso deste tipo com este nome");
        }
        Resource r = new Resource();
        apply(r, req);
        resources.save(r);
        audit.record(ENTITY, r.getId(), "CRIACAO", null, null, actor.username(), r.getType() + " " + r.getName(), null);
        return ResourceView.from(r);
    }

    public ResourceView update(CurrentUser actor, String id, ResourceRequest req) {
        Resource r = find(id);
        boolean renamed = !r.getName().equals(req.name().trim()) || r.getType() != req.type();
        if (renamed && resources.existsByTypeAndName(req.type(), req.name().trim())) {
            throw new ConflictException("Já existe um recurso deste tipo com este nome");
        }
        apply(r, req);
        resources.save(r);
        audit.record(ENTITY, r.getId(), "ALTERACAO", null, null, actor.username(), r.getType() + " " + r.getName(), null);
        return ResourceView.from(r);
    }

    /** Desativa o recurso (não apaga, para preservar o histórico). Bloqueado se houver reservas futuras. */
    public void deactivate(CurrentUser actor, String id) {
        Resource r = find(id);
        Set<ReservationStatus> active = EnumSet.of(ReservationStatus.SOLICITADA, ReservationStatus.APROVADA,
                ReservationStatus.EM_USO);
        if (reservations.countByItemsResourceIdAndEndTimeGreaterThanAndStatusIn(id, LocalDateTime.now(clock), active) > 0) {
            throw new ConflictException("O recurso possui reservas futuras; cancele-as antes de desativar");
        }
        r.setActive(false);
        resources.save(r);
        audit.record(ENTITY, r.getId(), "DESATIVACAO", null, null, actor.username(), r.getName(), null);
    }

    public MaintenanceView addMaintenance(CurrentUser actor, String resourceId, MaintenanceRequest req) {
        Resource r = find(resourceId);
        if (!req.endTime().isAfter(req.startTime())) {
            throw new BusinessRuleException("O término deve ser posterior ao início");
        }
        if (!busyResourceIds(List.of(resourceId), req.startTime(), req.endTime()).isEmpty()) {
            throw new ConflictException("Já existem reservas neste período; cancele-as antes de bloquear o recurso");
        }
        MaintenanceWindow m = new MaintenanceWindow();
        m.setResourceId(r.getId());
        m.setStartTime(req.startTime());
        m.setEndTime(req.endTime());
        m.setReason(req.reason());
        maintenance.save(m);
        audit.record(ENTITY, r.getId(), "MANUTENCAO_AGENDADA", null, null, actor.username(),
                req.startTime() + " a " + req.endTime() + ": " + req.reason(), null);
        return MaintenanceView.from(m);
    }

    public List<MaintenanceView> listMaintenance(String resourceId) {
        find(resourceId);
        return maintenance.findByResourceIdOrderByStartTime(resourceId).stream().map(MaintenanceView::from).toList();
    }

    public void removeMaintenance(CurrentUser actor, String resourceId, String maintenanceId) {
        MaintenanceWindow m = maintenance.findById(maintenanceId)
                .filter(w -> w.getResourceId().equals(resourceId))
                .orElseThrow(() -> new NotFoundException("Período de manutenção não encontrado"));
        maintenance.delete(m);
        audit.record(ENTITY, resourceId, "MANUTENCAO_REMOVIDA", null, null, actor.username(), m.getReason(), null);
    }

    /** Recursos que têm reserva (faixa travada) ou manutenção dentro do intervalo. */
    private Set<String> busyResourceIds(List<String> resourceIds, LocalDateTime start, LocalDateTime end) {
        Set<String> busy = new HashSet<>();
        locks.findInRange(resourceIds, Slots.first(start), Slots.last(end))
                .forEach(l -> busy.add(l.getResourceId()));
        maintenance.findOverlapping(resourceIds, start, end)
                .forEach(m -> busy.add(m.getResourceId()));
        return busy;
    }

    private Resource find(String id) {
        return resources.findById(id).orElseThrow(() -> new NotFoundException("Recurso não encontrado: " + id));
    }

    private static Pattern contains(String text) {
        return Pattern.compile(Pattern.quote(text.trim()), Pattern.CASE_INSENSITIVE);
    }

    private static void apply(Resource r, ResourceRequest req) {
        r.setType(req.type());
        r.setName(req.name().trim());
        r.setLocation(req.location() == null ? "" : req.location().trim());
        r.setCapacity(req.capacity());
        r.setCompetencies(req.competencies() == null ? "" : req.competencies().trim());
        r.setRestricted(req.restricted());
    }
}
