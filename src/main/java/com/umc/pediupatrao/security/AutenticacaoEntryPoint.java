package com.umc.pediupatrao.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/**
 * Requisição sem autenticação: APIs (/api/**) recebem 401 em JSON; páginas são redirecionadas ao login.
 * Não envia o cabeçalho WWW-Authenticate para o navegador não abrir a janela de login Basic.
 */
public class AutenticacaoEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint paginaLogin = new LoginUrlAuthenticationEntryPoint("/login");

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        if (RespostaJson.isApi(request)) {
            RespostaJson.escrever(response, HttpServletResponse.SC_UNAUTHORIZED, "Não autenticado",
                    "Autenticação necessária para acessar este recurso.", request.getRequestURI());
            return;
        }
        paginaLogin.commence(request, response, authException);
    }
}
