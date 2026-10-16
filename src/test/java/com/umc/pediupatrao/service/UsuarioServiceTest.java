package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.SessaoUsuarioService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServiceTest {

    private static final String SENHA_DIGITADA = "SenhaForte123";

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private SessaoUsuarioService sessaoUsuarioService;
    @Captor
    private ArgumentCaptor<List<AlteracaoCampo>> alteracoes;

    private UsuarioService service;

    @BeforeEach
    void configurar() {
        service = new UsuarioService(usuarioRepository, passwordEncoder, auditoriaService, sessaoUsuarioService);
        TestSeguranca.autenticar("admin", "ADMIN");
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId("u-novo");
            }
            return u;
        });
        when(usuarioRepository.findById("admin-id")).thenReturn(Optional.of(usuario("admin-id", "admin", Perfil.ADMIN)));
        when(usuarioRepository.findById("ana-id")).thenReturn(Optional.of(usuario("ana-id", "ana", Perfil.ATENDENTE)));
        when(usuarioRepository.findById("chefe-id")).thenReturn(Optional.of(usuario("chefe-id", "chefe", Perfil.ADMIN)));
    }

    @AfterEach
    void limpar() {
        TestSeguranca.limpar();
    }

    @Test
    void criarUsuarioCriptografaSenhaENaoRegistraSenhaNaAuditoria() {
        Usuario criado = service.criarUsuario(form("Gerente01", SENHA_DIGITADA, Perfil.GERENTE));

        assertThat(criado.getUsername()).isEqualTo("gerente01");
        assertThat(criado.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(criado.getPerfil()).isEqualTo(Perfil.GERENTE);
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_USUARIO), eq("USUARIO"), eq("u-novo"), anyString(),
                alteracoes.capture(), isNull());
        assertThat(alteracoes.getValue())
                .noneSatisfy(a -> assertThat(a.getValorNovo()).isIn(SENHA_DIGITADA, "$2a$10$hash"));
    }

    @Test
    void validaUsernameDuplicadoSenhaCurtaEPerfilObrigatorio() {
        when(usuarioRepository.existsByUsername("ana")).thenReturn(true);

        assertThatThrownBy(() -> service.criarUsuario(form("ana", SENHA_DIGITADA, Perfil.ATENDENTE)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("Já existe");
        assertThatThrownBy(() -> service.criarUsuario(form("joao", "1234", Perfil.ATENDENTE)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("senha");
        assertThatThrownBy(() -> service.criarUsuario(form("joao", SENHA_DIGITADA, null)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("perfil");
    }

    @Test
    void adminNaoPodeAlterarOProprioPerfil() {
        assertThatThrownBy(() -> service.atualizarUsuario("admin-id", form(null, null, Perfil.GERENTE)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("próprio perfil");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void sistemaMantemAoMenosUmAdmin() {
        when(usuarioRepository.countByPerfil(Perfil.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.atualizarUsuario("chefe-id", form(null, null, Perfil.ATENDENTE)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("ao menos um usuário ADMIN");
        assertThatThrownBy(() -> service.excluirUsuario("chefe-id"))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("ao menos um usuário ADMIN");
    }

    @Test
    void alterarPerfilAuditaEEncerraSessoesDoUsuario() {
        service.atualizarUsuario("ana-id", form(null, null, Perfil.GERENTE));

        verify(sessaoUsuarioService).encerrarSessoes("ana");
        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_USUARIO), eq("USUARIO"), eq("ana-id"), anyString(),
                alteracoes.capture(), isNull());
        assertThat(alteracoes.getValue()).singleElement().satisfies(a -> {
            assertThat(a.getCampo()).isEqualTo("perfil");
            assertThat(a.getValorAnterior()).isEqualTo("ATENDENTE");
            assertThat(a.getValorNovo()).isEqualTo("GERENTE");
        });
    }

    @Test
    void adminNaoPodeExcluirAPropriaConta() {
        assertThatThrownBy(() -> service.excluirUsuario("admin-id"))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("próprio usuário");
        verify(usuarioRepository, never()).deleteById(any());
    }

    private static UsuarioForm form(String username, String senha, Perfil perfil) {
        UsuarioForm form = new UsuarioForm();
        form.setUsername(username);
        form.setPassword(senha);
        form.setPerfil(perfil);
        return form;
    }

    private static Usuario usuario(String id, String username, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setUsername(username);
        usuario.setPerfil(perfil);
        usuario.setPassword("$2a$10$existente");
        return usuario;
    }
}
