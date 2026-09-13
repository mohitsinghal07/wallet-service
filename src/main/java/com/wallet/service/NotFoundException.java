package com.wallet.service;

/** Thrown when a referenced wallet does not exist. Maps to HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
