package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.ItemPedidoForm;
import com.umc.pediupatrao.dto.PedidoForm;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Regras de negócio dos pedidos: registro com cálculo de valores no servidor, fluxo de status,
 * desconto e cancelamento (exclusivos do GERENTE) e auditoria de todas as alterações.
 * A autorização é aplicada aqui (@PreAuthorize) além das regras de URL do SecurityConfig.
 */
@Service
public class PedidoService {

    public static final BigDecimal DESCONTO_MAXIMO = new BigDecimal("20.00");
    public static final int QUANTIDADE_MAXIMA_POR_ITEM = 50;
    public static final int MAXIMO_ITENS = 30;
    public static final int JUSTIFICATIVA_MINIMA = 10;
    public static final int JUSTIFICATIVA_MAXIMA = 500;
    static final String ENTIDADE = "PEDIDO";

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;
    private final SequenciaService sequenciaService;
    private final Clock clock;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
            ProdutoRepository produtoRepository, AuditoriaService auditoriaService,
            SequenciaService sequenciaService, Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
        this.sequenciaService = sequenciaService;
        this.clock = clock;
    }

    // ========================
    // CONSULTAS
    // ========================
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll(Sort.by(Sort.Direction.DESC, "dataHoraEntrada", "numero"));
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public Pedido buscarPorId(String id) {
        return carregar(id);
    }

    // ========================
    // ENTRADA DO PEDIDO (RF02 / RF03)
    // ========================
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido criarPedido(PedidoForm form) {
        exigirFormulario(form);
        Cliente cliente = carregarCliente(form.getClienteId());
        TipoAtendimento tipo = exigirTipoAtendimento(form.getTipoAtendimento());
        List<ItemPedido> itens = montarItens(form.getItens());

        LocalDateTime agora = agora();
        String usuario = UsuarioAutenticado.username();

        Pedido pedido = new Pedido();
        pedido.setNumero(sequenciaService.proximo("pedidos"));
        aplicarDados(pedido, cliente, tipo, form.getObservacoes(), itens);
        pedido.setPercentualDesconto(BigDecimal.ZERO.setScale(2));
        recalcularValores(pedido);

        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setDataHoraEntrada(agora);
        pedido.setResponsavelEntrada(usuario);
        pedido.registrarHistorico(new HistoricoStatus(null, StatusPedido.RECEBIDO, agora, usuario,
                UsuarioAutenticado.perfil(), "Entrada do pedido"));

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.INCLUSAO_PEDIDO, ENTIDADE, salvo.getId(), descricao(salvo),
                DiferencaCampos.comparar(Map.of(), retrato(salvo)), null);
        return salvo;
    }

    /** Alteração de cliente, itens e observações: permitida somente enquanto o pedido está RECEBIDO. */
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido editarPedido(String id, PedidoForm form) {
        exigirFormulario(form);
        Pedido pedido = carregar(id);
        garantirNaoCancelado(pedido);
        if (!pedido.getStatus().permiteEdicao()) {
            throw new RegraNegocioException("Somente pedidos com status Recebido podem ser alterados (status atual: "
                    + pedido.getStatus().getDescricao() + ").");
        }
        Cliente cliente = carregarCliente(form.getClienteId());
        TipoAtendimento tipo = exigirTipoAtendimento(form.getTipoAtendimento());
        List<ItemPedido> itens = montarItens(form.getItens());

        Map<String, String> antes = retrato(pedido);
        aplicarDados(pedido, cliente, tipo, form.getObservacoes(), itens);
        recalcularValores(pedido); // mantém o percentual de desconto já autorizado
        List<AlteracaoCampo> alteracoes = DiferencaCampos.comparar(antes, retrato(pedido));
        if (alteracoes.isEmpty()) {
            return pedido;
        }
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.ALTERACAO_PEDIDO, ENTIDADE, salvo.getId(), descricao(salvo),
                alteracoes, null);
        return salvo;
    }

    // ========================
    // FLUXO DE STATUS (RF03)
    // ========================
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido atualizarStatus(String id, String status) {
        StatusPedido novo;
        try {
            novo = StatusPedido.parse(status);
        } catch (IllegalArgumentException e) {
            throw new RegraNegocioException(e.getMessage());
        }
        Pedido pedido = carregar(id);
        garantirNaoCancelado(pedido);
        if (novo == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("O cancelamento deve ser feito pela operação de cancelamento, "
                    + "exclusiva do GERENTE e com justificativa obrigatória.");
        }
        StatusPedido atual = pedido.getStatus();
        if (!atual.podeAvancarPara(novo, pedido.getTipoAtendimento())) {
            Set<StatusPedido> permitidos = pedido.getProximosStatus();
            String proximo = permitidos.isEmpty() ? "nenhum (fluxo encerrado)"
                    : permitidos.stream().map(StatusPedido::getDescricao).collect(Collectors.joining(" ou "));
            throw new RegraNegocioException("Transição inválida: " + atual.getDescricao() + " → "
                    + novo.getDescricao() + ". Próximo status permitido: " + proximo + ".");
        }

        LocalDateTime agora = agora();
        String usuario = UsuarioAutenticado.username();
        List<AlteracaoCampo> alteracoes = new ArrayList<>();
        alteracoes.add(new AlteracaoCampo("status", atual.name(), novo.name()));

        pedido.setStatus(novo);
        if (novo.isSaida()) {
            pedido.setDataHoraSaida(agora);
            pedido.setResponsavelSaida(usuario);
            alteracoes.add(new AlteracaoCampo("dataHoraSaida", null, formatar(agora)));
            alteracoes.add(new AlteracaoCampo("responsavelSaida", null, usuario));
        }
        if (novo == StatusPedido.FINALIZADO) {
            pedido.setDataHoraFinalizacao(agora);
            pedido.setResponsavelFinalizacao(usuario);
            alteracoes.add(new AlteracaoCampo("dataHoraFinalizacao", null, formatar(agora)));
            alteracoes.add(new AlteracaoCampo("responsavelFinalizacao", null, usuario));
        }
        pedido.registrarHistorico(new HistoricoStatus(atual, novo, agora, usuario, UsuarioAutenticado.perfil(), null));

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.MUDANCA_STATUS_PEDIDO, ENTIDADE, salvo.getId(), descricao(salvo),
                alteracoes, null);
        return salvo;
    }

    // ========================
    // DESCONTO E CANCELAMENTO — exclusivos do GERENTE (RF04)
    // ========================
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual, String justificativa) {
        BigDecimal pct = validarPercentual(percentual);
        String motivo = textoOpcional(justificativa, 300, "A justificativa do desconto deve ter no máximo 300 caracteres.");

        Pedido pedido = carregar(id);
        garantirNaoCancelado(pedido);
        if (!pedido.getStatus().permiteDescontoOuCancelamento()) {
            throw new RegraNegocioException("O desconto só pode ser aplicado antes da saída para entrega ou retirada"
                    + " (status atual: " + pedido.getStatus().getDescricao() + ").");
        }

        Map<String, String> antes = retratoValores(pedido);
        pedido.setPercentualDesconto(pct);
        recalcularValores(pedido);
        List<AlteracaoCampo> alteracoes = DiferencaCampos.comparar(antes, retratoValores(pedido));
        if (alteracoes.isEmpty()) {
            throw new RegraNegocioException("O pedido já está com desconto de " + pct.toPlainString() + "%.");
        }
        pedido.setDataHoraDesconto(agora());
        pedido.setResponsavelDesconto(UsuarioAutenticado.username());
        pedido.setJustificativaDesconto(motivo);

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.DESCONTO_PEDIDO, ENTIDADE, salvo.getId(), descricao(salvo),
                alteracoes, motivo);
        return salvo;
    }

    /** Cancela o pedido preservando itens e valores; depois disso nenhuma alteração é aceita. */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelarPedido(String id, String justificativa) {
        Pedido pedido = carregar(id);
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("O pedido já está cancelado.");
        }
        if (!pedido.getStatus().permiteDescontoOuCancelamento()) {
            throw new RegraNegocioException("O pedido não pode ser cancelado após a saída para entrega ou retirada"
                    + " (status atual: " + pedido.getStatus().getDescricao() + ").");
        }
        String motivo = justificativa == null ? "" : justificativa.trim();
        if (motivo.length() < JUSTIFICATIVA_MINIMA || motivo.length() > JUSTIFICATIVA_MAXIMA) {
            throw new RegraNegocioException("A justificativa do cancelamento é obrigatória e deve ter entre "
                    + JUSTIFICATIVA_MINIMA + " e " + JUSTIFICATIVA_MAXIMA + " caracteres.");
        }

        StatusPedido anterior = pedido.getStatus();
        LocalDateTime agora = agora();
        String usuario = UsuarioAutenticado.username();
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setDataHoraCancelamento(agora);
        pedido.setResponsavelCancelamento(usuario);
        pedido.setJustificativaCancelamento(motivo);
        pedido.registrarHistorico(new HistoricoStatus(anterior, StatusPedido.CANCELADO, agora, usuario,
                UsuarioAutenticado.perfil(), motivo));

        Pedido salvo = pedidoRepository.save(pedido);
        List<AlteracaoCampo> alteracoes = List.of(
                new AlteracaoCampo("status", anterior.name(), StatusPedido.CANCELADO.name()),
                new AlteracaoCampo("dataHoraCancelamento", null, formatar(agora)),
                new AlteracaoCampo("responsavelCancelamento", null, usuario));
        auditoriaService.registrar(TipoOperacao.CANCELAMENTO_PEDIDO, ENTIDADE, salvo.getId(), descricao(salvo),
                alteracoes, motivo);
        return salvo;
    }

    // ========================
    // CÁLCULOS E VALIDAÇÕES
    // ========================
    /** Subtotal = soma dos itens; desconto = subtotal x percentual; total = subtotal - desconto. */
    static void recalcularValores(Pedido pedido) {
        BigDecimal subtotal = pedido.getItens().stream()
                .map(ItemPedido::getSubtotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal percentual = pedido.getPercentualDesconto() == null ? BigDecimal.ZERO : pedido.getPercentualDesconto();
        BigDecimal desconto = subtotal.multiply(percentual).divide(CEM, 2, RoundingMode.HALF_UP);
        pedido.setSubtotal(subtotal);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(subtotal.subtract(desconto));
    }

    private List<ItemPedido> montarItens(List<ItemPedidoForm> itensForm) {
        if (itensForm == null || itensForm.isEmpty()) {
            throw new RegraNegocioException("Inclua ao menos um item no pedido.");
        }
        if (itensForm.size() > MAXIMO_ITENS) {
            throw new RegraNegocioException("O pedido pode ter no máximo " + MAXIMO_ITENS + " itens.");
        }
        // Itens repetidos do mesmo produto são somados em uma única linha
        Map<String, Integer> quantidades = new LinkedHashMap<>();
        for (int i = 0; i < itensForm.size(); i++) {
            ItemPedidoForm item = itensForm.get(i);
            if (item == null || item.getProdutoId() == null || item.getProdutoId().isBlank()) {
                throw new RegraNegocioException("Selecione o produto do item " + (i + 1) + ".");
            }
            Integer quantidade = item.getQuantidade();
            if (quantidade == null || quantidade < 1 || quantidade > QUANTIDADE_MAXIMA_POR_ITEM) {
                throw new RegraNegocioException("A quantidade do item " + (i + 1) + " deve estar entre 1 e "
                        + QUANTIDADE_MAXIMA_POR_ITEM + ".");
            }
            quantidades.merge(item.getProdutoId().trim(), quantidade, Integer::sum);
        }

        List<ItemPedido> itens = new ArrayList<>();
        for (Map.Entry<String, Integer> entrada : quantidades.entrySet()) {
            Produto produto = produtoRepository.findById(entrada.getKey())
                    .orElseThrow(() -> new RegraNegocioException("Produto não encontrado: " + entrada.getKey()));
            int quantidade = entrada.getValue();
            if (quantidade > QUANTIDADE_MAXIMA_POR_ITEM) {
                throw new RegraNegocioException("A quantidade total do produto " + produto.getNome()
                        + " não pode passar de " + QUANTIDADE_MAXIMA_POR_ITEM + ".");
            }
            if (!produto.isDisponivel()) {
                throw new RegraNegocioException("O produto " + produto.getNome() + " está inativo e não pode ser vendido.");
            }
            if (produto.getPreco() == null || produto.getPreco() <= 0) {
                throw new RegraNegocioException("O produto " + produto.getNome() + " está sem preço válido.");
            }
            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);

            ItemPedido item = new ItemPedido();
            item.setProdutoId(produto.getId());
            item.setNomeProduto(produto.getNome());
            item.setTipoProduto(produto.getTipo());
            item.setPrecoUnitario(preco);
            item.setQuantidade(quantidade);
            item.setSubtotal(preco.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_UP));
            itens.add(item);
        }
        return itens;
    }

    private void aplicarDados(Pedido pedido, Cliente cliente, TipoAtendimento tipo, String observacoes,
            List<ItemPedido> itens) {
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setTipoAtendimento(tipo);
        if (tipo == TipoAtendimento.ENTREGA) {
            String endereco = cliente.getEnderecoFormatado();
            if (endereco == null) {
                throw new RegraNegocioException("O cliente " + cliente.getNome()
                        + " não possui endereço cadastrado. Atualize o cadastro ou escolha retirada no balcão.");
            }
            pedido.setEnderecoEntrega(endereco);
        } else {
            pedido.setEnderecoEntrega(null);
        }
        pedido.setObservacoes(textoOpcional(observacoes, 500, "As observações devem ter no máximo 500 caracteres."));
        pedido.setItens(itens);
    }

    private static BigDecimal validarPercentual(BigDecimal percentual) {
        if (percentual == null) {
            throw new RegraNegocioException("Informe o percentual de desconto.");
        }
        if (percentual.signum() < 0) {
            throw new RegraNegocioException("O desconto não pode ser negativo.");
        }
        if (percentual.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new RegraNegocioException("O desconto máximo permitido é de 20% do valor do pedido.");
        }
        if (percentual.stripTrailingZeros().scale() > 2) {
            throw new RegraNegocioException("Use no máximo duas casas decimais no percentual de desconto.");
        }
        return percentual.setScale(2, RoundingMode.UNNECESSARY);
    }

    private Pedido carregar(String id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
        if (pedido.getStatus() == null) {
            pedido.setStatus(StatusPedido.RECEBIDO); // pedidos antigos sem status
        }
        return pedido;
    }

    private Cliente carregarCliente(String clienteId) {
        if (clienteId == null || clienteId.isBlank()) {
            throw new RegraNegocioException("Selecione o cliente.");
        }
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado."));
    }

    private static void exigirFormulario(PedidoForm form) {
        if (form == null) {
            throw new RegraNegocioException("Dados do pedido não informados.");
        }
    }

    private static TipoAtendimento exigirTipoAtendimento(TipoAtendimento tipo) {
        if (tipo == null) {
            throw new RegraNegocioException("Informe se o pedido é para entrega ou retirada.");
        }
        return tipo;
    }

    private static void garantirNaoCancelado(Pedido pedido) {
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode ser alterado.");
        }
    }

    private static String textoOpcional(String texto, int maximo, String mensagem) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpo = texto.trim();
        if (limpo.length() > maximo) {
            throw new RegraNegocioException(mensagem);
        }
        return limpo;
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    // ========================
    // RETRATOS PARA AUDITORIA
    // ========================
    private static String descricao(Pedido pedido) {
        return pedido.getIdentificacao() + " — cliente " + pedido.getClienteNome();
    }

    /** Campos relevantes do pedido (sem dados pessoais além do nome do cliente). */
    private static Map<String, String> retrato(Pedido pedido) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("cliente", pedido.getClienteNome() + " (" + pedido.getClienteId() + ")");
        campos.put("tipoAtendimento", pedido.getTipoAtendimento() == null ? null : pedido.getTipoAtendimento().name());
        campos.put("itens", pedido.getItens().stream()
                .map(i -> i.getQuantidade() + "x " + i.getNomeProduto() + " @ " + i.getPrecoUnitario().toPlainString())
                .collect(Collectors.joining("; ")));
        campos.put("observacoes", pedido.getObservacoes());
        campos.putAll(retratoValores(pedido));
        campos.put("status", pedido.getStatus() == null ? null : pedido.getStatus().name());
        campos.put("dataHoraEntrada", formatar(pedido.getDataHoraEntrada()));
        campos.put("responsavelEntrada", pedido.getResponsavelEntrada());
        return campos;
    }

    private static Map<String, String> retratoValores(Pedido pedido) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("subtotal", texto(pedido.getSubtotal()));
        campos.put("percentualDesconto", texto(pedido.getPercentualDesconto()));
        campos.put("valorDesconto", texto(pedido.getValorDesconto()));
        campos.put("valorTotal", texto(pedido.getValorTotal()));
        return campos;
    }

    private static String texto(BigDecimal valor) {
        return valor == null ? null : valor.toPlainString();
    }

    private static String formatar(LocalDateTime dataHora) {
        return dataHora == null ? null : dataHora.format(FORMATO_DATA);
    }
}
