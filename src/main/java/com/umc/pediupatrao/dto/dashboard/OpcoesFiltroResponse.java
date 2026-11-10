package com.umc.pediupatrao.dto.dashboard;

import java.time.LocalDate;
import java.util.List;

public record OpcoesFiltroResponse(
        List<Opcao> status,
        List<Opcao> tiposAtendimento,
        List<String> categorias,
        List<Opcao> agrupamentos,
        List<Opcao> criteriosRanking,
        Periodo periodoPadrao,
        LocalDate primeiroPedido,
        LocalDate ultimoPedido,
        int periodoMaximoDias,
        int limiteMaximo) {

    public record Opcao(String valor, String descricao) {
    }
}
