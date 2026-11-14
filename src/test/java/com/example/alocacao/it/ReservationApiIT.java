package com.example.alocacao.it;

import com.example.alocacao.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/** Testes de API em caixa-preta: só HTTP, sem tocar nas classes internas da aplicação. */
class ReservationApiIT extends AbstractIntegrationTest {

    // ------------------------------------------------------------ autenticação e autorização

    @Test
    void requestWithoutCredentialsIsRejected() {
        anonymous().get("/api/resources").then().statusCode(401)
                .header("WWW-Authenticate", nullValue());
    }

    @Test
    void wrongPasswordIsRejected() {
        user("ana", Role.SOLICITANTE);
        anonymous().auth().preemptive().basic("ana", "senha-errada").get("/api/me").then().statusCode(401);
    }

    @ParameterizedTest(name = "{0} em {1} {2} -> 403")
    @CsvSource({
            "SOLICITANTE, POST,   /api/resources",
            "RESPONSAVEL, POST,   /api/resources",
            "SOLICITANTE, DELETE, /api/resources/1",
            "SOLICITANTE, POST,   /api/users",
            "RESPONSAVEL, GET,    /api/users",
            "SOLICITANTE, GET,    /api/audit",
            "SOLICITANTE, GET,    /api/notifications",
            "RESPONSAVEL, GET,    /api/notifications",
            "SOLICITANTE, POST,   /api/reservations/000000000000000000000001/approve",
            "ADMINISTRADOR, POST, /api/reservations/000000000000000000000001/approve",
            "SOLICITANTE, POST,   /api/reservations/000000000000000000000001/checkout",
            "ADMINISTRADOR, POST, /api/reservations/000000000000000000000001/return",
            "RESPONSAVEL, POST,   /api/reservations"
    })
    void roleIsForbiddenFromOperation(Role role, String method, String path) {
        user("u", role);
        as("u").body("{}").request(method, path).then().statusCode(403);
    }

    @Test
    void reportRequiresStaffRole() {
        user("ana", Role.SOLICITANTE);
        as("ana").get("/api/reports/utilization?from=2030-01-01&to=2030-01-31").then().statusCode(403);
    }

    // ------------------------------------------------------------ criação e regras de horário

    @Test
    void unrestrictedReservationIsCreatedAndAutoApproved() {
        user("ana", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);

        as("ana").body(reservationBody(List.of(room.getId()), at(20, 10), at(20, 12)))
                .post("/api/reservations").then()
                .statusCode(201)
                .header("Location", containsString("/api/reservations/"))
                .body("status", equalTo("APROVADA"))
                .body("requiresApproval", equalTo(false));
    }

    @Test
    void restrictedResourceRequiresApproval() {
        user("ana", Role.SOLICITANTE);
        Resource lab = resource(ResourceType.SALA, "Lab Química", true);

        as("ana").body(reservationBody(List.of(lab.getId()), at(20, 10), at(20, 12)))
                .post("/api/reservations").then()
                .statusCode(201)
                .body("status", equalTo("SOLICITADA"))
                .body("requiresApproval", equalTo(true));
    }

    @ParameterizedTest(name = "término {1} h com início {0} h -> 422")
    @CsvSource({"10, 10", "10, 9"})
    void endMustBeAfterStart(int startHour, int endHour) {
        user("ana", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);

        as("ana").body(reservationBody(List.of(room.getId()), at(20, startHour), at(20, endHour)))
                .post("/api/reservations").then().statusCode(422)
                .body("detail", containsString("término"));
    }

    @ParameterizedTest(name = "minutos {0}/{1} fora da faixa de 15 min -> 422")
    @CsvSource({"10, 5", "7, 0"})
    void timesMustBeMultiplesOfFifteenMinutes(int startMinute, int endMinute) {
        user("ana", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);

        as("ana").body(reservationBody(List.of(room.getId()), at(20, 10).plusMinutes(startMinute),
                        at(20, 12).plusMinutes(endMinute)))
                .post("/api/reservations").then().statusCode(422)
                .body("detail", containsString("15 minutos"));
    }

