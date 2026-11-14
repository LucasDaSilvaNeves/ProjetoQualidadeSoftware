package com.example.alocacao.service;

import com.example.alocacao.domain.Role;

/** Usuário autenticado, como a camada de serviço o enxerga. */
public record CurrentUser(String id, String username, Role role) {
}
