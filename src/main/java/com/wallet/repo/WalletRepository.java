package com.wallet.repo;

import com.wallet.domain.Wallet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class WalletRepository {
    private final JdbcTemplate jdbc;
    public WalletRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Wallet findById(UUID id) {
        return jdbc.query("SELECT id,user_id,balance_paise FROM wallets WHERE id=?", rs ->
                rs.next() ? new Wallet(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)) : null, id);
    }
    public Wallet findByUserId(String userId) {
        return jdbc.query("SELECT id,user_id,balance_paise FROM wallets WHERE user_id=?", rs ->
                rs.next() ? new Wallet(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)) : null, userId);
    }
    public UUID insertWalletIfAbsent(String userId) {
        return jdbc.query("INSERT INTO wallets(user_id,balance_paise) VALUES (?,0) ON CONFLICT (user_id) DO NOTHING RETURNING id",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, userId);
    }
    public Wallet lockById(UUID id) {
        return jdbc.query("SELECT id,user_id,balance_paise FROM wallets WHERE id=? FOR UPDATE", rs ->
                rs.next() ? new Wallet(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)) : null, id);
    }
    public int debitIfSufficient(UUID id, long amount) {
        return jdbc.update("UPDATE wallets SET balance_paise=balance_paise-?, updated_at=now() WHERE id=? AND balance_paise>=?", amount, id, amount);
    }
    public int credit(UUID id, long amount) {
        return jdbc.update("UPDATE wallets SET balance_paise=balance_paise+?, updated_at=now() WHERE id=?", amount, id);
    }
    public List<Wallet> findAll() {
        return jdbc.query("SELECT id,user_id,balance_paise FROM wallets ORDER BY id", (rs, n) ->
                new Wallet(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)));
    }
}
