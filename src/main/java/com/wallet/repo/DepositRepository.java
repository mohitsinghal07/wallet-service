package com.wallet.repo;

import com.wallet.domain.Deposit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class DepositRepository {
    private final JdbcTemplate jdbc;
    public DepositRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean insertIfAbsent(String key, UUID walletId, long amount) {
        return jdbc.update("INSERT INTO deposits(idempotency_key,wallet_id,amount_paise) VALUES (?,?,?) ON CONFLICT (idempotency_key) DO NOTHING",
                key, walletId, amount) == 1;
    }
    public Deposit findByKey(String key) {
        return jdbc.query("SELECT id,idempotency_key,wallet_id,amount_paise,created_at FROM deposits WHERE idempotency_key=?", rs -> {
            if (!rs.next()) return null;
            return new Deposit(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                    rs.getLong(4), rs.getTimestamp(5).toInstant());
        }, key);
    }
}
