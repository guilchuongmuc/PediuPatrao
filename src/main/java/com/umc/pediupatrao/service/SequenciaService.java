package com.umc.pediupatrao.service;

import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/** Gera números sequenciais atômicos (ex.: número do pedido) usando a coleção "contadores". */
@Service
public class SequenciaService {

    static final String COLECAO = "contadores";

    private final MongoTemplate mongoTemplate;

    public SequenciaService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public long proximo(String nome) {
        Document contador = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(nome)),
                new Update().inc("valor", 1L),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Document.class,
                COLECAO);
        if (contador == null) {
            throw new IllegalStateException("Não foi possível gerar a sequência " + nome);
        }
        return ((Number) contador.get("valor")).longValue();
    }
}
