package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Compara dois "retratos" (campo -> valor) de uma entidade e lista os campos alterados. */
public final class DiferencaCampos {

    private DiferencaCampos() {
    }

    public static List<AlteracaoCampo> comparar(Map<String, String> antes, Map<String, String> depois) {
        Map<String, String> anterior = antes == null ? Map.of() : antes;
        Map<String, String> novo = depois == null ? Map.of() : depois;

        Set<String> campos = new LinkedHashSet<>(anterior.keySet());
        campos.addAll(novo.keySet());

        List<AlteracaoCampo> alteracoes = new ArrayList<>();
        for (String campo : campos) {
            String valorAnterior = normalizar(anterior.get(campo));
            String valorNovo = normalizar(novo.get(campo));
            if (!Objects.equals(valorAnterior, valorNovo)) {
                alteracoes.add(new AlteracaoCampo(campo, valorAnterior, valorNovo));
            }
        }
        return alteracoes;
    }

    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
