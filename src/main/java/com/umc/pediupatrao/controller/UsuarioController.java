package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.dto.UsuarioResponse;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.service.UsuarioService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Administração de usuários via API — exclusiva do ADMIN. As respostas nunca incluem a senha. */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // Endpoint para listar todos os usuários
    @GetMapping
    public List<UsuarioResponse> listarTodos() {
        return usuarioService.listarTodos().stream().map(UsuarioResponse::de).toList();
    }

    // Endpoint para buscar um usuário por ID
    @GetMapping("/{id}")
    public UsuarioResponse buscarPorId(@PathVariable String id) {
        return usuarioService.buscarPorId(id)
                .map(UsuarioResponse::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    // Endpoint para criar um novo usuário
    @PostMapping
    public ResponseEntity<UsuarioResponse> criarUsuario(@Valid @RequestBody UsuarioForm usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.de(usuarioService.criarUsuario(usuario)));
    }

    // Endpoint para atualizar perfil e/ou senha de um usuário
    @PutMapping("/{id}")
    public UsuarioResponse atualizarUsuario(@PathVariable String id, @Valid @RequestBody UsuarioForm usuario) {
        return UsuarioResponse.de(usuarioService.atualizarUsuario(id, usuario));
    }

    // Endpoint para deletar um usuário
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarUsuario(@PathVariable String id) {
        usuarioService.excluirUsuario(id);
        return ResponseEntity.noContent().build();
    }
}
