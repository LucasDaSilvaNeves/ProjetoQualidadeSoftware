package com.example.alocacao.bdd;

import com.example.alocacao.domain.*;
import com.example.alocacao.it.Infra;
import com.example.alocacao.repository.ResourceRepository;
import com.example.alocacao.repository.UserRepository;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class ReservationSteps {

    private static final String PASSWORD = "Senha#12345";

    @LocalServerPort
    int port;
    @Autowired
    MongoTemplate mongo;
    @Autowired
    UserRepository users;
    @Autowired
    ResourceRepository resources;
    @Autowired
    PasswordEncoder encoder;

    private Response lastResponse;

    @Before
    public void cleanDatabase() {
        mongo.getCollectionNames().forEach(name -> mongo.remove(new Query(), name));
        Infra.WIREMOCK.resetAll();
        lastResponse = null;
    }

    @Dado("que existe a sala {string} sem restrição")
    public void existeSala(String name) {
        saveResource(ResourceType.SALA, name, false);
    }

    @Dado("que existe o laboratório {string} restrito")
    public void existeLaboratorioRestrito(String name) {
        saveResource(ResourceType.SALA, name, true);
    }

    @Dado("que existe o solicitante {string}")
    public void existeSolicitante(String username) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setFullName(username);
        u.setEmail(username + "@escola.com");
        u.setRole(Role.SOLICITANTE);
        users.save(u);
    }

    @Dado("que {string} reservou {string} de {int}h às {int}h")
    public void reservou(String username, String resource, int from, int to) {
        assertThat(reserve(username, resource, from, to).statusCode()).isEqualTo(201);
    }

    @Quando("{string} tenta reservar {string} de {int}h às {int}h")
    public void tentaReservar(String username, String resource, int from, int to) {
        lastResponse = reserve(username, resource, from, to);
    }

    @Entao("a resposta deve ser {int}")
    public void respostaDeveSer(int status) {
        assertThat(lastResponse.statusCode()).isEqualTo(status);
    }

    @Entao("a mensagem deve conter {string}")
    public void mensagemDeveConter(String text) {
        assertThat(lastResponse.jsonPath().getString("detail")).contains(text);
    }

    @Entao("o status da reserva deve ser {string}")
    public void statusDaReserva(String status) {
        assertThat(lastResponse.jsonPath().getString("status")).isEqualTo(status);
    }

    @Entao("a sala {string} deve ter apenas {int} reserva ativa")
    public void salaComUmaReserva(String resource, int expected) {
        long count = mongo.count(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("items.resourceName").is(resource)
                        .and("status").in(java.util.List.of("SOLICITADA", "APROVADA", "EM_USO", "CONCLUIDA"))),
                "reservations");
        assertThat(count).isEqualTo((long) expected);
    }

    private Response reserve(String username, String resourceName, int fromHour, int toHour) {
        Resource resource = resources.findAll().stream()
                .filter(r -> r.getName().equals(resourceName)).findFirst().orElseThrow();
        LocalDate day = LocalDate.now().plusDays(60);
        LocalDateTime start = day.atTime(fromHour, 0);
        LocalDateTime end = day.atTime(toHour, 0);
        return given().port(port).contentType("application/json")
                .auth().preemptive().basic(username, PASSWORD)
                .body(Map.of("resourceIds", List.of(resource.getId()), "startTime", start.toString(),
                        "endTime", end.toString(), "purpose", "Cenário BDD"))
                .post("/api/reservations");
    }

    private void saveResource(ResourceType type, String name, boolean restricted) {
        Resource r = new Resource();
        r.setType(type);
        r.setName(name);
        r.setRestricted(restricted);
        resources.save(r);
    }
}
