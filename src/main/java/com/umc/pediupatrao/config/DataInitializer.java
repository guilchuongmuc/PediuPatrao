package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Dados iniciais para uso e demonstração: um usuário de cada perfil e um cardápio básico.
 * Nada é sobrescrito; os registros só são criados quando não existem.
 * A senha inicial vem de app.seed.senha-inicial (variável APP_SEED_SENHA) e nunca é escrita no log.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    @Order(2)
    CommandLineRunner initDatabase(UsuarioRepository usuarios, ProdutoRepository produtos, PasswordEncoder encoder,
            @Value("${app.seed.enabled:true}") boolean habilitado,
            @Value("${app.seed.senha-inicial:}") String senhaInicial) {
        return args -> {
            if (!habilitado) {
                return;
            }
            if (senhaInicial == null || senhaInicial.length() < 8) {
                log.warn("app.seed.senha-inicial não configurada (mínimo 8 caracteres): usuários iniciais não criados.");
            } else {
                criarUsuario(usuarios, encoder, "admin", Perfil.ADMIN, senhaInicial);
                criarUsuario(usuarios, encoder, "gerente", Perfil.GERENTE, senhaInicial);
                criarUsuario(usuarios, encoder, "atendente", Perfil.ATENDENTE, senhaInicial);
            }
            if (produtos.count() == 0) {
                criarProduto(produtos, "Pizza Calabresa", "Pizza", 49.90);
                criarProduto(produtos, "Pizza Mussarela", "Pizza", 45.00);
                criarProduto(produtos, "Pizza Portuguesa", "Pizza", 54.90);
                criarProduto(produtos, "Esfiha de Carne", "Esfiha", 6.50);
                criarProduto(produtos, "Refrigerante 2L", "Bebida", 12.00);
                criarProduto(produtos, "Borda Recheada", "Adicional", 9.00);
                log.info("Cardápio inicial criado com 6 produtos.");
            }
        };
    }

    private static void criarUsuario(UsuarioRepository repo, PasswordEncoder encoder, String username, Perfil perfil,
            String senha) {
        repo.findByUsername(username).ifPresentOrElse(existente -> {
            // Usuário "admin" criado pela versão anterior sem perfil: recebe o perfil ADMIN
            if (existente.getPerfil() == null && perfil == Perfil.ADMIN) {
                existente.setPerfil(Perfil.ADMIN);
                repo.save(existente);
                log.info("Usuário {} atualizado com o perfil {}.", username, perfil);
            }
        }, () -> {
            Usuario usuario = new Usuario();
            usuario.setUsername(username);
            usuario.setPerfil(perfil);
            usuario.setPassword(encoder.encode(senha));
            repo.save(usuario);
            log.info("Usuário inicial {} ({}) criado.", username, perfil);
        });
    }

    private static void criarProduto(ProdutoRepository repo, String nome, String tipo, double preco) {
        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(preco);
        produto.setAtivo(true);
        repo.save(produto);
    }
}
