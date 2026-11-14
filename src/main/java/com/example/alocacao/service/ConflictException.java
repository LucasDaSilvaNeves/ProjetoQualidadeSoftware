package com.example.alocacao.service;

import java.util.List;

/** Conflito de estado ou de horário (HTTP 409). Guarda os recursos em conflito para a auditoria. */
public class ConflictException extends RuntimeException {

    private final transient List<String> resourceIds;

    public ConflictException(String message) {
        this(message, List.of());
    }

    public ConflictException(String message, List<String> resourceIds) {
        super(message);
        this.resourceIds = List.copyOf(resourceIds);
    }

    public List<String> getResourceIds() {
        return resourceIds;
    }
}
