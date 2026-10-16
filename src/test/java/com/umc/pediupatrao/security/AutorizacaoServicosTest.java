package com.umc.pediupatrao.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.AuditoriaFiltro;
import com.umc.pediupatrao.dto.PedidoForm;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.SequenciaService;
import com.umc.pediupatrao.service.UsuarioService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Segunda camada de autorização: mesmo que uma rota HTTP fosse liberada por engano,
 * os serviços recusam a operação para perfis não autorizados (@PreAuthorize).
 */
@SpringJUnitConfig(AutorizacaoServicosTest.Config.class)
class AutorizacaoServicosTest {

    @Configuration
    @EnableMethodSecurity
    @Import({PedidoService.class, ClienteService.class, UsuarioService.class, AuditoriaService.class, ProdutoService.class})
    static class Config {
        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }
    }

    @MockitoBean
    private PedidoRepository pedidoRepository;
    @MockitoBean
    private ClienteRepository clienteRepository;
    @MockitoBean
    private ProdutoRepository produtoRepository;
    @MockitoBean
    private UsuarioRepository usuarioRepository;
    @MockitoBean
    private RegistroAuditoriaRepository registroAuditoriaRepository;
    @MockitoBean
    private MongoTemplate mongoTemplate;
    @MockitoBean
    private SequenciaService sequenciaService;
    @MockitoBean
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private SessaoUsuarioService sessaoUsuarioService;

    @Autowired
    private PedidoService pedidoService;
    @Autowired
    private ClienteService clienteService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private AuditoriaService auditoriaService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminNaoAplicaDescontoNemCancela() {
        assertThatThrownBy(() -> pedidoService.aplicarDesconto("p1", BigDecimal.TEN, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> pedidoService.cancelarPedido("p1", "Justificativa qualquer"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoAplicaDescontoNemCancela() {
        assertThatThrownBy(() -> pedidoService.aplicarDesconto("p1", BigDecimal.TEN, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> pedidoService.cancelarPedido("p1", "Justificativa qualquer"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerentePassaPelaAutorizacaoDeDescontoECancelamento() {
        // Sem pedido no repositório: a exceção de "não encontrado" prova que a autorização foi concedida
        assertThatThrownBy(() -> pedidoService.aplicarDesconto("p1", BigDecimal.TEN, null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> pedidoService.cancelarPedido("p1", "Justificativa qualquer"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminConsultaMasNaoRegistraPedidos() {
        assertThatCode(() -> pedidoService.listarPedidos()).doesNotThrowAnyException();
        assertThatThrownBy(() -> pedidoService.criarPedido(new PedidoForm())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> pedidoService.atualizarStatus("p1", "PRONTO")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteConsultaMasNaoAlteraClientes() {
        when(clienteRepository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of());
        assertThatCode(() -> clienteService.listarClientes()).doesNotThrowAnyException();
        assertThatThrownBy(() -> clienteService.atualizar("c1", new Cliente())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> clienteService.criar(new Cliente())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminNaoAcessaClientes() {
        assertThatThrownBy(() -> clienteService.listarClientes()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = {"GERENTE"})
    void somenteAdminAdministraUsuarios() {
        assertThatThrownBy(() -> usuarioService.listarTodos()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> usuarioService.criarUsuario(new com.umc.pediupatrao.dto.UsuarioForm()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoConsultaAuditoria() {
        assertThatThrownBy(() -> auditoriaService.consultar(new AuditoriaFiltro(), PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> auditoriaService.verificarIntegridade()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteConsultaAuditoria() {
        when(mongoTemplate.count(any(Query.class), eq(RegistroAuditoria.class))).thenReturn(0L);
        assertThatCode(() -> auditoriaService.consultar(new AuditoriaFiltro(), PageRequest.of(0, 10)))
                .doesNotThrowAnyException();
    }

    @Test
    void semAutenticacaoNadaEhPermitido() {
        assertThatThrownBy(() -> pedidoService.listarPedidos())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
