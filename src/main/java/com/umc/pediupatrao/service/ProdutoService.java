package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Cardápio. Preços são informação comercial: somente o GERENTE altera, e as mudanças são auditadas.
 * Os pedidos guardam uma cópia do nome e do preço, então alterar um produto não muda pedidos antigos.
 */
@Service
public class ProdutoService {

    public static final List<String> TIPOS = List.of("Pizza", "Esfiha", "Lanche", "Bebida", "Porção", "Adicional");
    static final String ENTIDADE = "PRODUTO";

    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;

    public ProdutoService(ProdutoRepository produtoRepository, AuditoriaService auditoriaService) {
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public List<Produto> listarProdutos() {
        return produtoRepository.findAll(Sort.by("tipo", "nome"));
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public List<Produto> listarAtivos() {
        return listarProdutos().stream().filter(Produto::isDisponivel).toList();
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Optional<Produto> buscarPorId(String id) {
        return produtoRepository.findById(id);
    }

    /** Mantido por compatibilidade: sem id cria, com id atualiza. */
    @PreAuthorize("hasRole('GERENTE')")
    public Produto salvar(Produto produto) {
        return produto.getId() == null || produto.getId().isBlank()
                ? criar(produto)
                : atualizar(produto.getId(), produto);
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Produto criar(Produto dados) {
        Produto produto = new Produto();
        copiarCampos(dados, produto);
        Produto salvo = produtoRepository.save(produto);
        auditoriaService.registrar(TipoOperacao.INCLUSAO_PRODUTO, ENTIDADE, salvo.getId(), "Produto " + salvo.getNome(),
                DiferencaCampos.comparar(Map.of(), retrato(salvo)), null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Produto atualizar(String id, Produto dados) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado para atualização."));
        Map<String, String> antes = retrato(produto);
        copiarCampos(dados, produto);
        List<AlteracaoCampo> alteracoes = DiferencaCampos.comparar(antes, retrato(produto));
        if (alteracoes.isEmpty()) {
            return produto;
        }
        Produto salvo = produtoRepository.save(produto);
        auditoriaService.registrar(TipoOperacao.ALTERACAO_PRODUTO, ENTIDADE, salvo.getId(),
                "Produto " + salvo.getNome(), alteracoes, null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado."));
        produtoRepository.deleteById(id);
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_PRODUTO, ENTIDADE, id, "Produto " + produto.getNome(),
                DiferencaCampos.comparar(retrato(produto), Map.of()), null);
    }

    private static void copiarCampos(Produto origem, Produto destino) {
        if (origem.getNome() == null || origem.getNome().isBlank()) {
            throw new RegraNegocioException("Informe o nome do produto.");
        }
        if (origem.getTipo() == null || !TIPOS.contains(origem.getTipo())) {
            throw new RegraNegocioException("Tipo de produto inválido. Use: " + String.join(", ", TIPOS) + ".");
        }
        if (origem.getPreco() == null || origem.getPreco() <= 0) {
            throw new RegraNegocioException("O preço deve ser maior que zero.");
        }
        destino.setNome(origem.getNome().trim());
        destino.setTipo(origem.getTipo());
        destino.setPreco(BigDecimal.valueOf(origem.getPreco()).setScale(2, RoundingMode.HALF_UP).doubleValue());
        destino.setAtivo(Boolean.TRUE.equals(origem.getAtivo()));
    }

    private static Map<String, String> retrato(Produto produto) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("nome", produto.getNome());
        campos.put("tipo", produto.getTipo());
        campos.put("preco", produto.getPreco() == null ? null
                : BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP).toPlainString());
        campos.put("ativo", String.valueOf(Boolean.TRUE.equals(produto.getAtivo())));
        return campos;
    }
}
