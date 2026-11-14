package com.example.alocacao.it;

import com.example.alocacao.domain.AppUser;
import com.example.alocacao.domain.Resource;
import com.example.alocacao.domain.ResourceType;
import com.example.alocacao.domain.Role;
import com.example.alocacao.repository.AuditEntryRepository;
import com.example.alocacao.repository.ReservationRepository;
import com.example.alocacao.repository.SlotLockRepository;
import com.example.alocacao.service.ConflictException;
import com.example.alocacao.service.CurrentUser;
import com.example.alocacao.service.ReservationService;
import com.example.alocacao.web.dto.Dtos.CreateReservationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova da proteção contra dupla reserva: N solicitações simultâneas, sobre o mesmo recurso e horário,
 * contra um PostgreSQL real, devem resultar em EXATAMENTE uma reserva aceita.
 */
class ReservationConcurrencyIT extends AbstractIntegrationTest {

    private static final int ATTEMPTS = 12;

    @Autowired
    ReservationService service;
    @Autowired
    ReservationRepository reservationRepository;
    @Autowired
    SlotLockRepository slotLocks;
    @Autowired
    AuditEntryRepository auditRepository;

    @Test
    void simultaneousRequestsForSameRoomProduceSingleReservation() throws Exception {
        AppUser requester = user("ana", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);
        CurrentUser me = new CurrentUser(requester.getId(), requester.getUsername(), requester.getRole());
        CreateReservationRequest request =
                new CreateReservationRequest(List.of(room.getId()), at(40, 10), at(40, 12), "Aula");

        ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch ready = new CountDownLatch(ATTEMPTS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < ATTEMPTS; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    service.create(me, request);
                    accepted.incrementAndGet();
                } catch (ConflictException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(accepted.get()).as("reservas aceitas").isEqualTo(1);
        assertThat(rejected.get()).as("reservas rejeitadas").isEqualTo(ATTEMPTS - 1);
        assertThat(reservationRepository.count()).as("reservas gravadas").isEqualTo(1);
        assertThat(slotLocks.count()).as("faixas de 15 min travadas (2 h)").isEqualTo(8);
        assertThat(auditRepository.countByAction("CONFLITO_EVITADO")).as("conflitos evitados").isEqualTo(ATTEMPTS - 1);
    }

    @Test
    void simultaneousHttpRequestsProduceSingleReservation() throws Exception {
        user("ana", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 102", false);
        int attempts = 8;

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return createReservation("ana", List.of(room.getId()), at(41, 14), at(41, 16));
            }));
        }
        go.countDown();

        int created = 0;
        int conflicts = 0;
        for (Future<Integer> f : futures) {
            int status = f.get(60, TimeUnit.SECONDS);
            if (status == 201) {
                created++;
            } else if (status == 409) {
                conflicts++;
            }
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
        assertThat(conflicts).isEqualTo(attempts - 1);
    }
}
