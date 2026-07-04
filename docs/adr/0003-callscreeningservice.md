# InCallService owns the full call lifecycle

## Context

The Android App must detect incoming calls, capture the caller display name
(or number), and observe the call lifecycle (ringing → connected →
disconnected with cause) to emit Call Started and Call Ended events. The
target device is a Bigme Hibreak Pro running Android 14 (API 34).

## Decision

Use a non-UI **`InCallService`** (`CallRelayInCallService`) as the single
owner of the call lifecycle. In `onCallAdded(call)`, when the call is
ringing, it mints a `callId`, extracts caller info from `call.details`,
emits the Call Started Event, and registers a `Call.Callback`. On
`STATE_DISCONNECTED` the callback emits the Call Ended Event with the same
`callId` and unregisters itself.

A **`CallScreeningService`** (`CallRelayScreeningService`) is kept as a
pass-through role holder: `onScreenCall` always calls `allowCall()` and
emits no events. It exists only so the app holds the default
call-screening-app role, which causes Telecom to bind the `InCallService`
for incoming calls.

## Why

- **`CallScreeningService.onScreenCall` only provides `Call.Details`**, not a
  live `Call` object. A `Call.Callback` cannot be registered there, so the
  disconnect transition cannot be observed from the screening service.
- **`InCallService.onCallAdded(call)` provides the live `Call`** for ringing
  calls too, and `call.details` carries `callerDisplayName` and `handle` at
  that point. This lets one service own both the Started and Ended events
  and correlate them by a `callId` minted once — no cross-service state.
- **No `PendingCalls` map or TTL**: the `callId` is stored in a
  `Map<Call, String>` keyed by the `Call` object reference. The entry is
  removed on disconnect and lives exactly as long as the call.
- **Outgoing calls are filtered** by checking
  `call.details.callDirection == Call.Details.DIRECTION_OUTGOING` in
  `onCallAdded`; v1 forwards incoming calls only.
- **Android 14 fully supports it**: API 34, no deprecation.

## Tradeoff

The app must be set as the **default call screening app** in system settings
(so that `CallScreeningService` is bound and the `InCallService` receives
incoming calls). This is a one-time setup step. The screening service
always allows the call through — it observes, never blocks.

Becoming the default screening app displaces any existing spam filter (e.g.
Google's caller ID / spam protection). On the Bigme Hibreak Pro (a niche
e-ink phone), Google's spam filter is unlikely to be active, so this is
acceptable. If the user switches the default screening app away later, call
detection stops until it's re-enabled.

## Considered options

- **Two-service split (ScreeningService mints callId, InCallService
  recovers it via `PendingCalls` keyed by number)**: rejected — requires a
  shared map with TTL and number normalization to correlate the two
  services. The `InCallService` alone receives enough information
  (`call.details` at `STATE_RINGING`) to mint the `callId` and own both
  events, eliminating the correlation entirely.
- **`PhoneStateListener` / `TelephonyManager`**: rejected — deprecated since
  API 31, no caller display name (number only), unreliable for outgoing
  calls, number blanked on `RINGING` without `READ_CALL_LOG`.
- **`CallLog` content provider query**: rejected — post-hoc only, can't fire
  a live Call Started event while the phone is ringing.
