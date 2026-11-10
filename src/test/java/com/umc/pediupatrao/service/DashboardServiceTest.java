package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroConsulta;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboardParams;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltroResponse;
import com.umc.pediupatrao.dto.dashboard.PedidosRecentesResponse;
import com.umc.pediupatrao.dto.dashboard.RankingProdutosResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoResponse;
import com.umc.pediupatrao.dto.dashboard.SerieVendasResponse;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidosResponse;
import com.umc.pediupatrao.dto.dashboard.VendasPorAtendimentoResponse;
import com.umc.pediupatrao.dto.dashboard.VendasPorCategoriaResponse;
import com.umc.pediupatrao.dto.dashboard.VendasPorHorarioResponse;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import com.umc.pediupatrao.repository.DashboardRepository;
import com.umc.pediupatrao.repository.DashboardRepository.Contagem;
import com.umc.pediupatrao.repository.DashboardRepository.ContagemStatus;
import com.umc.pediupatrao.repository.DashboardRepository.Horarios;
import com.umc.pediupatrao.repository.DashboardRepository.Intervalo;
import com.umc.pediupatrao.repository.DashboardRepository.PontoSerie;
import com.umc.pediupatrao.repository.DashboardRepository.Tempos;
import com.umc.pediupatrao.repository.DashboardRepository.Totais;
import com.umc.pediupatrao.repository.DashboardRepository.VendaAtendimento;
import com.umc.pediupatrao.repository.DashboardRepository.VendaCategoria;
import com.umc.pediupatrao.repository.DashboardRepository.VendaProduto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DashboardServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 10);

    private DashboardRepository repository;
    private DashboardService service;

    @BeforeEach
    void preparar() {
        repository = mock(DashboardRepository.class);
        Clock relogio = Clock.fixed(HOJE.atTime(20, 0).atZone(ZONA).toInstant(), ZONA);
        service = new DashboardService(repository, relogio);
    }

    @Test
    void periodoPadraoSaoOsUltimos30DiasAteHoje() {
        FiltroConsulta filtro = service.resolverFiltro(null);

        assertThat(filtro.inicio()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(filtro.fim()).isEqualTo(HOJE);
        assertThat(filtro.dias()).isEqualTo(30);
        assertThat(filtro.status()).isEmpty();
    }

    @Test
    void somenteInicioInformadoVaiAteHoje() {
        FiltroConsulta filtro = service.resolverFiltro(FiltroDashboardParams.periodo(LocalDate.of(2026, 11, 1), null));

        assertThat(filtro.fim()).isEqualTo(HOJE);
        assertThat(filtro.dias()).isEqualTo(10);
    }

    @Test
    void filtroNormalizaCategoriaEStatus() {
        FiltroConsulta filtro = service.resolverFiltro(new FiltroDashboardParams(null, null,
                List.of(StatusPedido.PRONTO, StatusPedido.PRONTO, StatusPedido.CANCELADO), TipoAtendimento.ENTREGA,
                "  Pizza  "));

        assertThat(filtro.categoria()).isEqualTo("Pizza");
        assertThat(filtro.status()).containsExactlyInAnyOrder(StatusPedido.PRONTO, StatusPedido.CANCELADO);
        assertThat(filtro.tipoAtendimento()).isEqualTo(TipoAtendimento.ENTREGA);
    }

    @Test
    void categoriaEmBrancoEhIgnorada() {
        FiltroConsulta filtro = service.resolverFiltro(new FiltroDashboardParams(null, null, null, null, "   "));

        assertThat(filtro.categoria()).isNull();
    }

    @Test
    void rejeitaInicioPosteriorAoFim() {
        assertThatThrownBy(() -> service.resolverFiltro(
                FiltroDashboardParams.periodo(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1))))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("data inicial")
                .extracting("parametro").isEqualTo("inicio");
    }

    @Test
    void rejeitaPeriodoMaiorQue366Dias() {
        assertThatThrownBy(() -> service.resolverFiltro(
                FiltroDashboardParams.periodo(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2))))
                .isInstanceOf(ParametroInvalidoException.class)
                .extracting("parametro").isEqualTo("fim");
    }

    @Test
    void aceitaPeriodoDeExatamente366Dias() {
        FiltroConsulta filtro = service.resolverFiltro(
                FiltroDashboardParams.periodo(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1)));

        assertThat(filtro.dias()).isEqualTo(366);
    }

    @Test
    void rejeitaCategoriaMuitoLonga() {
        assertThatThrownBy(() -> service.resolverFiltro(
                new FiltroDashboardParams(null, null, null, null, "x".repeat(61))))
                .isInstanceOf(ParametroInvalidoException.class)
                .extracting("parametro").isEqualTo("categoria");
    }

    @Test
    void resumoCalculaIndicadoresEComparaComPeriodoAnterior() {
        when(repository.totais(any())).thenReturn(
                new Totais(10, 2, dec("400.00"), dec("50.00"), dec("20.00"), 30),
                new Totais(8, 0, dec("320.00"), dec("0"), dec("0"), 20));

        ResumoResponse resumo = service.resumo(FiltroDashboardParams.periodo(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10)));

        assertThat(resumo.faturamento()).isEqualByComparingTo("400.00");
        assertThat(resumo.totalPedidos()).isEqualTo(10);
        assertThat(resumo.pedidosValidos()).isEqualTo(8);
        assertThat(resumo.pedidosCancelados()).isEqualTo(2);
        assertThat(resumo.taxaCancelamento()).isEqualByComparingTo("20.00");
        assertThat(resumo.ticketMedio()).isEqualByComparingTo("50.00");
        assertThat(resumo.valorCancelado()).isEqualByComparingTo("50.00");
        assertThat(resumo.descontosConcedidos()).isEqualByComparingTo("20.00");
        assertThat(resumo.itensVendidos()).isEqualTo(30);
        assertThat(resumo.comparacao().faturamento().variacaoPercentual()).isEqualByComparingTo("25.00");
        assertThat(resumo.comparacao().totalPedidos().variacaoPercentual()).isEqualByComparingTo("25.00");
        assertThat(resumo.comparacao().ticketMedio().anterior()).isEqualByComparingTo("40.00");
        assertThat(resumo.comparacao().ticketMedio().variacaoPercentual()).isEqualByComparingTo("25.00");
        assertThat(resumo.comparacao().pedidosCancelados().variacaoPercentual()).isNull();

        assertThat(resumo.periodoAnterior().inicio()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(resumo.periodoAnterior().fim()).isEqualTo(LocalDate.of(2026, 10, 31));
        ArgumentCaptor<FiltroConsulta> filtros = ArgumentCaptor.forClass(FiltroConsulta.class);
        verify(repository, org.mockito.Mockito.times(2)).totais(filtros.capture());
        assertThat(filtros.getAllValues().get(1).dias()).isEqualTo(10);
    }

    @Test
    void resumoSemPedidosRetornaZerosSemDivisaoPorZero() {
        when(repository.totais(any())).thenReturn(Totais.VAZIO);

        ResumoResponse resumo = service.resumo(null);

        assertThat(resumo.faturamento()).isEqualByComparingTo("0");
        assertThat(resumo.ticketMedio()).isEqualByComparingTo("0");
        assertThat(resumo.taxaCancelamento()).isEqualByComparingTo("0");
        assertThat(resumo.comparacao().faturamento().variacaoPercentual()).isNull();
    }

    @Test
    void serieDiariaPreencheDiasSemVendas() {
        when(repository.serie(any(), eq(Agrupamento.DIA))).thenReturn(List.of(
                new PontoSerie(LocalDate.of(2026, 11, 2), 3, 1, dec("100.00")),
                new PontoSerie(LocalDate.of(2026, 11, 4), 1, 0, dec("45.50"))));

        SerieVendasResponse serie = service.vendas(FiltroDashboardParams.periodo(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5)), null);

        assertThat(serie.agrupamento()).isEqualTo(Agrupamento.DIA);
        assertThat(serie.pontos()).extracting(SerieVendasResponse.Ponto::rotulo)
                .containsExactly("01/11", "02/11", "03/11", "04/11", "05/11");
        assertThat(serie.pontos()).extracting(SerieVendasResponse.Ponto::pedidos).containsExactly(0L, 3L, 0L, 1L, 0L);
        assertThat(serie.pontos().get(1).ticketMedio()).isEqualByComparingTo("50.00");
        assertThat(serie.pontos().get(2).faturamento()).isEqualByComparingTo("0");
    }

    @Test
    void serieSemanalComecaNaSegundaFeira() {
        when(repository.serie(any(), eq(Agrupamento.SEMANA))).thenReturn(List.of());

        SerieVendasResponse serie = service.vendas(FiltroDashboardParams.periodo(
                LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 20)), Agrupamento.SEMANA);

        assertThat(serie.pontos()).extracting(SerieVendasResponse.Ponto::inicio).containsExactly(
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 16));
    }

    @Test
    void serieMensalUsaRotuloDoMes() {
        when(repository.serie(any(), eq(Agrupamento.MES))).thenReturn(List.of(
                new PontoSerie(LocalDate.of(2026, 10, 1), 5, 0, dec("250.00"))));

        SerieVendasResponse serie = service.vendas(FiltroDashboardParams.periodo(
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 11, 10)), Agrupamento.MES);

        assertThat(serie.pontos()).extracting(SerieVendasResponse.Ponto::rotulo)
                .containsExactly("set/2026", "out/2026", "nov/2026");
        assertThat(serie.pontos().get(1).faturamento()).isEqualByComparingTo("250.00");
    }

    @Test
    void horariosCompletam24HorasECalculamMediaPorDiaDaSemana() {
        when(repository.horarios(any())).thenReturn(new Horarios(
                List.of(new Contagem(20, 12, dec("600.00")), new Contagem(12, 4, dec("150.00"))),
                List.of(new Contagem(5, 10, dec("500.00")), new Contagem(1, 6, dec("250.00")))));

        VendasPorHorarioResponse horarios = service.vendasPorHorario(FiltroDashboardParams.periodo(
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 15)));

        assertThat(horarios.porHora()).hasSize(24);
        assertThat(horarios.porHora().get(20).pedidos()).isEqualTo(12);
        assertThat(horarios.porHora().get(20).rotulo()).isEqualTo("20h");
        assertThat(horarios.horaPico()).isEqualTo(20);
        assertThat(horarios.porDiaSemana()).hasSize(7);
        VendasPorHorarioResponse.DiaSemana sexta = horarios.porDiaSemana().get(4);
        assertThat(sexta.nome()).isEqualTo("Sexta-feira");
        assertThat(sexta.ocorrencias()).isEqualTo(2);
        assertThat(sexta.mediaPedidos()).isEqualByComparingTo("5.0");
        assertThat(horarios.diaPico()).isEqualTo("Sexta-feira");
    }

    @Test
    void horariosSemPedidosNaoTemPico() {
        when(repository.horarios(any())).thenReturn(new Horarios(List.of(), List.of()));

        VendasPorHorarioResponse horarios = service.vendasPorHorario(null);

        assertThat(horarios.horaPico()).isNull();
        assertThat(horarios.diaPico()).isNull();
        assertThat(horarios.porHora()).allMatch(h -> h.pedidos() == 0);
    }

    @Test
    void situacaoAgrupaStatusLegadoEContaPedidosEmAndamento() {
        when(repository.contagemPorStatus(any())).thenReturn(List.of(
                new ContagemStatus("RECEBIDO", 2, dec("90.00")),
                new ContagemStatus("EM_PREPARO", 1, dec("45.00")),
                new ContagemStatus("EM_PREPARACAO", 1, dec("50.00")),
                new ContagemStatus("FINALIZADO", 4, dec("200.00")),
                new ContagemStatus("CANCELADO", 2, dec("80.00"))));
        when(repository.temposMedios(any())).thenReturn(new Tempos(1_800_000d, 2_700_000d, null));

        SituacaoPedidosResponse situacao = service.situacaoPedidos(null);

        assertThat(situacao.total()).isEqualTo(10);
        assertThat(situacao.emAndamento()).isEqualTo(4);
        assertThat(situacao.finalizados()).isEqualTo(4);
        assertThat(situacao.cancelados()).isEqualTo(2);
        assertThat(situacao.porStatus()).hasSize(StatusPedido.values().length);
        SituacaoPedidosResponse.QuantidadeStatus preparo = situacao.porStatus().stream()
                .filter(s -> s.status() == StatusPedido.EM_PREPARACAO).findFirst().orElseThrow();
        assertThat(preparo.quantidade()).isEqualTo(2);
        assertThat(preparo.valor()).isEqualByComparingTo("95.00");
        assertThat(preparo.percentual()).isEqualByComparingTo("20.00");
        assertThat(situacao.temposMedios().ateProntoMinutos()).isEqualByComparingTo("30.0");
        assertThat(situacao.temposMedios().ateSaidaMinutos()).isEqualByComparingTo("45.0");
        assertThat(situacao.temposMedios().ateFinalizacaoMinutos()).isNull();
    }

    @Test
    void rankingOrdenaPorQuantidadeLimitaECalculaParticipacaoSobreOTotal() {
        when(repository.vendasPorProduto(any())).thenReturn(List.of(
                new VendaProduto("p1", "Pizza Calabresa", "Pizza", 20, 15, dec("998.00")),
                new VendaProduto("p2", "Esfiha de Carne", "Esfiha", 50, 8, dec("325.00")),
                new VendaProduto("p3", "Refrigerante 2L", "Bebida", 30, 25, dec("360.00"))));

        RankingProdutosResponse ranking = service.produtosMaisVendidos(null, null, 2);

        assertThat(ranking.criterio()).isEqualTo(CriterioRanking.QUANTIDADE);
        assertThat(ranking.produtosVendidos()).isEqualTo(3);
        assertThat(ranking.produtos()).extracting(RankingProdutosResponse.Produto::nome)
                .containsExactly("Esfiha de Carne", "Refrigerante 2L");
        assertThat(ranking.produtos().get(0).posicao()).isEqualTo(1);
        assertThat(ranking.produtos().get(0).participacao()).isEqualByComparingTo("50.00");
    }

    @Test
    void rankingPorFaturamento() {
        when(repository.vendasPorProduto(any())).thenReturn(List.of(
                new VendaProduto("p1", "Pizza Calabresa", "Pizza", 20, 15, dec("998.00")),
                new VendaProduto("p2", "Esfiha de Carne", "Esfiha", 50, 8, dec("325.00"))));

        RankingProdutosResponse ranking = service.produtosMaisVendidos(null, CriterioRanking.FATURAMENTO, null);

        assertThat(ranking.limite()).isEqualTo(DashboardService.LIMITE_PADRAO);
        assertThat(ranking.produtos()).extracting(RankingProdutosResponse.Produto::nome)
                .containsExactly("Pizza Calabresa", "Esfiha de Carne");
    }

    @Test
    void rankingRejeitaLimiteForaDoIntervalo() {
        assertThatThrownBy(() -> service.produtosMaisVendidos(null, null, 0))
                .isInstanceOf(ParametroInvalidoException.class)
                .extracting("parametro").isEqualTo("limite");
        assertThatThrownBy(() -> service.produtosMaisVendidos(null, null, 51))
                .isInstanceOf(ParametroInvalidoException.class);
    }

    @Test
    void atendimentoIncluiTiposSemVendas() {
        when(repository.vendasPorAtendimento(any())).thenReturn(List.of(
                new VendaAtendimento("ENTREGA", 4, dec("200.00"))));

        VendasPorAtendimentoResponse atendimento = service.vendasPorAtendimento(null);

        assertThat(atendimento.tipos()).extracting(VendasPorAtendimentoResponse.Tipo::tipo)
                .containsExactly("ENTREGA", "RETIRADA");
        assertThat(atendimento.tipos().get(0).ticketMedio()).isEqualByComparingTo("50.00");
        assertThat(atendimento.tipos().get(0).participacaoPedidos()).isEqualByComparingTo("100.00");
        assertThat(atendimento.tipos().get(1).pedidos()).isZero();
    }

    @Test
    void atendimentoFiltradoMostraSomenteOTipoEscolhido() {
        when(repository.vendasPorAtendimento(any())).thenReturn(List.of(
                new VendaAtendimento("RETIRADA", 2, dec("90.00"))));

        VendasPorAtendimentoResponse atendimento = service.vendasPorAtendimento(
                new FiltroDashboardParams(null, null, null, TipoAtendimento.RETIRADA, null));

        assertThat(atendimento.tipos()).extracting(VendasPorAtendimentoResponse.Tipo::tipo).containsExactly("RETIRADA");
    }

    @Test
    void atendimentoNaoInformadoDePedidosAntigos() {
        when(repository.vendasPorAtendimento(any())).thenReturn(List.of(
                new VendaAtendimento("NAO_INFORMADO", 1, dec("30.00"))));

        VendasPorAtendimentoResponse atendimento = service.vendasPorAtendimento(null);

        assertThat(atendimento.tipos()).hasSize(3);
        assertThat(atendimento.tipos().get(2).descricao()).isEqualTo("Não informado");
    }

    @Test
    void categoriasOrdenadasPorFaturamentoComParticipacao() {
        when(repository.vendasPorCategoria(any())).thenReturn(List.of(
                new VendaCategoria("Bebida", 10, 8, dec("100.00")),
                new VendaCategoria("Pizza", 6, 5, dec("300.00"))));

        VendasPorCategoriaResponse categorias = service.vendasPorCategoria(null);

        assertThat(categorias.faturamentoTotal()).isEqualByComparingTo("400.00");
        assertThat(categorias.categorias()).extracting(VendasPorCategoriaResponse.Categoria::categoria)
                .containsExactly("Pizza", "Bebida");
        assertThat(categorias.categorias().get(0).participacao()).isEqualByComparingTo("75.00");
    }

    @Test
    void recentesCalculaTempoEmAbertoSomenteParaPedidosEmAndamento() {
        Pedido emPreparo = pedido(StatusPedido.EM_PREPARACAO, HOJE.atTime(19, 15));
        Pedido finalizado = pedido(StatusPedido.FINALIZADO, HOJE.atTime(18, 0));
        when(repository.pedidosRecentes(any(), eq(5))).thenReturn(List.of(emPreparo, finalizado));

        PedidosRecentesResponse recentes = service.pedidosRecentes(null, 5);

        assertThat(recentes.limite()).isEqualTo(5);
        assertThat(recentes.pedidos().get(0).minutosEmAberto()).isEqualTo(45);
        assertThat(recentes.pedidos().get(0).statusDescricao()).isEqualTo("Em preparação");
        assertThat(recentes.pedidos().get(1).minutosEmAberto()).isNull();
    }

    @Test
    void opcoesDeFiltroTrazemListasEPeriodoDisponivel() {
        when(repository.intervaloPedidos()).thenReturn(new Intervalo(
                LocalDateTime.of(2026, 8, 1, 19, 0), LocalDateTime.of(2026, 11, 10, 19, 30)));
        when(repository.categorias()).thenReturn(List.of("Bebida", "Pizza"));

        OpcoesFiltroResponse opcoes = service.opcoesDeFiltro();

        assertThat(opcoes.status()).hasSize(StatusPedido.values().length);
        assertThat(opcoes.tiposAtendimento()).extracting(OpcoesFiltroResponse.Opcao::valor)
                .containsExactly("ENTREGA", "RETIRADA");
        assertThat(opcoes.categorias()).containsExactly("Bebida", "Pizza");
        assertThat(opcoes.periodoPadrao().inicio()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(opcoes.primeiroPedido()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(opcoes.periodoMaximoDias()).isEqualTo(366);
    }

    @Test
    void periodoAnteriorTemMesmoTamanhoEMantemFiltros() {
        FiltroConsulta filtro = new FiltroConsulta(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7),
                Set.of(StatusPedido.FINALIZADO), TipoAtendimento.ENTREGA, "Pizza");

        FiltroConsulta anterior = filtro.periodoAnterior();

        assertThat(anterior.inicio()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(anterior.fim()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(anterior.status()).containsExactly(StatusPedido.FINALIZADO);
        assertThat(anterior.categoria()).isEqualTo("Pizza");
    }

    private static Pedido pedido(StatusPedido status, LocalDateTime entrada) {
        Pedido pedido = new Pedido();
        pedido.setId(status.name());
        pedido.setNumero(1L);
        pedido.setClienteNome("Cliente Teste");
        pedido.setStatus(status);
        pedido.setTipoAtendimento(TipoAtendimento.ENTREGA);
        pedido.setValorTotal(dec("49.9"));
        pedido.setDataHoraEntrada(entrada);
        return pedido;
    }

    private static BigDecimal dec(String valor) {
        return new BigDecimal(valor);
    }
}
