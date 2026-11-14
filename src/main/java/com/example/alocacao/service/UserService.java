package com.example.alocacao.service;

import com.example.alocacao.domain.AppUser;
import com.example.alocacao.repository.UserRepository;
import com.example.alocacao.web.dto.Dtos.CreateUserRequest;
import com.example.alocacao.web.dto.Dtos.UserView;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(UserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    public UserView create(CurrentUser actor, CreateUserRequest req) {
        if (users.existsByUsername(req.username())) {
            throw new ConflictException("Já existe um usuário com este nome de usuário");
        }
        AppUser u = new AppUser();
        u.setUsername(req.username());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setFullName(req.fullName());
        u.setEmail(req.email());
        u.setRole(req.role());
        users.save(u);
        audit.record("USER", u.getId(), "CRIACAO", null, null, actor.username(),
                "Perfil: " + u.getRole(), null);
        return UserView.from(u);
    }

    public List<UserView> list() {
        return users.findAll().stream().map(UserView::from).toList();
    }
}
