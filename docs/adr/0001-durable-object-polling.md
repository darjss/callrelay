# Durable Object + polling for the event relay

## Context

The Relay (Cloudflare Worker) sits between the Android App and the DMS Plugin.
It must deliver Call Events from phone to desktop with at most 3s end-to-end
latency. Two sub-decisions: storage backend, and transport from Worker to
plugin.

## Decision

Use a **Durable Object** with SQLite storage as the event queue, and
**HTTP polling at 2s intervals** from the DMS Plugin to the Worker.

## Why

**Storage — Durable Object, not KV.** KV is eventually consistent: writes can
take 1–60s to propagate to reads, which would blow the 3s latency budget on a
coin flip. A Durable Object has strongly consistent storage — writes are
immediately visible to the next read. Cost is effectively zero on the $5
Workers plan (DO hibernates between polls; ~518k requests/mo well under the
included quota).

**Transport — polling, not WebSocket.** 2s polling yields ~2.1s end-to-end
latency, meeting the 3s requirement with margin. WebSocket would give
sub-second latency but adds reconnection logic in QML, connection-state
management, and a long-lived DO connection — complexity not justified by the
use case (call notifications tolerate seconds of latency). The event schema,
storage, and Android App are transport-agnostic: swapping polling for
WebSocket later requires changes only in the DMS Plugin.

## Considered options

- **KV + polling**: rejected — eventual consistency violates the latency budget.
- **Durable Object + WebSocket**: rejected for v1 — latency gain doesn't
  justify the QML/DO complexity. Open as a future upgrade path.
- **Durable Object + SSE**: rejected — no native EventSource in QML, same DO
  long-lived-stream cost as WebSocket with more parsing pain.
