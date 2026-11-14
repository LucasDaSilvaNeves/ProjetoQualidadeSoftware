package com.example.alocacao.repository;

import com.example.alocacao.domain.MaintenanceWindow;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface MaintenanceWindowRepository extends MongoRepository<MaintenanceWindow, String> {

    List<MaintenanceWindow> findByResourceIdOrderByStartTime(String resourceId);

    long countByResourceIdAndStartTimeLessThanAndEndTimeGreaterThan(String resourceId, LocalDateTime end,
                                                                    LocalDateTime start);

    /** Quantos períodos de manutenção do recurso se sobrepõem ao intervalo [start, end). */
    default long countOverlaps(String resourceId, LocalDateTime start, LocalDateTime end) {
        return countByResourceIdAndStartTimeLessThanAndEndTimeGreaterThan(resourceId, end, start);
    }

    @Query("{ 'resourceId': { $in: ?0 }, 'startTime': { $lt: ?2 }, 'endTime': { $gt: ?1 } }")
    List<MaintenanceWindow> findOverlapping(Collection<String> resourceIds, LocalDateTime start, LocalDateTime end);
}
