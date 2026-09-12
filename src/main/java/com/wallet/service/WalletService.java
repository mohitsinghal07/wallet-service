package com.wallet.service;

import com.wallet.domain.Wallet;
import com.wallet.repo.WalletRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WalletService {
    private final WalletRepository wallets;
    private final JdbcTemplate jdbc;

    public WalletService(WalletRepository wallets, JdbcTemplate jdbc) { this.wallets = wallets; this.jdbc = jdbc; }

    @Transactional
    public Wallet getOrCreate(String userId) {
        Wallet existing = wallets.findByUserId(userId);
        if (existing != null) return existing;
        UUID id = wallets.insertWalletIfAbsent(userId);
        return id != null ? wallets.findById(id) : wallets.findByUserId(userId);
    }

    public Wallet get(UUID id) { return wallets.findById(id); }

    public long totalBalance() {
        Long value = jdbc.queryForObject("SELECT COALESCE(SUM(balance_paise),0) FROM wallets", Long.class);
        return value == null ? 0L : value;
    }
}
