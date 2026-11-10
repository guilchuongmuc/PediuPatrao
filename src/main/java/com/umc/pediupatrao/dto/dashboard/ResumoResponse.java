package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

public record ResumoResponse(
        Periodo periodo,
        Periodo periodoAnterior,
        BigDecimal faturamento,
        long totalPedidos,
        long pedidosValidos,
        long pedidosCancelados,
        BigDecimal taxaCancelamento,
        BigDecimal valorCancelado,
        BigDecimal ticketMedio,
        BigDecimal descontosConcedidos,
        long itensVendidos,
        Comparacoes comparacao) {

    public record Comparacoes(
            Comparacao faturamento,
            Comparacao totalPedidos,
            Comparacao ticketMedio,
            Comparacao pedidosCancelados) {
    }

    public record Comparacao(BigDecimal anterior, BigDecimal variacaoPercentual) {
    }
}
