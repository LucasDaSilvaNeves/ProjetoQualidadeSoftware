package com.example.alocacao.service;

import com.example.alocacao.domain.Notification;
import com.example.alocacao.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Notificações: sempre grava uma notificação simulada; se houver URL de webhook configurada,
 * também envia para a API externa. Falha no envio NUNCA desfaz a reserva (o evento só é publicado depois que a reserva foi gravada).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final Clock clock;
    private final String webhookUrl;
    private final RestClient restClient;

    public NotificationService(NotificationRepository repository, Clock clock,
                               @Value("${app.notification.webhook-url:}") String webhookUrl) {
        this.repository = repository;
        this.clock = clock;
        this.webhookUrl = webhookUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(3).toMillis());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @EventListener
    public void onReservationEvent(ReservationEvent event) {
        boolean useWebhook = webhookUrl != null && !webhookUrl.isBlank();
        boolean delivered = true;
        if (useWebhook) {
            delivered = sendWebhook(event);
        }

        Notification n = new Notification();
        n.setRecipient(event.recipient());
        n.setSubject(event.type());
        n.setMessage(event.message());
        n.setChannel(useWebhook ? "WEBHOOK" : "SIMULADO");
        n.setDelivered(delivered);
        n.setCreatedAt(LocalDateTime.now(clock));
        repository.save(n);
    }

    private boolean sendWebhook(ReservationEvent event) {
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "event", event.type(),
                            "reservationId", event.reservationId(),
                            "recipient", event.recipient(),
                            "message", event.message()))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Falha ao enviar notificação {} da reserva {}: {}",
                    event.type(), event.reservationId(), e.getClass().getSimpleName());
            return false;
        }
    }
}
