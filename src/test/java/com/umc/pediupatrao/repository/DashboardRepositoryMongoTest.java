package com.umc.pediupatrao.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.umc.pediupatrao.config.MongoConfig;
import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.FiltroConsulta;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import com.umc.pediupatrao.repository.DashboardRepository.Contagem;
import com.umc.pediupatrao.repository.DashboardRepository.ContagemStatus;
import com.umc.pediupatrao.repository.DashboardRepository.PontoSerie;
import com.umc.pediupatrao.repository.DashboardRepository.Totais;
import com.umc.pediupatrao.repository.DashboardRepository.VendaAtendimento;
import com.umc.pediupatrao.repository.DashboardRepository.VendaCategoria;
import com.umc.pediupatrao.repository.DashboardRepository.VendaProduto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

@DataMongoTest
@Import({DashboardRepository.class, MongoConfig.class})
@TestPropertySource(properties = "spring.data.mongodb.database=pediupatrao_teste_dashboard")
class DashboardRepositoryMongoTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 3, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 3, 10);

    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private DashboardRepository repository;

    @BeforeAll
    static void exigirMongoLocal() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 27017), 700);
        } catch (Exception e) {
            assumeTrue(false, "MongoDB local indisponível em 127.0.0.1:27017");
        }
    }

    @BeforeEach
    void popularBanco() {
        mongoTemplate.getDb().drop();
        mongoTemplate.insert(produto("Pizza Calabresa", "Pizza"));
        mongoTemplate.insert(produto("Água 500ml", "Bebida"));
        mongoTemplate.insert(produto("Petit Gateau", "Sobremesa"));

        List<Pedido> pedidos = new ArrayList<>();
        Pedido p1 = pedido(1, LocalDateTime.of(2026, 3, 2, 19, 30), TipoAtendimento.ENTREGA, StatusPedido.FINALIZADO,
                "10.00", item("calabresa", "Pizza Calabresa", "Pizza", "49.90", 2),
                item("refri", "Refrigerante 2L", "Bebida", "12.00", 1));
        concluir(p1, 30, 40, 70);
        pedidos.add(p1);

        Pedido p2 = pedido(2, LocalDateTime.of(2026, 3, 2, 21, 0), TipoAtendimento.RETIRADA, StatusPedido.FINALIZADO,
                "0", item("mussarela", "Pizza Mussarela", "Pizza", "45.00", 1));
        concluir(p2, 20, 30, 30);
        pedidos.add(p2);

        Pedido p3 = pedido(3, LocalDateTime.of(2026, 3, 3, 20, 15), TipoAtendimento.ENTREGA, StatusPedido.CANCELADO,
                "0", item("calabresa", "Pizza Calabresa", "Pizza", "49.90", 1));
        p3.registrarHistorico(historico(StatusPedido.PRONTO, p3.getDataHoraEntrada().plusMinutes(5)));
        pedidos.add(p3);

        Pedido p4 = pedido(4, LocalDateTime.of(2026, 3, 8, 12, 5), TipoAtendimento.ENTREGA,
                StatusPedido.SAIU_PARA_ENTREGA, "0", item("esfiha", "Esfiha de Carne", "Esfiha", "6.50", 10),
                item("refri", "Refrigerante 2L", "Bebida", "12.00", 1));
        p4.setDataHoraSaida(p4.getDataHoraEntrada().plusMinutes(40));
        pedidos.add(p4);

        pedidos.add(pedido(5, LocalDateTime.of(2026, 3, 9, 23, 30), TipoAtendimento.RETIRADA, StatusPedido.RECEBIDO,
                "0", item("portuguesa", "Pizza Portuguesa", "Pizza", "54.90", 1)));

        pedidos.add(pedido(6, LocalDateTime.of(2026, 2, 27, 20, 0), TipoAtendimento.ENTREGA, StatusPedido.FINALIZADO,
                "0", item("calabresa", "Pizza Calabresa", "Pizza", "40.00", 2)));

        pedidos.add(pedido(7, LocalDateTime.of(2026, 3, 11, 0, 0), TipoAtendimento.ENTREGA, StatusPedido.FINALIZADO,
                "0", item("calabresa", "Pizza Calabresa", "Pizza", "49.90", 1)));

        mongoTemplate.insertAll(pedidos);
    }

    @Test
    void totaisSeparamFaturamentoDosCancelamentos() {
        Totais totais = repository.totais(FiltroConsulta.periodo(INICIO, FIM));

        assertThat(totais.pedidos()).isEqualTo(5);
        assertThat(totais.cancelados()).isEqualTo(1);
        assertThat(totais.faturamento()).isEqualByComparingTo("277.52");
        assertThat(totais.valorCancelado()).isEqualByComparingTo("49.90");
        assertThat(totais.descontos()).isEqualByComparingTo("11.18");
        assertThat(totais.itensVendidos()).isEqualTo(16);
    }

    @Test
    void periodoSemPedidosRetornaTotaisVazios() {
        Totais totais = repository.totais(FiltroConsulta.periodo(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)));

        assertThat(totais).isEqualTo(Totais.VAZIO);
    }

    @Test
    void periodoAnteriorConsideraOutroIntervalo() {
        Totais anterior = repository.totais(FiltroConsulta.periodo(INICIO, FIM).periodoAnterior());

        assertThat(anterior.pedidos()).isEqualTo(1);
        assertThat(anterior.faturamento()).isEqualByComparingTo("80.00");
    }

    @Test
    void serieDiariaRespeitaOFusoDaPizzaria() {
        Map<LocalDate, PontoSerie> serie = porData(repository.serie(FiltroConsulta.periodo(INICIO, FIM),
                Agrupamento.DIA));

        assertThat(serie).containsOnlyKeys(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 3),
                LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 9));
        assertThat(serie.get(LocalDate.of(2026, 3, 2)).pedidos()).isEqualTo(2);
        assertThat(serie.get(LocalDate.of(2026, 3, 2)).faturamento()).isEqualByComparingTo("145.62");
        assertThat(serie.get(LocalDate.of(2026, 3, 3)).cancelados()).isEqualTo(1);
        assertThat(serie.get(LocalDate.of(2026, 3, 3)).faturamento()).isEqualByComparingTo("0");
        assertThat(serie.get(LocalDate.of(2026, 3, 9)).faturamento()).isEqualByComparingTo("54.90");
    }

    @Test
    void serieSemanalAgrupaPelaSegundaFeira() {
        Map<LocalDate, PontoSerie> serie = porData(repository.serie(FiltroConsulta.periodo(INICIO, FIM),
                Agrupamento.SEMANA));

        assertThat(serie).containsOnlyKeys(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 9));
        assertThat(serie.get(LocalDate.of(2026, 3, 2)).pedidos()).isEqualTo(4);
    }

    @Test
    void serieMensalAgrupaPeloPrimeiroDia() {
        List<PontoSerie> serie = repository.serie(FiltroConsulta.periodo(LocalDate.of(2026, 2, 1), FIM),
                Agrupamento.MES);

        assertThat(serie).extracting(PontoSerie::inicio)
                .containsExactly(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
    }

    @Test
    void horariosIgnoramCanceladosEUsamHoraLocal() {
        DashboardRepository.Horarios horarios = repository.horarios(FiltroConsulta.periodo(INICIO, FIM));

        Map<Integer, Long> porHora = horarios.porHora().stream()
                .collect(Collectors.toMap(Contagem::chave, Contagem::pedidos));
        Map<Integer, Long> porDia = horarios.porDiaSemana().stream()
                .collect(Collectors.toMap(Contagem::chave, Contagem::pedidos));
        assertThat(porHora).containsOnly(Map.entry(19, 1L), Map.entry(21, 1L), Map.entry(12, 1L), Map.entry(23, 1L));
        assertThat(porDia).containsOnly(Map.entry(1, 3L), Map.entry(7, 1L));
    }

    @Test
    void contagemPorStatusIncluiTodosOsPedidosDoPeriodo() {
        Map<String, Long> status = repository.contagemPorStatus(FiltroConsulta.periodo(INICIO, FIM)).stream()
                .collect(Collectors.toMap(ContagemStatus::status, ContagemStatus::quantidade));

        assertThat(status).containsOnly(Map.entry("FINALIZADO", 2L), Map.entry("CANCELADO", 1L),
                Map.entry("SAIU_PARA_ENTREGA", 1L), Map.entry("RECEBIDO", 1L));
    }

    @Test
    void temposMediosUsamHistoricoESaida() {
        DashboardRepository.Tempos tempos = repository.temposMedios(FiltroConsulta.periodo(INICIO, FIM));

        assertThat(minutos(tempos.ateProntoMs())).isEqualByComparingTo("25.0");
        assertThat(minutos(tempos.ateSaidaMs())).isEqualByComparingTo("36.7");
        assertThat(minutos(tempos.ateFinalizacaoMs())).isEqualByComparingTo("50.0");
    }

    @Test
    void vendasPorProdutoSomamItensDePedidosNaoCancelados() {
        Map<String, VendaProduto> produtos = repository.vendasPorProduto(FiltroConsulta.periodo(INICIO, FIM)).stream()
                .collect(Collectors.toMap(VendaProduto::nome, Function.identity()));

        assertThat(produtos).hasSize(5);
        assertThat(produtos.get("Pizza Calabresa").quantidade()).isEqualTo(2);
        assertThat(produtos.get("Pizza Calabresa").faturamento()).isEqualByComparingTo("99.80");
        assertThat(produtos.get("Esfiha de Carne").quantidade()).isEqualTo(10);
        assertThat(produtos.get("Refrigerante 2L").pedidos()).isEqualTo(2);
        assertThat(produtos.get("Refrigerante 2L").categoria()).isEqualTo("Bebida");
    }

    @Test
    void filtroDeCategoriaIgnoraMaiusculasEAplicaAosItens() {
        FiltroConsulta bebidas = new FiltroConsulta(INICIO, FIM, Set.of(), null, "bebida");

        assertThat(repository.vendasPorProduto(bebidas)).extracting(VendaProduto::nome)
                .containsExactly("Refrigerante 2L");
        assertThat(repository.totais(bebidas).pedidos()).isEqualTo(2);
    }

    @Test
    void vendasPorCategoriaContamPedidosDistintos() {
        Map<String, VendaCategoria> categorias = repository.vendasPorCategoria(FiltroConsulta.periodo(INICIO, FIM))
                .stream().collect(Collectors.toMap(VendaCategoria::categoria, Function.identity()));

        assertThat(categorias).containsOnlyKeys("Pizza", "Esfiha", "Bebida");
        assertThat(categorias.get("Pizza").quantidade()).isEqualTo(4);
        assertThat(categorias.get("Pizza").pedidos()).isEqualTo(3);
        assertThat(categorias.get("Pizza").faturamento()).isEqualByComparingTo("199.70");
    }

    @Test
    void vendasPorAtendimento() {
        Map<String, VendaAtendimento> tipos = repository.vendasPorAtendimento(FiltroConsulta.periodo(INICIO, FIM))
                .stream().collect(Collectors.toMap(VendaAtendimento::tipo, Function.identity()));

        assertThat(tipos.get("ENTREGA").pedidos()).isEqualTo(2);
        assertThat(tipos.get("ENTREGA").faturamento()).isEqualByComparingTo("177.62");
        assertThat(tipos.get("RETIRADA").faturamento()).isEqualByComparingTo("99.90");
    }

    @Test
    void filtrosDeStatusEAtendimento() {
        FiltroConsulta cancelados = new FiltroConsulta(INICIO, FIM, Set.of(StatusPedido.CANCELADO), null, null);
        FiltroConsulta retiradas = new FiltroConsulta(INICIO, FIM, Set.of(), TipoAtendimento.RETIRADA, null);

        assertThat(repository.totais(cancelados).pedidos()).isEqualTo(1);
        assertThat(repository.horarios(cancelados).porHora()).isEmpty();
        assertThat(repository.totais(retiradas).pedidos()).isEqualTo(2);
    }

    @Test
    void pedidosRecentesOrdenadosDoMaisNovo() {
        List<Pedido> recentes = repository.pedidosRecentes(FiltroConsulta.periodo(INICIO, FIM), 3);

        assertThat(recentes).extracting(Pedido::getNumero).containsExactly(5L, 4L, 3L);
        assertThat(recentes.get(0).getValorTotal()).isEqualByComparingTo("54.90");
    }

    @Test
    void intervaloECategoriasDisponiveis() {
        DashboardRepository.Intervalo intervalo = repository.intervaloPedidos();

        assertThat(intervalo.primeiro()).isEqualTo(LocalDateTime.of(2026, 2, 27, 20, 0));
        assertThat(intervalo.ultimo()).isEqualTo(LocalDateTime.of(2026, 3, 11, 0, 0));
        assertThat(repository.categorias()).containsExactly("Bebida", "Esfiha", "Pizza", "Sobremesa");
    }

    private static Map<LocalDate, PontoSerie> porData(List<PontoSerie> serie) {
        return serie.stream().collect(Collectors.toMap(PontoSerie::inicio, Function.identity()));
    }

    private static BigDecimal minutos(Double milissegundos) {
        return BigDecimal.valueOf(milissegundos).divide(new BigDecimal("60000"), 1, RoundingMode.HALF_UP);
    }

    private static Produto produto(String nome, String tipo) {
        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(10.0);
        produto.setAtivo(true);
        return produto;
    }

    private static ItemPedido item(String produtoId, String nome, String tipo, String preco, int quantidade) {
        ItemPedido item = new ItemPedido();
        item.setProdutoId(produtoId);
        item.setNomeProduto(nome);
        item.setTipoProduto(tipo);
        item.setPrecoUnitario(new BigDecimal(preco));
        item.setQuantidade(quantidade);
        item.setSubtotal(new BigDecimal(preco).multiply(BigDecimal.valueOf(quantidade)));
        return item;
    }

    private static Pedido pedido(long numero, LocalDateTime entrada, TipoAtendimento tipo, StatusPedido status,
            String percentualDesconto, ItemPedido... itens) {
        Pedido pedido = new Pedido();
        pedido.setNumero(numero);
        pedido.setClienteId("c" + numero);
        pedido.setClienteNome("Cliente " + numero);
        pedido.setTipoAtendimento(tipo);
        pedido.setItens(new ArrayList<>(List.of(itens)));
        BigDecimal subtotal = pedido.getItens().stream().map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal percentual = new BigDecimal(percentualDesconto);
        BigDecimal desconto = subtotal.multiply(percentual).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        pedido.setSubtotal(subtotal);
        pedido.setPercentualDesconto(percentual);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(subtotal.subtract(desconto));
        pedido.setStatus(status);
        pedido.setDataHoraEntrada(entrada);
        pedido.setResponsavelEntrada("atendente");
        pedido.registrarHistorico(historico(StatusPedido.RECEBIDO, entrada));
        return pedido;
    }

    private static void concluir(Pedido pedido, int minutosPronto, int minutosSaida, int minutosFinalizacao) {
        LocalDateTime entrada = pedido.getDataHoraEntrada();
        pedido.registrarHistorico(historico(StatusPedido.PRONTO, entrada.plusMinutes(minutosPronto)));
        pedido.setDataHoraSaida(entrada.plusMinutes(minutosSaida));
        pedido.setDataHoraFinalizacao(entrada.plusMinutes(minutosFinalizacao));
    }

    private static HistoricoStatus historico(StatusPedido status, LocalDateTime dataHora) {
        return new HistoricoStatus(null, status, dataHora, "atendente", "ATENDENTE", null);
    }
}