    @Test
    void reservationLongerThan24HoursIsRejected() {
        user("ana", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);

        as("ana").body(reservationBody(List.of(room.getId()), at(20, 8), at(22, 8)))
                .post("/api/reservations").then().statusCode(422)
                .body("detail", containsString("24 horas"));
    }

    @Test
    void reservationInThePastIsRejected() {
        user("ana", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);

        as("ana").body(reservationBody(List.of(room.getId()), at(-2, 10), at(-2, 12)))
                .post("/api/reservations").then().statusCode(422);
    }

    @Test
    void missingFieldsReturnFieldErrors() {
        user("ana", Role.SOLICITANTE);

        as("ana").body(Map.of("resourceIds", List.of(), "purpose", ""))
                .post("/api/reservations").then().statusCode(400)
                .body("detail", equalTo("Dados inválidos"))
                .body("errors.resourceIds", notNullValue())
                .body("errors.startTime", notNullValue())
                .body("errors.endTime", notNullValue())
                .body("errors.purpose", notNullValue());
    }

    @Test
    void unknownResourceReturns404() {
        user("ana", Role.SOLICITANTE);

        as("ana").body(reservationBody(List.of("000000000000000000000000"), at(20, 10), at(20, 12)))
                .post("/api/reservations").then().statusCode(404);
    }

    // ------------------------------------------------------------ sobreposição

    @Test
    void overlappingReservationOfSameRoomIsRejected() {
        user("ana", Role.SOLICITANTE);
        user("bruno", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);

        as("ana").body(reservationBody(List.of(room.getId()), at(20, 10), at(20, 12)))
                .post("/api/reservations").then().statusCode(201);

        as("bruno").body(reservationBody(List.of(room.getId()), at(20, 11), at(20, 13)))
                .post("/api/reservations").then().statusCode(409)
                .body("detail", containsString("Sala 101"));
    }

    @Test
    void consecutiveReservationsAreAccepted() {
        user("ana", Role.SOLICITANTE);
        user("bruno", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);

        as("ana").body(reservationBody(List.of(room.getId()), at(20, 10), at(20, 12)))
                .post("/api/reservations").then().statusCode(201);
        as("bruno").body(reservationBody(List.of(room.getId()), at(20, 12), at(20, 14)))
                .post("/api/reservations").then().statusCode(201);
    }

    @Test
    void professorAgendaConflictIsDetectedEvenInADifferentRoom() {
        user("ana", Role.SOLICITANTE);
        Resource roomA = resource(ResourceType.SALA, "Sala A", false);
        Resource roomB = resource(ResourceType.SALA, "Sala B", false);
        Resource professor = resource(ResourceType.PROFESSOR, "Prof. Carlos", false);

        as("ana").body(reservationBody(List.of(roomA.getId(), professor.getId()), at(20, 10), at(20, 12)))
                .post("/api/reservations").then().statusCode(201);

        as("ana").body(reservationBody(List.of(roomB.getId(), professor.getId()), at(20, 11), at(20, 13)))
                .post("/api/reservations").then().statusCode(409)
                .body("detail", containsString("Prof. Carlos"));
    }

    @Test
    void cancelledReservationFreesTheSlot() {
        user("ana", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);

        String id = as("ana").body(reservationBody(List.of(room.getId()), at(20, 10), at(20, 12)))
                .post("/api/reservations").then().statusCode(201).extract().path("id");
        as("ana").post("/api/reservations/" + id + "/cancel").then().statusCode(200).body("status", equalTo("CANCELADA"));

        as("ana").body(reservationBody(List.of(room.getId()), at(20, 10), at(20, 12)))
                .post("/api/reservations").then().statusCode(201);
    }

