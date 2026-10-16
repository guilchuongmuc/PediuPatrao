package com.umc.pediupatrao.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Configuration
@Order(1)
public class MongoInitConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoInitConfig.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) throws Exception {
        for (String colecao : new String[]{"pedidos", "clientes", "usuarios", "produtos", "horariosAtendimento",
            "auditoria", "contadores"}) {
            if (!mongoTemplate.collectionExists(colecao)) {
                mongoTemplate.createCollection(colecao);
            }
        }
        criarIndices();
        migrarPedidosAntigos();
    }

    private void criarIndices() {
        // Auditoria: sequência única impede registros duplicados/sobrescritos; demais índices aceleram a consulta
        criarIndice("auditoria", new Index().on("sequencia", Sort.Direction.ASC).unique().named("ux_sequencia"));
        criarIndice("auditoria", new Index().on("dataHora", Sort.Direction.DESC).named("ix_dataHora"));
        criarIndice("auditoria", new Index().on("entidade", Sort.Direction.ASC)
                .on("entidadeId", Sort.Direction.ASC).named("ix_entidade"));
        criarIndice("auditoria", new Index().on("usuario", Sort.Direction.ASC).named("ix_usuario"));
        criarIndice("pedidos", new Index().on("numero", Sort.Direction.ASC).unique().sparse().named("ux_numero"));
        criarIndice("pedidos", new Index().on("clienteId", Sort.Direction.ASC).named("ix_clienteId"));
        criarIndice("usuarios", new Index().on("username", Sort.Direction.ASC).unique().named("ux_username"));
    }

    private void criarIndice(String colecao, Index indice) {
        try {
            mongoTemplate.indexOps(colecao).ensureIndex(indice);
        } catch (RuntimeException e) {
            // Dados antigos inconsistentes (ex.: usernames duplicados) não impedem a aplicação de subir
            log.warn("Não foi possível criar o índice {} em {}: {}", indice.getIndexKeys(), colecao, e.getMessage());
        }
    }

    /** Pedidos antigos não têm o campo de versão usado no controle de concorrência otimista. */
    private void migrarPedidosAntigos() {
        long atualizados = mongoTemplate.updateMulti(
                Query.query(Criteria.where("versao").exists(false)),
                new Update().set("versao", 0L),
                "pedidos").getModifiedCount();
        if (atualizados > 0) {
            log.info("{} pedido(s) antigo(s) preparados para o controle de versão.", atualizados);
        }
    }
}
