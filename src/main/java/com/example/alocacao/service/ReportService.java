package com.example.alocacao.service;

import com.example.alocacao.domain.Reservation;
import com.example.alocacao.domain.ReservationItem;
import com.example.alocacao.domain.ReservationStatus;
import com.example.alocacao.repository.AuditEntryRepository;
import com.example.alocacao.repository.ReservationRepository;
import com.example.alocacao.web.dto.Dtos.ResourceUsage;
import com.example.alocacao.web.dto.Dtos.UtilizationReport;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

@Service
public class ReportService {

    /** Estados em que a reserva realmente aloca o recurso. */
    private static final Set<ReservationStatus> ALLOCATING = EnumSet.of(
            ReservationStatus.APROVADA, ReservationStatus.EM_USO, ReservationStatus.CONCLUIDA);

    private final ReservationRepository reservations;
    private final AuditEntryRepository audit;

    public ReportService(ReservationRepository reservations, AuditEntryRepository audit) {
        this.reservations = reservations;
        this.audit = audit;
    }

    /** Período inclusivo: de {@code from} (00:00) até o fim do dia {@code to}. */
    public UtilizationReport utilization(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("A data final não pode ser anterior à inicial");
        }
        var start = from.atStartOfDay();
        var end = to.plusDays(1).atStartOfDay();

        Map<String, long[]> minutesAndCount = new HashMap<>();
        Map<String, ReservationItem> itemById = new HashMap<>();
        for (Reservation r : reservations.findByStartTimeGreaterThanEqualAndStartTimeLessThanAndStatusIn(
                start, end, ALLOCATING)) {
            long minutes = Duration.between(r.getStartTime(), r.getEndTime()).toMinutes();
            for (ReservationItem item : r.getItems()) {
                itemById.putIfAbsent(item.getResourceId(), item);
                long[] acc = minutesAndCount.computeIfAbsent(item.getResourceId(), k -> new long[2]);
                acc[0] += minutes;
                acc[1] += 1;
            }
        }

        List<ResourceUsage> usage = minutesAndCount.entrySet().stream()
                .map(e -> {
                    ReservationItem item = itemById.get(e.getKey());
                    return new ResourceUsage(item.getResourceId(), item.getResourceName(), item.getResourceType(),
                            e.getValue()[1], hours(e.getValue()[0]));
                })
                .sorted(Comparator.comparingDouble(ResourceUsage::allocatedHours).reversed()
                        .thenComparing(ResourceUsage::name))
                .toList();

        double total = hours(minutesAndCount.values().stream().mapToLong(a -> a[0]).sum());
        long conflicts = audit.countByActionAndReferenceTimeGreaterThanEqualAndReferenceTimeLessThan(
                ReservationService.CONFLICT_ACTION, start, end);
        return new UtilizationReport(from, to, conflicts, total, usage);
    }

    private static double hours(long minutes) {
        return Math.round(minutes / 60.0 * 100.0) / 100.0;
    }
}
