package com.umc.pediupatrao.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.umc.pediupatrao.dto.ErroResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/** Utilitário para respostas de erro JSON emitidas pelos filtros de segurança (fora do Spring MVC). */
final class RespostaJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private RespostaJson() {
    }

    static boolean isApi(HttpServletRequest request) {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        return uri.startsWith("/api/");
    }

    static void escrever(HttpServletResponse response, int status, String erro, String mensagem, String caminho)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        MAPPER.writeValue(response.getWriter(), ErroResponse.de(status, erro, mensagem, caminho));
    }
}
