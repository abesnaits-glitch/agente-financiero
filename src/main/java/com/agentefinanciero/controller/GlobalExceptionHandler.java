package com.agentefinanciero.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAll(Exception ex) {
        log.error("[Error] excepción no manejada: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(Map.of(
                        "error", ex.getClass().getSimpleName(),
                        "mensaje", ex.getMessage() != null ? ex.getMessage() : "sin mensaje"
                ));
    }
}
