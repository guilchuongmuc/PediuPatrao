package com.umc.pediupatrao.demo;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.SequenciaService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(3)
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DadosDemonstracaoInitializer implements CommandLineRunner {

    static final int PEDIDOS_MINIMOS = 100;

    private static final Logger log = LoggerFactory.getLogger(DadosDemonstracaoInitializer.class);

    private static final Object[][] CARDAPIO = {
        {"Pizza Calabresa", "Pizza", 49.90},
        {"Pizza Mussarela", "Pizza", 45.00},
        {"Pizza Portuguesa", "Pizza", 54.90},
        {"Pizza Frango com Catupiry", "Pizza", 52.90},
        {"Pizza Margherita", "Pizza", 47.90},
        {"Pizza Quatro Queijos", "Pizza", 56.90},
        {"Pizza de Chocolate com Morango", "Pizza", 51.90},
        {"Esfiha de Carne", "Esfiha", 6.50},
        {"Esfiha de Queijo", "Esfiha", 6.50},
        {"Esfiha de Calabresa", "Esfiha", 6.90},
        {"Refrigerante 2L", "Bebida", 12.00},
        {"Guaraná Lata 350ml", "Bebida", 6.00},
        {"Suco Natural 500ml", "Bebida", 9.00},
        {"Água Mineral 500ml", "Bebida", 4.00},
        {"Borda Recheada", "Adicional", 9.00},
        {"Batata Frita 400g", "Porção", 24.90}
    };

    private static final String[][] CLIENTES = {
        {"Ana Paula Ribeiro", "Rua Ipiranga", "120", "Centro"},
        {"Bruno Carvalho", "Rua Coronel Souza Franco", "455", "Centro"},
        {"Carla Mendes", "Avenida Voluntário Fernando Pinheiro Franco", "1020", "Centro"},
        {"Diego Souza", "Rua Dom Antônio Cândido de Alvarenga", "78", "Centro"},
        {"Eduarda Lima", "Avenida Japão", "310", "Vila Industrial"},
        {"Felipe Araújo", "Rua Manoel de Oliveira", "56", "Mogi Moderno"},
        {"Gabriela Rocha", "Avenida Francisco Rodrigues Filho", "2100", "Vila Mogilar"},
        {"Henrique Alves", "Rua Barão de Jaceguai", "890", "Centro"},
        {"Isabela Martins", "Rua Ricardo Vilela", "233", "Centro"},
        {"João Pedro Costa", "Avenida Ver. Narciso Yague Guimarães", "640", "Centro Cívico"},
        {"Larissa Gomes", "Rua Cabo Diogo Oliver", "1500", "Vila Oliveira"},
        {"Marcos Vinícius Pereira", "Rua Professor Álvaro Pavan", "47", "Alto do Ipiranga"},
        {"Natália Fernandes", "Avenida Pedro Machado", "1880", "Vila Mogilar"},
        {"Otávio Barbosa", "Rua Antônio Cândido Vieira", "95", "Brás Cubas"},
        {"Paula Cristina Dias", "Rua Benedito Laporte Vieira da Motta", "305", "Jardim Armênia"},
        {"Rafael Moreira", "Avenida Kaoru Hiramatsu", "720", "Parque Morumbi"}
    };

    private final MongoTemplate mongoTemplate;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final SequenciaService sequenciaService;
    private final Clock clock;
    private final int dias;
    private final long semente;

    public DadosDemonstracaoInitializer(MongoTemplate mongoTemplate, ProdutoRepository produtoRepository,
            ClienteRepository clienteRepository, PedidoRepository pedidoRepository,
            SequenciaService sequenciaService, Clock clock,
            @Value("${app.demo.dias:90}") int dias,
            @Value("${app.demo.semente:2026}") long semente) {
        this.mongoTemplate = mongoTemplate;
        this.produtoRepository = produtoRepository;
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.sequenciaService = sequenciaService;
        this.clock = clock;
        this.dias = Math.max(1, Math.min(dias, 366));
        this.semente = semente;
    }

    @Override
    public void run(String... args) {
        long existentes = pedidoRepository.count();
        if (existentes >= PEDIDOS_MINIMOS) {
            log.info("Dados de demonstração não carregados: o banco já possui {} pedidos.", existentes);
            return;
        }
        List<Produto> produtos = garantirCardapio();
        List<Cliente> clientes = garantirClientes();
        LocalDateTime agora = LocalDateTime.now(clock);
        List<Pedido> pedidos = new GeradorPedidosDemonstracao(semente, produtos, clientes)
                .gerar(agora.toLocalDate().minusDays(dias - 1L), agora);
        pedidos.forEach(pedido -> pedido.setNumero(sequenciaService.proximo("pedidos")));
        mongoTemplate.insert(pedidos, Pedido.class);
        log.info("Dados de demonstração carregados: {} pedidos em {} dias, {} produtos e {} clientes.",
                pedidos.size(), dias, produtos.size(), clientes.size());
    }

    private List<Produto> garantirCardapio() {
        Set<String> existentes = produtoRepository.findAll().stream()
                .map(p -> normalizar(p.getNome()))
                .collect(Collectors.toSet());
        List<Produto> novos = new ArrayList<>();
        for (Object[] item : CARDAPIO) {
            if (!existentes.contains(normalizar((String) item[0]))) {
                Produto produto = new Produto();
                produto.setNome((String) item[0]);
                produto.setTipo((String) item[1]);
                produto.setPreco((Double) item[2]);
                produto.setAtivo(true);
                novos.add(produto);
            }
        }
        produtoRepository.saveAll(novos);
        return produtoRepository.findAll().stream().filter(Produto::isDisponivel).toList();
    }

    private List<Cliente> garantirClientes() {
        Set<String> nomesDemonstracao = new HashSet<>();
        for (String[] dados : CLIENTES) {
            nomesDemonstracao.add(normalizar(dados[0]));
        }
        List<Cliente> existentes = clienteRepository.findAll().stream()
                .filter(c -> nomesDemonstracao.contains(normalizar(c.getNome())))
                .toList();
        Set<String> nomesExistentes = existentes.stream().map(c -> normalizar(c.getNome())).collect(Collectors.toSet());

        List<Cliente> novos = new ArrayList<>();
        for (int i = 0; i < CLIENTES.length; i++) {
            String[] dados = CLIENTES[i];
            if (!nomesExistentes.contains(normalizar(dados[0]))) {
                Cliente cliente = new Cliente();
                cliente.setNome(dados[0]);
                cliente.setTelefone(String.format("(11) 90000-%04d", i + 1));
                cliente.setLogradouro(dados[1]);
                cliente.setNumero(dados[2]);
                cliente.setBairro(dados[3]);
                cliente.setCidade("Mogi das Cruzes");
                cliente.setEstado("SP");
                cliente.setCep(String.format("08%03d-000", 700 + i * 5));
                novos.add(cliente);
            }
        }
        List<Cliente> todos = new ArrayList<>(existentes);
        clienteRepository.saveAll(novos).forEach(todos::add);
        return todos;
    }

    private static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
    }
}
