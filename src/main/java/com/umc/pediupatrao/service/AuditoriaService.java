package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.AuditoriaFiltro;
import com.umc.pediupatrao.dto.ResultadoIntegridade;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import com.umc.pediupatrao.security.UsuarioAutenticado;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Registro e consulta da auditoria.
 * <ul>
 * <li>Os registros só são incluídos (insert); não há operação de alteração ou exclusão.</li>
 * <li>Cada registro guarda o hash SHA-256 do anterior, formando uma cadeia verificável.</li>
 * <li>Valores de campos de credenciais são substituídos por [PROTEGIDO].</li>
 * <li>A consulta é restrita aos perfis ADMIN e GERENTE.</li>
 * </ul>
 */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    static final String HASH_INICIAL = "0".repeat(64);
    private static final String SEPARADOR = "\u001F";

    private final RegistroAuditoriaRepository repository;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public AuditoriaService(RegistroAuditoriaRepository repository, MongoTemplate mongoTemplate, Clock clock) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    /**
     * Registra um evento identificando o usuário autenticado e seu perfil.
     * Sincronizado para manter a sequência e o encadeamento de hashes consistentes.
     */
    public synchronized RegistroAuditoria registrar(TipoOperacao operacao, String entidade, String entidadeId,
            String descricao, List<AlteracaoCampo> alteracoes, String justificativa) {
        Objects.requireNonNull(operacao, "operação obrigatória");
        List<AlteracaoCampo> protegidas = protegerCredenciais(alteracoes);

        RegistroAuditoria ultimo = repository.findTopByOrderBySequenciaDesc().orElse(null);
        long sequencia = ultimo == null ? 1L : ultimo.getSequencia() + 1;
        String hashAnterior = ultimo == null ? HASH_INICIAL : ultimo.getHash();
        // MongoDB armazena milissegundos: truncar garante que o hash recalculado depois seja o mesmo
        LocalDateTime dataHora = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        String usuario = UsuarioAutenticado.username();
        String perfil = UsuarioAutenticado.perfil();
        String justificativaLimpa = justificativa == null || justificativa.isBlank() ? null : justificativa.trim();

        RegistroAuditoria semHash = new RegistroAuditoria(null, sequencia, dataHora, usuario, perfil, operacao,
                entidade, entidadeId, descricao, protegidas, justificativaLimpa, hashAnterior, null);
        RegistroAuditoria registro = new RegistroAuditoria(null, sequencia, dataHora, usuario, perfil, operacao,
                entidade, entidadeId, descricao, protegidas, justificativaLimpa, hashAnterior,
                calcularHash(semHash));

        RegistroAuditoria salvo = repository.insert(registro);
        // Log apenas com metadados (sem valores de campos)
        log.info("Auditoria #{}: {} {} {} por {} ({})", sequencia, operacao, entidade, entidadeId, usuario, perfil);
        return salvo;
    }

    /** Registra tentativa de acesso não autorizado de um usuário autenticado. Nunca lança exceção. */
    public void registrarAcessoNegado(String rota, String motivo) {
        if (!UsuarioAutenticado.isAutenticado()) {
            return;
        }
        try {
            registrar(TipoOperacao.ACESSO_NEGADO, "ROTA", rota, motivo, List.of(), null);
        } catch (RuntimeException e) {
            log.error("Falha ao registrar acesso negado na auditoria: {}", e.getMessage());
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public Page<RegistroAuditoria> consultar(AuditoriaFiltro filtro, Pageable pageable) {
        Query query = new Query();
        if (filtro != null) {
            if (temTexto(filtro.getEntidade())) {
                query.addCriteria(Criteria.where("entidade").is(filtro.getEntidade().trim().toUpperCase()));
            }
            if (temTexto(filtro.getEntidadeId())) {
                query.addCriteria(Criteria.where("entidadeId").is(filtro.getEntidadeId().trim()));
            }
            if (filtro.getOperacao() != null) {
                query.addCriteria(Criteria.where("operacao").is(filtro.getOperacao()));
            }
            if (temTexto(filtro.getUsuario())) {
                query.addCriteria(Criteria.where("usuario")
                        .regex("^" + Pattern.quote(filtro.getUsuario().trim()) + "$", "i"));
            }
            if (filtro.getDataInicio() != null || filtro.getDataFim() != null) {
                Criteria periodo = Criteria.where("dataHora");
                if (filtro.getDataInicio() != null) {
                    periodo = periodo.gte(filtro.getDataInicio().atStartOfDay());
                }
                if (filtro.getDataFim() != null) {
                    periodo = periodo.lt(filtro.getDataFim().plusDays(1).atStartOfDay());
                }
                query.addCriteria(periodo);
            }
        }
        long total = mongoTemplate.count(query, RegistroAuditoria.class);
        query.with(Sort.by(Sort.Direction.DESC, "sequencia")).with(pageable);
        List<RegistroAuditoria> registros = mongoTemplate.find(query, RegistroAuditoria.class);
        return new PageImpl<>(registros, pageable, total);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public RegistroAuditoria buscarPorId(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Registro de auditoria não encontrado."));
    }

    /** Recalcula a cadeia de hashes para detectar registros alterados, removidos ou inseridos no banco. */
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResultadoIntegridade verificarIntegridade() {
        List<RegistroAuditoria> registros = repository.findAllByOrderBySequenciaAsc();
        String hashEsperado = HASH_INICIAL;
        long sequenciaEsperada = 1;
        for (RegistroAuditoria registro : registros) {
            if (registro.getSequencia() == null || registro.getSequencia() != sequenciaEsperada) {
                return falha(registros, sequenciaEsperada, "Sequência interrompida: esperado #" + sequenciaEsperada
                        + ", encontrado #" + registro.getSequencia() + " (registro removido ou inserido fora da ordem).");
            }
            if (!hashEsperado.equals(registro.getHashAnterior())) {
                return falha(registros, registro.getSequencia(),
                        "O registro #" + registro.getSequencia() + " não está encadeado ao registro anterior.");
            }
            if (!calcularHash(registro).equals(registro.getHash())) {
                return falha(registros, registro.getSequencia(),
                        "O conteúdo do registro #" + registro.getSequencia() + " foi alterado após a gravação.");
            }
            hashEsperado = registro.getHash();
            sequenciaEsperada++;
        }
        return new ResultadoIntegridade(true, registros.size(), null,
                "Cadeia íntegra: " + registros.size() + " registro(s) verificado(s).");
    }

    private ResultadoIntegridade falha(List<RegistroAuditoria> registros, Long sequencia, String mensagem) {
        log.warn("Falha de integridade na auditoria: {}", mensagem);
        return new ResultadoIntegridade(false, registros.size(), sequencia, mensagem);
    }

    /** Hash SHA-256 sobre o conteúdo canônico do registro (todos os campos exceto id e o próprio hash). */
    static String calcularHash(RegistroAuditoria registro) {
        StringBuilder conteudo = new StringBuilder()
                .append(registro.getSequencia()).append(SEPARADOR)
                .append(registro.getDataHora()).append(SEPARADOR)
                .append(registro.getUsuario()).append(SEPARADOR)
                .append(registro.getPerfil()).append(SEPARADOR)
                .append(registro.getOperacao()).append(SEPARADOR)
                .append(registro.getEntidade()).append(SEPARADOR)
                .append(registro.getEntidadeId()).append(SEPARADOR)
                .append(registro.getDescricao()).append(SEPARADOR)
                .append(registro.getJustificativa()).append(SEPARADOR);
        for (AlteracaoCampo alteracao : registro.getAlteracoes()) {
            conteudo.append(alteracao.getCampo()).append('=')
                    .append(alteracao.getValorAnterior()).append("->")
                    .append(alteracao.getValorNovo()).append(SEPARADOR);
        }
        conteudo.append(registro.getHashAnterior());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(conteudo.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private static List<AlteracaoCampo> protegerCredenciais(List<AlteracaoCampo> alteracoes) {
        if (alteracoes == null) {
            return List.of();
        }
        return alteracoes.stream()
                .map(a -> DadosSensiveis.isCampoDeCredencial(a.getCampo())
                        ? new AlteracaoCampo(a.getCampo(), protegido(a.getValorAnterior()), protegido(a.getValorNovo()))
                        : a)
                .toList();
    }

    private static String protegido(String valor) {
        return valor == null ? null : DadosSensiveis.VALOR_PROTEGIDO;
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
