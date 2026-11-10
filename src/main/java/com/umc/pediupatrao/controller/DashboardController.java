package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboardParams;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltroResponse;
import com.umc.pediupatrao.dto.dashboard.PedidosRecentesResponse;
import com.umc.pediupatrao.dto.dashboard.RankingProdutosResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoResponse;
import com.umc.pediupatrao.dto.dashboard.SerieVendasResponse;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidosResponse;
import com.umc.pediupatrao.dto.dashboard.VendasPorAtendimentoResponse;
import com.umc.pediupatrao.dto.dashboard.VendasPorCategoriaResponse;
import com.umc.pediupatrao.dto.dashboard.VendasPorHorarioResponse;
import com.umc.pediupatrao.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumo")
    public ResumoResponse resumo(@ModelAttribute FiltroDashboardParams filtro) {
        return dashboardService.resumo(filtro);
    }

    @GetMapping("/vendas")
    public SerieVendasResponse vendas(@ModelAttribute FiltroDashboardParams filtro,
            @RequestParam(defaultValue = "DIA") Agrupamento agrupamento) {
        return dashboardService.vendas(filtro, agrupamento);
    }

    @GetMapping("/vendas/por-horario")
    public VendasPorHorarioResponse vendasPorHorario(@ModelAttribute FiltroDashboardParams filtro) {
        return dashboardService.vendasPorHorario(filtro);
    }

    @GetMapping("/vendas/por-categoria")
    public VendasPorCategoriaResponse vendasPorCategoria(@ModelAttribute FiltroDashboardParams filtro) {
        return dashboardService.vendasPorCategoria(filtro);
    }

    @GetMapping("/vendas/por-atendimento")
    public VendasPorAtendimentoResponse vendasPorAtendimento(@ModelAttribute FiltroDashboardParams filtro) {
        return dashboardService.vendasPorAtendimento(filtro);
    }

    @GetMapping("/pedidos/status")
    public SituacaoPedidosResponse situacaoPedidos(@ModelAttribute FiltroDashboardParams filtro) {
        return dashboardService.situacaoPedidos(filtro);
    }

    @GetMapping("/pedidos/recentes")
    public PedidosRecentesResponse pedidosRecentes(@ModelAttribute FiltroDashboardParams filtro,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.pedidosRecentes(filtro, limite);
    }

    @GetMapping("/produtos/mais-vendidos")
    public RankingProdutosResponse produtosMaisVendidos(@ModelAttribute FiltroDashboardParams filtro,
            @RequestParam(defaultValue = "QUANTIDADE") CriterioRanking criterio,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.produtosMaisVendidos(filtro, criterio, limite);
    }

    @GetMapping("/filtros")
    public OpcoesFiltroResponse filtros() {
        return dashboardService.opcoesDeFiltro();
    }
}
