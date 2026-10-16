package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.security.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Atributos comuns e tratamento de erros das telas (Thymeleaf). */
@ControllerAdvice(basePackageClasses = WebControllerAdvice.class)
public class WebControllerAdvice {

    /** Caminho atual, usado para destacar o item ativo da sidebar. */
    @ModelAttribute("uriAtual")
    public String uriAtual(HttpServletRequest request) {
        return request.getRequestURI();
    }

    /** Perfil do usuário logado (ADMIN, GERENTE ou ATENDENTE), exibido na sidebar. */
    @ModelAttribute("perfilLogado")
    public String perfilLogado() {
        return UsuarioAutenticado.perfil();
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public String naoEncontrado(RecursoNaoEncontradoException ex, Model model, HttpServletResponse response) {
        return paginaErro(model, response, HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(RegraNegocioException.class)
    public String regraNegocio(RegraNegocioException ex, Model model, HttpServletResponse response) {
        return paginaErro(model, response, 422, ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public String concorrencia(Model model, HttpServletResponse response) {
        return paginaErro(model, response, HttpServletResponse.SC_CONFLICT,
                "O registro foi alterado por outro usuário. Recarregue a página e tente novamente.");
    }

    private static String paginaErro(Model model, HttpServletResponse response, int status, String mensagem) {
        response.setStatus(status);
        model.addAttribute("status", status);
        model.addAttribute("message", mensagem);
        return "error";
    }
}
