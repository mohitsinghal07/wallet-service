package com.wallet.api;

import com.wallet.api.ApiModels.DepositRequest;
import com.wallet.api.ApiModels.WalletResponse;
import com.wallet.domain.Wallet;
import com.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/wallets")
public class WalletController {
    private final WalletService service;
    public WalletController(WalletService service){this.service=service;}

    @PostMapping
    public WalletResponse getOrCreate(@RequestHeader(value="Authorization", required=true) String auth) {
        String user = auth.replaceFirst("^Bearer\\s+", "");
        Wallet w = service.getOrCreate(user);
        return new WalletResponse(w.id(), w.userId(), w.balancePaise());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> get(@PathVariable UUID id) {
        Wallet w = service.get(id);
        return w == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(new WalletResponse(w.id(), w.userId(), w.balancePaise()));
    }

    @PostMapping("/{id}/deposit")
    public WalletResponse deposit(@PathVariable UUID id, @Valid @RequestBody DepositRequest r) {
        Wallet w = service.deposit(id, r.amount_paise(), r.idempotency_key());
        return new WalletResponse(w.id(), w.userId(), w.balancePaise());
    }
}
