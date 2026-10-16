package com.umc.pediupatrao.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StatusPedidoTest {

    @ParameterizedTest(name = "{0} -> {1} ({2}) permitido")
    @CsvSource({
        "RECEBIDO, EM_PREPARACAO, ENTREGA",
        "EM_PREPARACAO, PRONTO, ENTREGA",
        "PRONTO, SAIU_PARA_ENTREGA, ENTREGA",
        "PRONTO, RETIRADO, RETIRADA",
        "SAIU_PARA_ENTREGA, FINALIZADO, ENTREGA",
        "RETIRADO, FINALIZADO, RETIRADA"
    })
    void fluxoObrigatorioPermitido(StatusPedido atual, StatusPedido novo, TipoAtendimento tipo) {
        assertThat(atual.podeAvancarPara(novo, tipo)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} ({2}) bloqueado")
    @CsvSource({
        "EM_PREPARACAO, FINALIZADO, ENTREGA",   // finalizar pedido em preparação
        "RECEBIDO, PRONTO, ENTREGA",            // pular etapa
        "RECEBIDO, FINALIZADO, RETIRADA",
        "PRONTO, RETIRADO, ENTREGA",            // saída incompatível com o tipo de atendimento
        "PRONTO, SAIU_PARA_ENTREGA, RETIRADA",
        "PRONTO, EM_PREPARACAO, ENTREGA",       // voltar etapa
        "FINALIZADO, RECEBIDO, ENTREGA",
        "CANCELADO, EM_PREPARACAO, ENTREGA",    // nada muda após o cancelamento
        "CANCELADO, FINALIZADO, RETIRADA",
        "RECEBIDO, CANCELADO, ENTREGA"          // cancelamento não faz parte do fluxo normal
    })
    void transicoesInvalidasBloqueadas(StatusPedido atual, StatusPedido novo, TipoAtendimento tipo) {
        assertThat(atual.podeAvancarPara(novo, tipo)).isFalse();
    }

    @Test
    void descontoECancelamentoSomenteAntesDaSaida() {
        assertThat(StatusPedido.RECEBIDO.permiteDescontoOuCancelamento()).isTrue();
        assertThat(StatusPedido.EM_PREPARACAO.permiteDescontoOuCancelamento()).isTrue();
        assertThat(StatusPedido.PRONTO.permiteDescontoOuCancelamento()).isTrue();
        assertThat(StatusPedido.SAIU_PARA_ENTREGA.permiteDescontoOuCancelamento()).isFalse();
        assertThat(StatusPedido.RETIRADO.permiteDescontoOuCancelamento()).isFalse();
        assertThat(StatusPedido.FINALIZADO.permiteDescontoOuCancelamento()).isFalse();
        assertThat(StatusPedido.CANCELADO.permiteDescontoOuCancelamento()).isFalse();
    }

    @Test
    void parseAceitaNomeEDescricao() {
        assertThat(StatusPedido.parse("EM PREPARAÇÃO")).isEqualTo(StatusPedido.EM_PREPARACAO);
        assertThat(StatusPedido.parse("saiu para entrega")).isEqualTo(StatusPedido.SAIU_PARA_ENTREGA);
        assertThat(StatusPedido.parse("RETIRADO")).isEqualTo(StatusPedido.RETIRADO);
        assertThatThrownBy(() -> StatusPedido.parse("ENTREGUE_ONTEM")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statusLegadosSaoConvertidos() {
        assertThat(StatusPedido.fromLegado("EM_PREPARO")).isEqualTo(StatusPedido.EM_PREPARACAO);
        assertThat(StatusPedido.fromLegado("ENTREGUE")).isEqualTo(StatusPedido.FINALIZADO);
        assertThat(StatusPedido.fromLegado("CANCELADO")).isEqualTo(StatusPedido.CANCELADO);
        assertThat(Perfil.fromLegado("USER")).isEqualTo(Perfil.ATENDENTE);
        assertThat(Perfil.fromLegado("ROLE_ADMIN")).isEqualTo(Perfil.ADMIN);
        assertThat(Perfil.fromLegado("SUPERUSUARIO")).isNull();
    }
}
