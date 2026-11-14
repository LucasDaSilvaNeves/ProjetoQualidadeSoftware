package com.example.alocacao.unit;

import com.example.alocacao.domain.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.example.alocacao.domain.ReservationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReservationStatusTest {

    /** Oráculo independente da implementação: tabela explícita de transições permitidas. */
    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED = Map.of(
            SOLICITADA, EnumSet.of(APROVADA, REJEITADA, CANCELADA),
            APROVADA, EnumSet.of(EM_USO, CANCELADA, NAO_COMPARECEU, SOLICITADA),
            EM_USO, EnumSet.of(CONCLUIDA),
            CONCLUIDA, EnumSet.noneOf(ReservationStatus.class),
            REJEITADA, EnumSet.noneOf(ReservationStatus.class),
            CANCELADA, EnumSet.noneOf(ReservationStatus.class),
            NAO_COMPARECEU, EnumSet.noneOf(ReservationStatus.class));

    static Stream<Arguments> allTransitions() {
        return Stream.of(values()).flatMap(from -> Stream.of(values())
                .map(to -> Arguments.of(from, to, ALLOWED.get(from).contains(to))));
    }

    @ParameterizedTest(name = "{0} -> {1} permitido? {2}")
    @MethodSource("allTransitions")
    void transitionTable(ReservationStatus from, ReservationStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"REJEITADA", "CANCELADA", "NAO_COMPARECEU"})
    void releasedStatesDoNotBlockTheResource(ReservationStatus status) {
        assertThat(status.isBlocking()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"SOLICITADA", "APROVADA", "EM_USO", "CONCLUIDA"})
    void activeStatesBlockTheResource(ReservationStatus status) {
        assertThat(status.isBlocking()).isTrue();
    }

    @Test
    void onlyInUseAndConcludedCountAsStarted() {
        assertThat(Stream.of(values()).filter(ReservationStatus::isStarted))
                .containsExactlyInAnyOrder(EM_USO, CONCLUIDA);
    }
}
