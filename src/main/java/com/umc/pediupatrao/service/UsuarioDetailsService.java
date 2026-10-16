package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Carrega o usuário para autenticação atribuindo a role correspondente ao perfil cadastrado
 * (ROLE_ADMIN, ROLE_GERENTE ou ROLE_ATENDENTE). Usuários sem perfil válido ficam desabilitados.
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String login = username == null ? "" : username.trim();
        Usuario usuario = usuarioRepository.findByUsername(login)
                .or(() -> usuarioRepository.findByUsername(login.toLowerCase()))
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        boolean semPerfil = usuario.getPerfil() == null;
        return User.builder()
                .username(usuario.getUsername())
                .password(usuario.getPassword())
                .authorities(semPerfil ? List.of() : List.of(new SimpleGrantedAuthority(usuario.getPerfil().getAuthority())))
                .disabled(semPerfil)
                .build();
    }
}
