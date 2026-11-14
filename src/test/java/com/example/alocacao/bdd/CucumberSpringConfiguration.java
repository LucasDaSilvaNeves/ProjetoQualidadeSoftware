package com.example.alocacao.bdd;

import com.example.alocacao.it.Infra;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = com.example.alocacao.AlocacaoApplication.class)
public class CucumberSpringConfiguration {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        Infra.register(registry);
    }
}
