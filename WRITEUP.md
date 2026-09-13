# Wallet Service — Design Write-up

**Data model.** Money is **integer paise** end-to-end (`BIGINT` in Postgres, `long` in Java — no
floats/decimals). Three tables, schema owned by Flyway:
- `wallets(id UUID PK, user_id UNIQUE, balance_paise BIGINT CHECK >= 0)`
- `transfers(id UUID PK, idempotency_key UNIQUE, from_wallet_id FK, to_wallet_id FK, amount_paise BIGINT CHECK > 0, status)`
- `deposits(id UUID PK, idempotency_key UNIQUE, wallet_id FK, amount_paise BIGINT CHECK > 0)`

Wallets start at zero; `POST /wallets/{id}/deposit` is the only operation that adds money (so
transfers are demonstrable end-to-end over the API). Conservation is scoped to *transfers*.

**Simplest-correct mechanism (conservation + no-overdraft).** Each transfer is one PostgreSQL
transaction (Read Committed): (1) lock both wallet rows `SELECT … FOR UPDATE` in **ascending-UUID
order**; (2) claim the idempotency key; (3) debit with a conditional `UPDATE … WHERE balance_paise
>= :amount` (0 rows ⇒ decline cleanly, no partial apply); (4) credit; (5) finalize status. All
commit together, so the summed balance is invariant and no balance goes negative; `CHECK
(balance_paise >= 0)` is a hard backstop. **Deadlock note (a real bug I fixed):** the sorted
`FOR UPDATE` runs *before* the transfer `INSERT`, because the FK columns make the INSERT take
`FOR KEY SHARE` locks on both wallet rows in unsorted `(from,to)` order — claiming the key first
made `A→B`/`B→A` deadlock (reproduced as a `deadlock detected` storm); sorted locking up front
fixes it.

**Heavier alternatives rejected.** *SERIALIZABLE* — adds serialization-failure retries for a
two-row write set with no gain over row locks. *Distributed/app locks (Redis)* — extra
coordination; the DB should stay authoritative for money. *Ledger/event-sourcing* —
projection/reconciliation complexity the invariants don't need. *Advisory locks* — unnecessary
once sorted row locks give exact serialization.

**Where idempotency lives.** A `UNIQUE(idempotency_key)` constraint, claimed with `INSERT … ON
CONFLICT DO NOTHING` **in the same transaction as the debit/credit** — key and money move commit
or roll back together, closing the TOCTOU gap a separate pre-check would open, and staying correct
across multiple instances (unlike in-memory dedup). A concurrent duplicate blocks on the unique
index, then reads the committed row and replays; same key + **different body ⇒ `409`**.
Race-free get-or-create uses the same idea (`UNIQUE(user_id)` + `INSERT … ON CONFLICT DO NOTHING`
+ re-select ⇒ N concurrent creates yield one wallet). Deposits reuse the mechanism.

**Consistency vs availability.** Consistency-first (CP). Money moves only when Postgres can
atomically commit debit + credit + idempotency record; during a DB outage the service fails closed
rather than accept an uncertain local write to reconcile later. Conscious trade: less availability
during a DB failure in exchange for one authoritative committed state.

**AI directed vs decided.** Every correctness decision is mine and defensible: integer paise; DB
constraints as the source of truth for the race-free and exactly-once invariants; the idempotency
claim in the same transaction as the ledger move; sorted `FOR UPDATE` before the insert; conditional
debit for no-overdraft; consistency-first. I designed the schema and core logic and directed/reviewed
all code; I used an AI assistant to speed up implementation — boilerplate (`pom.xml`, entry point,
tests), the `burst.sh` verification script, Docker/Compose/Render, and turning my decisions
into code (including the deposit feature).

**Free-tier note.** Runs at **₹0** on a free web instance + free managed PostgreSQL. Trade-offs: the
free database expires after ~30 days and the web instance sleeps after ~15 min idle (cold first request after idle takes around 1:30 min and thereafter milliseconds) — suitable for evaluation, not durable production.
