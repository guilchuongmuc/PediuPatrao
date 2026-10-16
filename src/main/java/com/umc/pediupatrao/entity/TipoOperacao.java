package com.umc.pediupatrao.entity;

/** Tipos de evento registrados na auditoria. */
public enum TipoOperacao {

    INCLUSAO_PEDIDO("Inclusão de pedido"),
    ALTERACAO_PEDIDO("Alteração de pedido"),
    MUDANCA_STATUS_PEDIDO("Mudança de status do pedido"),
    DESCONTO_PEDIDO("Desconto em pedido"),
    CANCELAMENTO_PEDIDO("Cancelamento de pedido"),

    INCLUSAO_CLIENTE("Inclusão de cliente"),
    ALTERACAO_CLIENTE("Alteração de cliente"),
    EXCLUSAO_CLIENTE("Exclusão de cliente"),

    INCLUSAO_PRODUTO("Inclusão de produto"),
    ALTERACAO_PRODUTO("Alteração de produto"),
    EXCLUSAO_PRODUTO("Exclusão de produto"),

    INCLUSAO_USUARIO("Inclusão de usuário"),
    ALTERACAO_USUARIO("Alteração de usuário"),
    EXCLUSAO_USUARIO("Exclusão de usuário"),

    ACESSO_NEGADO("Tentativa de acesso não autorizado");

    private final String descricao;

    TipoOperacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
