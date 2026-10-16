package com.umc.pediupatrao.security;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Encerra as sessões ativas de um usuário quando seu perfil ou senha mudam (ou quando é excluído),
 * para que permissões antigas não continuem valendo até o próximo login.
 */
@Service
public class SessaoUsuarioService {

    private final SessionRegistry sessionRegistry;

    public SessaoUsuarioService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public int encerrarSessoes(String username) {
        int encerradas = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UserDetails usuario && usuario.getUsername().equals(username)) {
                for (SessionInformation sessao : sessionRegistry.getAllSessions(principal, false)) {
                    sessao.expireNow();
                    encerradas++;
                }
            }
        }
        return encerradas;
    }
}
