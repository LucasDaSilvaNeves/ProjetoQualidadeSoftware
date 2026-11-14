package com.example.alocacao.it;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MongoDBContainer;

/**
 * Infraestrutura compartilhada pelos testes de integração: um MongoDB REAL em container
 * (Testcontainers) e um WireMock simulando a API externa de notificações.
 * Os dois sobem uma única vez por execução (padrão singleton).
 */
public final class Infra {

    public static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");
    public static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        MONGO.start();
        WIREMOCK.start();
    }

    private Infra() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("alocacao"));
        registry.add("app.notification.webhook-url", () -> WIREMOCK.baseUrl() + "/notify");
    }
}
