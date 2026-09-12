package com.wallet.api;

import com.wallet.api.ApiModels.ErrorResponse;
import com.wallet.service.TransferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.MDC;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private String cid(){return MDC.get("correlation_id");}
    @ExceptionHandler(TransferService.IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(){return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("IDEMPOTENCY_CONFLICT","same idempotency_key was used with a different request body",cid()));}
    @ExceptionHandler(TransferService.NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(TransferService.NotFoundException e){return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND",e.getMessage(),cid()));}
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException e){return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST",e.getMessage(),cid()));}
}
