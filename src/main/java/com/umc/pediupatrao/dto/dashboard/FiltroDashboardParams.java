package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

public record FiltroDashboardParams(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
        List<StatusPedido> status,
        TipoAtendimento tipoAtendimento,
        String categoria) {

    public static FiltroDashboardParams vazio() {
        return new FiltroDashboardParams(null, null, null, null, null);
    }

    public static FiltroDashboardParams periodo(LocalDate inicio, LocalDate fim) {
        return new FiltroDashboardParams(inicio, fim, null, null, null);
    }
}
