package com.umc.pediupatrao.entity;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Set;

/**
 * Estados do pedido e regras de transição do fluxo obrigatório:
 * RECEBIDO -> EM_PREPARACAO -> PRONTO -> SAIU_PARA_ENTREGA | RETIRADO -> FINALIZADO.
 * CANCELADO é excepcional e só é alcançado pela operação de cancelamento (GERENTE).
 */
public enum StatusPedido {

    RECEBIDO("Recebido", "bg-secondary"),
    EM_PREPARACAO("Em preparação", "bg-warning text-dark"),
    PRONTO("Pronto", "bg-info text-dark"),
    SAIU_PARA_ENTREGA("Saiu para entrega", "bg-primary"),
    RETIRADO("Retirado", "bg-primary"),
    FINALIZADO("Finalizado", "bg-success"),
    CANCELADO("Cancelado", "bg-danger");

    private final String descricao;
    private final String corBadge;

    StatusPedido(String descricao, String corBadge) {
        this.descricao = descricao;
        this.corBadge = corBadge;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getCorBadge() {
        return corBadge;
    }

    /** Status que representam a saída do pedido (entrega ou retirada). */
    public boolean isSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    /** Desconto e cancelamento só são permitidos antes da saída para entrega/retirada. */
    public boolean permiteDescontoOuCancelamento() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    /** Itens e dados do pedido só podem ser alterados antes de ir para a cozinha. */
    public boolean permiteEdicao() {
        return this == RECEBIDO;
    }

    /**
     * Próximos status permitidos no fluxo normal (não inclui CANCELADO).
     * Quando o tipo de atendimento não é conhecido (pedidos antigos), aceita as duas formas de saída.
     */
    public Set<StatusPedido> proximosNoFluxo(TipoAtendimento tipoAtendimento) {
        return switch (this) {
            case RECEBIDO -> EnumSet.of(EM_PREPARACAO);
            case EM_PREPARACAO -> EnumSet.of(PRONTO);
            case PRONTO -> {
                if (tipoAtendimento == TipoAtendimento.ENTREGA) {
                    yield EnumSet.of(SAIU_PARA_ENTREGA);
                }
                if (tipoAtendimento == TipoAtendimento.RETIRADA) {
                    yield EnumSet.of(RETIRADO);
                }
                yield EnumSet.of(SAIU_PARA_ENTREGA, RETIRADO);
            }
            case SAIU_PARA_ENTREGA, RETIRADO -> EnumSet.of(FINALIZADO);
            case FINALIZADO, CANCELADO -> EnumSet.noneOf(StatusPedido.class);
        };
    }

    public boolean podeAvancarPara(StatusPedido novo, TipoAtendimento tipoAtendimento) {
        return novo != null && proximosNoFluxo(tipoAtendimento).contains(novo);
    }

    /**
     * Aceita o nome do enum ou a descrição ("EM PREPARAÇÃO", "saiu para entrega"...).
     *
     * @throws IllegalArgumentException quando o valor não corresponde a nenhum status
     */
    public static StatusPedido parse(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Status não informado.");
        }
        String semAcento = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String normalizado = semAcento.toUpperCase().replaceAll("[\\s/-]+", "_");
        try {
            return StatusPedido.valueOf(normalizado);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Status inválido: " + valor);
        }
    }

    /** Converte status gravados pela versão anterior do sistema (texto livre). */
    public static StatusPedido fromLegado(String valor) {
        if (valor == null || valor.isBlank()) {
            return RECEBIDO;
        }
        String normalizado = valor.trim().toUpperCase();
        return switch (normalizado) {
            case "EM_PREPARO", "PREPARANDO" -> EM_PREPARACAO;
            case "ENTREGUE", "CONCLUIDO" -> FINALIZADO;
            case "PENDENTE", "NOVO", "ABERTO" -> RECEBIDO;
            default -> {
                try {
                    yield parse(normalizado);
                } catch (IllegalArgumentException e) {
                    yield RECEBIDO;
                }
            }
        };
    }
}
