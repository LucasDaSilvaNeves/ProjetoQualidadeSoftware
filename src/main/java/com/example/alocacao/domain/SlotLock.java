package com.example.alocacao.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Trava de uma faixa de 15 minutos de um recurso. O {@code _id} é "recurso:faixa", portanto o MongoDB
 * (que garante unicidade de _id de forma atômica) impede que duas reservas ocupem a mesma faixa,
 * mesmo com requisições simultâneas ou várias instâncias da aplicação.
 */
@Document("slot_locks")
@CompoundIndex(name = "idx_slot_resource_slot", def = "{'resourceId': 1, 'slotIndex': 1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SlotLock {

    @Id
    private String id;

    @Indexed
    private String reservationId;

    private String resourceId;
    private long slotIndex;
}
