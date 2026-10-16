package com.umc.pediupatrao.controller;

import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrega o token CSRF da sessão para clientes de API (curl/PowerShell), que devem enviá-lo
 * no cabeçalho X-CSRF-TOKEN nas requisições POST/PUT/DELETE. Exige autenticação.
 */
@RestController
public class CsrfController {

    @GetMapping("/api/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
                "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName(),
                "token", token.getToken());
    }
}
