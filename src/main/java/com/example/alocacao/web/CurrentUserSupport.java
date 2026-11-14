package com.example.alocacao.web;

import com.example.alocacao.security.AppUserPrincipal;
import com.example.alocacao.service.CurrentUser;

final class CurrentUserSupport {

    private CurrentUserSupport() {
    }

    static CurrentUser of(AppUserPrincipal principal) {
        return principal.toCurrentUser();
    }
}