    @Test
    void updatingToAnOccupiedSlotIsRejected() {
        user("ana", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);
        as("ana").body(reservationBody(List.of(room.getId()), at(20, 10), at(20, 12)))
                .post("/api/reservations").then().statusCode(201);
        String second = as("ana").body(reservationBody(List.of(room.getId()), at(20, 14), at(20, 16)))
                .post("/api/reservations").then().statusCode(201).extract().path("id");

        as("ana").body(Map.of("startTime", at(20, 11).toString(), "endTime", at(20, 13).toString(), "purpose", "Nova"))
                .put("/api/reservations/" + second).then().statusCode(409);
        as("ana").body(Map.of("startTime", at(20, 12).toString(), "endTime", at(20, 13).toString(), "purpose", "Nova"))
                .put("/api/reservations/" + second).then().statusCode(200).body("purpose", equalTo("Nova"));
    }

    // ------------------------------------------------------------ manutenção

    @Test
    void resourceUnderMaintenanceCannotBeReserved() {
        user("adm", Role.ADMINISTRADOR);
        user("ana", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);

        as("adm").body(Map.of("startTime", at(20, 8).toString(), "endTime", at(20, 18).toString(), "reason", "Pintura"))
                .post("/api/resources/" + room.getId() + "/maintenance").then().statusCode(201);

        as("ana").body(reservationBody(List.of(room.getId()), at(20, 10), at(20, 12)))
                .post("/api/reservations").then().statusCode(422)
                .body("detail", containsString("manutenção"));
        as("ana").body(reservationBody(List.of(room.getId()), at(21, 10), at(21, 12)))
                .post("/api/reservations").then().statusCode(201);
    }

    // ------------------------------------------------------------ aprovação e visibilidade

    @Test
    void onlyResponsavelApprovesRestrictedReservation() {
        user("ana", Role.SOLICITANTE);
        user("rui", Role.RESPONSAVEL);
        Resource lab = resource(ResourceType.SALA, "Lab Química", true);
        String id = as("ana").body(reservationBody(List.of(lab.getId()), at(20, 10), at(20, 12)))
                .post("/api/reservations").then().statusCode(201).extract().path("id");

        as("ana").post("/api/reservations/" + id + "/approve").then().statusCode(403);
        as("rui").post("/api/reservations/" + id + "/approve").then().statusCode(200).body("status", equalTo("APROVADA"));
    }

    @Test
    void requesterCannotSeeAnotherRequestersReservation() {
        user("ana", Role.SOLICITANTE);
        user("bruno", Role.SOLICITANTE);
        user("rui", Role.RESPONSAVEL);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);
        String id = as("ana").body(reservationBody(List.of(room.getId()), at(20, 10), at(20, 12)))
                .post("/api/reservations").then().statusCode(201).extract().path("id");

