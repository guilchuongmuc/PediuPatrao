package com.umc.pediupatrao.dto.dashboard;

public enum Agrupamento {

    DIA("Dia"),
    SEMANA("Semana"),
    MES("Mês");

    private final String descricao;

    Agrupamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
