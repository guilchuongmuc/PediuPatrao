package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidosRecentesResponse(int limite, List<PedidoRecente> pedidos) {

    public record PedidoRecente(
            String id,
            Long numero,
            String cliente,
            TipoAtendimento tipoAtendimento,
            StatusPedido status,
            String statusDescricao,
            BigDecimal valorTotal,
            LocalDateTime dataHoraEntrada,
            Long minutosEmAberto) {
    }
}
