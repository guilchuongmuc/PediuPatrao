package com.umc.pediupatrao.dto.dashboard;

import java.time.LocalDate;

public record Periodo(LocalDate inicio, LocalDate fim, long dias) {
}
