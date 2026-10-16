package com.umc.pediupatrao.security;

import com.umc.pediupatrao.service.AuditoriaService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

/**
 * Trata tentativas de acesso indevido (perfil sem permissão ou token CSRF inválido):
 * registra na auditoria, gera log de aviso e responde 403 (JSON para APIs, página de erro para telas).
 */
public class AcessoNegadoHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(AcessoNegadoHandler.class);

    private final AuditoriaService auditoriaService;

    public AcessoNegadoHandler(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        String motivo = accessDeniedException instanceof CsrfException
                ? "Token CSRF ausente ou inválido."
                : "O perfil " + UsuarioAutenticado.perfil() + " não tem permissão para esta operação.";
        String rota = request.getMethod() + " " + request.getRequestURI();

        log.warn("Acesso negado: usuario={} perfil={} rota={} motivo={}",
                UsuarioAutenticado.username(), UsuarioAutenticado.perfil(), rota, motivo);
        auditoriaService.registrarAcessoNegado(rota, motivo);

        if (response.isCommitted()) {
            return;
        }
        if (RespostaJson.isApi(request)) {
            RespostaJson.escrever(response, HttpServletResponse.SC_FORBIDDEN, "Acesso negado", motivo,
                    request.getRequestURI());
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }
}
