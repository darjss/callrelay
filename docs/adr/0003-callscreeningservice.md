# CallScreeningService for Android call detection

## Context

The Android App must detect incoming and outgoing calls, capture the caller
display name (or number), and observe the call lifecycle (ringing →
connected → disconnected with cause) to emit Call Started and Call Ended
events. The target device is a Bigme Hibreak Pro running Android 14 (API 34).

## Decision

Use **`CallScreeningService`** (API 29+) with a `Call.Callback` registered on
each `Call` object to observe state transitions through disconnect.

## Why

- **Caller display name on ringing**: `Call.Details.callerDisplayName` is
  available immediately on `onScreenCall(STATE_NEW)`. Falls back to
  `Call.Details.handle` (the `tel:` number) when the name is absent.
- **Live Call Started event**: `onScreenCall` fires on `STATE_NEW` before the
  user answers — the "phone is ringing" moment, mapped to a Call Started
  Event with a fresh `callId`.
- **Outgoing calls**: `CallScreeningService` fires for outgoing calls too,
  giving direction = `outgoing` for the Call Started Event.
- **Outcome on disconnect**: `Call.Callback.onStateChanged` →
  `STATE_DISCONNECTED` with `Call.Details.disconnectCause` maps to the
  `Outcome` enum (`answered`, `missed`, `declined`, `rejected`, `cancelled`,
  `error`). This is the Call Ended Event, carrying the same `callId`.
- **Android 14 fully supports it**: API 34, no deprecation, Google's
  recommended API.

## Tradeoff

The app must be set as the **default call screening app** in system settings.
This is a one-time setup step. The service always calls
`response.allowCall()` (pass-through) — it observes, never blocks.

Becoming the default screening app displaces any existing spam filter (e.g.
Google's caller ID / spam protection). On the Bigme Hibreak Pro (a niche
e-ink phone), Google's spam filter is unlikely to be active, so this is
acceptable. If the user switches the default screening app away later, call
detection stops until it's re-enabled.

## Considered options

- **`PhoneStateListener` / `TelephonyManager`**: rejected — deprecated since
  API 31, no caller display name (number only), unreliable for outgoing
  calls, number blanked on `RINGING` without `READ_CALL_LOG`.
- **`CallLog` content provider query**: rejected — post-hoc only, can't fire
  a live Call Started event while the phone is ringing.
