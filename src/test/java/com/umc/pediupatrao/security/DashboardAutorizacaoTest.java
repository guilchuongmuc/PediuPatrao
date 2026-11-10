package com.umc.pediupatrao.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.repository.DashboardRepository;
import com.umc.pediupatrao.service.DashboardService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(DashboardAutorizacaoTest.Config.class)
class DashboardAutorizacaoTest {

    @Configuration
    @EnableMethodSecurity
    @Import(DashboardService.class)
    static class Config {
        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }
    }

    @MockitoBean
    private DashboardRepository dashboardRepository;

    @Autowired
    private DashboardService dashboardService;

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoConsultaODashboard() {
        assertThatThrownBy(() -> dashboardService.resumo(null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> dashboardService.produtosMaisVendidos(null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> dashboardService.opcoesDeFiltro()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteConsultaODashboard() {
        when(dashboardRepository.totais(any())).thenReturn(DashboardRepository.Totais.VAZIO);
        assertThatCode(() -> dashboardService.resumo(null)).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminConsultaODashboard() {
        when(dashboardRepository.totais(any())).thenReturn(DashboardRepository.Totais.VAZIO);
        assertThatCode(() -> dashboardService.resumo(null)).doesNotThrowAnyException();
    }

    @Test
    void semAutenticacaoODashboardEhNegado() {
        assertThatThrownBy(() -> dashboardService.resumo(null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
