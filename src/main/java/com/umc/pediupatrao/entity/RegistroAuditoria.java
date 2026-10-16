package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Evento de auditoria. Imutável: não há setters, o repositório só expõe inclusão e consulta,
 * e cada registro é encadeado ao anterior por hash SHA-256 para detectar adulterações no banco.
 */
@Document(collection = "auditoria")
public class RegistroAuditoria {

    @Id
    private final String id;
    private final Long sequencia;
    private final LocalDateTime dataHora;
    private final String usuario;
    private final String perfil;
    private final TipoOperacao operacao;
    private final String entidade;
    private final String entidadeId;
    private final String descricao;
    private final List<AlteracaoCampo> alteracoes;
    private final String justificativa;
    private final String hashAnterior;
    private final String hash;

    @PersistenceCreator
    public RegistroAuditoria(String id, Long sequencia, LocalDateTime dataHora, String usuario, String perfil,
            TipoOperacao operacao, String entidade, String entidadeId, String descricao,
            List<AlteracaoCampo> alteracoes, String justificativa, String hashAnterior, String hash) {
        this.id = id;
        this.sequencia = sequencia;
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.perfil = perfil;
        this.operacao = operacao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.descricao = descricao;
        this.alteracoes = alteracoes == null ? List.of() : List.copyOf(alteracoes);
        this.justificativa = justificativa;
        this.hashAnterior = hashAnterior;
        this.hash = hash;
    }

    public String getId() {
        return id;
    }

    public Long getSequencia() {
        return sequencia;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getPerfil() {
        return perfil;
    }

    public TipoOperacao getOperacao() {
        return operacao;
    }

    public String getEntidade() {
        return entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public String getDescricao() {
        return descricao;
    }

    public List<AlteracaoCampo> getAlteracoes() {
        return alteracoes;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public String getHashAnterior() {
        return hashAnterior;
    }

    public String getHash() {
        return hash;
    }
}
