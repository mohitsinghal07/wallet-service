#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
N="${N:-25}"
K="${K:-50}"
AUTH_A="Bearer ${AUTH_A:-burst-A}"
AUTH_B="Bearer ${AUTH_B:-burst-B}"

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

jqc(){ jq -c .; }

printf '%s\n' '== 1. concurrent get-or-create =='
seq "$N" | xargs -n1 -P"$N" -I{} curl -sS -H "Authorization: $AUTH_A" -X POST "$BASE_URL/wallets" > "$tmp/wallets.out"
awk 'NF' "$tmp/wallets.out" | jq -s 'map(.id) | unique | {unique_wallets:length, ids:.}'
A_ID=$(jq -rs '.[0].id' "$tmp/wallets.out")

curl -sS -H "Authorization: $AUTH_B" -X POST "$BASE_URL/wallets" > "$tmp/b.json"
B_ID=$(jq -r .id "$tmp/b.json")

echo '== 1b. seed balances via deposit (idempotent funding boundary) =='
for id in "$A_ID" "$B_ID"; do
  curl -sS -X POST "$BASE_URL/wallets/$id/deposit" -H 'Content-Type: application/json' -d "{\"amount_paise\":100000,\"idempotency_key\":\"seed-$id\"}" | jqc
done

echo '== 2. idempotent retry storm =='
KEY="burst-$(date +%s%N)"
seq "$K" | xargs -n1 -P"$K" -I{} curl -sS -X POST "$BASE_URL/transfers" -H 'Content-Type: application/json' -d "{\"from\":\"$A_ID\",\"to\":\"$B_ID\",\"amount_paise\":1,\"idempotency_key\":\"$KEY\"}" > "$tmp/retries.out"
jq -s 'map(.id) | unique | {unique_transfer_ids:length, transfer_ids:.}' "$tmp/retries.out"

printf '%s\n' '== 3. contention conservation smoke =='
for id in "$A_ID" "$B_ID"; do curl -sS "$BASE_URL/wallets/$id" > "$tmp/$id.before"; done
BEFORE_A=$(jq -r .balance_paise "$tmp/$A_ID.before")
BEFORE_B=$(jq -r .balance_paise "$tmp/$B_ID.before")
seq 100 | xargs -n1 -P50 -I{} bash -c 'curl -sS -X POST "$0/transfers" -H "Content-Type: application/json" -d "{\"from\":\"$1\",\"to\":\"$2\",\"amount_paise\":1,\"idempotency_key\":\"contention-{}-ab\"}" >/dev/null' "$BASE_URL" "$A_ID" "$B_ID" || true
seq 100 | xargs -n1 -P50 -I{} bash -c 'curl -sS -X POST "$0/transfers" -H "Content-Type: application/json" -d "{\"from\":\"$1\",\"to\":\"$2\",\"amount_paise\":1,\"idempotency_key\":\"contention-{}-ba\"}" >/dev/null' "$BASE_URL" "$B_ID" "$A_ID" || true
AFTER_A=$(curl -sS "$BASE_URL/wallets/$A_ID" | jq -r .balance_paise)
AFTER_B=$(curl -sS "$BASE_URL/wallets/$B_ID" | jq -r .balance_paise)
printf 'Before total=%s After total=%s\n' "$((BEFORE_A+BEFORE_B))" "$((AFTER_A+AFTER_B))"
printf 'Balances: A=%s B=%s\n' "$AFTER_A" "$AFTER_B"
