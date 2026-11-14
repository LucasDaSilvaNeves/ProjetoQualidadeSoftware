package com.example.alocacao.service;

import com.example.alocacao.domain.*;
import com.example.alocacao.repository.*;
import com.example.alocacao.web.dto.Dtos.CreateReservationRequest;
import com.example.alocacao.web.dto.Dtos.ReservationView;
import com.example.alocacao.web.dto.Dtos.UpdateReservationRequest;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Regras centrais de reserva.
 * <p>
 * Proteção contra dupla reserva em duas camadas:
 * 1) verificação prévia (mensagem amigável e contagem de "conflitos evitados");
 * 2) travas de horário ({@link SlotLock}): cada faixa de 15 minutos de cada recurso é um documento cujo
 * {@code _id} é "recurso:faixa". O MongoDB garante a unicidade do _id de forma atômica, então, se duas
 * requisições passarem pela verificação prévia ao mesmo tempo, somente uma consegue gravar todas as travas.
 * Quem perde desfaz as travas que já gravou e recebe 409.
 */
@Service
public class ReservationService {

    static final String ENTITY = "RESERVATION";
    static final String CONFLICT_ACTION = "CONFLITO_EVITADO";
    private static final int PAGE_LIMIT = 200;

    private final ReservationRepository reservations;
    private final ResourceRepository resources;
    private final SlotLockRepository locks;
    private final MaintenanceWindowRepository maintenance;
    private final UserRepository users;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ReservationService(ReservationRepository reservations, ResourceRepository resources,
                              SlotLockRepository locks, MaintenanceWindowRepository maintenance,
                              UserRepository users, AuditService audit, ApplicationEventPublisher events,
                              Clock clock) {
        this.reservations = reservations;
        this.resources = resources;
        this.locks = locks;
        this.maintenance = maintenance;
        this.users = users;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ consultas

    public List<ReservationView> list(CurrentUser user, ReservationStatus status) {
        Pageable page = PageRequest.of(0, PAGE_LIMIT, Sort.by(Sort.Direction.DESC, "startTime"));
        Page<Reservation> result = user.role() == Role.SOLICITANTE
                ? reservations.findByRequesterId(user.id(), page)
                : reservations.findAll(page);
        return result.getContent().stream()
                .filter(r -> status == null || r.getStatus() == status)
                .map(ReservationView::from)
                .toList();
    }

    public ReservationView get(CurrentUser user, String id) {
        Reservation r = find(id);
        requireVisible(user, r);
        return ReservationView.from(r);
    }

    // ------------------------------------------------------------------ criação e alteração

    public ReservationView create(CurrentUser user, CreateReservationRequest req) {
        validatePeriod(req.startTime(), req.endTime());
        Map<String, Resource> found = loadResources(req.resourceIds());
        Map<String, String> targets = namesById(found.values());
        checkMaintenance(targets, req.startTime(), req.endTime());

        String id = new ObjectId().toHexString();
        try {
            checkOverlaps(targets, req.startTime(), req.endTime(), "");
            acquireSlots(targets, req.startTime(), req.endTime(), id, Set.of());
        } catch (ConflictException e) {
            recordConflicts(e, user, req.startTime());
            throw e;
        }

        AppUser requester = users.findById(user.id())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        LocalDateTime now = LocalDateTime.now(clock);
        Reservation r = new Reservation();
        r.setId(id);
        r.setRequesterId(requester.getId());
        r.setRequesterName(requester.getFullName());
        r.setRequesterEmail(requester.getEmail());
        r.setStartTime(req.startTime());
        r.setEndTime(req.endTime());
        r.setPurpose(req.purpose().trim());
        r.setStatus(ReservationStatus.SOLICITADA);
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        found.values().forEach(res -> r.getItems().add(
                new ReservationItem(res.getId(), res.getName(), res.getType(), res.isRestricted())));

        try {
            reservations.save(r);
        } catch (RuntimeException e) {
            locks.deleteByReservationId(id); // não deixa travas órfãs
            throw e;
        }

        audit.record(ENTITY, r.getId(), "CRIACAO", null, ReservationStatus.SOLICITADA.name(), user.username(),
                "Recursos: " + String.join(", ", new TreeSet<>(targets.values())), null);
        publish(r, "RESERVA_SOLICITADA");

        // Aprovação obrigatória apenas para recursos restritos; os demais são aprovados automaticamente.
        if (found.values().stream().noneMatch(Resource::isRestricted)) {
            changeStatus(r, ReservationStatus.APROVADA, "sistema", "APROVACAO_AUTOMATICA",
                    "Nenhum recurso restrito na reserva", null);
        }
        return ReservationView.from(r);
    }

    public ReservationView update(CurrentUser user, String id, UpdateReservationRequest req) {
        Reservation r = find(id);
        requireOwnerOrStaff(user, r);
        if (r.getStatus() != ReservationStatus.SOLICITADA && r.getStatus() != ReservationStatus.APROVADA) {
            throw new BusinessRuleException("Somente reservas solicitadas ou aprovadas podem ser alteradas");
        }
        validatePeriod(req.startTime(), req.endTime());

        Map<String, String> targets = new LinkedHashMap<>();
        r.getItems().forEach(i -> targets.put(i.getResourceId(), i.getResourceName()));
        checkMaintenance(targets, req.startTime(), req.endTime());

        Set<String> oldIds = lockIds(targets.keySet(), r.getStartTime(), r.getEndTime());
        Set<String> newIds = lockIds(targets.keySet(), req.startTime(), req.endTime());
        try {
            checkOverlaps(targets, req.startTime(), req.endTime(), r.getId());
            acquireSlots(targets, req.startTime(), req.endTime(), r.getId(), oldIds);
        } catch (ConflictException e) {
            recordConflicts(e, user, req.startTime());
            throw e;
        }

        r.setStartTime(req.startTime());
        r.setEndTime(req.endTime());
        r.setPurpose(req.purpose().trim());
        r.setUpdatedAt(LocalDateTime.now(clock));
        try {
            reservations.save(r);
        } catch (RuntimeException e) {
            locks.deleteAllById(difference(newIds, oldIds)); // desfaz só as travas novas
            throw e;
        }
        locks.deleteAllById(difference(oldIds, newIds));      // libera as faixas que não são mais usadas

        audit.record(ENTITY, r.getId(), "ALTERACAO", r.getStatus().name(), r.getStatus().name(),
                user.username(), "Novo horário: " + req.startTime() + " a " + req.endTime(), null);

        if (r.getStatus() == ReservationStatus.APROVADA && r.getItems().stream().anyMatch(ReservationItem::isRestricted)) {
            changeStatus(r, ReservationStatus.SOLICITADA, user.username(), "REAPROVACAO_NECESSARIA",
                    "Horário alterado; recurso restrito exige nova aprovação", null);
        }
        return ReservationView.from(r);
    }

    // ------------------------------------------------------------------ transições de estado

    public ReservationView approve(CurrentUser user, String id, String note) {
        requireRole(user, Role.RESPONSAVEL);
        Reservation r = find(id);
        changeStatus(r, ReservationStatus.APROVADA, user.username(), "APROVACAO", note, x -> x.setDecisionNote(note));
        return ReservationView.from(r);
    }

    public ReservationView reject(CurrentUser user, String id, String note) {
        requireRole(user, Role.RESPONSAVEL);
        Reservation r = find(id);
        changeStatus(r, ReservationStatus.REJEITADA, user.username(), "REJEICAO", note, x -> x.setDecisionNote(note));
        return ReservationView.from(r);
    }

    public ReservationView cancel(CurrentUser user, String id) {
        Reservation r = find(id);
        requireOwnerOrStaff(user, r);
        changeStatus(r, ReservationStatus.CANCELADA, user.username(), "CANCELAMENTO", null, null);
        return ReservationView.from(r);
    }

    /** Retirada de materiais/equipamentos e início do uso da sala. */
    public ReservationView checkout(CurrentUser user, String id) {
        requireRole(user, Role.RESPONSAVEL);
        Reservation r = find(id);
        changeStatus(r, ReservationStatus.EM_USO, user.username(), "RETIRADA", null, x -> {
            x.setCheckedOutAt(LocalDateTime.now(clock));
            x.setCheckedOutBy(user.username());
        });
        return ReservationView.from(r);
    }

    /** Devolução de materiais/equipamentos e conclusão da reserva. */
    public ReservationView giveBack(CurrentUser user, String id) {
        requireRole(user, Role.RESPONSAVEL);
        Reservation r = find(id);
        changeStatus(r, ReservationStatus.CONCLUIDA, user.username(), "DEVOLUCAO", null, x -> {
            x.setReturnedAt(LocalDateTime.now(clock));
            x.setReturnedBy(user.username());
        });
        return ReservationView.from(r);
    }

    public ReservationView noShow(CurrentUser user, String id) {
        requireRole(user, Role.RESPONSAVEL);
        Reservation r = find(id);
        if (LocalDateTime.now(clock).isBefore(r.getStartTime())) {
            throw new BusinessRuleException("Não é possível registrar não comparecimento antes do horário de início");
        }
        changeStatus(r, ReservationStatus.NAO_COMPARECEU, user.username(), "NAO_COMPARECIMENTO", null, null);
        return ReservationView.from(r);
    }

    /** Reservas iniciadas (EM_USO ou CONCLUIDA) não podem ser apagadas; o histórico de auditoria é mantido. */
    public void delete(CurrentUser user, String id) {
        Reservation r = find(id);
        requireOwnerOrStaff(user, r);
        if (r.getStatus().isStarted()) {
            throw new ConflictException("Reservas iniciadas não podem ser apagadas");
        }
        audit.record(ENTITY, r.getId(), "EXCLUSAO", r.getStatus().name(), null, user.username(), null, null);
        reservations.delete(r);
        locks.deleteByReservationId(r.getId());
    }

    // ------------------------------------------------------------------ regras internas

    private void changeStatus(Reservation r, ReservationStatus target, String actor, String action,
                              String details, Consumer<Reservation> extra) {
        ReservationStatus from = r.getStatus();
        if (!from.canTransitionTo(target)) {
            throw new BusinessRuleException("Transição de estado inválida: " + from + " → " + target);
        }
        r.setStatus(target);
        r.setUpdatedAt(LocalDateTime.now(clock));
        if (extra != null) {
            extra.accept(r);
        }
        reservations.save(r); // @Version: falha com OptimisticLockingFailureException se outra decisão chegou antes
        if (!target.isBlocking()) {
            locks.deleteByReservationId(r.getId()); // libera o horário dos recursos
        }
        audit.record(ENTITY, r.getId(), action, from.name(), target.name(), actor, details, null);
        publish(r, "RESERVA_" + target.name());
    }

    private void validatePeriod(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new BusinessRuleException("O término deve ser posterior ao início");
        }
        if (start.isBefore(LocalDateTime.now(clock))) {
            throw new BusinessRuleException("O início não pode estar no passado");
        }
        if (!Slots.isAligned(start) || !Slots.isAligned(end)) {
            throw new BusinessRuleException("Início e término devem ser múltiplos de " + Slots.SLOT_MINUTES + " minutos");
        }
        if (Duration.between(start, end).toMinutes() > (long) Slots.MAX_SLOTS_PER_RESERVATION * Slots.SLOT_MINUTES) {
            throw new BusinessRuleException("A reserva pode durar no máximo 24 horas");
        }
    }

