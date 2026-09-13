package com.wallet.service;

/** Thrown when an idempotency_key is reused with a different request body. Maps to HTTP 409. */
public class IdempotencyConflictException extends RuntimeException {}
