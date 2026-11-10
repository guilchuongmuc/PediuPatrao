package com.umc.pediupatrao.service;

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
import com.umc.pediupatrao.repository.DashboardRepository.Intervalo;
import com.umc.pediupatrao.repository.DashboardRepository.PontoSerie;
import com.umc.pediupatrao.repository.DashboardRepository.Tempos;
import com.umc.pediupatrao.repository.DashboardRepository.Totais;
import com.umc.pediupatrao.repository.DashboardRepository.VendaAtendimento;
import com.umc.pediupatrao.repository.DashboardRepository.VendaCategoria;
import com.umc.pediupatrao.repository.DashboardRepository.VendaProduto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    public static final int PERIODO_PADRAO_DIAS = 30;
    public static final int PERIODO_MAXIMO_DIAS = 366;
    public static final int LIMITE_PADRAO = 10;
    public static final int LIMITE_MAXIMO = 50;
    public static final int TAMANHO_MAXIMO_CATEGORIA = 60;

    static final String PERFIS_AUTORIZADOS = "hasAnyRole('ADMIN','GERENTE')";

    private static final Set<StatusPedido> EM_ANDAMENTO = EnumSet.of(StatusPedido.RECEBIDO,
            StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO, StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.RETIRADO);
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final BigDecimal MINUTO_EM_MS = new BigDecimal("60000");
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final String[] MESES = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out",
        "nov", "dez"};

    private final DashboardRepository repository;
    private final Clock clock;

    public DashboardService(DashboardRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @PreAuthorize(PERFIS_AUTORIZADOS)
    public ResumoResponse resumo(FiltroDashboardParams params) {
        FiltroConsulta filtro = resolverFiltro(params);
        FiltroConsulta filtroAnterior = filtro.periodoAnterior();
        Totais atual = repository.totais(filtro);
        Totais anterior = repository.totais(filtroAnterior);

        BigDecimal ticketAtual = ticketMedio(atual.faturamento(), atual.pedidos() - atual.cancelados());
        BigDecimal ticketAnterior = ticketMedio(anterior.faturamento(), anterior.pedidos() - anterior.cancelados());

        ResumoResponse.Comparacoes comparacoes = new ResumoResponse.Comparacoes(
                comparar(atual.faturamento(), anterior.faturamento()),
                comparar(BigDecimal.valueOf(atual.pedidos()), BigDecimal.valueOf(anterior.pedidos())),
                comparar(ticketAtual, ticketAnterior),
                comparar(BigDecimal.valueOf(atual.cancelados()), BigDecimal.valueOf(anterior.cancelados())));

        return new ResumoResponse(
                filtro.periodo(),
                filtroAnterior.periodo(),
                dinheiro(atual.faturamento()),
                atual.pedidos(),
                atual.pedidos() - atual.cancelados(),
                atual.cancelados(),
                percentual(BigDecimal.valueOf(atual.cancelados()), BigDecimal.valueOf(atual.pedidos())),
                dinheiro(atual.valorCancelado()),
                ticketAtual,
                dinheiro(atual.descontos()),
                atual.itensVendidos(),
                comparacoes);
    }

    @PreAuthorize(PERFIS_AUTORIZADOS)
    public SerieVendasResponse vendas(FiltroDashboardParams params, Agrupamento agrupamento) {
        FiltroConsulta filtro = resolverFiltro(params);
        Agrupamento unidade = agrupamento == null ? Agrupamento.DIA : agrupamento;
        Map<LocalDate, PontoSerie> porInicio = repository.serie(filtro, unidade).stream()
                .collect(Collectors.toMap(PontoSerie::inicio, Function.identity(), (a, b) -> a));

        LocalDate hoje = LocalDate.now(clock);
        List<SerieVendasResponse.Ponto> pontos = new ArrayList<>();
        for (LocalDate inicio = inicioDoGrupo(filtro.inicio(), unidade); !inicio.isAfter(filtro.fim());
                inicio = proximoGrupo(inicio, unidade)) {
            LocalDate fim = proximoGrupo(inicio, unidade).minusDays(1);
            boolean parcial = inicio.isBefore(filtro.inicio()) || fim.isAfter(filtro.fim())
                    || (!hoje.isBefore(inicio) && !hoje.isAfter(fim));
            PontoSerie ponto = porInicio.get(inicio);
            long pedidos = ponto == null ? 0 : ponto.pedidos();
            long cancelados = ponto == null ? 0 : ponto.cancelados();
            BigDecimal faturamento = ponto == null ? BigDecimal.ZERO : ponto.faturamento();
            pontos.add(new SerieVendasResponse.Ponto(inicio, fim, rotulo(inicio, unidade), parcial, pedidos,
                    cancelados, dinheiro(faturamento), ticketMedio(faturamento, pedidos - cancelados)));
        }
        return new SerieVendasResponse(filtro.periodo(), unidade, pontos);
    }

    @PreAuthorize(PERFIS_AUTORIZADOS)
    public VendasPorHorarioResponse vendasPorHorario(FiltroDashboardParams params) {
        FiltroConsulta filtro = resolverFiltro(params);
        DashboardRepository.Horarios horarios = repository.horarios(filtro);

        Map<Integer, Contagem> porHora = indexar(horarios.porHora());
        List<VendasPorHorarioResponse.Hora> horas = new ArrayList<>();
        for (int hora = 0; hora < 24; hora++) {
            Contagem contagem = porHora.get(hora);
            horas.add(new VendasPorHorarioResponse.Hora(hora, String.format("%02dh", hora),
                    contagem == null ? 0 : contagem.pedidos(),
                    dinheiro(contagem == null ? BigDecimal.ZERO : contagem.faturamento())));
        }

        Map<Integer, Contagem> porDia = indexar(horarios.porDiaSemana());
        Map<DayOfWeek, Long> ocorrencias = ocorrenciasPorDiaDaSemana(filtro);
        List<VendasPorHorarioResponse.DiaSemana> dias = new ArrayList<>();
        for (DayOfWeek dia : DayOfWeek.values()) {
            Contagem contagem = porDia.get(dia.getValue());
            long pedidos = contagem == null ? 0 : contagem.pedidos();
            long vezes = ocorrencias.getOrDefault(dia, 0L);
            dias.add(new VendasPorHorarioResponse.DiaSemana(dia.getValue(), nomeDoDia(dia), vezes, pedidos,
                    vezes == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(pedidos).divide(BigDecimal.valueOf(vezes), 1,
                            RoundingMode.HALF_UP),
                    dinheiro(contagem == null ? BigDecimal.ZERO : contagem.faturamento())));
        }

        Integer horaPico = horas.stream()
                .filter(h -> h.pedidos() > 0)
                .max(Comparator.comparingLong(VendasPorHorarioResponse.Hora::pedidos))
                .map(VendasPorHorarioResponse.Hora::hora)
                .orElse(null);
        String diaPico = dias.stream()
                .filter(d -> d.pedidos() > 0)
                .max(Comparator.comparing(VendasPorHorarioResponse.DiaSemana::mediaPedidos))
                .map(VendasPorHorarioResponse.DiaSemana::nome)
                .orElse(null);
        return new VendasPorHorarioResponse(filtro.periodo(), horas, dias, horaPico, diaPico);
    }

    @PreAuthorize(PERFIS_AUTORIZADOS)
    public VendasPorCategoriaResponse vendasPorCategoria(FiltroDashboardParams params) {
        FiltroConsulta filtro = resolverFiltro(params);
        List<VendaCategoria> vendas = repository.vendasPorCategoria(filtro);
        BigDecimal total = somar(vendas.stream().map(VendaCategoria::faturamento).toList());
        List<VendasPorCategoriaResponse.Categoria> categorias = vendas.stream()
                .sorted(Comparator.comparing(VendaCategoria::faturamento).reversed()
                        .thenComparing(VendaCategoria::categoria))
                .map(v -> new VendasPorCategoriaResponse.Categoria(v.categoria(), v.quantidade(), v.pedidos(),
                        dinheiro(v.faturamento()), percentual(v.faturamento(), total)))
                .toList();
        return new VendasPorCategoriaResponse(filtro.periodo(), dinheiro(total), categorias);
    }

    @PreAuthorize(PERFIS_AUTORIZADOS)
    public VendasPorAtendimentoResponse vendasPorAtendimento(FiltroDashboardParams params) {
        FiltroConsulta filtro = resolverFiltro(params);
        Map<String, VendaAtendimento> porTipo = new LinkedHashMap<>();
        for (TipoAtendimento tipo : TipoAtendimento.values()) {
            porTipo.put(tipo.name(), new VendaAtendimento(tipo.name(), 0, BigDecimal.ZERO));
        }
        for (VendaAtendimento venda : repository.vendasPorAtendimento(filtro)) {
            porTipo.put(venda.tipo(), venda);
        }
        if (filtro.tipoAtendimento() != null) {
            porTipo.keySet().removeIf(tipo -> !tipo.equals(filtro.tipoAtendimento().name()));
        }

        long totalPedidos = porTipo.values().stream().mapToLong(VendaAtendimento::pedidos).sum();
        BigDecimal totalFaturamento = somar(porTipo.values().stream().map(VendaAtendimento::faturamento).toList());
        List<VendasPorAtendimentoResponse.Tipo> tipos = porTipo.values().stream()
                .map(v -> new VendasPorAtendimentoResponse.Tipo(v.tipo(), descricaoAtendimento(v.tipo()),
                        v.pedidos(), dinheiro(v.faturamento()), ticketMedio(v.faturamento(), v.pedidos()),
                        percentual(BigDecimal.valueOf(v.pedidos()), BigDecimal.valueOf(totalPedidos)),
                        percentual(v.faturamento(), totalFaturamento)))
                .toList();
        return new VendasPorAtendimentoResponse(filtro.periodo(), tipos);
    }

    @PreAuthorize(PERFIS_AUTORIZADOS)
    public SituacaoPedidosResponse situacaoPedidos(FiltroDashboardParams params) {
        FiltroConsulta filtro = resolverFiltro(params);
        Map<StatusPedido, Long> quantidades = new EnumMap<>(StatusPedido.class);
        Map<StatusPedido, BigDecimal> valores = new EnumMap<>(StatusPedido.class);
        for (ContagemStatus contagem : repository.contagemPorStatus(filtro)) {
            StatusPedido status = StatusPedido.fromLegado(contagem.status());
            quantidades.merge(status, contagem.quantidade(), Long::sum);
            valores.merge(status, contagem.valor(), BigDecimal::add);
        }

        long total = quantidades.values().stream().mapToLong(Long::longValue).sum();
        List<SituacaoPedidosResponse.QuantidadeStatus> porStatus = Arrays.stream(StatusPedido.values())
                .map(status -> {
                    long quantidade = quantidades.getOrDefault(status, 0L);
                    return new SituacaoPedidosResponse.QuantidadeStatus(status, status.getDescricao(), quantidade,
                            percentual(BigDecimal.valueOf(quantidade), BigDecimal.valueOf(total)),
                            dinheiro(valores.getOrDefault(status, BigDecimal.ZERO)));
                })
                .toList();
        long emAndamento = EM_ANDAMENTO.stream().mapToLong(s -> quantidades.getOrDefault(s, 0L)).sum();

        Tempos tempos = repository.temposMedios(filtro);
        SituacaoPedidosResponse.TemposMedios temposMedios = new SituacaoPedidosResponse.TemposMedios(
                minutos(tempos.ateProntoMs()), minutos(tempos.ateSaidaMs()), minutos(tempos.ateFinalizacaoMs()));

        return new SituacaoPedidosResponse(filtro.periodo(), total, emAndamento,
                quantidades.getOrDefault(StatusPedido.FINALIZADO, 0L),
                quantidades.getOrDefault(StatusPedido.CANCELADO, 0L), porStatus, temposMedios);
    }

    @PreAuthorize(PERFIS_AUTORIZADOS)
    public PedidosRecentesResponse pedidosRecentes(FiltroDashboardParams params, Integer limite) {
        FiltroConsulta filtro = resolverFiltro(params);
        int quantidade = validarLimite(limite);
        LocalDateTime agora = LocalDateTime.now(clock);
        List<PedidosRecentesResponse.PedidoRecente> pedidos = repository.pedidosRecentes(filtro, quantidade).stream()
                .map(p -> paraPedidoRecente(p, agora))
                .toList();
        return new PedidosRecentesResponse(quantidade, pedidos);
    }

    @PreAuthorize(PERFIS_AUTORIZADOS)
    public RankingProdutosResponse produtosMaisVendidos(FiltroDashboardParams params, CriterioRanking criterio,
            Integer limite) {
        FiltroConsulta filtro = resolverFiltro(params);
        CriterioRanking ordem = criterio == null ? CriterioRanking.QUANTIDADE : criterio;
        int quantidade = validarLimite(limite);
        List<VendaProduto> vendas = repository.vendasPorProduto(filtro);

        Function<VendaProduto, BigDecimal> medida = ordem == CriterioRanking.QUANTIDADE
                ? v -> BigDecimal.valueOf(v.quantidade())
                : VendaProduto::faturamento;
        Comparator<VendaProduto> comparador = ordem == CriterioRanking.QUANTIDADE
                ? Comparator.comparingLong(VendaProduto::quantidade).reversed()
                        .thenComparing(VendaProduto::faturamento, Comparator.reverseOrder())
                : Comparator.comparing(VendaProduto::faturamento).reversed()
                        .thenComparing(Comparator.comparingLong(VendaProduto::quantidade).reversed());
        comparador = comparador.thenComparing(v -> Objects.toString(v.nome(), ""));

        BigDecimal total = somar(vendas.stream().map(medida).toList());
        List<VendaProduto> ordenados = vendas.stream().sorted(comparador).limit(quantidade).toList();
        List<RankingProdutosResponse.Produto> produtos = new ArrayList<>();
        for (int i = 0; i < ordenados.size(); i++) {
            VendaProduto v = ordenados.get(i);
            produtos.add(new RankingProdutosResponse.Produto(i + 1, v.produtoId(), v.nome(), v.categoria(),
                    v.quantidade(), v.pedidos(), dinheiro(v.faturamento()), percentual(medida.apply(v), total)));
        }
        return new RankingProdutosResponse(filtro.periodo(), ordem, quantidade, vendas.size(), produtos);
    }

    @PreAuthorize(PERFIS_AUTORIZADOS)
    public OpcoesFiltroResponse opcoesDeFiltro() {
        Intervalo intervalo = repository.intervaloPedidos();
        LocalDate hoje = LocalDate.now(clock);
        return new OpcoesFiltroResponse(
                Arrays.stream(StatusPedido.values())
                        .map(s -> new OpcoesFiltroResponse.Opcao(s.name(), s.getDescricao())).toList(),
                Arrays.stream(TipoAtendimento.values())
                        .map(t -> new OpcoesFiltroResponse.Opcao(t.name(), t.getDescricao())).toList(),
                repository.categorias(),
                Arrays.stream(Agrupamento.values())
                        .map(a -> new OpcoesFiltroResponse.Opcao(a.name(), a.getDescricao())).toList(),
                Arrays.stream(CriterioRanking.values())
                        .map(c -> new OpcoesFiltroResponse.Opcao(c.name(), c.getDescricao())).toList(),
                FiltroConsulta.periodo(hoje.minusDays(PERIODO_PADRAO_DIAS - 1L), hoje).periodo(),
                intervalo.primeiro() == null ? null : intervalo.primeiro().toLocalDate(),
                intervalo.ultimo() == null ? null : intervalo.ultimo().toLocalDate(),
                PERIODO_MAXIMO_DIAS,
                LIMITE_MAXIMO);
    }

    FiltroConsulta resolverFiltro(FiltroDashboardParams params) {
        FiltroDashboardParams p = params == null ? FiltroDashboardParams.vazio() : params;
        LocalDate hoje = LocalDate.now(clock);
        LocalDate fim = p.fim() != null ? p.fim() : hoje;
        LocalDate inicio = p.inicio() != null ? p.inicio() : fim.minusDays(PERIODO_PADRAO_DIAS - 1L);

        if (inicio.isAfter(fim)) {
            throw new ParametroInvalidoException("inicio", "A data inicial não pode ser posterior à data final.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) + 1 > PERIODO_MAXIMO_DIAS) {
            throw new ParametroInvalidoException("fim",
                    "O período consultado não pode passar de " + PERIODO_MAXIMO_DIAS + " dias.");
        }

        String categoria = p.categoria() == null || p.categoria().isBlank() ? null : p.categoria().trim();
        if (categoria != null && categoria.length() > TAMANHO_MAXIMO_CATEGORIA) {
            throw new ParametroInvalidoException("categoria",
                    "A categoria deve ter no máximo " + TAMANHO_MAXIMO_CATEGORIA + " caracteres.");
        }
        Set<StatusPedido> status = p.status() == null ? Set.of() : new HashSet<>(p.status());
        return new FiltroConsulta(inicio, fim, status, p.tipoAtendimento(), categoria);
    }

    private static int validarLimite(Integer limite) {
        if (limite == null) {
            return LIMITE_PADRAO;
        }
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new ParametroInvalidoException("limite",
                    "O parâmetro limite deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return limite;
    }

    private static PedidosRecentesResponse.PedidoRecente paraPedidoRecente(Pedido pedido, LocalDateTime agora) {
        StatusPedido status = pedido.getStatus();
        Long minutos = null;
        if (status != null && EM_ANDAMENTO.contains(status) && pedido.getDataHoraEntrada() != null) {
            minutos = Math.max(0, Duration.between(pedido.getDataHoraEntrada(), agora).toMinutes());
        }
        return new PedidosRecentesResponse.PedidoRecente(
                pedido.getId(),
                pedido.getNumero(),
                pedido.getClienteNome(),
                pedido.getTipoAtendimento(),
                status,
                status == null ? null : status.getDescricao(),
                pedido.getValorTotal() == null ? null : dinheiro(pedido.getValorTotal()),
                pedido.getDataHoraEntrada(),
                minutos);
    }

    private static LocalDate inicioDoGrupo(LocalDate data, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> data;
            case SEMANA -> data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> data.withDayOfMonth(1);
        };
    }

    private static LocalDate proximoGrupo(LocalDate data, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> data.plusDays(1);
            case SEMANA -> data.plusWeeks(1);
            case MES -> data.plusMonths(1);
        };
    }

    private static String rotulo(LocalDate data, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA, SEMANA -> data.format(DIA_MES);
            case MES -> MESES[data.getMonthValue() - 1] + "/" + data.getYear();
        };
    }

    private static Map<DayOfWeek, Long> ocorrenciasPorDiaDaSemana(FiltroConsulta filtro) {
        Map<DayOfWeek, Long> ocorrencias = new EnumMap<>(DayOfWeek.class);
        for (LocalDate data = filtro.inicio(); !data.isAfter(filtro.fim()); data = data.plusDays(1)) {
            ocorrencias.merge(data.getDayOfWeek(), 1L, Long::sum);
        }
        return ocorrencias;
    }

    private static String nomeDoDia(DayOfWeek dia) {
        String nome = dia.getDisplayName(TextStyle.FULL, PT_BR);
        return nome.substring(0, 1).toUpperCase(PT_BR) + nome.substring(1);
    }

    private static String descricaoAtendimento(String tipo) {
        for (TipoAtendimento atendimento : TipoAtendimento.values()) {
            if (atendimento.name().equals(tipo)) {
                return atendimento.getDescricao();
            }
        }
        return "Não informado";
    }

    private static Map<Integer, Contagem> indexar(List<Contagem> contagens) {
        return contagens.stream().collect(Collectors.toMap(Contagem::chave, Function.identity(), (a, b) -> a));
    }

    private static ResumoResponse.Comparacao comparar(BigDecimal atual, BigDecimal anterior) {
        BigDecimal base = anterior == null ? BigDecimal.ZERO : anterior;
        BigDecimal variacao = base.signum() == 0 ? null
                : atual.subtract(base).multiply(CEM).divide(base, 2, RoundingMode.HALF_UP);
        return new ResumoResponse.Comparacao(base.setScale(2, RoundingMode.HALF_UP), variacao);
    }

    private static BigDecimal ticketMedio(BigDecimal faturamento, long pedidos) {
        if (pedidos <= 0) {
            return dinheiro(BigDecimal.ZERO);
        }
        return faturamento.divide(BigDecimal.valueOf(pedidos), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return parte.multiply(CEM).divide(total, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal minutos(Double milissegundos) {
        if (milissegundos == null) {
            return null;
        }
        return BigDecimal.valueOf(milissegundos).divide(MINUTO_EM_MS, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal somar(List<BigDecimal> valores) {
        return valores.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }
}
