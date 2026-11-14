package com.example.alocacao.repository;

import com.example.alocacao.domain.Reservation;
import com.example.alocacao.domain.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends MongoRepository<Reservation, String> {

    Page<Reservation> findByRequesterId(String requesterId, Pageable pageable);

    List<Reservation> findByStartTimeGreaterThanEqualAndStartTimeLessThanAndStatusIn(
            LocalDateTime from, LocalDateTime to, Collection<ReservationStatus> statuses);

    long countByItemsResourceIdAndEndTimeGreaterThanAndStatusIn(
            String resourceId, LocalDateTime now, Collection<ReservationStatus> statuses);
}
