package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.CancelamentoForm;
import com.umc.pediupatrao.dto.DescontoForm;
import com.umc.pediupatrao.dto.PedidoForm;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.PedidoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API de pedidos. O corpo aceito (PedidoForm) não contém valores, status ou responsáveis:
 * esses dados são definidos pelo servidor.
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<Pedido> criarPedido(@Valid @RequestBody PedidoForm pedido) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.criarPedido(pedido));
    }

    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @GetMapping("/{id}")
    public Pedido buscarPedido(@PathVariable String id) {
        return pedidoService.buscarPorId(id);
    }

    @PutMapping("/{id}")
    public Pedido editarPedido(@PathVariable String id, @Valid @RequestBody PedidoForm pedido) {
        return pedidoService.editarPedido(id, pedido);
    }

    /** Avança o fluxo: ?status=EM_PREPARACAO | PRONTO | SAIU_PARA_ENTREGA | RETIRADO | FINALIZADO */
    @PutMapping("/{id}/status")
    public Pedido atualizarStatus(@PathVariable String id, @RequestParam String status) {
        return pedidoService.atualizarStatus(id, status);
    }

    /** Exclusivo do GERENTE: {"percentual": 10, "justificativa": "..."} */
    @PostMapping("/{id}/desconto")
    public Pedido aplicarDesconto(@PathVariable String id, @Valid @RequestBody DescontoForm desconto) {
        return pedidoService.aplicarDesconto(id, desconto.getPercentual(), desconto.getJustificativa());
    }

    /** Exclusivo do GERENTE: {"justificativa": "..."} */
    @PostMapping("/{id}/cancelamento")
    public Pedido cancelarPedido(@PathVariable String id, @Valid @RequestBody CancelamentoForm cancelamento) {
        return pedidoService.cancelarPedido(id, cancelamento.getJustificativa());
    }
}