        as("bruno").get("/api/reservations/" + id).then().statusCode(403);
        as("bruno").get("/api/reservations").then().statusCode(200).body("$", hasSize(0));
        as("rui").get("/api/reservations/" + id).then().statusCode(200);
    }

    // ------------------------------------------------------------ exclusão de reservas iniciadas

    @Test
    void startedReservationCannotBeDeleted() {
        user("ana", Role.SOLICITANTE);
        user("rui", Role.RESPONSAVEL);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);
        String id = as("ana").body(reservationBody(List.of(room.getId()), at(20, 10), at(20, 12)))
                .post("/api/reservations").then().statusCode(201).extract().path("id");
        as("rui").post("/api/reservations/" + id + "/checkout").then().statusCode(200).body("status", equalTo("EM_USO"));

        as("ana").delete("/api/reservations/" + id).then().statusCode(409)
                .body("detail", containsString("iniciadas"));
        as("ana").post("/api/reservations/" + id + "/cancel").then().statusCode(422);
    }

    @Test
    void notStartedReservationCanBeDeleted() {
        user("ana", Role.SOLICITANTE);
        Resource room = resource(ResourceType.SALA, "Sala 101", false);
        String id = as("ana").body(reservationBody(List.of(room.getId()), at(20, 10), at(20, 12)))
                .post("/api/reservations").then().statusCode(201).extract().path("id");

        as("ana").delete("/api/reservations/" + id).then().statusCode(204);
        as("ana").get("/api/reservations/" + id).then().statusCode(404);
    }

    // ------------------------------------------------------------ pesquisa

    @Test
    void searchFiltersByTypeCapacityLocationCompetencyAndAvailability() {
        user("adm", Role.ADMINISTRADOR);
        user("ana", Role.SOLICITANTE);
        as("adm").body(Map.of("type", "SALA", "name", "Auditório", "location", "Bloco B", "capacity", 200, "restricted", false))
                .post("/api/resources").then().statusCode(201);
        as("adm").body(Map.of("type", "SALA", "name", "Sala 5", "location", "Bloco A", "capacity", 30, "restricted", false))
                .post("/api/resources").then().statusCode(201);
        as("adm").body(Map.of("type", "PROFESSOR", "name", "Profa. Marina", "competencies", "Banco de dados, SQL", "restricted", false))
                .post("/api/resources").then().statusCode(201);

        as("ana").get("/api/resources?type=SALA&minCapacity=100").then().statusCode(200)
                .body("name", contains("Auditório"));
        as("ana").get("/api/resources?location=bloco a").then().body("name", contains("Sala 5"));
        as("ana").get("/api/resources?competency=sql").then().body("name", contains("Profa. Marina"));

        String salaId = as("ana").get("/api/resources?location=bloco a").path("[0].id");
        as("ana").body(reservationBody(List.of(salaId), at(20, 10), at(20, 12)))
                .post("/api/reservations").then().statusCode(201);

        as("ana").get("/api/resources?type=SALA&availableFrom=" + at(20, 11) + "&availableTo=" + at(20, 13))
                .then().body("name", contains("Auditório"));
        as("ana").get("/api/resources?type=SALA&availableFrom=" + at(20, 12) + "&availableTo=" + at(20, 13))
                .then().body("name", containsInAnyOrder("Auditório", "Sala 5"));
    }

    @Test
    void searchByAvailabilityRequiresBothBoundaries() {
        user("ana", Role.SOLICITANTE);
        as("ana").get("/api/resources?availableFrom=" + at(20, 11)).then().statusCode(422);
    }

    // ------------------------------------------------------------ tratamento seguro de erros

    @Test
    void errorResponsesDoNotLeakInternalDetails() {
        user("ana", Role.SOLICITANTE);

        String body = as("ana").get("/api/reservations/abc").then().statusCode(404).extract().asString();
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("Exception").doesNotContain("at com.example").doesNotContain("org.springframework");

        as("ana").body("{ isto não é json").post("/api/reservations").then().statusCode(400);
        as("ana").get("/api/rota-inexistente").then().statusCode(anyOf(is(404), is(403)));
    }

    @Test
    void resourceAdministrationValidatesInput() {
        user("adm", Role.ADMINISTRADOR);

        as("adm").body(Map.of("type", "SALA", "name", "", "capacity", -5, "restricted", false))
                .post("/api/resources").then().statusCode(400)
                .body("errors.name", notNullValue())
                .body("errors.capacity", notNullValue());
    }

    @Test
    void userCreationValidatesPasswordAndRejectsDuplicates() {
        user("adm", Role.ADMINISTRADOR);
        Map<String, Object> ok = Map.of("username", "novo", "password", "Senha#12345", "fullName", "Novo",
                "email", "novo@escola.com", "role", "SOLICITANTE");

        as("adm").body(Map.of("username", "novo", "password", "curta", "fullName", "Novo",
                "email", "novo@escola.com", "role", "SOLICITANTE")).post("/api/users").then().statusCode(400)
                .body("errors.password", notNullValue());
        as("adm").body(ok).post("/api/users").then().statusCode(201).body("password", nullValue())
                .body("passwordHash", nullValue());
        as("adm").body(ok).post("/api/users").then().statusCode(409);
    }
}
