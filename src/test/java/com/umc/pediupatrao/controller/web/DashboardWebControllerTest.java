package com.umc.pediupatrao.controller.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {DashboardWebController.class, HomeController.class})
@Import(SecurityConfig.class)
class DashboardWebControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void semAutenticacaoRedirecionaParaLogin() throws Exception {
        mvc.perform(get("/dashboard").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void gerenteAbreODashboard() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "dashboard :: content"))
                .andExpect(content().string(containsString("id=\"dashboard\"")))
                .andExpect(content().string(containsString("data-api=\"/api/dashboard\"")))
                .andExpect(content().string(containsString("/js/dashboard.js")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAbreODashboard() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "atendente", roles = "ATENDENTE")
    void atendenteNaoAcessaODashboard() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void menuExibeOAtalhoParaGerente() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/dashboard\"")));
    }

    @Test
    @WithMockUser(username = "atendente", roles = "ATENDENTE")
    void menuOcultaOAtalhoParaAtendente() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/dashboard\""))));
    }
}
