package com.wallet.domain;

import java.time.Instant;
import java.util.UUID;

public record Transfer(UUID id, String idempotencyKey, UUID from, UUID to, long amountPaise,
                       TransferStatus status, Instant createdAt, Instant completedAt) {}
