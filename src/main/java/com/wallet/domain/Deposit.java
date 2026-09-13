package com.wallet.domain;

import java.time.Instant;
import java.util.UUID;

public record Deposit(UUID id, String idempotencyKey, UUID walletId, long amountPaise, Instant createdAt) {}
