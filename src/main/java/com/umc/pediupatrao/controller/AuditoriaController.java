package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.AuditoriaFiltro;
import com.umc.pediupatrao.dto.ResultadoIntegridade;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta da auditoria (ADMIN e GERENTE). Somente leitura: não há POST, PUT ou DELETE,
 * e o SecurityConfig nega qualquer outro método nestas rotas.
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public Map<String, Object> consultar(AuditoriaFiltro filtro,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 100));
        Page<RegistroAuditoria> registros = auditoriaService.consultar(filtro, pageRequest);
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("registros", registros.getContent());
        resposta.put("pagina", registros.getNumber());
        resposta.put("tamanho", registros.getSize());
        resposta.put("totalRegistros", registros.getTotalElements());
        resposta.put("totalPaginas", registros.getTotalPages());
        return resposta;
    }

    @GetMapping("/integridade")
    public ResultadoIntegridade verificarIntegridade() {
        return auditoriaService.verificarIntegridade();
    }

    @GetMapping("/{id}")
    public RegistroAuditoria buscar(@PathVariable String id) {
        return auditoriaService.buscarPorId(id);
    }
}
