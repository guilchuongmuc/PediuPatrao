package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.security.UsuarioAutenticado;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("perfilAtual", Perfil.fromLegado(UsuarioAutenticado.perfil()));
        model.addAttribute("content", "home :: content");
        return "layout";
    }

    @GetMapping("/configuracoes")
    public String configuracoes(Model model) {
        model.addAttribute("content", "configuracoes :: content");
        return "layout";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}
