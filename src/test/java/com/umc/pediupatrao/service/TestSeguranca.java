package com.umc.pediupatrao.service;

import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Utilitário dos testes unitários: simula o usuário autenticado no SecurityContext. */
final class TestSeguranca {

    private TestSeguranca() {
    }

    static void autenticar(String usuario, String perfil) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuario, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + perfil))));
    }

    static void limpar() {
        SecurityContextHolder.clearContext();
    }
}
