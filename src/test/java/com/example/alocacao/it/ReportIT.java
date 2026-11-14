package com.example.alocacao.it;

import com.example.alocacao.domain.Resource;
import com.example.alocacao.domain.ResourceType;
import com.example.alocacao.domain.Role;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportIT extends AbstractIntegrationTest {

    @Test
    void utilizationReportShowsHoursPerResourceAndAvoidedConflicts() {
        user("ana", Role.SOLICITANTE);
        user("rui", Role.RESPONSAVEL);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);
        Resource projector = resource(ResourceType.MATERIAL, "Projetor 01", false);

        // Sala: 2h + 3h = 5h; projetor: 2h
        assertThat(createReservation("ana", List.of(room.getId(), projector.getId()), at(50, 8), at(50, 10))).isEqualTo(201);
        assertThat(createReservation("ana", List.of(room.getId()), at(50, 14), at(50, 17))).isEqualTo(201);
        // Tentativa conflitante: precisa contar como "conflito evitado" e NÃO como horas alocadas
        assertThat(createReservation("ana", List.of(room.getId()), at(50, 9), at(50, 11))).isEqualTo(409);

        LocalDate day = at(50, 0).toLocalDate();
        JsonPath report = as("rui").get("/api/reports/utilization?from=" + day + "&to=" + day)
                .then().statusCode(200).extract().jsonPath();

        assertThat(report.getLong("conflictsAvoided")).isEqualTo(1L);
        assertThat(report.getDouble("totalAllocatedHours")).isEqualTo(7.0);
        assertThat(report.getList("resources")).hasSize(2);
        assertThat(report.getString("resources[0].name")).isEqualTo("Sala 101");
        assertThat(report.getDouble("resources[0].allocatedHours")).isEqualTo(5.0);
        assertThat(report.getLong("resources[0].reservations")).isEqualTo(2L);
        assertThat(report.getDouble("resources[1].allocatedHours")).isEqualTo(2.0);
    }

    @Test
    void cancelledReservationsDoNotCountAsAllocatedHours() {
        user("ana", Role.SOLICITANTE);
        user("rui", Role.RESPONSAVEL);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);
        int id = as("ana").body(reservationBody(List.of(room.getId()), at(51, 8), at(51, 10)))
                .post("/api/reservations").then().statusCode(201).extract().path("id");
        as("ana").post("/api/reservations/" + id + "/cancel").then().statusCode(200);

        LocalDate day = at(51, 0).toLocalDate();
        assertThat(as("rui").get("/api/reports/utilization?from=" + day + "&to=" + day)
                .jsonPath().getDouble("totalAllocatedHours")).isZero();
    }

    @Test
    void invalidPeriodIsRejected() {
        user("rui", Role.RESPONSAVEL);
        as("rui").get("/api/reports/utilization?from=2030-02-10&to=2030-02-01").then().statusCode(422);
    }
}
