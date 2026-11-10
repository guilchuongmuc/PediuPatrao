package com.umc.pediupatrao.dto.dashboard;

public enum CriterioRanking {

    QUANTIDADE("Quantidade vendida"),
    FATURAMENTO("Faturamento");

    private final String descricao;

    CriterioRanking(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
