package com.umc.pediupatrao.demo;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class GeradorPedidosDemonstracao {

    private static final int[] PEDIDOS_POR_DIA_DA_SEMANA = {0, 6, 7, 8, 10, 16, 20, 17};
    private static final int[] HORAS = {11, 12, 13, 14, 17, 18, 19, 20, 21, 22, 23};
    private static final int[] PESOS_HORAS = {2, 4, 3, 1, 1, 3, 7, 9, 7, 4, 1};
    private static final Map<String, Integer> POPULARIDADE = Map.ofEntries(
            Map.entry("calabresa", 25), Map.entry("mussarela", 20), Map.entry("frango", 18),
            Map.entry("portuguesa", 12), Map.entry("quatro queijos", 9), Map.entry("margherita", 8),
            Map.entry("chocolate", 7), Map.entry("refrigerante", 40), Map.entry("guaraná", 25),
            Map.entry("suco", 20), Map.entry("água", 12), Map.entry("esfiha de carne", 40),
            Map.entry("esfiha de queijo", 30), Map.entry("esfiha de calabresa", 20));
    private static final String[] JUSTIFICATIVAS_CANCELAMENTO = {
        "Cliente desistiu do pedido por telefone.",
        "Endereço fora da área de entrega.",
        "Pagamento não confirmado pelo cliente.",
        "Pedido registrado em duplicidade.",
        "Cliente não pôde aguardar o tempo de preparo."
    };
    private static final String[] MOTIVOS_DESCONTO = {
        "Cupom de fidelidade",
        "Promoção da semana",
        "Compensação por atraso anterior",
        "Primeira compra do cliente"
    };
    private static final String[] OBSERVACOES = {
        "Sem cebola.",
        "Troco para R$ 100,00.",
        "Interfone com defeito, ligar ao chegar.",
        "Pizza bem assada.",
        "Mandar sachês de ketchup."
    };
    private static final int[] PERCENTUAIS_DESCONTO = {5, 10, 15};
    private static final BigDecimal CEM = new BigDecimal("100");

    private final Random random;
    private final List<Produto> produtos;
    private final List<Cliente> clientes;

    public GeradorPedidosDemonstracao(long semente, List<Produto> produtos, List<Cliente> clientes) {
        if (produtos.isEmpty() || clientes.isEmpty()) {
            throw new IllegalArgumentException("É preciso ao menos um produto e um cliente para gerar pedidos.");
        }
        this.random = new Random(semente);
        this.produtos = List.copyOf(produtos);
        this.clientes = List.copyOf(clientes);
    }

    public List<Pedido> gerar(LocalDate inicio, LocalDateTime agora) {
        List<Pedido> pedidos = new ArrayList<>();
        LocalDate hoje = agora.toLocalDate();
        long totalDias = ChronoUnit.DAYS.between(inicio, hoje) + 1;
        for (LocalDate dia = inicio; !dia.isAfter(hoje); dia = dia.plusDays(1)) {
            double progresso = totalDias <= 1 ? 1 : (double) ChronoUnit.DAYS.between(inicio, dia) / (totalDias - 1);
            double fator = (0.8 + 0.4 * progresso) * (0.75 + random.nextDouble() * 0.5);
            int quantidade = (int) Math.round(PEDIDOS_POR_DIA_DA_SEMANA[dia.getDayOfWeek().getValue()] * fator);
            List<LocalDateTime> entradas = new ArrayList<>();
            for (int i = 0; i < quantidade; i++) {
                entradas.add(dia.atTime(sortearHora(), random.nextInt(60), random.nextInt(60)));
            }
            entradas.sort(null);
            for (LocalDateTime entrada : entradas) {
                if (!entrada.isAfter(agora)) {
                    pedidos.add(gerarPedido(entrada, agora));
                }
            }
        }
        return pedidos;
    }

    private Pedido gerarPedido(LocalDateTime entrada, LocalDateTime agora) {
        Cliente cliente = clientes.get(random.nextInt(clientes.size()));
        TipoAtendimento tipo = random.nextDouble() < 0.62 ? TipoAtendimento.ENTREGA : TipoAtendimento.RETIRADA;
        boolean movimentado = isMovimentado(entrada.getDayOfWeek());

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setTipoAtendimento(tipo);
        pedido.setEnderecoEntrega(tipo == TipoAtendimento.ENTREGA ? cliente.getEnderecoFormatado() : null);
        pedido.setObservacoes(random.nextDouble() < 0.15 ? OBSERVACOES[random.nextInt(OBSERVACOES.length)] : null);
        pedido.setItens(sortearItens(movimentado));
        pedido.setSubtotal(pedido.getItens().stream().map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
        aplicarDesconto(pedido, entrada);

        String responsavelEntrada = random.nextDouble() < 0.8 ? "atendente" : "gerente";
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setDataHoraEntrada(entrada);
        pedido.setResponsavelEntrada(responsavelEntrada);
        pedido.registrarHistorico(new HistoricoStatus(null, StatusPedido.RECEBIDO, entrada, responsavelEntrada,
                perfil(responsavelEntrada), "Entrada do pedido"));

        LocalDateTime preparo = entrada.plusMinutes(2 + random.nextInt(6));
        LocalDateTime pronto = preparo.plusMinutes(15 + random.nextInt(16) + (movimentado ? 5 : 0));
        LocalDateTime saida = pronto.plusMinutes(tipo == TipoAtendimento.ENTREGA ? 3 + random.nextInt(8)
                : 5 + random.nextInt(20));
        LocalDateTime finalizacao = tipo == TipoAtendimento.ENTREGA ? saida.plusMinutes(15 + random.nextInt(26))
                : saida.plusMinutes(1 + random.nextInt(3));
        StatusPedido statusSaida = tipo == TipoAtendimento.ENTREGA ? StatusPedido.SAIU_PARA_ENTREGA
                : StatusPedido.RETIRADO;

        if (random.nextDouble() < 0.06) {
            int etapa = random.nextInt(3);
            LocalDateTime cancelamento = switch (etapa) {
                case 0 -> entrada.plusMinutes(1);
                case 1 -> preparo.plusMinutes(1 + random.nextInt(5));
                default -> pronto.plusMinutes(1);
            };
            if (etapa >= 1 && avancar(pedido, StatusPedido.EM_PREPARACAO, preparo, agora, "atendente") && etapa == 2) {
                avancar(pedido, StatusPedido.PRONTO, pronto, agora, "atendente");
            }
            cancelar(pedido, cancelamento, agora);
            return pedido;
        }

        if (avancar(pedido, StatusPedido.EM_PREPARACAO, preparo, agora, "atendente")
                && avancar(pedido, StatusPedido.PRONTO, pronto, agora, "atendente")
                && avancar(pedido, statusSaida, saida, agora, "atendente")) {
            pedido.setDataHoraSaida(saida);
            pedido.setResponsavelSaida("atendente");
            String responsavelFinal = random.nextDouble() < 0.7 ? "atendente" : "gerente";
            if (avancar(pedido, StatusPedido.FINALIZADO, finalizacao, agora, responsavelFinal)) {
                pedido.setDataHoraFinalizacao(finalizacao);
                pedido.setResponsavelFinalizacao(responsavelFinal);
            }
        }
        return pedido;
    }

    private boolean avancar(Pedido pedido, StatusPedido novo, LocalDateTime quando, LocalDateTime agora,
            String usuario) {
        if (quando.isAfter(agora)) {
            return false;
        }
        pedido.registrarHistorico(new HistoricoStatus(pedido.getStatus(), novo, quando, usuario, perfil(usuario),
                null));
        pedido.setStatus(novo);
        return true;
    }

    private void cancelar(Pedido pedido, LocalDateTime quando, LocalDateTime agora) {
        if (quando.isAfter(agora)) {
            return;
        }
        String justificativa = JUSTIFICATIVAS_CANCELAMENTO[random.nextInt(JUSTIFICATIVAS_CANCELAMENTO.length)];
        pedido.registrarHistorico(new HistoricoStatus(pedido.getStatus(), StatusPedido.CANCELADO, quando, "gerente",
                "GERENTE", justificativa));
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setDataHoraCancelamento(quando);
        pedido.setResponsavelCancelamento("gerente");
        pedido.setJustificativaCancelamento(justificativa);
    }

    private void aplicarDesconto(Pedido pedido, LocalDateTime entrada) {
        BigDecimal percentual = BigDecimal.ZERO.setScale(2);
        if (random.nextDouble() < 0.1) {
            percentual = BigDecimal.valueOf(PERCENTUAIS_DESCONTO[random.nextInt(PERCENTUAIS_DESCONTO.length)])
                    .setScale(2);
            pedido.setDataHoraDesconto(entrada.plusMinutes(1));
            pedido.setResponsavelDesconto("gerente");
            pedido.setJustificativaDesconto(MOTIVOS_DESCONTO[random.nextInt(MOTIVOS_DESCONTO.length)]);
        }
        BigDecimal desconto = pedido.getSubtotal().multiply(percentual).divide(CEM, 2, RoundingMode.HALF_UP);
        pedido.setPercentualDesconto(percentual);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(pedido.getSubtotal().subtract(desconto));
    }

    private List<ItemPedido> sortearItens(boolean movimentado) {
        Map<String, ItemPedido> itens = new LinkedHashMap<>();
        int pizzas = random.nextDouble() < 0.88 ? (random.nextDouble() < 0.35 ? 2 : 1) : 0;
        if (pizzas > 0 && movimentado && random.nextDouble() < 0.2) {
            pizzas++;
        }
        for (int i = 0; i < pizzas; i++) {
            adicionar(itens, "Pizza", 1);
        }
        if (pizzas == 0 || random.nextDouble() < 0.25) {
            adicionar(itens, "Esfiha", 4 + random.nextInt(9));
        }
        if (random.nextDouble() < 0.7) {
            adicionar(itens, "Bebida", random.nextDouble() < 0.25 ? 2 : 1);
        }
        if (pizzas > 0 && random.nextDouble() < 0.2) {
            adicionar(itens, "Adicional", 1);
        }
        if (random.nextDouble() < 0.12) {
            adicionar(itens, "Porção", 1);
        }
        if (itens.isEmpty()) {
            adicionar(itens, produtos.get(random.nextInt(produtos.size())), 1);
        }
        return new ArrayList<>(itens.values());
    }

    private void adicionar(Map<String, ItemPedido> itens, String tipo, int quantidade) {
        List<Produto> candidatos = produtos.stream().filter(p -> tipo.equalsIgnoreCase(p.getTipo())).toList();
        if (!candidatos.isEmpty()) {
            adicionar(itens, sortearPonderado(candidatos), quantidade);
        }
    }

    private void adicionar(Map<String, ItemPedido> itens, Produto produto, int quantidade) {
        ItemPedido item = itens.computeIfAbsent(produto.getId(), id -> {
            ItemPedido novo = new ItemPedido();
            novo.setProdutoId(produto.getId());
            novo.setNomeProduto(produto.getNome());
            novo.setTipoProduto(produto.getTipo());
            novo.setPrecoUnitario(BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP));
            novo.setQuantidade(0);
            return novo;
        });
        item.setQuantidade(item.getQuantidade() + quantidade);
        item.setSubtotal(item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())));
    }

    private Produto sortearPonderado(List<Produto> candidatos) {
        int[] pesos = candidatos.stream().mapToInt(GeradorPedidosDemonstracao::popularidade).toArray();
        int total = 0;
        for (int peso : pesos) {
            total += peso;
        }
        int sorteio = random.nextInt(total);
        for (int i = 0; i < candidatos.size(); i++) {
            sorteio -= pesos[i];
            if (sorteio < 0) {
                return candidatos.get(i);
            }
        }
        return candidatos.get(candidatos.size() - 1);
    }

    private int sortearHora() {
        int total = 0;
        for (int peso : PESOS_HORAS) {
            total += peso;
        }
        int sorteio = random.nextInt(total);
        for (int i = 0; i < HORAS.length; i++) {
            sorteio -= PESOS_HORAS[i];
            if (sorteio < 0) {
                return HORAS[i];
            }
        }
        return HORAS[HORAS.length - 1];
    }

    private static int popularidade(Produto produto) {
        String nome = produto.getNome() == null ? "" : produto.getNome().toLowerCase(Locale.ROOT);
        return POPULARIDADE.entrySet().stream()
                .filter(e -> nome.contains(e.getKey()))
                .mapToInt(Map.Entry::getValue)
                .max()
                .orElse(6);
    }

    private static boolean isMovimentado(DayOfWeek dia) {
        return dia == DayOfWeek.FRIDAY || dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
    }

    private static String perfil(String usuario) {
        return "gerente".equals(usuario) ? "GERENTE" : "ATENDENTE";
    }
}
