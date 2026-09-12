package com.wallet.repo;

import com.wallet.domain.Transfer;
import com.wallet.domain.TransferStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class TransferRepository {
    private final JdbcTemplate jdbc;
    public TransferRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Transfer findByKey(String key) {
        return jdbc.query("SELECT id,idempotency_key,from_wallet_id,to_wallet_id,amount_paise,status,created_at,completed_at FROM transfers WHERE idempotency_key=?", rs -> {
            if (!rs.next()) return null;
            return new Transfer(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                    rs.getLong(5), TransferStatus.valueOf(rs.getString(6)), rs.getTimestamp(7).toInstant(),
                    rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant());
        }, key);
    }
    public Transfer findById(UUID id) {
        return jdbc.query("SELECT id,idempotency_key,from_wallet_id,to_wallet_id,amount_paise,status,created_at,completed_at FROM transfers WHERE id=?", rs -> {
            if (!rs.next()) return null;
            return new Transfer(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                    rs.getLong(5), TransferStatus.valueOf(rs.getString(6)), rs.getTimestamp(7).toInstant(),
                    rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant());
        }, id);
    }
    public boolean insertIfAbsent(String key, UUID from, UUID to, long amount) {
        return jdbc.update("INSERT INTO transfers(idempotency_key,from_wallet_id,to_wallet_id,amount_paise,status) VALUES (?,?,?,?,?) ON CONFLICT (idempotency_key) DO NOTHING",
                key, from, to, amount, TransferStatus.PROCESSING.name()) == 1;
    }
    public void complete(UUID id) {
        jdbc.update("UPDATE transfers SET status=?, completed_at=now() WHERE id=?", TransferStatus.COMPLETED.name(), id);
    }
    public void decline(UUID id) {
        jdbc.update("UPDATE transfers SET status=?, completed_at=now() WHERE id=?", TransferStatus.DECLINED_INSUFFICIENT_FUNDS.name(), id);
    }
}
