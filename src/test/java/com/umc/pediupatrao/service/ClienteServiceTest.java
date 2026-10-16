package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Captor
    private ArgumentCaptor<List<AlteracaoCampo>> alteracoes;

    private ClienteService service;
    private Cliente existente;

    @BeforeEach
    void configurar() {
        service = new ClienteService(clienteRepository, pedidoRepository, auditoriaService);
        existente = cliente("Rua das Flores", "(11) 98765-4321");
        existente.setId("c1");
        existente.setEstado("SP"); // já normalizado, como gravado pelo serviço
        existente.setEndereco("Endereço antigo em texto livre");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(existente));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId("novo");
            }
            return c;
        });
    }

    @Test
    void alteracaoDeEnderecoRegistraValorAnteriorENovo() {
        service.atualizar("c1", cliente("Av. Brasil", "(11) 98765-4321"));

        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_CLIENTE), eq("CLIENTE"), eq("c1"), anyString(),
                alteracoes.capture(), isNull());
        assertThat(alteracoes.getValue()).singleElement().satisfies(a -> {
            assertThat(a.getCampo()).isEqualTo("logradouro");
            assertThat(a.getValorAnterior()).isEqualTo("Rua das Flores");
            assertThat(a.getValorNovo()).isEqualTo("Av. Brasil");
        });
        assertThat(existente.getEndereco()).as("campo legado preservado").isEqualTo("Endereço antigo em texto livre");
    }

    @Test
    void telefoneEhAuditadoComMascara() {
        service.atualizar("c1", cliente("Rua das Flores", "(11) 91234-5678"));

        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_CLIENTE), eq("CLIENTE"), eq("c1"), anyString(),
                alteracoes.capture(), isNull());
        AlteracaoCampo telefone = alteracoes.getValue().get(0);
        assertThat(telefone.getCampo()).isEqualTo("telefone");
        assertThat(telefone.getValorAnterior()).isEqualTo("(11) *****-4321");
        assertThat(telefone.getValorNovo()).isEqualTo("(11) *****-5678");
    }

    @Test
    void semAlteracaoNaoGeraRegistro() {
        service.atualizar("c1", cliente("Rua das Flores", "(11) 98765-4321"));

        verify(clienteRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void inclusaoIgnoraIdEnviadoEAudita() {
        Cliente dados = cliente("Rua A", "(11) 3333-4444");
        dados.setId("c1"); // tentativa de sobrescrever outro cliente

        Cliente criado = service.criar(dados);

        assertThat(criado.getId()).isEqualTo("novo");
        assertThat(existente.getLogradouro()).isEqualTo("Rua das Flores");
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_CLIENTE), eq("CLIENTE"), eq("novo"), anyString(),
                alteracoes.capture(), isNull());
        assertThat(alteracoes.getValue()).anySatisfy(a -> {
            assertThat(a.getCampo()).isEqualTo("telefone");
            assertThat(a.getValorNovo()).isEqualTo("(11) *****-4444");
        });
    }

    @Test
    void exclusaoBloqueadaParaClienteComPedidos() {
        when(pedidoRepository.existsByClienteId("c1")).thenReturn(true);

        assertThatThrownBy(() -> service.excluir("c1"))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("possui pedidos");
        verify(clienteRepository, never()).deleteById(any());
    }

    private static Cliente cliente(String logradouro, String telefone) {
        Cliente cliente = new Cliente();
        cliente.setNome("Maria Souza");
        cliente.setTelefone(telefone);
        cliente.setLogradouro(logradouro);
        cliente.setNumero("100");
        cliente.setCidade("Mogi das Cruzes");
        cliente.setEstado("sp");
        return cliente;
    }
}
