package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Cadastro de clientes com auditoria de inclusões, alterações (endereço e telefone) e exclusões.
 * ATENDENTE cadastra e atualiza; GERENTE consulta e pode excluir clientes sem pedidos.
 */
@Service
public class ClienteService {

    static final String ENTIDADE = "CLIENTE";

    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, PedidoRepository pedidoRepository,
            AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll(Sort.by("nome"));
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    /** Mantido por compatibilidade: sem id cria, com id atualiza. */
    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente salvar(Cliente cliente) {
        return cliente.getId() == null || cliente.getId().isBlank()
                ? criar(cliente)
                : atualizar(cliente.getId(), cliente);
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente criar(Cliente dados) {
        Cliente cliente = new Cliente(); // ignora qualquer id enviado na requisição
        copiarCamposEditaveis(dados, cliente);
        Cliente salvo = clienteRepository.save(cliente);
        auditoriaService.registrar(TipoOperacao.INCLUSAO_CLIENTE, ENTIDADE, salvo.getId(),
                "Cliente " + salvo.getNome(), protegerTelefone(DiferencaCampos.comparar(Map.of(), retrato(salvo))),
                null);
        return salvo;
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente atualizar(String id, Cliente dados) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado para atualização."));
        Map<String, String> antes = retrato(cliente);
        copiarCamposEditaveis(dados, cliente); // o campo legado "endereco" é preservado
        List<AlteracaoCampo> alteracoes = DiferencaCampos.comparar(antes, retrato(cliente));
        if (alteracoes.isEmpty()) {
            return cliente;
        }
        Cliente salvo = clienteRepository.save(cliente);
        auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, ENTIDADE, salvo.getId(),
                "Cliente " + salvo.getNome(), protegerTelefone(alteracoes), null);
        return salvo;
    }

    /** Exclusão bloqueada para clientes com pedidos, preservando o histórico. */
    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
        if (pedidoRepository.existsByClienteId(id)) {
            throw new RegraNegocioException("O cliente " + cliente.getNome()
                    + " possui pedidos registrados e não pode ser excluído (histórico preservado).");
        }
        clienteRepository.deleteById(id);
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_CLIENTE, ENTIDADE, id, "Cliente " + cliente.getNome(),
                List.of(new AlteracaoCampo("nome", cliente.getNome(), null)), null);
    }

    private static void copiarCamposEditaveis(Cliente origem, Cliente destino) {
        destino.setNome(limpar(origem.getNome()));
        destino.setTelefone(limpar(origem.getTelefone()));
        destino.setCep(limpar(origem.getCep()));
        destino.setLogradouro(limpar(origem.getLogradouro()));
        destino.setNumero(limpar(origem.getNumero()));
        destino.setComplemento(limpar(origem.getComplemento()));
        destino.setBairro(limpar(origem.getBairro()));
        destino.setCidade(limpar(origem.getCidade()));
        String estado = limpar(origem.getEstado());
        destino.setEstado(estado == null ? null : estado.toUpperCase());
        if (destino.getNome() == null || destino.getTelefone() == null) {
            throw new RegraNegocioException("Nome e telefone do cliente são obrigatórios.");
        }
    }

    /** Valores atuais dos campos auditados (comparação feita sobre os valores completos). */
    private static Map<String, String> retrato(Cliente cliente) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("nome", cliente.getNome());
        campos.put("telefone", cliente.getTelefone());
        campos.put("cep", cliente.getCep());
        campos.put("logradouro", cliente.getLogradouro());
        campos.put("numero", cliente.getNumero());
        campos.put("complemento", cliente.getComplemento());
        campos.put("bairro", cliente.getBairro());
        campos.put("cidade", cliente.getCidade());
        campos.put("estado", cliente.getEstado());
        return campos;
    }

    /** Minimização de dados pessoais: o telefone é gravado mascarado na auditoria (DDD + 4 últimos dígitos). */
    private static List<AlteracaoCampo> protegerTelefone(List<AlteracaoCampo> alteracoes) {
        return alteracoes.stream()
                .map(a -> "telefone".equals(a.getCampo())
                        ? new AlteracaoCampo(a.getCampo(), DadosSensiveis.mascararTelefone(a.getValorAnterior()),
                                DadosSensiveis.mascararTelefone(a.getValorNovo()))
                        : a)
                .toList();
    }

    private static String limpar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
