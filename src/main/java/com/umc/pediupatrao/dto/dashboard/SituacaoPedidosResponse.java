package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;
import java.util.List;

public record SituacaoPedidosResponse(
        Periodo periodo,
        long total,
        long emAndamento,
        long finalizados,
        long cancelados,
        List<QuantidadeStatus> porStatus,
        TemposMedios temposMedios) {

    public record QuantidadeStatus(
            StatusPedido status,
            String descricao,
            long quantidade,
            BigDecimal percentual,
            BigDecimal valor) {
    }

    public record TemposMedios(
            BigDecimal ateProntoMinutos,
            BigDecimal ateSaidaMinutos,
            BigDecimal ateFinalizacaoMinutos) {
    }
}
