package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.dto.AuditoriaFiltro;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Consulta da auditoria (ADMIN e GERENTE). Tela somente leitura. */
@Controller
@RequestMapping("/auditoria")
public class AuditoriaWebController {

    private static final int TAMANHO_PAGINA = 20;
    private static final List<String> ENTIDADES = List.of("PEDIDO", "CLIENTE", "PRODUTO", "USUARIO", "ROTA");

    private final AuditoriaService auditoriaService;

    public AuditoriaWebController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public String consultar(@ModelAttribute("filtro") AuditoriaFiltro filtro,
            @RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("filtro", filtro);
        model.addAttribute("registros",
                auditoriaService.consultar(filtro, PageRequest.of(Math.max(pagina, 0), TAMANHO_PAGINA)));
        model.addAttribute("operacoes", TipoOperacao.values());
        model.addAttribute("entidades", ENTIDADES);
        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }

    @GetMapping("/integridade")
    public String verificarIntegridade(Model model) {
        model.addAttribute("integridade", auditoriaService.verificarIntegridade());
        return consultar(new AuditoriaFiltro(), 0, model);
    }
}
