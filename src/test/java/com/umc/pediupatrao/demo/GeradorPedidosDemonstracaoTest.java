package com.umc.pediupatrao.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import com.umc.pediupatrao.service.PedidoService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class GeradorPedidosDemonstracaoTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 10, 21, 0);
    private static final LocalDate INICIO = AGORA.toLocalDate().minusDays(59);
    private static final Set<StatusPedido> COM_SAIDA = EnumSet.of(StatusPedido.SAIU_PARA_ENTREGA,
            StatusPedido.RETIRADO, StatusPedido.FINALIZADO);

    @Test
    void pedidosGeradosRespeitamAsRegrasDoSistema() {
        List<Pedido> pedidos = gerador(7).gerar(INICIO, AGORA);

        assertThat(pedidos).hasSizeGreaterThan(400);
        LocalDateTime anterior = LocalDateTime.MIN;
        for (Pedido pedido : pedidos) {
            assertThat(pedido.getDataHoraEntrada()).isAfterOrEqualTo(anterior).isBeforeOrEqualTo(AGORA);
            anterior = pedido.getDataHoraEntrada();

            BigDecimal soma = pedido.getItens().stream().map(ItemPedido::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(pedido.getItens()).isNotEmpty();
            assertThat(pedido.getSubtotal()).isEqualByComparingTo(soma);
            assertThat(pedido.getValorTotal()).isEqualByComparingTo(pedido.getSubtotal().subtract(pedido.getValorDesconto()));
            assertThat(pedido.getPercentualDesconto()).isBetween(BigDecimal.ZERO, PedidoService.DESCONTO_MAXIMO);

            List<HistoricoStatus> historico = pedido.getHistorico();
            assertThat(historico.get(0).getStatusNovo()).isEqualTo(StatusPedido.RECEBIDO);
            assertThat(historico.get(historico.size() - 1).getStatusNovo()).isEqualTo(pedido.getStatus());
            for (int i = 1; i < historico.size(); i++) {
                HistoricoStatus atual = historico.get(i);
                assertThat(atual.getDataHora()).isAfterOrEqualTo(historico.get(i - 1).getDataHora())
                        .isBeforeOrEqualTo(AGORA);
                if (atual.getStatusNovo() != StatusPedido.CANCELADO) {
                    assertThat(atual.getStatusAnterior().podeAvancarPara(atual.getStatusNovo(),
                            pedido.getTipoAtendimento())).isTrue();
                }
            }

            if (pedido.getTipoAtendimento() == TipoAtendimento.ENTREGA) {
                assertThat(pedido.getEnderecoEntrega()).isNotBlank();
            }
            if (COM_SAIDA.contains(pedido.getStatus())) {
                assertThat(pedido.getDataHoraSaida()).isNotNull();
                assertThat(pedido.getResponsavelSaida()).isNotBlank();
            }
            if (pedido.getStatus() == StatusPedido.FINALIZADO) {
                assertThat(pedido.getDataHoraFinalizacao()).isAfterOrEqualTo(pedido.getDataHoraSaida());
            }
            if (pedido.getStatus() == StatusPedido.CANCELADO) {
                assertThat(pedido.getResponsavelCancelamento()).isEqualTo("gerente");
                assertThat(pedido.getJustificativaCancelamento().length())
                        .isBetween(PedidoService.JUSTIFICATIVA_MINIMA, PedidoService.JUSTIFICATIVA_MAXIMA);
                assertThat(pedido.getDataHoraSaida()).isNull();
            }
        }
    }

    @Test
    void mesmaSementeGeraOsMesmosPedidos() {
        List<Pedido> primeira = gerador(42).gerar(INICIO, AGORA);
        List<Pedido> segunda = gerador(42).gerar(INICIO, AGORA);

        assertThat(primeira).hasSameSizeAs(segunda);
        assertThat(primeira).extracting(Pedido::getDataHoraEntrada)
                .containsExactlyElementsOf(segunda.stream().map(Pedido::getDataHoraEntrada).toList());
        assertThat(primeira).extracting(Pedido::getValorTotal)
                .containsExactlyElementsOf(segunda.stream().map(Pedido::getValorTotal).toList());
    }

    @Test
    void fimDeSemanaTemMaisMovimentoQueDiasUteis() {
        Map<DayOfWeek, Long> porDia = gerador(3).gerar(INICIO, AGORA).stream()
                .collect(Collectors.groupingBy(p -> p.getDataHoraEntrada().getDayOfWeek(), Collectors.counting()));

        assertThat(porDia.get(DayOfWeek.SATURDAY)).isGreaterThan(porDia.get(DayOfWeek.MONDAY) * 2);
        assertThat(porDia.get(DayOfWeek.FRIDAY)).isGreaterThan(porDia.get(DayOfWeek.TUESDAY));
    }

    @Test
    void diaAtualTemPedidosAindaEmAndamento() {
        List<Pedido> hoje = gerador(11).gerar(AGORA.toLocalDate(), AGORA);

        assertThat(hoje).isNotEmpty();
        assertThat(hoje).anyMatch(p -> p.getStatus() != StatusPedido.FINALIZADO
                && p.getStatus() != StatusPedido.CANCELADO);
    }

    @Test
    void geraCancelamentosEDescontosEmProporcaoRealista() {
        List<Pedido> pedidos = gerador(5).gerar(INICIO, AGORA);
        double cancelados = pedidos.stream().filter(p -> p.getStatus() == StatusPedido.CANCELADO).count();
        double comDesconto = pedidos.stream().filter(p -> p.getValorDesconto().signum() > 0).count();

        assertThat(cancelados / pedidos.size()).isBetween(0.02, 0.12);
        assertThat(comDesconto / pedidos.size()).isBetween(0.04, 0.18);
    }

    @Test
    void exigeProdutosEClientes() {
        assertThatThrownBy(() -> new GeradorPedidosDemonstracao(1, List.of(), clientes()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static GeradorPedidosDemonstracao gerador(long semente) {
        return new GeradorPedidosDemonstracao(semente, produtos(), clientes());
    }

    private static List<Produto> produtos() {
        List<Produto> produtos = new ArrayList<>();
        produtos.add(produto("1", "Pizza Calabresa", "Pizza", 49.90));
        produtos.add(produto("2", "Pizza Mussarela", "Pizza", 45.00));
        produtos.add(produto("3", "Esfiha de Carne", "Esfiha", 6.50));
        produtos.add(produto("4", "Refrigerante 2L", "Bebida", 12.00));
        produtos.add(produto("5", "Borda Recheada", "Adicional", 9.00));
        produtos.add(produto("6", "Batata Frita 400g", "Porção", 24.90));
        return produtos;
    }

    private static List<Cliente> clientes() {
        Cliente cliente = new Cliente();
        cliente.setId("c1");
        cliente.setNome("Cliente Demonstração");
        cliente.setLogradouro("Rua Ipiranga");
        cliente.setNumero("120");
        cliente.setBairro("Centro");
        cliente.setCidade("Mogi das Cruzes");
        cliente.setEstado("SP");
        return List.of(cliente);
    }

    private static Produto produto(String id, String nome, String tipo, double preco) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(preco);
        produto.setAtivo(true);
        return produto;
    }
}
