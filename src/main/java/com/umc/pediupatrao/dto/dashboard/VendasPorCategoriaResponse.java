package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record VendasPorCategoriaResponse(Periodo periodo, BigDecimal faturamentoTotal, List<Categoria> categorias) {

    public record Categoria(
            String categoria,
            long quantidade,
            long pedidos,
            BigDecimal faturamento,
            BigDecimal participacao) {
    }
}
