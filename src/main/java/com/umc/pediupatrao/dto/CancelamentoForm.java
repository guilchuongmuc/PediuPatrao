package com.umc.pediupatrao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CancelamentoForm {

    @NotBlank(message = "A justificativa do cancelamento é obrigatória.")
    @Size(min = 10, max = 500, message = "A justificativa deve ter entre 10 e 500 caracteres.")
    private String justificativa;

    public String getJustificativa() {
        return justificativa;
    }

    public void setJustificativa(String justificativa) {
        this.justificativa = justificativa;
    }
}
