package com.example.alocacao.unit;

import com.example.alocacao.domain.*;
import com.example.alocacao.repository.*;
import com.example.alocacao.service.*;
import com.example.alocacao.web.dto.Dtos.CreateReservationRequest;
import com.example.alocacao.web.dto.Dtos.ReservationView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Testes unitários isolados: persistência e relógio são simulados; regras de negócio são reais. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 10, 0);
    private static final LocalDateTime START = LocalDateTime.of(2030, 1, 2, 9, 0);
    private static final LocalDateTime END = LocalDateTime.of(2030, 1, 2, 11, 0);

    @Mock ReservationRepository reservations;
    @Mock ResourceRepository resources;
    @Mock SlotLockRepository locks;
    @Mock MaintenanceWindowRepository maintenance;
    @Mock UserRepository users;
    @Mock AuditService audit;
    @Mock ApplicationEventPublisher events;

    ReservationService service;
    final CurrentUser solicitante = new CurrentUser("1", "ana", Role.SOLICITANTE);
    final CurrentUser responsavel = new CurrentUser("2", "rui", Role.RESPONSAVEL);
    Resource room;
    Resource restrictedLab;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"));
        service = new ReservationService(reservations, resources, locks, maintenance, users, audit, events, clock);

        room = resource("10", "Sala 101", false);
        restrictedLab = resource("11", "Lab Química", true);
        when(resources.findAllById(List.of("10"))).thenReturn(List.of(room));
        when(resources.findAllById(List.of("11"))).thenReturn(List.of(restrictedLab));
        when(users.findById("1")).thenReturn(Optional.of(user("1", Role.SOLICITANTE)));
        when(locks.findAllById(any())).thenReturn(List.of());
        when(reservations.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------------------------------------------------------------- validação de horário

    @ParameterizedTest(name = "início {0}, término {1} deve ser rejeitado")
    @CsvSource({
            "2030-01-02T09:00, 2030-01-02T09:00",   // término == início
            "2030-01-02T09:00, 2030-01-02T08:00",   // término antes do início
            "2029-12-31T09:00, 2029-12-31T11:00",   // início no passado
            "2030-01-02T09:10, 2030-01-02T11:00",   // fora da faixa de 15 min
            "2030-01-02T09:00, 2030-01-03T09:15"    // mais de 24 h
    })
    void rejectsInvalidPeriods(LocalDateTime start, LocalDateTime end) {
        assertThatThrownBy(() -> service.create(solicitante, request(List.of("10"), start, end)))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(resources);
        verify(reservations, never()).save(any());
    }

    // ---------------------------------------------------------------- recursos

    @Test
    void rejectsUnknownResource() {
        when(resources.findAllById(List.of("99"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(solicitante, request(List.of("99"), START, END)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsInactiveResource() {
        room.setActive(false);

        assertThatThrownBy(() -> service.create(solicitante, request(List.of("10"), START, END)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Sala 101");
    }

    @Test
    void blocksResourceUnderMaintenance() {
        when(maintenance.countOverlaps("10", START, END)).thenReturn(1L);

        assertThatThrownBy(() -> service.create(solicitante, request(List.of("10"), START, END)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("manutenção");
        verify(reservations, never()).save(any());
    }

    // ---------------------------------------------------------------- conflitos

    @Test
    void rejectsOverlapAndRegistersAvoidedConflict() {
        when(locks.findAllById(any())).thenReturn(List.of(new SlotLock("10:1", "outraReserva", "10", 1)));

        assertThatThrownBy(() -> service.create(solicitante, request(List.of("10"), START, END)))
                .isInstanceOf(ConflictException.class);

        verify(reservations, never()).save(any());
        verify(locks, never()).insert(any(SlotLock.class));
        verify(audit).record(eq("RESOURCE"), eq("10"), eq("CONFLITO_EVITADO"), isNull(), isNull(),
                eq("ana"), anyString(), eq(START));
    }

    @Test
    void duplicateKeyOnSlotLockBecomesConflictAndUndoesPartialLocks() {
        // Simula a corrida: a verificação prévia passou, mas a 3ª faixa já tinha sido travada por outra requisição.
        AtomicInteger calls = new AtomicInteger();
        when(locks.insert(any(SlotLock.class))).thenAnswer(inv -> {
            if (calls.incrementAndGet() == 3) {
                throw new DuplicateKeyException("E11000 duplicate key");
            }
            return inv.getArgument(0);
        });

        assertThatThrownBy(() -> service.create(solicitante, request(List.of("10"), START, END)))
                .isInstanceOf(ConflictException.class);

        verify(locks).deleteAllById(argThat((Iterable<String> ids) -> {
            int n = 0;
            for (String ignored : ids) {
                n++;
            }
            return n == 2; // as duas travas gravadas antes da falha foram desfeitas
        }));
        verify(reservations, never()).save(any());
        verify(audit).record(eq("RESOURCE"), eq("10"), eq("CONFLITO_EVITADO"), any(), any(), any(), any(), any());
    }

    @Test
    void failureToSaveReservationReleasesTheLocks() {
        when(reservations.save(any(Reservation.class))).thenThrow(new IllegalStateException("falha"));

        assertThatThrownBy(() -> service.create(solicitante, request(List.of("10"), START, END)))
                .isInstanceOf(IllegalStateException.class);

        verify(locks).deleteByReservationId(anyString());
    }

    @Test
    void allSlotsOfTheIntervalAreLocked() {
        service.create(solicitante, request(List.of("10"), START, END));

        verify(locks, times(8)).insert(any(SlotLock.class)); // 2 h = 8 faixas de 15 min
    }

    // ---------------------------------------------------------------- aprovação

    @Test
    void unrestrictedResourceIsApprovedAutomatically() {
        ReservationView view = service.create(solicitante, request(List.of("10"), START, END));

        assertThat(view.status()).isEqualTo(ReservationStatus.APROVADA);
        verify(audit).record(eq("RESERVATION"), anyString(), eq("CRIACAO"), isNull(), eq("SOLICITADA"), eq("ana"), anyString(), isNull());
        verify(audit).record(eq("RESERVATION"), anyString(), eq("APROVACAO_AUTOMATICA"), eq("SOLICITADA"), eq("APROVADA"), eq("sistema"), anyString(), isNull());
        verify(events, times(2)).publishEvent(any(ReservationEvent.class));
    }

    @Test
    void restrictedResourceStaysRequestedUntilApproved() {
        ReservationView view = service.create(solicitante, request(List.of("11"), START, END));

        assertThat(view.status()).isEqualTo(ReservationStatus.SOLICITADA);
        assertThat(view.requiresApproval()).isTrue();
        verify(audit, never()).record(any(), any(), eq("APROVACAO_AUTOMATICA"), any(), any(), any(), any(), any());
    }

    @Test
    void onlyResponsavelCanApprove() {
        assertThatThrownBy(() -> service.approve(solicitante, "100", null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.approve(new CurrentUser("3", "adm", Role.ADMINISTRADOR), "100", null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(reservations);
    }

    @Test
    void approvalOfRestrictedReservationIsAudited() {
        Reservation r = reservation(ReservationStatus.SOLICITADA, START);
        when(reservations.findById("100")).thenReturn(Optional.of(r));

        ReservationView view = service.approve(responsavel, "100", "ok");

        assertThat(view.status()).isEqualTo(ReservationStatus.APROVADA);
        verify(audit).record(eq("RESERVATION"), eq("100"), eq("APROVACAO"), eq("SOLICITADA"), eq("APROVADA"), eq("rui"), eq("ok"), isNull());
    }

    // ---------------------------------------------------------------- estados e exclusão

    @Test
    void cancelledReservationReleasesTheSlots() {
        Reservation r = reservation(ReservationStatus.APROVADA, START);
        when(reservations.findById("100")).thenReturn(Optional.of(r));

        service.cancel(solicitante, "100");

        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCELADA);
        verify(locks).deleteByReservationId("100");
    }

    @Test
    void cannotCancelReservationInUse() {
        when(reservations.findById("100")).thenReturn(Optional.of(reservation(ReservationStatus.EM_USO, START)));

        assertThatThrownBy(() -> service.cancel(solicitante, "100")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void otherRequesterCannotCancel() {
        when(reservations.findById("100")).thenReturn(Optional.of(reservation(ReservationStatus.APROVADA, START)));

        assertThatThrownBy(() -> service.cancel(new CurrentUser("9", "bia", Role.SOLICITANTE), "100"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @CsvSource({"EM_USO", "CONCLUIDA"})
    void startedReservationsCannotBeDeleted(ReservationStatus status) {
        when(reservations.findById("100")).thenReturn(Optional.of(reservation(status, START)));

        assertThatThrownBy(() -> service.delete(solicitante, "100")).isInstanceOf(ConflictException.class);
        verify(reservations, never()).delete(any(Reservation.class));
    }

    @Test
    void notStartedReservationCanBeDeletedAndIsAudited() {
        Reservation r = reservation(ReservationStatus.CANCELADA, START);
        when(reservations.findById("100")).thenReturn(Optional.of(r));

        service.delete(solicitante, "100");

        verify(reservations).delete(r);
        verify(locks).deleteByReservationId("100");
        verify(audit).record(eq("RESERVATION"), eq("100"), eq("EXCLUSAO"), eq("CANCELADA"), isNull(), eq("ana"), isNull(), isNull());
    }

    @Test
    void noShowIsRejectedBeforeStartTime() {
        when(reservations.findById("100")).thenReturn(Optional.of(reservation(ReservationStatus.APROVADA, START)));

        assertThatThrownBy(() -> service.noShow(responsavel, "100")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void noShowIsAcceptedAfterStartTime() {
        Reservation r = reservation(ReservationStatus.APROVADA, NOW.minusHours(1));
        when(reservations.findById("100")).thenReturn(Optional.of(r));

        assertThat(service.noShow(responsavel, "100").status()).isEqualTo(ReservationStatus.NAO_COMPARECEU);
    }

    @Test
    void checkoutAndReturnRecordWhoAndWhen() {
        Reservation r = reservation(ReservationStatus.APROVADA, START);
        when(reservations.findById("100")).thenReturn(Optional.of(r));

        service.checkout(responsavel, "100");
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.EM_USO);
        assertThat(r.getCheckedOutBy()).isEqualTo("rui");
        assertThat(r.getCheckedOutAt()).isEqualTo(NOW);

        service.giveBack(responsavel, "100");
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CONCLUIDA);
        assertThat(r.getReturnedBy()).isEqualTo("rui");
    }

    @Test
    void everyStateChangeGeneratesAnAuditEntry() {
        Reservation r = reservation(ReservationStatus.SOLICITADA, START);
        when(reservations.findById("100")).thenReturn(Optional.of(r));

        service.approve(responsavel, "100", null);
        service.checkout(responsavel, "100");
        service.giveBack(responsavel, "100");

        verify(audit, times(3)).record(eq("RESERVATION"), eq("100"), anyString(), anyString(), anyString(), eq("rui"), any(), any());
    }

    // ---------------------------------------------------------------- helpers

    private static CreateReservationRequest request(List<Long> ids, LocalDateTime start, LocalDateTime end) {
        return new CreateReservationRequest(ids, start, end, "Aula de Qualidade");
    }

    private static Resource resource(String id, String name, boolean restricted) {
        Resource r = new Resource();
        r.setId(id);
        r.setType(ResourceType.SALA);
        r.setName(name);
        r.setRestricted(restricted);
        return r;
    }

    private static AppUser user(String id, Role role) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setUsername("ana");
        u.setFullName("Ana");
        u.setEmail("ana@escola.com");
        u.setRole(role);
        return u;
    }

    private Reservation reservation(ReservationStatus status, LocalDateTime start) {
        Reservation r = new Reservation();
        r.setId("100");
        r.setRequesterId("1");
        r.setRequesterName("Ana");
        r.setRequesterEmail("ana@escola.com");
        r.setStartTime(start);
        r.setEndTime(start.plusHours(2));
        r.setPurpose("Aula");
        r.setStatus(status);
        r.setCreatedAt(NOW);
        r.setUpdatedAt(NOW);
        r.getItems().add(new ReservationItem(room.getId(), room.getName(), room.getType(), room.isRestricted()));
        return r;
    }
}
