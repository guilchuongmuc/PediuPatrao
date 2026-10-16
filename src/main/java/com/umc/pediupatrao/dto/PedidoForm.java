package com.umc.pediupatrao.dto;

import java.util.ArrayList;
import java.util.List;

import com.umc.pediupatrao.entity.TipoAtendimento;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


public class PedidoForm {

    @NotBlank(message = "Selecione o cliente.")
    private String clienteId;

    @NotNull(message = "Informe se o pedido é para entrega ou retirada.")
    private TipoAtendimento tipoAtendimento;

    @Size(max = 500, message = "As observações devem ter no máximo 500 caracteres.")
    private String observacoes;

    @Valid
    @NotEmpty(message = "Inclua ao menos um item no pedido.")
    @Size(max = 30, message = "O pedido pode ter no máximo 30 itens.")
    private List<ItemPedidoForm> itens = new ArrayList<>();

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public TipoAtendimento getTipoAtendimento() {
        return tipoAtendimento;
    }

    public void setTipoAtendimento(TipoAtendimento tipoAtendimento) {
        this.tipoAtendimento = tipoAtendimento;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public List<ItemPedidoForm> getItens() {
        return itens;
    }

    public void setItens(List<ItemPedidoForm> itens) {
        this.itens = itens;
    }
}
