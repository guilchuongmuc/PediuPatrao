package com.umc.pediupatrao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboardParams;
import com.umc.pediupatrao.dto.dashboard.Periodo;
import com.umc.pediupatrao.dto.dashboard.RankingProdutosResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoResponse;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    private static final Periodo PERIODO = new Periodo(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), 10);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void semAutenticacaoRetorna401EmJson() throws Exception {
        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verify(dashboardService, never()).resumo(any());
    }

    @Test
    @WithMockUser(username = "atendente", roles = "ATENDENTE")
    void atendenteRecebe403() throws Exception {
        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verify(dashboardService, never()).resumo(any());
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void gerenteConsultaOResumoComFiltros() throws Exception {
        when(dashboardService.resumo(any())).thenReturn(resumo());

        mvc.perform(get("/api/dashboard/resumo")
                        .param("inicio", "2026-11-01")
                        .param("fim", "2026-11-10")
                        .param("status", "FINALIZADO,SAIU_PARA_ENTREGA")
                        .param("tipoAtendimento", "ENTREGA")
                        .param("categoria", "Pizza"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faturamento").value(1234.5))
                .andExpect(jsonPath("$.totalPedidos").value(30))
                .andExpect(jsonPath("$.periodo.inicio").value("2026-11-01"))
                .andExpect(jsonPath("$.comparacao.faturamento.variacaoPercentual").value(12.5));

        ArgumentCaptor<FiltroDashboardParams> filtro = ArgumentCaptor.forClass(FiltroDashboardParams.class);
        verify(dashboardService).resumo(filtro.capture());
        assertThat(filtro.getValue().inicio()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(filtro.getValue().fim()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(filtro.getValue().status()).containsExactly(StatusPedido.FINALIZADO, StatusPedido.SAIU_PARA_ENTREGA);
        assertThat(filtro.getValue().tipoAtendimento()).isEqualTo(TipoAtendimento.ENTREGA);
        assertThat(filtro.getValue().categoria()).isEqualTo("Pizza");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminConsultaORanking() throws Exception {
        when(dashboardService.produtosMaisVendidos(any(), eq(CriterioRanking.FATURAMENTO), eq(5)))
                .thenReturn(new RankingProdutosResponse(PERIODO, CriterioRanking.FATURAMENTO, 5, 1, List.of(
                        new RankingProdutosResponse.Produto(1, "p1", "Pizza Calabresa", "Pizza", 12, 10,
                                new BigDecimal("598.80"), new BigDecimal("100.00")))));

        mvc.perform(get("/api/dashboard/produtos/mais-vendidos").param("criterio", "FATURAMENTO").param("limite", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criterio").value("FATURAMENTO"))
                .andExpect(jsonPath("$.produtos[0].nome").value("Pizza Calabresa"))
                .andExpect(jsonPath("$.produtos[0].posicao").value(1));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void agrupamentoPadraoEhDia() throws Exception {
        mvc.perform(get("/api/dashboard/vendas")).andExpect(status().isOk());
        verify(dashboardService).vendas(any(), eq(Agrupamento.DIA));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void limiteNaoInformadoChegaNuloAoServico() throws Exception {
        mvc.perform(get("/api/dashboard/pedidos/recentes")).andExpect(status().isOk());
        verify(dashboardService).pedidosRecentes(any(), isNull());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void dataEmFormatoInvalidoRetorna400() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").param("inicio", "10/11/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.campos.inicio").exists());
        verify(dashboardService, never()).resumo(any());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void statusDesconhecidoRetorna400() throws Exception {
        mvc.perform(get("/api/dashboard/pedidos/status").param("status", "ENTREGUE_ONTEM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.status").exists());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void agrupamentoInvalidoRetorna400() throws Exception {
        mvc.perform(get("/api/dashboard/vendas").param("agrupamento", "ANO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Valor inválido para o parâmetro 'agrupamento'."))
                .andExpect(jsonPath("$.campos.agrupamento").exists());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void regraDeParametroDoServicoRetorna400() throws Exception {
        when(dashboardService.resumo(any())).thenThrow(
                new ParametroInvalidoException("inicio", "A data inicial não pode ser posterior à data final."));

        mvc.perform(get("/api/dashboard/resumo").param("inicio", "2026-11-10").param("fim", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Parâmetro inválido"))
                .andExpect(jsonPath("$.campos.inicio").value("A data inicial não pode ser posterior à data final."));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void dashboardEhSomenteLeitura() throws Exception {
        mvc.perform(post("/api/dashboard/resumo").with(csrf()))
                .andExpect(status().isForbidden());
    }

    private static ResumoResponse resumo() {
        ResumoResponse.Comparacao comparacao = new ResumoResponse.Comparacao(new BigDecimal("1097.33"),
                new BigDecimal("12.50"));
        return new ResumoResponse(PERIODO, PERIODO, new BigDecimal("1234.50"), 30, 28, 2, new BigDecimal("6.67"),
                new BigDecimal("80.00"), new BigDecimal("44.09"), new BigDecimal("15.00"), 70,
                new ResumoResponse.Comparacoes(comparacao, comparacao, comparacao, comparacao));
    }
}
