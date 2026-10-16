package com.umc.pediupatrao.entity;

/** Define se a saída do pedido será por entrega (SAIU_PARA_ENTREGA) ou retirada no balcão (RETIRADO). */
public enum TipoAtendimento {

    ENTREGA("Entrega"),
    RETIRADA("Retirada no balcão");

    private final String descricao;

    TipoAtendimento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
