package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.ResultadoIntegridade;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditoriaServiceTest {

    @Mock
    private RegistroAuditoriaRepository repository;
    @Mock
    private MongoTemplate mongoTemplate;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-23T17:35:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final List<RegistroAuditoria> gravados = new ArrayList<>();
    private AuditoriaService service;

    @BeforeEach
    void configurar() {
        service = new AuditoriaService(repository, mongoTemplate, clock);
        // Simula a coleção: insert acrescenta, findTop devolve o último, findAll devolve em ordem
        when(repository.insert(any(RegistroAuditoria.class))).thenAnswer(inv -> {
            RegistroAuditoria registro = inv.getArgument(0);
            gravados.add(registro);
            return registro;
        });
        when(repository.findTopByOrderBySequenciaDesc())
                .thenAnswer(inv -> gravados.isEmpty() ? Optional.empty() : Optional.of(gravados.get(gravados.size() - 1)));
        when(repository.findAllByOrderBySequenciaAsc()).thenAnswer(inv -> List.copyOf(gravados));
        TestSeguranca.autenticar("atendente01", "ATENDENTE");
    }

    @AfterEach
    void limpar() {
        TestSeguranca.limpar();
    }

    @Test
    void registraUsuarioPerfilDataHoraOperacaoEValores() {
        RegistroAuditoria registro = service.registrar(TipoOperacao.ALTERACAO_CLIENTE, "CLIENTE", "c1", "Cliente Maria",
                List.of(new AlteracaoCampo("logradouro", "Rua das Flores", "Av. Brasil")), null);

        assertThat(registro.getUsuario()).isEqualTo("atendente01");
        assertThat(registro.getPerfil()).isEqualTo("ATENDENTE");
        assertThat(registro.getDataHora()).isEqualTo(LocalDateTime.of(2026, 9, 23, 14, 35));
        assertThat(registro.getOperacao()).isEqualTo(TipoOperacao.ALTERACAO_CLIENTE);
        assertThat(registro.getEntidadeId()).isEqualTo("c1");
        assertThat(registro.getAlteracoes()).singleElement().satisfies(a -> {
            assertThat(a.getValorAnterior()).isEqualTo("Rua das Flores");
            assertThat(a.getValorNovo()).isEqualTo("Av. Brasil");
        });
        assertThat(registro.getSequencia()).isEqualTo(1L);
        assertThat(registro.getHashAnterior()).isEqualTo(AuditoriaService.HASH_INICIAL);
        assertThat(registro.getHash()).hasSize(64);
    }

    @Test
    void registrosSaoEncadeadosPorHash() {
        RegistroAuditoria primeiro = registrarExemplo("Rua A", "Rua B");
        RegistroAuditoria segundo = registrarExemplo("Rua B", "Rua C");

        assertThat(segundo.getSequencia()).isEqualTo(2L);
        assertThat(segundo.getHashAnterior()).isEqualTo(primeiro.getHash());
    }

    @Test
    void camposDeCredenciaisNuncaSaoGravados() {
        RegistroAuditoria registro = service.registrar(TipoOperacao.ALTERACAO_USUARIO, "USUARIO", "u1", "Usuário ana",
                List.of(new AlteracaoCampo("senha", "segredoAntigo", "segredoNovo"),
                        new AlteracaoCampo("password", null, "outraSenha")), null);

        assertThat(registro.getAlteracoes()).allSatisfy(a -> {
            assertThat(a.getValorNovo()).isEqualTo(DadosSensiveis.VALOR_PROTEGIDO);
            assertThat(a.getValorAnterior()).isIn(null, DadosSensiveis.VALOR_PROTEGIDO);
        });
    }

    @Test
    void verificacaoDeIntegridadeAprovaCadeiaOriginal() {
        registrarExemplo("Rua A", "Rua B");
        registrarExemplo("Rua B", "Rua C");
        registrarExemplo("Rua C", "Rua D");

        ResultadoIntegridade resultado = service.verificarIntegridade();

        assertThat(resultado.integro()).isTrue();
        assertThat(resultado.registrosVerificados()).isEqualTo(3);
    }

    @Test
    void verificacaoDetectaRegistroAdulteradoNoBanco() {
        registrarExemplo("Rua A", "Rua B");
        RegistroAuditoria original = registrarExemplo("Rua B", "Rua C");
        registrarExemplo("Rua C", "Rua D");

        // Alguém altera o "valor novo" diretamente no MongoDB, mantendo o hash antigo
        RegistroAuditoria adulterado = new RegistroAuditoria(original.getId(), original.getSequencia(),
                original.getDataHora(), original.getUsuario(), original.getPerfil(), original.getOperacao(),
                original.getEntidade(), original.getEntidadeId(), original.getDescricao(),
                List.of(new AlteracaoCampo("logradouro", "Rua B", "Rua FALSA")), original.getJustificativa(),
                original.getHashAnterior(), original.getHash());
        gravados.set(1, adulterado);

        ResultadoIntegridade resultado = service.verificarIntegridade();

        assertThat(resultado.integro()).isFalse();
        assertThat(resultado.sequenciaComProblema()).isEqualTo(2L);
    }

    @Test
    void verificacaoDetectaRegistroRemovido() {
        registrarExemplo("Rua A", "Rua B");
        registrarExemplo("Rua B", "Rua C");
        registrarExemplo("Rua C", "Rua D");
        gravados.remove(1);

        assertThat(service.verificarIntegridade().integro()).isFalse();
    }

    @Test
    void acessoNegadoDeAnonimoNaoGeraRegistro() {
        TestSeguranca.limpar();
        service.registrarAcessoNegado("POST /api/pedidos", "Token CSRF ausente ou inválido.");
        verify(repository, never()).insert(any(RegistroAuditoria.class));
    }

    private RegistroAuditoria registrarExemplo(String anterior, String novo) {
        return service.registrar(TipoOperacao.ALTERACAO_CLIENTE, "CLIENTE", "c1", "Cliente Maria",
                List.of(new AlteracaoCampo("logradouro", anterior, novo)), null);
    }
}
