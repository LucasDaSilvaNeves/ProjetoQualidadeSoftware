package com.example.alocacao.domain;

/**
 * Máquina de estados da reserva.
 * Fluxo principal: SOLICITADA -> APROVADA -> EM_USO -> CONCLUIDA.
 * Alternativos: REJEITADA, CANCELADA e NAO_COMPARECEU.
 */
public enum ReservationStatus {
    SOLICITADA,
    APROVADA,
    EM_USO,
    CONCLUIDA,
    REJEITADA,
    CANCELADA,
    NAO_COMPARECEU;

    public boolean canTransitionTo(ReservationStatus target) {
        return switch (this) {
            case SOLICITADA -> target == APROVADA || target == REJEITADA || target == CANCELADA;
            // APROVADA -> SOLICITADA acontece quando um horário aprovado é alterado e exige nova aprovação
            case APROVADA -> target == EM_USO || target == CANCELADA
                    || target == NAO_COMPARECEU || target == SOLICITADA;
            case EM_USO -> target == CONCLUIDA;
            default -> false;
        };
    }

    /** Reservas nesses estados ocupam o horário do recurso. */
    public boolean isBlocking() {
        return this != REJEITADA && this != CANCELADA && this != NAO_COMPARECEU;
    }

    /** Reservas iniciadas (em uso ou concluídas) não podem ser apagadas. */
    public boolean isStarted() {
        return this == EM_USO || this == CONCLUIDA;
    }
}
