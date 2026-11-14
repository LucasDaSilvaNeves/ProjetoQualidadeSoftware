package com.example.alocacao.config;

import com.example.alocacao.domain.*;
import com.example.alocacao.repository.ResourceRepository;
import com.example.alocacao.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Dados de demonstração. Só roda com o perfil "dev"; as senhas abaixo NÃO devem ser usadas em produção. */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final String DEV_PASSWORD = "Senha#12345";

    private final UserRepository users;
    private final ResourceRepository resources;
    private final PasswordEncoder encoder;

    public DevDataSeeder(UserRepository users, ResourceRepository resources, PasswordEncoder encoder) {
        this.users = users;
        this.resources = resources;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        user("admin", "Administrador", Role.ADMINISTRADOR);
        user("responsavel", "Responsável de Recursos", Role.RESPONSAVEL);
        user("professor", "Professor Solicitante", Role.SOLICITANTE);

        resource(ResourceType.SALA, "Sala 101", "Bloco A", 40, "", false);
        resource(ResourceType.SALA, "Laboratório de Química", "Bloco C", 20, "", true);
        resource(ResourceType.PROFESSOR, "Prof. Carlos", "", null, "java, qualidade de software", false);
        resource(ResourceType.PROFESSOR, "Profa. Marina", "", null, "banco de dados", true);
        resource(ResourceType.MATERIAL, "Projetor 01", "Almoxarifado", null, "", false);
    }

    private void user(String username, String fullName, Role role) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setFullName(fullName);
        u.setEmail(username + "@escola.local");
        u.setRole(role);
        u.setPasswordHash(encoder.encode(DEV_PASSWORD));
        users.save(u);
    }

    private void resource(ResourceType type, String name, String location, Integer capacity,
                          String competencies, boolean restricted) {
        Resource r = new Resource();
        r.setType(type);
        r.setName(name);
        r.setLocation(location);
        r.setCapacity(capacity);
        r.setCompetencies(competencies);
        r.setRestricted(restricted);
        resources.save(r);
    }
}
