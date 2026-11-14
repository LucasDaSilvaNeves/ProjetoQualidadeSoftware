package com.example.alocacao.config;

import com.example.alocacao.domain.AppUser;
import com.example.alocacao.domain.Role;
import com.example.alocacao.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Cria o primeiro administrador, somente se ADMIN_PASSWORD for informada (nenhuma senha fica no código). */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder,
                          @Value("${app.bootstrap.admin-username}") String username,
                          @Value("${app.bootstrap.admin-password}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank() || users.existsByUsername(username)) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setUsername(username);
        admin.setPasswordHash(encoder.encode(password));
        admin.setFullName("Administrador");
        admin.setEmail(username + "@localhost");
        admin.setRole(Role.ADMINISTRADOR);
        users.save(admin);
        log.info("Administrador inicial '{}' criado", username);
    }
}