    private Map<String, Resource> loadResources(List<String> requestedIds) {
        List<String> ids = requestedIds.stream().distinct().toList();
        Map<String, Resource> found = new LinkedHashMap<>();
        resources.findAllById(ids).forEach(r -> found.put(r.getId(), r));
        if (found.size() != ids.size()) {
            List<String> missing = ids.stream().filter(i -> !found.containsKey(i)).toList();
            throw new NotFoundException("Recurso(s) não encontrado(s): " + missing);
        }
        List<Resource> inactive = found.values().stream().filter(r -> !r.isActive()).toList();
        if (!inactive.isEmpty()) {
            throw new BusinessRuleException("Recurso(s) desativado(s): "
                    + inactive.stream().map(Resource::getName).sorted().collect(Collectors.joining(", ")));
        }
        return found;
    }

    private void checkMaintenance(Map<String, String> targets, LocalDateTime start, LocalDateTime end) {
        List<String> blocked = targets.entrySet().stream()
                .filter(e -> maintenance.countOverlaps(e.getKey(), start, end) > 0)
                .map(Map.Entry::getValue)
                .sorted()
                .toList();
        if (!blocked.isEmpty()) {
            throw new BusinessRuleException("Recurso(s) em manutenção no período: " + String.join(", ", blocked));
        }
    }

