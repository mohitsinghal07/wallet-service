package com.wallet.domain;

import java.util.UUID;

public record Wallet(UUID id, String userId, long balancePaise) {}
