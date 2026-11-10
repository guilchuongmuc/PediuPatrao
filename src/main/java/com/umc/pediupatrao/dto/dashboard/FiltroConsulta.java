package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record FiltroConsulta(LocalDate inicio, LocalDate fim, Set<StatusPedido> status,
        TipoAtendimento tipoAtendimento, String categoria) {

    public FiltroConsulta {
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fim, "fim");
        status = copiar(status);
    }

    public static FiltroConsulta periodo(LocalDate inicio, LocalDate fim) {
        return new FiltroConsulta(inicio, fim, Set.of(), null, null);
    }

    public LocalDateTime inicioInclusivo() {
        return inicio.atStartOfDay();
    }

    public LocalDateTime fimExclusivo() {
        return fim.plusDays(1).atStartOfDay();
    }

    public long dias() {
        return ChronoUnit.DAYS.between(inicio, fim) + 1;
    }

    public Periodo periodo() {
        return new Periodo(inicio, fim, dias());
    }

    public FiltroConsulta periodoAnterior() {
        LocalDate fimAnterior = inicio.minusDays(1);
        return new FiltroConsulta(fimAnterior.minusDays(dias() - 1), fimAnterior, status, tipoAtendimento, categoria);
    }

    private static Set<StatusPedido> copiar(Collection<StatusPedido> status) {
        if (status == null) {
            return Set.of();
        }
        EnumSet<StatusPedido> copia = EnumSet.noneOf(StatusPedido.class);
        status.stream().filter(Objects::nonNull).forEach(copia::add);
        return Collections.unmodifiableSet(copia);
    }
}
