package com.example.alocacao.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Recurso dentro de uma reserva (documento embutido, com cópia dos dados usados nas telas e relatórios). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReservationItem {
    private String resourceId;
    private String resourceName;
    private ResourceType resourceType;
    private boolean restricted;
}
