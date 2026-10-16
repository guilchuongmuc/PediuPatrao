package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.service.ProdutoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    private final ProdutoService produtoService;

    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public List<Produto> listarProdutos() {
        return produtoService.listarProdutos();
    }

    @GetMapping("/{id}")
    public Produto buscarProduto(@PathVariable String id) {
        return produtoService.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluirProduto(@PathVariable String id) {
        produtoService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<Produto> salvarProduto(@Valid @RequestBody Produto produto) {
        Produto salvo = produtoService.criar(produto);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Produto> atualizarProduto(@PathVariable String id, @Valid @RequestBody Produto produto) {
        Produto atualizado = produtoService.atualizar(id, produto);
        return ResponseEntity.ok(atualizado);
    }
}
