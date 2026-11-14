package com.example.alocacao.service;

/** Violação de regra de negócio (HTTP 422): horário inválido, recurso em manutenção, transição inválida... */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
