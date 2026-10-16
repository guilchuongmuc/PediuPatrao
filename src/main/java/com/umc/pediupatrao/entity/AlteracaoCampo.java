package com.umc.pediupatrao.entity;

import org.springframework.data.annotation.PersistenceCreator;

/** Campo modificado em um evento de auditoria, com os valores anterior e novo. Imutável. */
public class AlteracaoCampo {

    private final String campo;
    private final String valorAnterior;
    private final String valorNovo;

    @PersistenceCreator
    public AlteracaoCampo(String campo, String valorAnterior, String valorNovo) {
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
    }

    public String getCampo() {
        return campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNovo() {
        return valorNovo;
    }
}
