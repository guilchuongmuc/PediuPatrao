package com.umc.pediupatrao.dto;


public record ResultadoIntegridade(boolean integro, long registrosVerificados, Long sequenciaComProblema,
        String mensagem) {
}
