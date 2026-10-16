package com.umc.pediupatrao.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.web.PedidoWebController;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Autorização efetiva no backend: as requisições HTTP são bloqueadas pelo Spring Security
 * independentemente do que a interface exibe. Os serviços são simulados (mocks).
 */
@WebMvcTest(controllers = {PedidoController.class, ClienteController.class, UsuarioController.class,
    AuditoriaController.class, PedidoWebController.class})
@Import(SecurityConfig.class)
class SegurancaHttpTest {

    private static final String JUSTIFICATIVA = "{\"justificativa\":\"Cliente desistiu do pedido\"}";
    private static final String DESCONTO = "{\"percentual\":10}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoService pedidoService;
    @MockitoBean
    private ClienteService clienteService;
    @MockitoBean
    private ProdutoService produtoService;
    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    // ===== Autenticação =====

    @Test
    void apiSemAutenticacaoRetorna401EmJson() throws Exception {
        mvc.perform(get("/api/pedidos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void telaSemAutenticacaoRedirecionaParaLogin() throws Exception {
        mvc.perform(get("/pedidos").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void cadastroDeUsuarioNaoEhMaisPublico() throws Exception {
        mvc.perform(post("/api/usuarios").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"invasor\",\"password\":\"12345678\",\"perfil\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());
        verify(usuarioService, never()).criarUsuario(any());
    }

    // ===== Cancelamento e desconto: exclusivos do GERENTE =====

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoCancelaPedidoEATentativaEhAuditada() throws Exception {
        mvc.perform(post("/api/pedidos/p1/cancelamento").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(JUSTIFICATIVA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("Acesso negado"));
        verify(pedidoService, never()).cancelarPedido(anyString(), anyString());
        verify(auditoriaService).registrarAcessoNegado(contains("/api/pedidos/p1/cancelamento"), anyString());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoAplicaDescontoNemCancela() throws Exception {
        mvc.perform(post("/api/pedidos/p1/desconto").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/p1/cancelamento").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(JUSTIFICATIVA))
                .andExpect(status().isForbidden());
        verify(pedidoService, never()).aplicarDesconto(anyString(), any(), any());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteAplicaDesconto() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setId("p1");
        pedido.setStatus(StatusPedido.RECEBIDO);
        when(pedidoService.aplicarDesconto(eq("p1"), any(), any())).thenReturn(pedido);

        mvc.perform(post("/api/pedidos/p1/desconto").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("p1"));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void descontoAcimaDoLimiteECancelamentoSemJustificativaSaoRejeitados() throws Exception {
        mvc.perform(post("/api/pedidos/p1/desconto").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"percentual\":25}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.percentual").exists());
        mvc.perform(post("/api/pedidos/p1/cancelamento").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.justificativa").exists());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void requisicaoSemTokenCsrfEhBloqueada() throws Exception {
        mvc.perform(post("/api/pedidos/p1/desconto")
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value("Token CSRF ausente ou inválido."));
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoCancelaPelaTela() throws Exception {
        mvc.perform(post("/pedidos/p1/cancelar").with(csrf()).param("justificativa", "Cliente desistiu do pedido"))
                .andExpect(status().isForbidden());
        verify(pedidoService, never()).cancelarPedido(anyString(), anyString());
    }

    // ===== Pedidos: registro e transições =====

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminSomenteConsultaPedidos() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isOk());
        mvc.perform(post("/api/pedidos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/p1/status").param("status", "PRONTO").with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/pedidos/novo")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void transicaoInvalidaRetorna422ComMensagem() throws Exception {
        when(pedidoService.atualizarStatus("p1", "FINALIZADO"))
                .thenThrow(new RegraNegocioException("Transição inválida: Em preparação → Finalizado."));

        mvc.perform(put("/api/pedidos/p1/status").param("status", "FINALIZADO").with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Transição inválida: Em preparação → Finalizado."));
    }

    // ===== Clientes =====

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteConsultaMasNaoAlteraCliente() throws Exception {
        when(clienteService.listarClientes()).thenReturn(List.of());
        mvc.perform(get("/api/clientes")).andExpect(status().isOk());
        mvc.perform(put("/api/clientes/c1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"telefone\":\"(11) 98765-4321\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoExcluiCliente() throws Exception {
        mvc.perform(delete("/api/clientes/c1").with(csrf())).andExpect(status().isForbidden());
        verify(clienteService, never()).excluir(anyString());
    }

    // ===== Usuários e perfis: somente ADMIN =====

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteNaoCriaUsuariosNemSeAutoEleva() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"gerente2\",\"password\":\"12345678\",\"perfil\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/usuarios/u1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"perfil\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listagemDeUsuariosNaoExpoeSenha() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId("u1");
        usuario.setUsername("ana");
        usuario.setPassword("$2a$10$hashSecreto");
        usuario.setPerfil(Perfil.ATENDENTE);
        when(usuarioService.listarTodos()).thenReturn(List.of(usuario));

        mvc.perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("ana"))
                .andExpect(jsonPath("$[0].perfil").value("ATENDENTE"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    // ===== Auditoria: consulta restrita e somente leitura =====

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoConsultaAuditoria() throws Exception {
        mvc.perform(get("/api/auditoria")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteConsultaAuditoria() throws Exception {
        when(auditoriaService.consultar(any(), any())).thenReturn(Page.empty());
        mvc.perform(get("/api/auditoria")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registrosDeAuditoriaNaoPodemSerAlteradosNemExcluidos() throws Exception {
        mvc.perform(delete("/api/auditoria/a1").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put("/api/auditoria/a1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auditoria").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}
