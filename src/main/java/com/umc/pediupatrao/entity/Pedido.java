package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Pedido da pizzaria. Valores (subtotal, desconto e total) são sempre calculados no servidor
 * pelo PedidoService; não existem endpoints que aceitem esses campos do cliente HTTP.
 */
@Document(collection = "pedidos")
public class Pedido {

    @Id
    private String id;
    private Long numero; // número sequencial exibido para a equipe (#1, #2...)

    // Cliente (nome copiado no registro para preservar o histórico)
    private String clienteId;
    private String clienteNome;
    private TipoAtendimento tipoAtendimento;
    private String enderecoEntrega;
    private String observacoes;

    // Itens e valores
    private List<ItemPedido> itens = new ArrayList<>();
    private BigDecimal subtotal;
    private BigDecimal percentualDesconto;
    private BigDecimal valorDesconto;
    private BigDecimal valorTotal;

    private StatusPedido status;

    // Entrada
    private LocalDateTime dataHoraEntrada;
    private String responsavelEntrada;

    // Saída (SAIU_PARA_ENTREGA ou RETIRADO)
    private LocalDateTime dataHoraSaida;
    private String responsavelSaida;

    // Finalização
    private LocalDateTime dataHoraFinalizacao;
    private String responsavelFinalizacao;

    // Desconto (exclusivo do GERENTE)
    private LocalDateTime dataHoraDesconto;
    private String responsavelDesconto;
    private String justificativaDesconto;

    // Cancelamento (exclusivo do GERENTE)
    private LocalDateTime dataHoraCancelamento;
    private String responsavelCancelamento;
    private String justificativaCancelamento;

    private List<HistoricoStatus> historico = new ArrayList<>();

    @Version
    private Long versao; // controle de concorrência otimista

    // Campos da versão anterior do sistema, mantidos para leitura de pedidos antigos
    private String tipoPizza;
    private String sabor;
    private Integer quantidade;

    @Transient
    public String getIdentificacao() {
        return numero != null ? "Pedido #" + numero : "Pedido " + id;
    }

    @Transient
    public Set<StatusPedido> getProximosStatus() {
        return status == null ? Set.of() : status.proximosNoFluxo(tipoAtendimento);
    }

    @Transient
    public boolean isPermiteDescontoOuCancelamento() {
        return status != null && status.permiteDescontoOuCancelamento();
    }

    @Transient
    public boolean isPermiteEdicao() {
        return status != null && status.permiteEdicao();
    }

    public void registrarHistorico(HistoricoStatus registro) {
        if (historico == null) {
            historico = new ArrayList<>();
        }
        historico.add(registro);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getNumero() {
        return numero;
    }

    public void setNumero(Long numero) {
        this.numero = numero;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public String getClienteNome() {
        return clienteNome;
    }

    public void setClienteNome(String clienteNome) {
        this.clienteNome = clienteNome;
    }

    public TipoAtendimento getTipoAtendimento() {
        return tipoAtendimento;
    }

    public void setTipoAtendimento(TipoAtendimento tipoAtendimento) {
        this.tipoAtendimento = tipoAtendimento;
    }

    public String getEnderecoEntrega() {
        return enderecoEntrega;
    }

    public void setEnderecoEntrega(String enderecoEntrega) {
        this.enderecoEntrega = enderecoEntrega;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public List<ItemPedido> getItens() {
        return itens;
    }

    public void setItens(List<ItemPedido> itens) {
        this.itens = itens;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getPercentualDesconto() {
        return percentualDesconto;
    }

    public void setPercentualDesconto(BigDecimal percentualDesconto) {
        this.percentualDesconto = percentualDesconto;
    }

    public BigDecimal getValorDesconto() {
        return valorDesconto;
    }

    public void setValorDesconto(BigDecimal valorDesconto) {
        this.valorDesconto = valorDesconto;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public void setStatus(StatusPedido status) {
        this.status = status;
    }

    public LocalDateTime getDataHoraEntrada() {
        return dataHoraEntrada;
    }

    public void setDataHoraEntrada(LocalDateTime dataHoraEntrada) {
        this.dataHoraEntrada = dataHoraEntrada;
    }

    public String getResponsavelEntrada() {
        return responsavelEntrada;
    }

    public void setResponsavelEntrada(String responsavelEntrada) {
        this.responsavelEntrada = responsavelEntrada;
    }

    public LocalDateTime getDataHoraSaida() {
        return dataHoraSaida;
    }

    public void setDataHoraSaida(LocalDateTime dataHoraSaida) {
        this.dataHoraSaida = dataHoraSaida;
    }

    public String getResponsavelSaida() {
        return responsavelSaida;
    }

    public void setResponsavelSaida(String responsavelSaida) {
        this.responsavelSaida = responsavelSaida;
    }

    public LocalDateTime getDataHoraFinalizacao() {
        return dataHoraFinalizacao;
    }

    public void setDataHoraFinalizacao(LocalDateTime dataHoraFinalizacao) {
        this.dataHoraFinalizacao = dataHoraFinalizacao;
    }

    public String getResponsavelFinalizacao() {
        return responsavelFinalizacao;
    }

    public void setResponsavelFinalizacao(String responsavelFinalizacao) {
        this.responsavelFinalizacao = responsavelFinalizacao;
    }

    public LocalDateTime getDataHoraDesconto() {
        return dataHoraDesconto;
    }

    public void setDataHoraDesconto(LocalDateTime dataHoraDesconto) {
        this.dataHoraDesconto = dataHoraDesconto;
    }

    public String getResponsavelDesconto() {
        return responsavelDesconto;
    }

    public void setResponsavelDesconto(String responsavelDesconto) {
        this.responsavelDesconto = responsavelDesconto;
    }

    public String getJustificativaDesconto() {
        return justificativaDesconto;
    }

    public void setJustificativaDesconto(String justificativaDesconto) {
        this.justificativaDesconto = justificativaDesconto;
    }

    public LocalDateTime getDataHoraCancelamento() {
        return dataHoraCancelamento;
    }

    public void setDataHoraCancelamento(LocalDateTime dataHoraCancelamento) {
        this.dataHoraCancelamento = dataHoraCancelamento;
    }

    public String getResponsavelCancelamento() {
        return responsavelCancelamento;
    }

    public void setResponsavelCancelamento(String responsavelCancelamento) {
        this.responsavelCancelamento = responsavelCancelamento;
    }

    public String getJustificativaCancelamento() {
        return justificativaCancelamento;
    }

    public void setJustificativaCancelamento(String justificativaCancelamento) {
        this.justificativaCancelamento = justificativaCancelamento;
    }

    public List<HistoricoStatus> getHistorico() {
        return historico;
    }

    public void setHistorico(List<HistoricoStatus> historico) {
        this.historico = historico;
    }

    public Long getVersao() {
        return versao;
    }

    public void setVersao(Long versao) {
        this.versao = versao;
    }

    public String getTipoPizza() {
        return tipoPizza;
    }

    public void setTipoPizza(String tipoPizza) {
        this.tipoPizza = tipoPizza;
    }

    public String getSabor() {
        return sabor;
    }

    public void setSabor(String sabor) {
        this.sabor = sabor;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }
}
