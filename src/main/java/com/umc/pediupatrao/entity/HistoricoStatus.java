package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;

/** Registro de cada mudança de status do pedido (linha do tempo exibida no detalhe). */
public class HistoricoStatus {

    private StatusPedido statusAnterior;
    private StatusPedido statusNovo;
    private LocalDateTime dataHora;
    private String usuario;
    private String perfil;
    private String observacao;

    public HistoricoStatus() {
    }

    public HistoricoStatus(StatusPedido statusAnterior, StatusPedido statusNovo, LocalDateTime dataHora,
            String usuario, String perfil, String observacao) {
        this.statusAnterior = statusAnterior;
        this.statusNovo = statusNovo;
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.perfil = perfil;
        this.observacao = observacao;
    }

    public StatusPedido getStatusAnterior() {
        return statusAnterior;
    }

    public void setStatusAnterior(StatusPedido statusAnterior) {
        this.statusAnterior = statusAnterior;
    }

    public StatusPedido getStatusNovo() {
        return statusNovo;
    }

    public void setStatusNovo(StatusPedido statusNovo) {
        this.statusNovo = statusNovo;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getPerfil() {
        return perfil;
    }

    public void setPerfil(String perfil) {
        this.perfil = perfil;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }
}
