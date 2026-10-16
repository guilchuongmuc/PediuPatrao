package com.umc.pediupatrao.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Item enviado pelo formulário/API: apenas produto e quantidade. O preço vem do cadastro do produto. */
public class ItemPedidoForm {

    @NotBlank(message = "Selecione o produto.")
    private String produtoId;

    @NotNull(message = "Informe a quantidade.")
    @Min(value = 1, message = "A quantidade mínima é 1.")
    @Max(value = 50, message = "A quantidade máxima por item é 50.")
    private Integer quantidade;

    public ItemPedidoForm() {
    }

    public ItemPedidoForm(String produtoId, Integer quantidade) {
        this.produtoId = produtoId;
        this.quantidade = quantidade;
    }

    public String getProdutoId() {
        return produtoId;
    }

    public void setProdutoId(String produtoId) {
        this.produtoId = produtoId;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }
}
