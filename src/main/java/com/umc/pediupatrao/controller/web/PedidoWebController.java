package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.dto.ItemPedidoForm;
import com.umc.pediupatrao.dto.PedidoForm;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Telas de pedidos. Todas as regras e permissões são verificadas no PedidoService e no SecurityConfig. */
@Controller
@RequestMapping("/pedidos")
public class PedidoWebController {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;

    public PedidoWebController(PedidoService pedidoService, ClienteService clienteService,
            ProdutoService produtoService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.produtoService = produtoService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("pedidos", pedidoService.listarPedidos());
        model.addAttribute("content", "pedidos/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        PedidoForm form = new PedidoForm();
        form.getItens().add(new ItemPedidoForm(null, 1));
        return formulario(model, form, null);
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("pedidoForm") PedidoForm form, BindingResult resultado,
            Model model, RedirectAttributes redirectAttributes) {
        if (resultado.hasErrors()) {
            return formulario(model, form, null);
        }
        try {
            Pedido pedido = pedidoService.criarPedido(form);
            redirectAttributes.addFlashAttribute("sucesso", pedido.getIdentificacao() + " registrado com sucesso.");
            return "redirect:/pedidos/" + pedido.getId();
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return formulario(model, form, null);
        }
    }

    @GetMapping("/{id}")
    public String detalhe(@PathVariable String id, Model model) {
        model.addAttribute("pedido", pedidoService.buscarPorId(id));
        model.addAttribute("content", "pedidos/detalhe :: content");
        return "layout";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        Pedido pedido = pedidoService.buscarPorId(id);
        if (!pedido.isPermiteEdicao()) {
            redirectAttributes.addFlashAttribute("erro", "Somente pedidos com status Recebido podem ser alterados.");
            return "redirect:/pedidos/" + id;
        }
        PedidoForm form = new PedidoForm();
        form.setClienteId(pedido.getClienteId());
        form.setTipoAtendimento(pedido.getTipoAtendimento());
        form.setObservacoes(pedido.getObservacoes());
        pedido.getItens().forEach(item -> form.getItens().add(new ItemPedidoForm(item.getProdutoId(), item.getQuantidade())));
        return formulario(model, form, pedido);
    }

    @PostMapping("/{id}/editar")
    public String atualizar(@PathVariable String id, @Valid @ModelAttribute("pedidoForm") PedidoForm form,
            BindingResult resultado, Model model, RedirectAttributes redirectAttributes) {
        if (resultado.hasErrors()) {
            return formulario(model, form, pedidoService.buscarPorId(id));
        }
        try {
            Pedido pedido = pedidoService.editarPedido(id, form);
            redirectAttributes.addFlashAttribute("sucesso", pedido.getIdentificacao() + " atualizado com sucesso.");
            return "redirect:/pedidos/" + id;
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return formulario(model, form, pedidoService.buscarPorId(id));
        }
    }

    @PostMapping("/{id}/status")
    public String atualizarStatus(@PathVariable String id, @RequestParam String status,
            RedirectAttributes redirectAttributes) {
        try {
            Pedido pedido = pedidoService.atualizarStatus(id, status);
            redirectAttributes.addFlashAttribute("sucesso", pedido.getIdentificacao() + ": status alterado para "
                    + pedido.getStatus().getDescricao() + ".");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/{id}/desconto")
    public String aplicarDesconto(@PathVariable String id, @RequestParam(required = false) BigDecimal percentual,
            @RequestParam(required = false) String justificativa, RedirectAttributes redirectAttributes) {
        try {
            Pedido pedido = pedidoService.aplicarDesconto(id, percentual, justificativa);
            NumberFormat numero = NumberFormat.getNumberInstance(PT_BR);
            numero.setMinimumFractionDigits(2);
            redirectAttributes.addFlashAttribute("sucesso", "Desconto de " + numero.format(pedido.getPercentualDesconto())
                    + "% aplicado. Novo total: " + NumberFormat.getCurrencyInstance(PT_BR).format(pedido.getValorTotal()) + ".");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable String id, @RequestParam(required = false) String justificativa,
            RedirectAttributes redirectAttributes) {
        try {
            Pedido pedido = pedidoService.cancelarPedido(id, justificativa);
            redirectAttributes.addFlashAttribute("sucesso", pedido.getIdentificacao() + " cancelado.");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    private String formulario(Model model, PedidoForm form, Pedido pedidoExistente) {
        model.addAttribute("pedidoForm", form);
        model.addAttribute("pedidoExistente", pedidoExistente);
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtoService.listarAtivos());
        model.addAttribute("tiposAtendimento", TipoAtendimento.values());
        model.addAttribute("content", "pedidos/form :: content");
        return "layout";
    }
}
