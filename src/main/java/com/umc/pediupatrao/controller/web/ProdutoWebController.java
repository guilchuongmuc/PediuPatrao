package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.ProdutoService;
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
@RequestMapping("/produtos")
public class ProdutoWebController {

    private final ProdutoService produtoService;

    public ProdutoWebController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public String produtos(Model model) {
        model.addAttribute("produtos", produtoService.listarProdutos());
        model.addAttribute("content", "produtos/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novoProdutoForm(Model model) {
        Produto produto = new Produto();
        produto.setAtivo(true);
        return formulario(model, produto);
    }

    @PostMapping("/salvar")
    public String salvarProduto(@Valid @ModelAttribute Produto produto, BindingResult resultado, Model model,
            RedirectAttributes redirectAttributes) {
        produto.setId(null);
        if (resultado.hasErrors()) {
            return formulario(model, produto);
        }
        try {
            produtoService.criar(produto);
            redirectAttributes.addFlashAttribute("sucesso", "Produto criado com sucesso!");
            return "redirect:/produtos";
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return formulario(model, produto);
        }
    }

    @GetMapping("/editar/{id}")
    public String editarProdutoForm(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        return produtoService.buscarPorId(id)
                .map(produto -> formulario(model, produto))
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("erro", "Produto não encontrado.");
                    return "redirect:/produtos";
                });
    }

    @PostMapping("/editar/{id}")
    public String atualizarProduto(@PathVariable String id, @Valid @ModelAttribute Produto produto,
            BindingResult resultado, Model model, RedirectAttributes redirectAttributes) {
        produto.setId(id);
        if (resultado.hasErrors()) {
            return formulario(model, produto);
        }
        try {
            produtoService.atualizar(id, produto);
            redirectAttributes.addFlashAttribute("sucesso", "Produto atualizado com sucesso!");
            return "redirect:/produtos";
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            model.addAttribute("erro", e.getMessage());
            return formulario(model, produto);
        }
    }

    @PostMapping("/excluir/{id}")
    public String excluirProduto(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            produtoService.excluir(id);
            redirectAttributes.addFlashAttribute("sucesso", "Produto removido com sucesso!");
        } catch (RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/produtos";
    }

    private static String formulario(Model model, Produto produto) {
        model.addAttribute("produto", produto);
        model.addAttribute("tipos", ProdutoService.TIPOS);
        model.addAttribute("content", "produtos/form :: content");
        return "layout";
    }
}
