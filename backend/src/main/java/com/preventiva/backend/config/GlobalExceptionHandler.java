package com.preventiva.backend.config;

import com.preventiva.backend.dto.ApiErrorResponseDto;
import com.preventiva.backend.dto.FieldErrorDto;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponseDto> manejarIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        return construirRespuesta(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler({NoSuchElementException.class, EntityNotFoundException.class})
    public ResponseEntity<ApiErrorResponseDto> manejarNoEncontrado(
            RuntimeException ex, HttpServletRequest request) {
        return construirRespuesta(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponseDto> manejarMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorDto> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> FieldErrorDto.builder()
                        .campo(error.getField())
                        .mensaje(error.getDefaultMessage())
                        .build())
                .toList();

        return construirRespuesta(
                HttpStatus.BAD_REQUEST,
                "La solicitud contiene campos inválidos.",
                request,
                fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponseDto> manejarJsonInvalido(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return construirRespuesta(
                HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud no es un JSON válido.",
                request,
                null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponseDto> manejarExcepcionGenerica(
            Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {}", request.getRequestURI(), ex);

        return construirRespuesta(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request, null);
    }

    private ResponseEntity<ApiErrorResponseDto> construirRespuesta(
            HttpStatus status, String mensaje, HttpServletRequest request, List<FieldErrorDto> fieldErrors) {
        ApiErrorResponseDto body = ApiErrorResponseDto.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(mensaje)
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(status).body(body);
    }
}
