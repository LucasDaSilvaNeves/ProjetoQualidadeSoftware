package com.example.alocacao.repository;

import com.example.alocacao.domain.SlotLock;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;

public interface SlotLockRepository extends MongoRepository<SlotLock, String> {

    @Query("{ 'resourceId': { $in: ?0 }, 'slotIndex': { $gte: ?1, $lte: ?2 } }")
    List<SlotLock> findInRange(Collection<String> resourceIds, long firstSlot, long lastSlot);

    void deleteByReservationId(String reservationId);

    long countByReservationId(String reservationId);
}
