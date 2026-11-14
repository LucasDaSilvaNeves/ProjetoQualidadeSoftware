package com.example.alocacao.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Faixas de 15 minutos usadas nas travas de horário. Reservas devem começar e terminar em múltiplos
 * de 15 minutos; assim [10:00, 12:00) ocupa 8 faixas e [12:00, 14:00) ocupa outras 8, sem sobreposição.
 */
public final class Slots {

    public static final int SLOT_MINUTES = 15;
    public static final int MAX_SLOTS_PER_RESERVATION = 96; // 24 horas

    private Slots() {
    }

    public static boolean isAligned(LocalDateTime t) {
        return t.getSecond() == 0 && t.getNano() == 0 && t.getMinute() % SLOT_MINUTES == 0;
    }

    private static long epochMinutes(LocalDateTime t) {
        return t.toEpochSecond(ZoneOffset.UTC) / 60;
    }

    /** Primeira faixa tocada pelo intervalo. */
    public static long first(LocalDateTime start) {
        return Math.floorDiv(epochMinutes(start), SLOT_MINUTES);
    }

    /** Última faixa tocada pelo intervalo [start, end) (o fim é exclusivo). */
    public static long last(LocalDateTime end) {
        return Math.floorDiv(epochMinutes(end) - 1, SLOT_MINUTES);
    }

    public static List<Long> covering(LocalDateTime start, LocalDateTime end) {
        List<Long> slots = new ArrayList<>();
        for (long s = first(start); s <= last(end); s++) {
            slots.add(s);
        }
        return slots;
    }

    public static String lockId(String resourceId, long slot) {
        return resourceId + ":" + slot;
    }
}
