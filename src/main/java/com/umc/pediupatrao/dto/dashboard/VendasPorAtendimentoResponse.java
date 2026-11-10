package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record VendasPorAtendimentoResponse(Periodo periodo, List<Tipo> tipos) {

    public record Tipo(
            String tipo,
            String descricao,
            long pedidos,
            BigDecimal faturamento,
            BigDecimal ticketMedio,
            BigDecimal participacaoPedidos,
            BigDecimal participacaoFaturamento) {
    }
}
