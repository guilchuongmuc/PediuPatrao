package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record RankingProdutosResponse(
        Periodo periodo,
        CriterioRanking criterio,
        int limite,
        long produtosVendidos,
        List<Produto> produtos) {

    public record Produto(
            int posicao,
            String produtoId,
            String nome,
            String categoria,
            long quantidade,
            long pedidos,
            BigDecimal faturamento,
            BigDecimal participacao) {
    }
}
