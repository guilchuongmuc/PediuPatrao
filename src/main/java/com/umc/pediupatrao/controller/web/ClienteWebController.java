package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/clientes")
public class ClienteWebController {

    private final ClienteService clienteService;

    public ClienteWebController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public String clientes(Model model) {
        // A tabela é carregada pela API /api/clientes (JavaScript da própria página)
        model.addAttribute("content", "clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novoClienteForm(Model model) {
        return formulario(model, new Cliente());
    }

    @PostMapping("/salvar")
    public String salvarCliente(@Valid @ModelAttribute Cliente cliente, BindingResult resultado, Model model,
            RedirectAttributes redirectAttributes) {
        cliente.setId(null);
        if (resultado.hasErrors()) {
            return formulario(model, cliente);
        }
        try {
            clienteService.criar(cliente);
            redirectAttributes.addFlashAttribute("sucesso", "Cliente criado com sucesso!");
            return "redirect:/clientes";
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return formulario(model, cliente);
        }
    }

    @GetMapping("/editar/{id}")
    public String editarClienteForm(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        return clienteService.buscarPorId(id)
                .map(cliente -> formulario(model, cliente))
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("erro", "Cliente não encontrado.");
                    return "redirect:/clientes";
                });
    }

    @PostMapping("/editar/{id}")
    public String atualizarCliente(@PathVariable String id, @Valid @ModelAttribute Cliente cliente,
            BindingResult resultado, Model model, RedirectAttributes redirectAttributes) {
        cliente.setId(id);
        if (resultado.hasErrors()) {
            return formulario(model, cliente);
        }
        try {
            clienteService.atualizar(id, cliente);
            redirectAttributes.addFlashAttribute("sucesso", "Cliente atualizado com sucesso!");
            return "redirect:/clientes";
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            model.addAttribute("erro", e.getMessage());
            return formulario(model, cliente);
        }
    }

    private static String formulario(Model model, Cliente cliente) {
        model.addAttribute("cliente", cliente);
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }
}