    /** Verificação prévia: mostra quais recursos estão ocupados. A garantia final é {@link #acquireSlots}. */
    private void checkOverlaps(Map<String, String> targets, LocalDateTime start, LocalDateTime end, String excludeId) {
        Set<String> busy = new LinkedHashSet<>();
        for (SlotLock lock : locks.findAllById(lockIds(targets.keySet(), start, end))) {
            if (!lock.getReservationId().equals(excludeId)) {
                busy.add(lock.getResourceId());
            }
        }
        if (!busy.isEmpty()) {
            String names = busy.stream().map(targets::get).sorted().collect(Collectors.joining(", "));
            throw new ConflictException("Conflito de horário: já existe reserva para " + names, new ArrayList<>(busy));
        }
    }

    /** Grava as travas de horário. Tudo ou nada: se uma faixa já estiver ocupada, desfaz o que gravou. */
    private void acquireSlots(Map<String, String> targets, LocalDateTime start, LocalDateTime end,
                              String reservationId, Set<String> alreadyOwned) {
        List<String> inserted = new ArrayList<>();
        for (Map.Entry<String, String> target : targets.entrySet()) {
            for (long slot : Slots.covering(start, end)) {
                String lockId = Slots.lockId(target.getKey(), slot);
                if (alreadyOwned.contains(lockId)) {
                    continue;
                }
                try {
                    locks.insert(new SlotLock(lockId, reservationId, target.getKey(), slot));
                    inserted.add(lockId);
                } catch (DataIntegrityViolationException e) { // DuplicateKeyException: faixa já ocupada
                    locks.deleteAllById(inserted);
                    throw new ConflictException(
                            "Conflito de horário: " + target.getValue()
                                    + " foi reservado por outra solicitação ao mesmo tempo",
                            List.of(target.getKey()));
                }
            }
        }
    }

