package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.ItemPedidoForm;
import com.umc.pediupatrao.dto.PedidoForm;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private SequenciaService sequenciaService;
    @Captor
    private ArgumentCaptor<List<AlteracaoCampo>> alteracoes;

    // 23/09/2026 14:35 no horário de Brasília
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-23T17:35:00Z"), ZoneId.of("America/Sao_Paulo"));
    private PedidoService service;

    @BeforeEach
    void configurar() {
        service = new PedidoService(pedidoRepository, clienteRepository, produtoRepository, auditoriaService,
                sequenciaService, clock);
        TestSeguranca.autenticar("atendente01", "ATENDENTE");

        when(sequenciaService.proximo("pedidos")).thenReturn(7L);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido pedido = inv.getArgument(0);
            if (pedido.getId() == null) {
                pedido.setId("p1");
            }
            return pedido;
        });
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("c1", true)));
        when(clienteRepository.findById("c2")).thenReturn(Optional.of(cliente("c2", false)));
        when(produtoRepository.findById("calabresa")).thenReturn(Optional.of(produto("calabresa", "Pizza Calabresa", 49.90, true)));
        when(produtoRepository.findById("refri")).thenReturn(Optional.of(produto("refri", "Refrigerante 2L", 12.00, true)));
        when(produtoRepository.findById("inativo")).thenReturn(Optional.of(produto("inativo", "Pizza Antiga", 30.00, false)));
    }

    @AfterEach
    void limpar() {
        TestSeguranca.limpar();
    }

    // ===== RF02: registro do pedido e cálculo no servidor =====

    @Test
    void criarPedidoCalculaValoresComPrecosCadastradosERegistraEntrada() {
        Pedido pedido = service.criarPedido(form("c1", TipoAtendimento.ENTREGA,
                new ItemPedidoForm("calabresa", 2), new ItemPedidoForm("refri", 1)));

        assertThat(pedido.getNumero()).isEqualTo(7L);
        assertThat(pedido.getItens()).hasSize(2);
        assertThat(pedido.getItens().get(0).getPrecoUnitario()).isEqualByComparingTo("49.90");
        assertThat(pedido.getItens().get(0).getSubtotal()).isEqualByComparingTo("99.80");
        assertThat(pedido.getSubtotal()).isEqualByComparingTo("111.80");
        assertThat(pedido.getValorDesconto()).isEqualByComparingTo("0.00");
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("111.80");
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.RECEBIDO);
        assertThat(pedido.getResponsavelEntrada()).isEqualTo("atendente01");
        assertThat(pedido.getDataHoraEntrada()).isEqualTo(LocalDateTime.of(2026, 9, 23, 14, 35));
        assertThat(pedido.getEnderecoEntrega()).contains("Rua das Flores");
        assertThat(pedido.getHistorico()).hasSize(1);
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_PEDIDO), eq("PEDIDO"), eq("p1"), anyString(),
                any(), isNull());
    }

    @Test
    void itensRepetidosDoMesmoProdutoSaoSomados() {
        Pedido pedido = service.criarPedido(form("c1", TipoAtendimento.RETIRADA,
                new ItemPedidoForm("calabresa", 1), new ItemPedidoForm("calabresa", 2)));

        assertThat(pedido.getItens()).hasSize(1);
        assertThat(pedido.getItens().get(0).getQuantidade()).isEqualTo(3);
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("149.70");
    }

    @Test
    void naoPermiteProdutoInativoQuantidadeInvalidaOuPedidoSemItens() {
        assertThatThrownBy(() -> service.criarPedido(form("c1", TipoAtendimento.RETIRADA, new ItemPedidoForm("inativo", 1))))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("inativo");
        assertThatThrownBy(() -> service.criarPedido(form("c1", TipoAtendimento.RETIRADA, new ItemPedidoForm("refri", 0))))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("quantidade");
        assertThatThrownBy(() -> service.criarPedido(form("c1", TipoAtendimento.RETIRADA, new ItemPedidoForm("refri", 51))))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("quantidade");
        assertThatThrownBy(() -> service.criarPedido(form("c1", TipoAtendimento.RETIRADA)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("ao menos um item");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void entregaExigeEnderecoDoCliente() {
        assertThatThrownBy(() -> service.criarPedido(form("c2", TipoAtendimento.ENTREGA, new ItemPedidoForm("refri", 1))))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("endereço");
    }

    // ===== RF03: fluxo de status, entrada e saída =====

    @Test
    void fluxoCompletoDeEntregaRegistraSaidaEFinalizacao() {
        Pedido pedido = pedidoNoStatus(StatusPedido.RECEBIDO, TipoAtendimento.ENTREGA);

        service.atualizarStatus("p1", "EM_PREPARACAO");
        service.atualizarStatus("p1", "PRONTO");
        TestSeguranca.autenticar("gerente01", "GERENTE");
        service.atualizarStatus("p1", "SAIU_PARA_ENTREGA");

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.SAIU_PARA_ENTREGA);
        assertThat(pedido.getResponsavelSaida()).isEqualTo("gerente01");
        assertThat(pedido.getDataHoraSaida()).isEqualTo(LocalDateTime.of(2026, 9, 23, 14, 35));

        service.atualizarStatus("p1", "FINALIZADO");
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.FINALIZADO);
        assertThat(pedido.getResponsavelFinalizacao()).isEqualTo("gerente01");
        assertThat(pedido.getHistorico()).hasSize(4);

        verify(auditoriaService, org.mockito.Mockito.times(4)).registrar(eq(TipoOperacao.MUDANCA_STATUS_PEDIDO),
                eq("PEDIDO"), eq("p1"), anyString(), alteracoes.capture(), isNull());
        assertThat(alteracoes.getAllValues().get(2))
                .anySatisfy(a -> {
                    assertThat(a.getCampo()).isEqualTo("responsavelSaida");
                    assertThat(a.getValorNovo()).isEqualTo("gerente01");
                });
    }

    @Test
    void naoPermiteFinalizarPedidoEmPreparacao() {
        pedidoNoStatus(StatusPedido.EM_PREPARACAO, TipoAtendimento.ENTREGA);

        assertThatThrownBy(() -> service.atualizarStatus("p1", "FINALIZADO"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Transição inválida")
                .hasMessageContaining("Pronto");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void saidaDeveCorresponderAoTipoDeAtendimento() {
        pedidoNoStatus(StatusPedido.PRONTO, TipoAtendimento.ENTREGA);
        assertThatThrownBy(() -> service.atualizarStatus("p1", "RETIRADO"))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("Saiu para entrega");
    }

    @Test
    void cancelamentoNaoPodeSerFeitoPeloFluxoDeStatus() {
        pedidoNoStatus(StatusPedido.RECEBIDO, TipoAtendimento.RETIRADA);
        assertThatThrownBy(() -> service.atualizarStatus("p1", "CANCELADO"))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("GERENTE");
    }

    @Test
    void pedidoCanceladoNaoAceitaNenhumaAlteracao() {
        pedidoNoStatus(StatusPedido.CANCELADO, TipoAtendimento.RETIRADA);
        TestSeguranca.autenticar("gerente01", "GERENTE");

        assertThatThrownBy(() -> service.atualizarStatus("p1", "EM_PREPARACAO"))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("cancelado");
        assertThatThrownBy(() -> service.aplicarDesconto("p1", BigDecimal.TEN, null))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("cancelado");
        assertThatThrownBy(() -> service.cancelarPedido("p1", "Cancelar de novo para teste"))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("cancelado");
        assertThatThrownBy(() -> service.editarPedido("p1", form("c1", TipoAtendimento.RETIRADA, new ItemPedidoForm("refri", 1))))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("cancelado");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void statusDesconhecidoERejeitado() {
        pedidoNoStatus(StatusPedido.RECEBIDO, TipoAtendimento.RETIRADA);
        assertThatThrownBy(() -> service.atualizarStatus("p1", "ENTREGUE_ONTEM"))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("Status inválido");
    }

    // ===== RF04: desconto =====

    @Test
    void descontoRecalculaTotalERegistraValoresAnterioresENovos() {
        Pedido pedido = pedidoNoStatus(StatusPedido.EM_PREPARACAO, TipoAtendimento.RETIRADA);
        TestSeguranca.autenticar("gerente01", "GERENTE");

        service.aplicarDesconto("p1", new BigDecimal("15"), "Cliente fidelidade");

        assertThat(pedido.getPercentualDesconto()).isEqualByComparingTo("15.00");
        assertThat(pedido.getValorDesconto()).isEqualByComparingTo("15.00");
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("85.00");
        assertThat(pedido.getResponsavelDesconto()).isEqualTo("gerente01");
        verify(auditoriaService).registrar(eq(TipoOperacao.DESCONTO_PEDIDO), eq("PEDIDO"), eq("p1"), anyString(),
                alteracoes.capture(), eq("Cliente fidelidade"));
        assertThat(alteracoes.getValue()).anySatisfy(a -> {
            assertThat(a.getCampo()).isEqualTo("valorTotal");
            assertThat(a.getValorAnterior()).isEqualTo("100.00");
            assertThat(a.getValorNovo()).isEqualTo("85.00");
        });
    }

    @Test
    void descontoLimitadoA20PorCentoENaoNegativo() {
        pedidoNoStatus(StatusPedido.RECEBIDO, TipoAtendimento.RETIRADA);
        TestSeguranca.autenticar("gerente01", "GERENTE");

        assertThatThrownBy(() -> service.aplicarDesconto("p1", new BigDecimal("20.01"), null))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("20%");
        assertThatThrownBy(() -> service.aplicarDesconto("p1", new BigDecimal("-1"), null))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("negativo");
        assertThatThrownBy(() -> service.aplicarDesconto("p1", new BigDecimal("10.555"), null))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("casas decimais");
    }

    @Test
    void descontoNaoAcumulaEUsaSempreOSubtotal() {
        Pedido pedido = pedidoNoStatus(StatusPedido.RECEBIDO, TipoAtendimento.RETIRADA);
        TestSeguranca.autenticar("gerente01", "GERENTE");

        service.aplicarDesconto("p1", new BigDecimal("10"), null);
        service.aplicarDesconto("p1", new BigDecimal("20"), null);

        assertThat(pedido.getValorTotal()).isEqualByComparingTo("80.00");
    }

    @Test
    void descontoBloqueadoAposSaida() {
        pedidoNoStatus(StatusPedido.SAIU_PARA_ENTREGA, TipoAtendimento.ENTREGA);
        TestSeguranca.autenticar("gerente01", "GERENTE");

        assertThatThrownBy(() -> service.aplicarDesconto("p1", BigDecimal.TEN, null))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("antes da saída");
    }

    // ===== RF04: cancelamento =====

    @Test
    void cancelamentoExigeJustificativa() {
        pedidoNoStatus(StatusPedido.RECEBIDO, TipoAtendimento.RETIRADA);
        TestSeguranca.autenticar("gerente01", "GERENTE");

        assertThatThrownBy(() -> service.cancelarPedido("p1", null))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("justificativa");
        assertThatThrownBy(() -> service.cancelarPedido("p1", "   curta "))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("justificativa");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void cancelamentoPreservaDadosHistoricosERegistraAuditoria() {
        Pedido pedido = pedidoNoStatus(StatusPedido.PRONTO, TipoAtendimento.RETIRADA);
        TestSeguranca.autenticar("gerente01", "GERENTE");

        service.cancelarPedido("p1", "Cliente desistiu da compra");

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(pedido.getResponsavelCancelamento()).isEqualTo("gerente01");
        assertThat(pedido.getJustificativaCancelamento()).isEqualTo("Cliente desistiu da compra");
        assertThat(pedido.getDataHoraCancelamento()).isEqualTo(LocalDateTime.of(2026, 9, 23, 14, 35));
        assertThat(pedido.getItens()).hasSize(1);
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("100.00");
        verify(auditoriaService).registrar(eq(TipoOperacao.CANCELAMENTO_PEDIDO), eq("PEDIDO"), eq("p1"), anyString(),
                alteracoes.capture(), eq("Cliente desistiu da compra"));
        assertThat(alteracoes.getValue().get(0).getValorAnterior()).isEqualTo("PRONTO");
        assertThat(alteracoes.getValue().get(0).getValorNovo()).isEqualTo("CANCELADO");
    }

    @Test
    void cancelamentoBloqueadoAposSaida() {
        pedidoNoStatus(StatusPedido.RETIRADO, TipoAtendimento.RETIRADA);
        TestSeguranca.autenticar("gerente01", "GERENTE");

        assertThatThrownBy(() -> service.cancelarPedido("p1", "Tentativa após a retirada"))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("após a saída");
    }

    // ===== Alteração de pedido =====

    @Test
    void edicaoPermitidaSomenteNoStatusRecebidoEAuditada() {
        pedidoNoStatus(StatusPedido.EM_PREPARACAO, TipoAtendimento.RETIRADA);
        assertThatThrownBy(() -> service.editarPedido("p1", form("c1", TipoAtendimento.RETIRADA, new ItemPedidoForm("refri", 2))))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("Recebido");

        Pedido pedido = pedidoNoStatus(StatusPedido.RECEBIDO, TipoAtendimento.RETIRADA);
        service.editarPedido("p1", form("c1", TipoAtendimento.RETIRADA, new ItemPedidoForm("refri", 2)));

        assertThat(pedido.getValorTotal()).isEqualByComparingTo("24.00");
        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_PEDIDO), eq("PEDIDO"), eq("p1"), anyString(),
                alteracoes.capture(), isNull());
        assertThat(alteracoes.getValue()).extracting(AlteracaoCampo::getCampo).contains("itens", "valorTotal");
    }

    // ===== Utilitários =====

    private Pedido pedidoNoStatus(StatusPedido status, TipoAtendimento tipo) {
        ItemPedido item = new ItemPedido();
        item.setProdutoId("x");
        item.setNomeProduto("Combo");
        item.setPrecoUnitario(new BigDecimal("100.00"));
        item.setQuantidade(1);
        item.setSubtotal(new BigDecimal("100.00"));

        Pedido pedido = new Pedido();
        pedido.setId("p1");
        pedido.setNumero(1L);
        pedido.setClienteId("c1");
        pedido.setClienteNome("Maria");
        pedido.setTipoAtendimento(tipo);
        pedido.setItens(new ArrayList<>(List.of(item)));
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        pedido.setStatus(status);
        PedidoService.recalcularValores(pedido);
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedido));
        return pedido;
    }

    private static PedidoForm form(String clienteId, TipoAtendimento tipo, ItemPedidoForm... itens) {
        PedidoForm form = new PedidoForm();
        form.setClienteId(clienteId);
        form.setTipoAtendimento(tipo);
        form.setItens(new ArrayList<>(List.of(itens)));
        return form;
    }

    private static Cliente cliente(String id, boolean comEndereco) {
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setNome("Cliente " + id);
        cliente.setTelefone("(11) 98765-4321");
        if (comEndereco) {
            cliente.setLogradouro("Rua das Flores");
            cliente.setNumero("10");
            cliente.setCidade("Mogi das Cruzes");
            cliente.setEstado("SP");
        }
        return cliente;
    }

    private static Produto produto(String id, String nome, double preco, boolean ativo) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setTipo("Pizza");
        produto.setPreco(preco);
        produto.setAtivo(ativo);
        return produto;
    }
}
