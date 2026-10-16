package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.security.UsuarioAutenticado;
import com.umc.pediupatrao.service.UsuarioService;
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

/** Administração de usuários (somente ADMIN — SecurityConfig e @PreAuthorize no UsuarioService). */
@Controller
@RequestMapping("/usuarios")
public class UsuarioWebController {

    private final UsuarioService usuarioService;

    public UsuarioWebController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String listarUsuarios(Model model) {
        model.addAttribute("usuarios", usuarioService.listarTodos());
        model.addAttribute("usuarioLogado", UsuarioAutenticado.username());
        model.addAttribute("content", "usuarios/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novoUsuarioForm(Model model) {
        return formulario(model, new UsuarioForm(), null);
    }

    @PostMapping("/salvar")
    public String salvarUsuario(@Valid @ModelAttribute("usuarioForm") UsuarioForm form, BindingResult resultado,
            Model model, RedirectAttributes redirectAttributes) {
        if (resultado.hasErrors()) {
            return formulario(model, form, null);
        }
        try {
            Usuario usuario = usuarioService.criarUsuario(form);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário " + usuario.getUsername() + " criado com sucesso!");
            return "redirect:/usuarios";
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return formulario(model, form, null);
        }
    }

    @GetMapping("/editar/{id}")
    public String editarUsuarioForm(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        return usuarioService.buscarPorId(id).map(usuario -> {
            UsuarioForm form = new UsuarioForm();
            form.setUsername(usuario.getUsername());
            form.setPerfil(usuario.getPerfil());
            return formulario(model, form, usuario);
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("erro", "Usuário não encontrado.");
            return "redirect:/usuarios";
        });
    }

    @PostMapping("/editar/{id}")
    public String atualizarUsuario(@PathVariable String id, @Valid @ModelAttribute("usuarioForm") UsuarioForm form,
            BindingResult resultado, Model model, RedirectAttributes redirectAttributes) {
        Usuario existente = usuarioService.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        form.setUsername(existente.getUsername());
        if (resultado.hasErrors()) {
            return formulario(model, form, existente);
        }
        try {
            usuarioService.atualizarUsuario(id, form);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
            return "redirect:/usuarios";
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return formulario(model, form, existente);
        }
    }

    @PostMapping("/deletar/{id}")
    public String deletarUsuario(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            usuarioService.excluirUsuario(id);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    private static String formulario(Model model, UsuarioForm form, Usuario existente) {
        model.addAttribute("usuarioForm", form);
        model.addAttribute("usuarioId", existente == null ? null : existente.getId());
        model.addAttribute("proprioUsuario",
                existente != null && existente.getUsername().equals(UsuarioAutenticado.username()));
        model.addAttribute("perfis", Perfil.values());
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }
}
