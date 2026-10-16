package com.umc.pediupatrao.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acesso ao usuário autenticado na requisição atual (para responsáveis e auditoria). */
public final class UsuarioAutenticado {

    public static final String SISTEMA = "sistema";

    private UsuarioAutenticado() {
    }

    public static boolean isAutenticado() {
        return autenticacao() != null;
    }

    public static String username() {
        Authentication auth = autenticacao();
        return auth == null ? SISTEMA : auth.getName();
    }

    /** Perfil (sem o prefixo ROLE_) do usuário autenticado, ou SISTEMA para processos internos. */
    public static String perfil() {
        Authentication auth = autenticacao();
        if (auth == null) {
            return SISTEMA.toUpperCase();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .findFirst()
                .orElse("SEM_PERFIL");
    }

    private static Authentication autenticacao() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth;
    }
}
