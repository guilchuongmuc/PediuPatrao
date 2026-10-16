package com.umc.pediupatrao.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;


@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroResponse(
        LocalDateTime timestamp,
        int status,
        String erro,
        String mensagem,
        String caminho,
        Map<String, String> campos) {

    public static ErroResponse de(int status, String erro, String mensagem, String caminho) {
        return new ErroResponse(LocalDateTime.now(), status, erro, mensagem, caminho, null);
    }
}
