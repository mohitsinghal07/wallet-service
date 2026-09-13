package com.wallet.service;

import com.wallet.domain.Deposit;
import com.wallet.domain.Wallet;
import com.wallet.repo.DepositRepository;
import com.wallet.repo.WalletRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WalletService {
    private final WalletRepository wallets;
    private final DepositRepository deposits;
    private final JdbcTemplate jdbc;
    private final Counter deposited;
    private final Counter depositReplays;
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    public WalletService(WalletRepository wallets, DepositRepository deposits, JdbcTemplate jdbc, MeterRegistry registry) {
        this.wallets = wallets;
        this.deposits = deposits;
        this.jdbc = jdbc;
        this.deposited = registry.counter("wallet_deposits_total");
        this.depositReplays = registry.counter("wallet_deposits_idempotent_replays_total");
    }

    @Transactional
    public Wallet getOrCreate(String userId) {
        Wallet existing = wallets.findByUserId(userId);
        if (existing != null) return existing;
        UUID id = wallets.insertWalletIfAbsent(userId);
        return id != null ? wallets.findById(id) : wallets.findByUserId(userId);
    }

    /**
     * Funds a wallet, idempotently. This is the one operation that increases the total money
     * supply; transfers only move money between wallets. The idempotency_key is claimed in the
     * same transaction as the credit: a repeat with the same body replays without re-crediting,
     * and a repeat with a different body is a conflict (409).
     */
    @Transactional
    public Wallet deposit(UUID id, long amount, String key) {
        if (amount <= 0) throw new IllegalArgumentException("amount_paise must be positive");
        if (wallets.findById(id) == null) throw new NotFoundException("wallet not found");

        boolean claimed = deposits.insertIfAbsent(key, id, amount);
        if (!claimed) {
            Deposit existing = deposits.findByKey(key);
            if (existing == null) throw new IllegalStateException("idempotency race could not be resolved");
            if (!existing.walletId().equals(id) || existing.amountPaise() != amount) {
                throw new IdempotencyConflictException();
            }
            depositReplays.increment();
            log.info("deposit.idempotent_replay idempotency_key={} wallet_id={} amount_paise={}", key, id, amount);
            return wallets.findById(id);
        }

        wallets.credit(id, amount);
        deposited.increment();
        log.info("wallet.deposited wallet_id={} amount_paise={} idempotency_key={}", id, amount, key);
        return wallets.findById(id);
    }

    public Wallet get(UUID id) { return wallets.findById(id); }

    public long totalBalance() {
        Long value = jdbc.queryForObject("SELECT COALESCE(SUM(balance_paise),0) FROM wallets", Long.class);
        return value == null ? 0L : value;
    }
}
