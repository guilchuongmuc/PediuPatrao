package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


public class DescontoForm {

    @NotNull(message = "Informe o percentual de desconto.")
    @DecimalMin(value = "0.00", message = "O desconto não pode ser negativo.")
    @DecimalMax(value = "20.00", message = "O desconto máximo é de 20% do valor do pedido.")
    @Digits(integer = 2, fraction = 2, message = "Use no máximo duas casas decimais.")
    private BigDecimal percentual;

    @Size(max = 300, message = "A justificativa deve ter no máximo 300 caracteres.")
    private String justificativa;

    public BigDecimal getPercentual() {
        return percentual;
    }

    public void setPercentual(BigDecimal percentual) {
        this.percentual = percentual;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public void setJustificativa(String justificativa) {
        this.justificativa = justificativa;
    }
}