    private static Set<String> lockIds(Collection<String> resourceIds, LocalDateTime start, LocalDateTime end) {
        Set<String> ids = new LinkedHashSet<>();
        for (String resourceId : resourceIds) {
            for (long slot : Slots.covering(start, end)) {
                ids.add(Slots.lockId(resourceId, slot));
            }
        }
        return ids;
    }

    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> result = new LinkedHashSet<>(a);
        result.removeAll(b);
        return result;
    }

    private static Map<String, String> namesById(Collection<Resource> list) {
        Map<String, String> map = new LinkedHashMap<>();
        list.forEach(r -> map.put(r.getId(), r.getName()));
        return map;
    }

    private void recordConflicts(ConflictException e, CurrentUser user, LocalDateTime requestedStart) {
        for (String resourceId : e.getResourceIds()) {
            audit.record("RESOURCE", resourceId, CONFLICT_ACTION, null, null, user.username(),
                    "Tentativa de reserva conflitante bloqueada", requestedStart);
        }
    }

    private void publish(Reservation r, String type) {
        events.publishEvent(new ReservationEvent(r.getId(), type, r.getRequesterEmail(),
                "Reserva #" + r.getId() + ": " + type.replace("RESERVA_", "")));
    }

    private Reservation find(String id) {
        return reservations.findById(id).orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + id));
    }

    private static void requireRole(CurrentUser user, Role role) {
        if (user.role() != role) {
            throw new AccessDeniedException("Operação restrita ao perfil " + role);
        }
    }

    private static void requireVisible(CurrentUser user, Reservation r) {
        if (user.role() == Role.SOLICITANTE && !r.getRequesterId().equals(user.id())) {
            throw new AccessDeniedException("Reserva de outro usuário");
        }
    }

    private static void requireOwnerOrStaff(CurrentUser user, Reservation r) {
        if (user.role() == Role.SOLICITANTE && !r.getRequesterId().equals(user.id())) {
            throw new AccessDeniedException("Reserva de outro usuário");
        }
    }
}
