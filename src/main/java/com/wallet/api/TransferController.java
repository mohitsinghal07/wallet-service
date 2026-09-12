package com.wallet.api;

import com.wallet.api.ApiModels.TransferRequest;
import com.wallet.api.ApiModels.TransferResponse;
import com.wallet.domain.Transfer;
import com.wallet.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/transfers")
public class TransferController {
    private final TransferService service;
    public TransferController(TransferService service){this.service=service;}

    @PostMapping
    public TransferResponse transfer(@Valid @RequestBody TransferRequest r) {
        TransferService.Result result = service.transfer(UUID.fromString(r.from()), UUID.fromString(r.to()), r.amount_paise(), r.idempotency_key());
        Transfer t = result.transfer();
        return new TransferResponse(t.id(), t.from().toString(), t.to().toString(), t.amountPaise(), t.status().name(), result.replay());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> get(@PathVariable UUID id) {
        Transfer t = service.get(id);
        if (t == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new TransferResponse(t.id(), t.from().toString(), t.to().toString(), t.amountPaise(), t.status().name(), false));
    }
}
