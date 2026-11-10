package com.umc.pediupatrao.exception;

import com.umc.pediupatrao.dto.ErroResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Erros das APIs REST em JSON padronizado. AccessDeniedException NÃO é tratada aqui de propósito:
 * ela segue para o Spring Security, que responde 403 e registra a tentativa na auditoria.
 */
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> naoEncontrado(RecursoNaoEncontradoException ex, HttpServletRequest request) {
        return resposta(HttpStatus.NOT_FOUND, "Não encontrado", ex.getMessage(), request);
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResponse> regraNegocio(RegraNegocioException ex, HttpServletRequest request) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio violada", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> validacao(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError erro : ex.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(erro.getField(), erro.isBindingFailure()
                    ? "Valor inválido: " + erro.getRejectedValue()
                    : erro.getDefaultMessage());
        }
        ErroResponse corpo = new ErroResponse(LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(),
                "Dados inválidos", "Verifique os campos informados.", request.getRequestURI(), campos);
        return ResponseEntity.badRequest().body(corpo);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErroResponse> requisicaoInvalida(Exception ex, HttpServletRequest request) {
        return resposta(HttpStatus.BAD_REQUEST, "Requisição inválida",
                "Corpo ou parâmetros da requisição em formato inválido.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResponse> tipoInvalido(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        String mensagem = "Valor inválido para o parâmetro '" + ex.getName() + "'.";
        ErroResponse corpo = new ErroResponse(LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(),
                "Requisição inválida", mensagem, request.getRequestURI(),
                Map.of(ex.getName(), "Valor inválido: " + ex.getValue()));
        return ResponseEntity.badRequest().body(corpo);
    }

    @ExceptionHandler(ParametroInvalidoException.class)
    public ResponseEntity<ErroResponse> parametroInvalido(ParametroInvalidoException ex, HttpServletRequest request) {
        ErroResponse corpo = new ErroResponse(LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(),
                "Parâmetro inválido", ex.getMessage(), request.getRequestURI(),
                Map.of(ex.getParametro(), ex.getMessage()));
        return ResponseEntity.badRequest().body(corpo);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErroResponse> concorrencia(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return resposta(HttpStatus.CONFLICT, "Conflito",
                "O registro foi alterado por outro usuário. Recarregue e tente novamente.", request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErroResponse> duplicado(DuplicateKeyException ex, HttpServletRequest request) {
        return resposta(HttpStatus.CONFLICT, "Conflito", "Registro duplicado.", request);
    }

    private static ResponseEntity<ErroResponse> resposta(HttpStatus status, String erro, String mensagem,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErroResponse.de(status.value(), erro, mensagem, request.getRequestURI()));
    }
}
