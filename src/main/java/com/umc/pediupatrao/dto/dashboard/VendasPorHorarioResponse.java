package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record VendasPorHorarioResponse(
        Periodo periodo,
        List<Hora> porHora,
        List<DiaSemana> porDiaSemana,
        Integer horaPico,
        String diaPico) {

    public record Hora(int hora, String rotulo, long pedidos, BigDecimal faturamento) {
    }

    public record DiaSemana(
            int dia,
            String nome,
            long ocorrencias,
            long pedidos,
            BigDecimal mediaPedidos,
            BigDecimal faturamento) {
    }
}
