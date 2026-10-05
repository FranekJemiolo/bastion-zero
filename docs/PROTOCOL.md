# Mesh protocol

Source of truth: [`survival_packet.proto`](https://github.com/FranekJemiolo/bastion-zero/blob/main/shared/src/commonMain/proto/survival_packet.proto).

## Identity

A node's identity is its **Ed25519 public key** (32 bytes), generated at install. `senderId` carries it directly, so any receiver can verify without a directory.

## Signing

`signature = Ed25519(secretKey, encode(packet with signature = ∅, ttl = 0))`

`ttl` is the only field relays change, so it is excluded. Packets must stay under ~200 bytes for BLE: ~100 bytes are fixed overhead (key + signature), leaving ~100 for payload.

## Ordering: Lamport clocks

Wall-clock time is advisory (`wallClockHint`) and never trusted. Each node keeps a Lamport counter: `tick()` when authoring, `max(local, remote)+1` after accepting a packet.

## Inbound validation (cheapest check first)

1. Structure: 32-byte `senderId`, 64-byte `signature`, `lamport > 0` → else **MALFORMED**
2. Exact signature in the last-500 cache → **DUPLICATE**
3. Per-sender 64-wide sliding window on `lamport` → **STALE** (older than window) / **DUPLICATE** (already seen)
4. Ed25519 verify → **BAD_SIGNATURE**
5. Commit to cache + window, merge clock → **ACCEPT**

State is committed only *after* verification so forged packets cannot push a victim's window forward. The window (not just the cache) is what defeats replays after the cache rotates, while still accepting legitimate out-of-order floods.

## Known limits

- Sender window state is capped at 1024 senders (oldest evicted); evicted senders' very old packets could be replayed once. Mitigation planned: persistent store in Phase 3.
- A valid captured packet can still be *relayed* to far-away places within its window; TTL bounds amplification but not geography.
- Payloads are signed, not encrypted. The Dead Man's Switch payload encryption (Phase 4) will use a separate scheme.
