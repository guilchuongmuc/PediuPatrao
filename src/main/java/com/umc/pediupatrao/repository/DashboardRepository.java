package com.umc.pediupatrao.repository;

import com.mongodb.client.AggregateIterable;
import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.FiltroConsulta;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;
import java.text.Collator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardRepository {

    static final String PEDIDOS = "pedidos";
    static final String PRODUTOS = "produtos";
    static final String SEM_CATEGORIA = "Sem categoria";
    static final String ATENDIMENTO_NAO_INFORMADO = "NAO_INFORMADO";

    private static final String CANCELADO = StatusPedido.CANCELADO.name();
    private static final Decimal128 ZERO = new Decimal128(BigDecimal.ZERO);
    private static final String ENTRADA = "$dataHoraEntrada";

    private final MongoTemplate mongoTemplate;
    private final ZoneId zona;

    public DashboardRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.zona = ZoneId.systemDefault();
    }

    public record Totais(long pedidos, long cancelados, BigDecimal faturamento, BigDecimal valorCancelado,
            BigDecimal descontos, long itensVendidos) {

        public static final Totais VAZIO = new Totais(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }

    public record PontoSerie(LocalDate inicio, long pedidos, long cancelados, BigDecimal faturamento) {
    }

    public record Contagem(int chave, long pedidos, BigDecimal faturamento) {
    }

    public record Horarios(List<Contagem> porHora, List<Contagem> porDiaSemana) {
    }

    public record ContagemStatus(String status, long quantidade, BigDecimal valor) {
    }

    public record Tempos(Double ateProntoMs, Double ateSaidaMs, Double ateFinalizacaoMs) {

        public static final Tempos VAZIO = new Tempos(null, null, null);
    }

    public record VendaProduto(String produtoId, String nome, String categoria, long quantidade, long pedidos,
            BigDecimal faturamento) {
    }

    public record VendaCategoria(String categoria, long quantidade, long pedidos, BigDecimal faturamento) {
    }

    public record VendaAtendimento(String tipo, long pedidos, BigDecimal faturamento) {
    }

    public record Intervalo(LocalDateTime primeiro, LocalDateTime ultimo) {
    }

    public Totais totais(FiltroConsulta filtro) {
        Document grupo = new Document("_id", null)
                .append("pedidos", new Document("$sum", 1))
                .append("cancelados", somaSe(cancelado(), 1, 0))
                .append("faturamento", somaSe(cancelado(), ZERO, valorDecimal("$valorTotal")))
                .append("valorCancelado", somaSe(cancelado(), valorDecimal("$valorTotal"), ZERO))
                .append("descontos", somaSe(cancelado(), ZERO, valorDecimal("$valorDesconto")))
                .append("itensVendidos", somaSe(cancelado(), 0, quantidadeDeItens()));
        Document resultado = agregar(List.of(match(filtro, false), new Document("$group", grupo))).first();
        if (resultado == null) {
            return Totais.VAZIO;
        }
        return new Totais(
                inteiro(resultado.get("pedidos")),
                inteiro(resultado.get("cancelados")),
                decimal(resultado.get("faturamento")),
                decimal(resultado.get("valorCancelado")),
                decimal(resultado.get("descontos")),
                inteiro(resultado.get("itensVendidos")));
    }

    public List<PontoSerie> serie(FiltroConsulta filtro, Agrupamento agrupamento) {
        Document truncamento = new Document("date", ENTRADA)
                .append("unit", unidade(agrupamento))
                .append("timezone", idZona());
        if (agrupamento == Agrupamento.SEMANA) {
            truncamento.append("startOfWeek", "monday");
        }
        Document grupo = new Document("_id", new Document("$dateTrunc", truncamento))
                .append("pedidos", new Document("$sum", 1))
                .append("cancelados", somaSe(cancelado(), 1, 0))
                .append("faturamento", somaSe(cancelado(), ZERO, valorDecimal("$valorTotal")));
        List<PontoSerie> pontos = new ArrayList<>();
        for (Document d : agregar(List.of(match(filtro, false), new Document("$group", grupo),
                new Document("$sort", new Document("_id", 1))))) {
            pontos.add(new PontoSerie(
                    paraData(d.getDate("_id")),
                    inteiro(d.get("pedidos")),
                    inteiro(d.get("cancelados")),
                    decimal(d.get("faturamento"))));
        }
        return pontos;
    }

    public Horarios horarios(FiltroConsulta filtro) {
        Document porHora = new Document("$group", new Document("_id",
                new Document("$hour", new Document("date", ENTRADA).append("timezone", idZona())))
                .append("pedidos", new Document("$sum", 1))
                .append("faturamento", new Document("$sum", valorDecimal("$valorTotal"))));
        Document porDia = new Document("$group", new Document("_id",
                new Document("$isoDayOfWeek", new Document("date", ENTRADA).append("timezone", idZona())))
                .append("pedidos", new Document("$sum", 1))
                .append("faturamento", new Document("$sum", valorDecimal("$valorTotal"))));
        Document facetas = new Document("$facet", new Document("porHora", List.of(porHora))
                .append("porDiaSemana", List.of(porDia)));
        Document resultado = agregar(List.of(match(filtro, true), facetas)).first();
        if (resultado == null) {
            return new Horarios(List.of(), List.of());
        }
        return new Horarios(contagens(resultado.getList("porHora", Document.class)),
                contagens(resultado.getList("porDiaSemana", Document.class)));
    }

    public List<ContagemStatus> contagemPorStatus(FiltroConsulta filtro) {
        Document grupo = new Document("_id", "$status")
                .append("quantidade", new Document("$sum", 1))
                .append("valor", new Document("$sum", valorDecimal("$valorTotal")));
        List<ContagemStatus> contagens = new ArrayList<>();
        for (Document d : agregar(List.of(match(filtro, false), new Document("$group", grupo)))) {
            Object status = d.get("_id");
            contagens.add(new ContagemStatus(status == null ? null : status.toString(),
                    inteiro(d.get("quantidade")), decimal(d.get("valor"))));
        }
        return contagens;
    }

    public Tempos temposMedios(FiltroConsulta filtro) {
        Document filtroPronto = new Document("$filter", new Document("input", new Document("$ifNull",
                Arrays.asList("$historico", List.of())))
                .append("as", "h")
                .append("cond", new Document("$eq", Arrays.asList("$$h.statusNovo", StatusPedido.PRONTO.name()))));
        Document dataPronto = new Document("$let", new Document("vars",
                new Document("pronto", new Document("$arrayElemAt", Arrays.asList(filtroPronto, 0))))
                .append("in", "$$pronto.dataHora"));
        Document projecao = new Document("$project", new Document()
                .append("atePronto", new Document("$subtract", Arrays.asList(dataPronto, ENTRADA)))
                .append("ateSaida", new Document("$subtract", Arrays.asList("$dataHoraSaida", ENTRADA)))
                .append("ateFinalizacao", new Document("$subtract", Arrays.asList("$dataHoraFinalizacao", ENTRADA))));
        Document grupo = new Document("$group", new Document("_id", null)
                .append("atePronto", new Document("$avg", "$atePronto"))
                .append("ateSaida", new Document("$avg", "$ateSaida"))
                .append("ateFinalizacao", new Document("$avg", "$ateFinalizacao")));
        Document resultado = agregar(List.of(match(filtro, true), projecao, grupo)).first();
        if (resultado == null) {
            return Tempos.VAZIO;
        }
        return new Tempos(real(resultado.get("atePronto")), real(resultado.get("ateSaida")),
                real(resultado.get("ateFinalizacao")));
    }

    public List<VendaProduto> vendasPorProduto(FiltroConsulta filtro) {
        List<Document> pipeline = itensVendidos(filtro);
        pipeline.add(new Document("$group", new Document("_id",
                new Document("$ifNull", Arrays.asList("$itens.produtoId", "$itens.nomeProduto")))
                .append("nome", new Document("$last", "$itens.nomeProduto"))
                .append("categoria", new Document("$last", "$itens.tipoProduto"))
                .append("quantidade", new Document("$sum", new Document("$ifNull", Arrays.asList("$itens.quantidade", 0))))
                .append("pedidos", new Document("$addToSet", "$_id"))
                .append("faturamento", new Document("$sum", valorDecimal("$itens.subtotal")))));
        List<VendaProduto> vendas = new ArrayList<>();
        for (Document d : agregar(pipeline)) {
            Object id = d.get("_id");
            vendas.add(new VendaProduto(
                    id == null ? null : id.toString(),
                    d.getString("nome"),
                    textoOu(d.getString("categoria"), SEM_CATEGORIA),
                    inteiro(d.get("quantidade")),
                    tamanho(d.get("pedidos")),
                    decimal(d.get("faturamento"))));
        }
        return vendas;
    }

    public List<VendaCategoria> vendasPorCategoria(FiltroConsulta filtro) {
        List<Document> pipeline = itensVendidos(filtro);
        pipeline.add(new Document("$group", new Document("_id",
                new Document("$ifNull", Arrays.asList("$itens.tipoProduto", SEM_CATEGORIA)))
                .append("quantidade", new Document("$sum", new Document("$ifNull", Arrays.asList("$itens.quantidade", 0))))
                .append("pedidos", new Document("$addToSet", "$_id"))
                .append("faturamento", new Document("$sum", valorDecimal("$itens.subtotal")))));
        List<VendaCategoria> vendas = new ArrayList<>();
        for (Document d : agregar(pipeline)) {
            vendas.add(new VendaCategoria(
                    textoOu(d.getString("_id"), SEM_CATEGORIA),
                    inteiro(d.get("quantidade")),
                    tamanho(d.get("pedidos")),
                    decimal(d.get("faturamento"))));
        }
        return vendas;
    }

    public List<VendaAtendimento> vendasPorAtendimento(FiltroConsulta filtro) {
        Document grupo = new Document("_id",
                new Document("$ifNull", Arrays.asList("$tipoAtendimento", ATENDIMENTO_NAO_INFORMADO)))
                .append("pedidos", new Document("$sum", 1))
                .append("faturamento", new Document("$sum", valorDecimal("$valorTotal")));
        List<VendaAtendimento> vendas = new ArrayList<>();
        for (Document d : agregar(List.of(match(filtro, true), new Document("$group", grupo)))) {
            vendas.add(new VendaAtendimento(d.get("_id").toString(), inteiro(d.get("pedidos")),
                    decimal(d.get("faturamento"))));
        }
        return vendas;
    }

    public List<Pedido> pedidosRecentes(FiltroConsulta filtro, int limite) {
        BasicQuery consulta = new BasicQuery(criterios(filtro, false));
        consulta.with(Sort.by(Sort.Direction.DESC, "dataHoraEntrada", "numero")).limit(limite);
        return mongoTemplate.find(consulta, Pedido.class, PEDIDOS);
    }

    public Intervalo intervaloPedidos() {
        Document existe = new Document("dataHoraEntrada", new Document("$type", "date"));
        Document projecao = new Document("dataHoraEntrada", 1);
        Document primeiro = mongoTemplate.getCollection(PEDIDOS).find(existe).projection(projecao)
                .sort(new Document("dataHoraEntrada", 1)).limit(1).first();
        Document ultimo = mongoTemplate.getCollection(PEDIDOS).find(existe).projection(projecao)
                .sort(new Document("dataHoraEntrada", -1)).limit(1).first();
        return new Intervalo(dataHora(primeiro), dataHora(ultimo));
    }

    public List<String> categorias() {
        TreeSet<String> categorias = new TreeSet<>(Collator.getInstance(Locale.of("pt", "BR")));
        adicionarTextos(categorias, mongoTemplate.getCollection(PRODUTOS).distinct("tipo", String.class));
        adicionarTextos(categorias, mongoTemplate.getCollection(PEDIDOS).distinct("itens.tipoProduto", String.class));
        return List.copyOf(categorias);
    }

    private List<Document> itensVendidos(FiltroConsulta filtro) {
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(match(filtro, true));
        pipeline.add(new Document("$sort", new Document("dataHoraEntrada", 1)));
        pipeline.add(new Document("$unwind", "$itens"));
        if (filtro.categoria() != null) {
            pipeline.add(new Document("$match", new Document("itens.tipoProduto", categoria(filtro.categoria()))));
        }
        return pipeline;
    }

    private Document match(FiltroConsulta filtro, boolean somenteVendas) {
        return new Document("$match", criterios(filtro, somenteVendas));
    }

    Document criterios(FiltroConsulta filtro, boolean somenteVendas) {
        Document criterios = new Document("dataHoraEntrada", new Document("$gte", paraDate(filtro.inicioInclusivo()))
                .append("$lt", paraDate(filtro.fimExclusivo())));
        Document status = criterioStatus(filtro, somenteVendas);
        if (status != null) {
            criterios.append("status", status);
        }
        if (filtro.tipoAtendimento() != null) {
            criterios.append("tipoAtendimento", filtro.tipoAtendimento().name());
        }
        if (filtro.categoria() != null) {
            criterios.append("itens.tipoProduto", categoria(filtro.categoria()));
        }
        return criterios;
    }

    private static Document criterioStatus(FiltroConsulta filtro, boolean somenteVendas) {
        if (filtro.status().isEmpty()) {
            return somenteVendas ? new Document("$ne", CANCELADO) : null;
        }
        List<String> nomes = filtro.status().stream()
                .filter(s -> !somenteVendas || s != StatusPedido.CANCELADO)
                .map(Enum::name)
                .toList();
        return new Document("$in", nomes);
    }

    private static Document categoria(String categoria) {
        return new Document("$regex", "^" + Pattern.quote(categoria) + "$").append("$options", "i");
    }

    private static Document cancelado() {
        return new Document("$eq", Arrays.asList("$status", CANCELADO));
    }

    private static Document somaSe(Document condicao, Object verdadeiro, Object falso) {
        return new Document("$sum", new Document("$cond", Arrays.asList(condicao, verdadeiro, falso)));
    }

    private static Document valorDecimal(String campo) {
        return new Document("$convert", new Document("input", campo)
                .append("to", "decimal")
                .append("onError", ZERO)
                .append("onNull", ZERO));
    }

    private static Document quantidadeDeItens() {
        return new Document("$sum", new Document("$map", new Document("input",
                new Document("$ifNull", Arrays.asList("$itens", List.of())))
                .append("as", "item")
                .append("in", new Document("$ifNull", Arrays.asList("$$item.quantidade", 0)))));
    }

    private static String unidade(Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> "day";
            case SEMANA -> "week";
            case MES -> "month";
        };
    }

    private AggregateIterable<Document> agregar(List<Document> pipeline) {
        return mongoTemplate.getCollection(PEDIDOS).aggregate(pipeline);
    }

    private static List<Contagem> contagens(List<Document> documentos) {
        List<Contagem> contagens = new ArrayList<>();
        if (documentos == null) {
            return contagens;
        }
        for (Document d : documentos) {
            Object chave = d.get("_id");
            if (chave instanceof Number numero) {
                contagens.add(new Contagem(numero.intValue(), inteiro(d.get("pedidos")), decimal(d.get("faturamento"))));
            }
        }
        return contagens;
    }

    private String idZona() {
        ZoneId normalizada = zona.normalized();
        if (normalizada instanceof ZoneOffset offset) {
            return offset.getTotalSeconds() == 0 ? "UTC" : offset.getId();
        }
        return zona.getId();
    }

    private Date paraDate(LocalDateTime dataHora) {
        return Date.from(dataHora.atZone(zona).toInstant());
    }

    private LocalDate paraData(Date data) {
        return data.toInstant().atZone(zona).toLocalDate();
    }

    private LocalDateTime dataHora(Document documento) {
        if (documento == null || documento.getDate("dataHoraEntrada") == null) {
            return null;
        }
        return LocalDateTime.ofInstant(documento.getDate("dataHoraEntrada").toInstant(), zona);
    }

    private static void adicionarTextos(Collection<String> destino, Iterable<String> origem) {
        for (String texto : origem) {
            if (texto != null && !texto.isBlank()) {
                destino.add(texto.trim());
            }
        }
    }

    private static String textoOu(String texto, String padrao) {
        return texto == null || texto.isBlank() ? padrao : texto;
    }

    private static long inteiro(Object valor) {
        return valor instanceof Number numero ? numero.longValue() : 0L;
    }

    private static long tamanho(Object valor) {
        return valor instanceof Collection<?> colecao ? colecao.size() : 0L;
    }

    private static Double real(Object valor) {
        return valor instanceof Number numero ? numero.doubleValue() : null;
    }

    private static BigDecimal decimal(Object valor) {
        if (valor instanceof Decimal128 decimal) {
            return decimal.bigDecimalValue();
        }
        if (valor instanceof BigDecimal decimal) {
            return decimal;
        }
        if (valor instanceof Number numero) {
            return new BigDecimal(numero.toString());
        }
        return BigDecimal.ZERO;
    }
}
