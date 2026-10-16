package com.umc.pediupatrao.config;

import com.umc.pediupatrao.security.AcessoNegadoHandler;
import com.umc.pediupatrao.security.AutenticacaoEntryPoint;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Autorização por perfil aplicada no backend em duas camadas:
 * 1) regras de URL abaixo (telas e APIs);
 * 2) @PreAuthorize nos serviços (@EnableMethodSecurity), que protege as regras mesmo se uma rota mudar.
 * Rotas não listadas são negadas (denyAll). CSRF permanece ativo em todos os formulários e APIs.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] RECURSOS_PUBLICOS = {
        "/login", "/error", "/favicon.ico", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**"
    };

    private final UsuarioDetailsService usuarioDetailsService;
    private final AuditoriaService auditoriaService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService, AuditoriaService auditoriaService) {
        this.usuarioDetailsService = usuarioDetailsService;
        this.auditoriaService = auditoriaService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AutenticacaoEntryPoint entryPoint = new AutenticacaoEntryPoint();

        http
                // CSRF ativo (padrão): formulários Thymeleaf recebem o token automaticamente e o
                // JavaScript envia o cabeçalho X-CSRF-TOKEN lido das meta tags do layout
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(RECURSOS_PUBLICOS).permitAll()

                        // Usuários e perfis: somente ADMIN
                        .requestMatchers("/usuarios", "/usuarios/**", "/api/usuarios", "/api/usuarios/**", "/admin")
                        .hasRole("ADMIN")

                        // Auditoria: somente consulta, por ADMIN e GERENTE (outros métodos caem no denyAll)
                        .requestMatchers(HttpMethod.GET, "/auditoria", "/auditoria/**", "/api/auditoria", "/api/auditoria/**")
                        .hasAnyRole("ADMIN", "GERENTE")

                        // Pedidos: desconto e cancelamento exclusivos do GERENTE (inclusive em relação ao ADMIN)
                        .requestMatchers(HttpMethod.POST, "/pedidos/*/desconto", "/pedidos/*/cancelar",
                                "/api/pedidos/*/desconto", "/api/pedidos/*/cancelamento")
                        .hasRole("GERENTE")
                        .requestMatchers(HttpMethod.GET, "/pedidos/novo", "/pedidos/*/editar")
                        .hasAnyRole("GERENTE", "ATENDENTE")
                        .requestMatchers(HttpMethod.GET, "/pedidos", "/pedidos/**", "/api/pedidos", "/api/pedidos/**")
                        .hasAnyRole("ADMIN", "GERENTE", "ATENDENTE")
                        .requestMatchers(HttpMethod.POST, "/pedidos", "/pedidos/**", "/api/pedidos", "/api/pedidos/**")
                        .hasAnyRole("GERENTE", "ATENDENTE")
                        .requestMatchers(HttpMethod.PUT, "/api/pedidos/**")
                        .hasAnyRole("GERENTE", "ATENDENTE")

                        // Clientes: ATENDENTE cadastra/atualiza; GERENTE consulta e exclui
                        .requestMatchers(HttpMethod.DELETE, "/api/clientes/**").hasRole("GERENTE")
                        .requestMatchers(HttpMethod.GET, "/clientes/novo", "/clientes/editar/*").hasRole("ATENDENTE")
                        .requestMatchers(HttpMethod.GET, "/clientes", "/clientes/**", "/api/clientes", "/api/clientes/**")
                        .hasAnyRole("GERENTE", "ATENDENTE")
                        .requestMatchers(HttpMethod.POST, "/clientes/**", "/api/clientes", "/api/clientes/**")
                        .hasRole("ATENDENTE")
                        .requestMatchers(HttpMethod.PUT, "/api/clientes/**").hasRole("ATENDENTE")

                        // Produtos: GERENTE mantém o cardápio; ATENDENTE consulta
                        .requestMatchers(HttpMethod.GET, "/produtos/novo", "/produtos/editar/*").hasRole("GERENTE")
                        .requestMatchers(HttpMethod.GET, "/produtos", "/produtos/**", "/api/produtos", "/api/produtos/**")
                        .hasAnyRole("GERENTE", "ATENDENTE")
                        .requestMatchers("/produtos/**", "/api/produtos", "/api/produtos/**").hasRole("GERENTE")

                        // Páginas comuns a qualquer usuário autenticado
                        .requestMatchers(HttpMethod.GET, "/", "/configuracoes", "/api/csrf").authenticated()

                        // Qualquer outra rota é negada
                        .anyRequest().denyAll()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                // HTTP Basic permite testar as APIs via curl/PowerShell (o CSRF continua exigido)
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(new AcessoNegadoHandler(auditoriaService))
                )
                // Registro de sessões: permite encerrar sessões de um usuário cujo perfil foi alterado
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry())
                        .expiredUrl("/login?expirada")
                );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect("/"); // Redireciona para a página inicial após login bem-sucedido
        };
    }
}
