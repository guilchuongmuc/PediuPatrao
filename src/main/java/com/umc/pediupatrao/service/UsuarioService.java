package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.SessaoUsuarioService;
import com.umc.pediupatrao.security.UsuarioAutenticado;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Administração de usuários e perfis — exclusiva do ADMIN (anotação na classe vale para todos os métodos).
 * Proteções contra atribuição indevida de privilégios:
 * <ul>
 * <li>somente ADMIN cria usuários ou altera perfis (não existe cadastro público);</li>
 * <li>o ADMIN não pode alterar o próprio perfil nem excluir a própria conta;</li>
 * <li>o sistema sempre mantém ao menos um ADMIN;</li>
 * <li>ao mudar perfil/senha ou excluir, as sessões ativas do usuário são encerradas.</li>
 * </ul>
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioService {

    static final String ENTIDADE = "USUARIO";
    static final int SENHA_MINIMA = 8;
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,30}$");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final SessaoUsuarioService sessaoUsuarioService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService, SessaoUsuarioService sessaoUsuarioService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.sessaoUsuarioService = sessaoUsuarioService;
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll(Sort.by("username"));
    }

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public Usuario criarUsuario(UsuarioForm form) {
        String username = normalizarUsername(form.getUsername());
        if (usuarioRepository.existsByUsername(username)) {
            throw new RegraNegocioException("Já existe um usuário com o username " + username + ".");
        }
        Perfil perfil = exigirPerfil(form.getPerfil());
        validarSenha(form.getPassword());

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPerfil(perfil);
        usuario.setPassword(passwordEncoder.encode(form.getPassword()));
        Usuario salvo = usuarioRepository.save(usuario);

        auditoriaService.registrar(TipoOperacao.INCLUSAO_USUARIO, ENTIDADE, salvo.getId(), "Usuário " + username,
                List.of(new AlteracaoCampo("username", null, username),
                        new AlteracaoCampo("perfil", null, perfil.name()),
                        new AlteracaoCampo("senha", null, "definida")), null);
        return salvo;
    }

    /** Altera perfil e/ou senha. O username é imutável para preservar a rastreabilidade da auditoria. */
    public Usuario atualizarUsuario(String id, UsuarioForm form) {
        Usuario usuario = carregar(id);
        boolean proprioUsuario = usuario.getUsername().equals(UsuarioAutenticado.username());
        Perfil novoPerfil = exigirPerfil(form.getPerfil());
        List<AlteracaoCampo> alteracoes = new ArrayList<>();

        if (novoPerfil != usuario.getPerfil()) {
            if (proprioUsuario) {
                throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
            }
            if (usuario.getPerfil() == Perfil.ADMIN && usuarioRepository.countByPerfil(Perfil.ADMIN) <= 1) {
                throw new RegraNegocioException("O sistema deve manter ao menos um usuário ADMIN.");
            }
            alteracoes.add(new AlteracaoCampo("perfil",
                    usuario.getPerfil() == null ? null : usuario.getPerfil().name(), novoPerfil.name()));
            usuario.setPerfil(novoPerfil);
        }
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            validarSenha(form.getPassword());
            usuario.setPassword(passwordEncoder.encode(form.getPassword()));
            alteracoes.add(new AlteracaoCampo("senha", "anterior", "redefinida"));
        }
        if (alteracoes.isEmpty()) {
            return usuario;
        }

        Usuario salvo = usuarioRepository.save(usuario);
        if (!proprioUsuario) {
            sessaoUsuarioService.encerrarSessoes(salvo.getUsername());
        }
        auditoriaService.registrar(TipoOperacao.ALTERACAO_USUARIO, ENTIDADE, salvo.getId(),
                "Usuário " + salvo.getUsername(), alteracoes, null);
        return salvo;
    }

    public void excluirUsuario(String id) {
        Usuario usuario = carregar(id);
        if (usuario.getUsername().equals(UsuarioAutenticado.username())) {
            throw new RegraNegocioException("Você não pode excluir o seu próprio usuário.");
        }
        if (usuario.getPerfil() == Perfil.ADMIN && usuarioRepository.countByPerfil(Perfil.ADMIN) <= 1) {
            throw new RegraNegocioException("O sistema deve manter ao menos um usuário ADMIN.");
        }
        usuarioRepository.deleteById(id);
        sessaoUsuarioService.encerrarSessoes(usuario.getUsername());
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_USUARIO, ENTIDADE, id, "Usuário " + usuario.getUsername(),
                List.of(new AlteracaoCampo("perfil",
                        usuario.getPerfil() == null ? null : usuario.getPerfil().name(), null)), null);
    }

    private Usuario carregar(String id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    private static String normalizarUsername(String username) {
        String normalizado = username == null ? "" : username.trim().toLowerCase();
        if (!USERNAME.matcher(normalizado).matches()) {
            throw new RegraNegocioException(
                    "O username deve ter de 3 a 30 caracteres (letras, números, ponto, hífen ou sublinhado).");
        }
        return normalizado;
    }

    private static Perfil exigirPerfil(Perfil perfil) {
        if (perfil == null) {
            throw new RegraNegocioException("Selecione um perfil válido: ADMIN, GERENTE ou ATENDENTE.");
        }
        return perfil;
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < SENHA_MINIMA || senha.length() > 72) {
            throw new RegraNegocioException("A senha deve ter entre " + SENHA_MINIMA + " e 72 caracteres.");
        }
    }
}
