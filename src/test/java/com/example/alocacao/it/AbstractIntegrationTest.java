package com.example.alocacao.it;

import com.example.alocacao.domain.*;
import com.example.alocacao.repository.ResourceRepository;
import com.example.alocacao.repository.UserRepository;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "Senha#12345";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        Infra.register(registry);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected MongoTemplate mongo;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected ResourceRepository resources;
    @Autowired
    protected PasswordEncoder encoder;

    @BeforeEach
    void cleanDatabase() {
        // remove os documentos mas preserva os índices únicos criados na inicialização
        mongo.getCollectionNames().forEach(name -> mongo.remove(new Query(), name));
        Infra.WIREMOCK.resetAll();
    }

    protected AppUser user(String username, Role role) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setFullName("Usuário " + username);
        u.setEmail(username + "@escola.com");
        u.setRole(role);
        return users.save(u);
    }

    protected Resource resource(ResourceType type, String name, boolean restricted) {
        Resource r = new Resource();
        r.setType(type);
        r.setName(name);
        r.setRestricted(restricted);
        return resources.save(r);
    }

    /** Requisição já autenticada (HTTP Basic) como o usuário informado. */
    protected RequestSpecification as(String username) {
        return given().port(port).contentType(ContentType.JSON).auth().preemptive().basic(username, PASSWORD);
    }

    protected RequestSpecification anonymous() {
        return given().port(port).contentType(ContentType.JSON);
    }

    /** Horário futuro: dias à frente, na hora cheia informada. */
    protected static LocalDateTime at(int daysAhead, int hour) {
        return LocalDate.now().plusDays(daysAhead).atTime(hour, 0);
    }

    protected static Map<String, Object> reservationBody(List<String> resourceIds, LocalDateTime start, LocalDateTime end) {
        return Map.of("resourceIds", resourceIds, "startTime", start.toString(), "endTime", end.toString(),
                "purpose", "Aula de teste");
    }

    protected int createReservation(String username, List<String> resourceIds, LocalDateTime start, LocalDateTime end) {
        return as(username).body(reservationBody(resourceIds, start, end))
                .post("/api/reservations").then().extract().statusCode();
    }
}
