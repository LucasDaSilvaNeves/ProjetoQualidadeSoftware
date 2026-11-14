package com.example.alocacao.unit;

import com.example.alocacao.service.Slots;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlotsTest {

    private static LocalDateTime t(int hour, int minute) {
        return LocalDateTime.of(2030, 1, 2, hour, minute);
    }

    @ParameterizedTest(name = "{0}:{1} alinhado? {2}")
    @CsvSource({"10, 0, true", "10, 15, true", "10, 45, true", "10, 5, false", "10, 59, false"})
    void alignmentToFifteenMinutes(int hour, int minute, boolean expected) {
        assertThat(Slots.isAligned(t(hour, minute))).isEqualTo(expected);
    }

    @Test
    void secondsBreakAlignment() {
        assertThat(Slots.isAligned(t(10, 0).plusSeconds(1))).isFalse();
    }

    @ParameterizedTest(name = "{0}:{1} a {2}:{3} ocupa {4} faixas")
    @CsvSource({"10, 0, 12, 0, 8", "10, 0, 10, 15, 1", "10, 15, 11, 0, 3", "0, 0, 23, 45, 95"})
    void numberOfSlotsCovered(int h1, int m1, int h2, int m2, int expected) {
        assertThat(Slots.covering(t(h1, m1), t(h2, m2))).hasSize(expected);
    }

    @Test
    void consecutiveReservationsDoNotShareSlots() {
        Set<Long> morning = new HashSet<>(Slots.covering(t(10, 0), t(12, 0)));
        List<Long> afternoon = Slots.covering(t(12, 0), t(14, 0));

        assertThat(afternoon).doesNotContainAnyElementsOf(morning);
    }

    @Test
    void overlappingReservationsShareAtLeastOneSlot() {
        Set<Long> first = new HashSet<>(Slots.covering(t(10, 0), t(12, 0)));
        List<Long> second = Slots.covering(t(11, 45), t(13, 0));

        assertThat(second).anyMatch(first::contains);
    }

    @Test
    void lockIdCombinesResourceAndSlot() {
        assertThat(Slots.lockId("abc", 42)).isEqualTo("abc:42");
    }
}
