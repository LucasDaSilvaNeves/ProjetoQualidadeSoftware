package com.example.alocacao.it;

import com.example.alocacao.domain.Resource;
import com.example.alocacao.domain.ResourceType;
import com.example.alocacao.domain.Role;
import com.example.alocacao.domain.SlotLock;
import com.example.alocacao.repository.SlotLockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifica, direto no MongoDB, as garantias de unicidade em que a proteção contra dupla reserva se apoia. */
class MongoConstraintIT extends AbstractIntegrationTest {

    @Autowired
    SlotLockRepository locks;

    @Test
    void sameSlotOfSameResourceCannotBeLockedTwice() {
        locks.insert(new SlotLock("res1:100", "reservaA", "res1", 100));

        assertThatThrownBy(() -> locks.insert(new SlotLock("res1:100", "reservaB", "res1", 100)))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(locks.count()).isEqualTo(1);
    }

    @Test
    void adjacentSlotsAndOtherResourcesAreIndependent() {
        locks.insert(new SlotLock("res1:100", "reservaA", "res1", 100));
        locks.insert(new SlotLock("res1:101", "reservaB", "res1", 101));   // faixa seguinte
        locks.insert(new SlotLock("res2:100", "reservaC", "res2", 100));   // outro recurso, mesma faixa

        assertThat(locks.count()).isEqualTo(3);
    }

    @Test
    void releasingAReservationFreesItsSlots() {
        locks.insert(new SlotLock("res1:100", "reservaA", "res1", 100));
        locks.insert(new SlotLock("res1:101", "reservaA", "res1", 101));
        locks.deleteByReservationId("reservaA");

        locks.insert(new SlotLock("res1:100", "reservaB", "res1", 100));
        assertThat(locks.count()).isEqualTo(1);
    }

    @Test
    void usernameMustBeUnique() {
        user("ana", Role.SOLICITANTE);

        assertThatThrownBy(() -> user("ana", Role.SOLICITANTE)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void resourceNameMustBeUniquePerType() {
        resource(ResourceType.SALA, "Sala 1", false);
        resource(ResourceType.MATERIAL, "Sala 1", false); // outro tipo: permitido

        assertThatThrownBy(() -> resource(ResourceType.SALA, "Sala 1", false))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
