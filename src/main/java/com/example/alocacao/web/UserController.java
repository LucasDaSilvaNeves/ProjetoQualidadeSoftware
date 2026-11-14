package com.example.alocacao.web;

import com.example.alocacao.security.AppUserPrincipal;
import com.example.alocacao.service.UserService;
import com.example.alocacao.web.dto.Dtos.CreateUserRequest;
import com.example.alocacao.web.dto.Dtos.UserView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Usuários")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal AppUserPrincipal me) {
        return Map.of("id", me.toCurrentUser().id(),
                "username", me.getUsername(),
                "role", me.toCurrentUser().role());
    }

    @GetMapping("/users")
    public List<UserView> list() {
        return service.list();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody CreateUserRequest request) {
        return service.create(CurrentUserSupport.of(me), request);
    }
}
