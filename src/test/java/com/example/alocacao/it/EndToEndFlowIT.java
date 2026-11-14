package com.example.alocacao.it;

import com.example.alocacao.domain.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * Teste ponta a ponta: percorre o fluxo completo de um semestre típico pelos três perfis,
 * usando apenas HTTP e um PostgreSQL real.
 */
class EndToEndFlowIT extends AbstractIntegrationTest {

    @Test
    void fullReservationLifecycleAcrossAllRoles() {
        user("adm", Role.ADMINISTRADOR);
        user("rui", Role.RESPONSAVEL);
        user("ana", Role.SOLICITANTE);
        user("bruno", Role.SOLICITANTE);

        // 1. Administrador cadastra recursos (um restrito) e agenda manutenção de outro
        String lab = id(as("adm").body(Map.of("type", "SALA", "name", "Lab Redes", "location", "Bloco C",
                "capacity", 25, "restricted", true)).post("/api/resources"));
        String projector = id(as("adm").body(Map.of("type", "MATERIAL", "name", "Projetor 7", "restricted", false))
                .post("/api/resources"));
        String prof = id(as("adm").body(Map.of("type", "PROFESSOR", "name", "Prof. Lima", "competencies", "redes, linux",
                "restricted", false)).post("/api/resources"));
        String broken = id(as("adm").body(Map.of("type", "SALA", "name", "Sala em reforma", "restricted", false))
                .post("/api/resources"));
        as("adm").body(Map.of("startTime", at(60, 0).toString(), "endTime", at(61, 0).toString(), "reason", "Reforma"))
                .post("/api/resources/" + broken + "/maintenance").then().statusCode(201);

        // 2. Solicitante pesquisa disponibilidade e solicita reserva com recurso restrito
        as("ana").get("/api/resources?type=SALA&minCapacity=20&availableFrom=" + at(60, 10) + "&availableTo=" + at(60, 12))
                .then().statusCode(200).body("name", contains("Lab Redes"));

        String reservation = as("ana").body(reservationBody(List.of(lab, projector, prof), at(60, 10), at(60, 12)))
                .post("/api/reservations").then().statusCode(201)
                .body("status", equalTo("SOLICITADA")).body("requiresApproval", equalTo(true))
                .extract().path("id");

        // 3. Outro solicitante tenta o mesmo professor no mesmo horário: conflito
        as("bruno").body(reservationBody(List.of(prof), at(60, 11), at(60, 13)))
                .post("/api/reservations").then().statusCode(409);

        // 4. Responsável aprova; o solicitante não consegue aprovar por conta própria
        as("ana").post("/api/reservations/" + reservation + "/approve").then().statusCode(403);
        as("rui").body(Map.of("note", "Aprovado para a aula de redes"))
                .post("/api/reservations/" + reservation + "/approve").then().statusCode(200)
                .body("status", equalTo("APROVADA"));

        // 5. Retirada e devolução do material
        as("rui").post("/api/reservations/" + reservation + "/checkout").then().statusCode(200)
                .body("status", equalTo("EM_USO")).body("checkedOutBy", equalTo("rui"));
        as("ana").delete("/api/reservations/" + reservation).then().statusCode(409);
        as("rui").post("/api/reservations/" + reservation + "/return").then().statusCode(200)
                .body("status", equalTo("CONCLUIDA")).body("returnedBy", equalTo("rui"));

        // 6. Histórico auditável completo, em ordem
        as("rui").get("/api/audit?entityType=RESERVATION&entityId=" + reservation).then().statusCode(200)
                .body("action", contains("CRIACAO", "APROVACAO", "RETIRADA", "DEVOLUCAO"))
                .body("actor", contains("ana", "rui", "rui", "rui"))
                .body("toStatus", contains("SOLICITADA", "APROVADA", "EM_USO", "CONCLUIDA"));

        // 7. Relatório reflete a carga horária e o conflito evitado
        String day = at(60, 0).toLocalDate().toString();
        as("rui").get("/api/reports/utilization?from=" + day + "&to=" + day).then().statusCode(200)
                .body("conflictsAvoided", equalTo(1))
                .body("totalAllocatedHours", equalTo(6.0f));

        // 8. Notificações simuladas foram registradas para o solicitante
        as("adm").get("/api/notifications").then().statusCode(200)
                .body("recipient", everyItem(anyOf(equalTo("ana@escola.com"), equalTo("bruno@escola.com"))))
                .body("size()", greaterThanOrEqualTo(4));
    }

    private static String id(io.restassured.response.Response response) {
        return response.then().statusCode(201).extract().path("id");
    }
}
