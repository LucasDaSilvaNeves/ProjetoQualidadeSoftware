package com.example.alocacao.service;

/** Evento publicado a cada mudança relevante; o envio da notificação acontece após o commit. */
public record ReservationEvent(String reservationId, String type, String recipient, String message) {
}
