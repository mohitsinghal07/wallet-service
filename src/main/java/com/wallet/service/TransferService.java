package com.wallet.service;

import com.wallet.domain.Transfer;
import com.wallet.domain.TransferStatus;
import com.wallet.domain.Wallet;
import com.wallet.repo.TransferRepository;
import com.wallet.repo.WalletRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransferService {
    private final TransferRepository transfers;
    private final WalletRepository wallets;
    private final Counter created;
    private final Counter declined;
    private final Counter replayed;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TransferService.class);

    public TransferService(TransferRepository transfers, WalletRepository wallets, MeterRegistry registry) {
        this.transfers = transfers; this.wallets = wallets;
        this.created = registry.counter("wallet_transfers_created_total");
        this.declined = registry.counter("wallet_transfers_declined_insufficient_funds_total");
        this.replayed = registry.counter("wallet_transfers_idempotent_replays_total");
    }

    @Transactional
    public Result transfer(UUID from, UUID to, long amount, String key) {
        if (from.equals(to)) throw new IllegalArgumentException("from and to wallets must differ");
        if (amount <= 0) throw new IllegalArgumentException("amount_paise must be positive");

        boolean inserted = transfers.insertIfAbsent(key, from, to, amount);
        if (!inserted) {
            Transfer existing = transfers.findByKey(key);
            if (existing == null) throw new IllegalStateException("idempotency race could not be resolved");
            if (!existing.from().equals(from) || !existing.to().equals(to) || existing.amountPaise() != amount) {
                throw new IdempotencyConflictException();
            }
            replayed.increment();
            log.info("transfer.idempotent_replay idempotency_key={} transfer_id={} status={}", key, existing.id(), existing.status());
            return new Result(existing, true);
        }

        Transfer createdTransfer = transfers.findByKey(key);
        created.increment();
        log.info("transfer.created transfer_id={} idempotency_key={} from={} to={} amount_paise={}",
                createdTransfer.id(), key, from, to, amount);

        UUID first = from.compareTo(to) < 0 ? from : to;
        UUID second = first.equals(from) ? to : from;
        Wallet firstWallet = wallets.lockById(first);
        Wallet secondWallet = wallets.lockById(second);
        if (firstWallet == null || secondWallet == null) throw new NotFoundException("wallet not found");

        if (wallets.debitIfSufficient(from, amount) != 1) {
            transfers.decline(createdTransfer.id());
            declined.increment();
            log.info("transfer.declined_insufficient_funds transfer_id={} from={} to={} amount_paise={}", createdTransfer.id(), from, to, amount);
            return new Result(transfers.findById(createdTransfer.id()), false);
        }
        log.info("transfer.debited transfer_id={} wallet_id={} amount_paise={}", createdTransfer.id(), from, amount);
        wallets.credit(to, amount);
        log.info("transfer.credited transfer_id={} wallet_id={} amount_paise={}", createdTransfer.id(), to, amount);
        transfers.complete(createdTransfer.id());
        return new Result(transfers.findById(createdTransfer.id()), false);
    }

    public Transfer get(UUID id) { return transfers.findById(id); }
    public record Result(Transfer transfer, boolean replay) {}
}
