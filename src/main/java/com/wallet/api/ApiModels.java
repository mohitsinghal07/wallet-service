package com.wallet.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() {}
    public record WalletResponse(UUID id, String user_id, long balance_paise) {}
    public record TransferRequest(@NotBlank String from, @NotBlank String to, @Min(1) long amount_paise,
                                   @NotBlank String idempotency_key) {}
    public record TransferResponse(UUID id, String from, String to, long amount_paise,
                                   String status, boolean idempotent_replay) {}
    public record ErrorResponse(String error, String message, String correlation_id) {}
}
