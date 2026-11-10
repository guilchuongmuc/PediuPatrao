package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SerieVendasResponse(Periodo periodo, Agrupamento agrupamento, List<Ponto> pontos) {

    public record Ponto(
            LocalDate inicio,
            LocalDate fim,
            String rotulo,
            boolean parcial,
            long pedidos,
            long cancelados,
            BigDecimal faturamento,
            BigDecimal ticketMedio) {
    }
}
