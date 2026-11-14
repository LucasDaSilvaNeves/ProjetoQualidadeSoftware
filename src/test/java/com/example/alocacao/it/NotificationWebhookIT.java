package com.example.alocacao.it;

import com.example.alocacao.domain.Notification;
import com.example.alocacao.domain.Reservation;
import com.example.alocacao.domain.Resource;
import com.example.alocacao.domain.ResourceType;
import com.example.alocacao.domain.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/** Integração externa verificada com WireMock: a API de notificações é simulada por HTTP real. */
class NotificationWebhookIT extends AbstractIntegrationTest {

    @Test
    void webhookIsCalledForEachStateChange() {
        Infra.WIREMOCK.stubFor(post(urlEqualTo("/notify")).willReturn(aResponse().withStatus(200)));
        user("ana", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);

        as("ana").body(reservationBody(List.of(room.getId()), at(20, 10), at(20, 12)))
                .post("/api/reservations").then().statusCode(201);

        // SOLICITADA + APROVADA (automática)
        Infra.WIREMOCK.verify(2, postRequestedFor(urlEqualTo("/notify"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(matchingJsonPath("$.recipient", equalTo("ana@escola.com")))
                .withRequestBody(matchingJsonPath("$.reservationId")));
        assertThat(mongo.count(query(where("channel").is("WEBHOOK").and("delivered").is(true)), Notification.class))
                .isEqualTo(2L);
    }

    @Test
    void failureOfTheExternalApiDoesNotBreakTheReservation() {
        Infra.WIREMOCK.stubFor(post(urlEqualTo("/notify")).willReturn(aResponse().withStatus(500)));
        user("ana", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);

        as("ana").body(reservationBody(List.of(room.getId()), at(20, 10), at(20, 12)))
                .post("/api/reservations").then().statusCode(201);

        assertThat(mongo.count(query(new org.springframework.data.mongodb.core.query.Criteria()), Reservation.class))
                .isEqualTo(1L);
        assertThat(mongo.count(query(where("delivered").is(false)), Notification.class)).isEqualTo(2L);
    }

    @Test
    void unreachableExternalApiDoesNotBreakTheReservation() {
        Infra.WIREMOCK.stubFor(post(urlEqualTo("/notify"))
                .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));
        user("ana", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);

        as("ana").body(reservationBody(List.of(room.getId()), at(20, 10), at(20, 12)))
                .post("/api/reservations").then().statusCode(201);
    }

    @Test
    void rolledBackReservationSendsNoNotification() {
        Infra.WIREMOCK.stubFor(post(urlEqualTo("/notify")).willReturn(aResponse().withStatus(200)));
        user("ana", Role.SOLICITANTE);
        user("bruno", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);
        as("ana").body(reservationBody(List.of(room.getId()), at(20, 10), at(20, 12))).post("/api/reservations");
        Infra.WIREMOCK.resetRequests();

        as("bruno").body(reservationBody(List.of(room.getId()), at(20, 11), at(20, 13)))
                .post("/api/reservations").then().statusCode(409);

        Infra.WIREMOCK.verify(0, postRequestedFor(urlEqualTo("/notify")));
    }
}
