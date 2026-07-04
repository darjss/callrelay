package dev.darjs.callrelay

import android.telecom.Call
import android.telecom.DisconnectCause
import android.telecom.InCallService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Owns the full incoming-call lifecycle: mints a [callId] on
 * [Call.STATE_RINGING], emits the Call Started Event, registers a
 * [Call.Callback], and on [Call.STATE_DISCONNECTED] emits the Call Ended Event
 * with the same [callId].
 *
 * `CallScreeningService.onScreenCall` only receives a [Call.Details] — no live
 * [Call] to register a callback on — so this non-UI [InCallService] is the
 * single owner. [CallRelayScreeningService] is kept only to hold the
 * ROLE_CALL_SCREENING role and pass calls through.
 */
class CallRelayInCallService : InCallService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sender = EventSender()

    // Live Call → callId. Keyed by the Call object reference; removed on
    // disconnect. No TTL needed — the entry lives exactly as long as the call.
    private val callIds = mutableMapOf<Call, String>()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        // Skip outgoing calls — v1 forwards incoming calls only.
        if (call.details.callDirection == Call.Details.DIRECTION_OUTGOING) return
        // Only mint/start events for ringing calls.
        if (call.state != Call.STATE_RINGING) return

        val callId = UUID.randomUUID().toString()
        val details = call.details
        callIds[call] = callId

        val started = CallEvent(
            callId = callId,
            type = EventType.STARTED,
            direction = "incoming",
            callerName = details.callerDisplayName,
            callerNumber = details.handle?.schemeSpecificPart,
            timestamp = System.currentTimeMillis(),
        )
        scope.launch { sender.sendEvent(started) }

        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                if (state == Call.STATE_DISCONNECTED) {
                    c.unregisterCallback(this)
                    sendEndedEvent(c)
                }
            }
        }
        call.registerCallback(callback)
    }

    private fun sendEndedEvent(call: Call) {
        val callId = callIds.remove(call) ?: return
        val details = call.details
        val event = CallEvent(
            callId = callId,
            type = EventType.ENDED,
            // TODO: drop callerName/callerNumber once the DMS plugin looks up
            //   caller info from its activeCalls map instead of the Ended event.
            callerName = details.callerDisplayName,
            callerNumber = details.handle?.schemeSpecificPart,
            outcome = mapDisconnectCause(details.disconnectCause),
            timestamp = System.currentTimeMillis(),
        )
        scope.launch { sender.sendEvent(event) }
    }
}

/**
 * Maps an Android [DisconnectCause] code to the Relay's [Outcome] enum.
 *
 * - MISSED → [Outcome.MISSED]
 * - REJECTED → [Outcome.DECLINED]
 * - ANSWERED_ELSEWHERE / LOCAL / REMOTE → [Outcome.ANSWERED]
 * - ERROR or anything else → [Outcome.ERROR]
 */
fun mapDisconnectCause(cause: Int): Outcome = when (cause) {
    DisconnectCause.MISSED -> Outcome.MISSED
    DisconnectCause.REJECTED -> Outcome.DECLINED
    DisconnectCause.ANSWERED_ELSEWHERE -> Outcome.ANSWERED
    DisconnectCause.LOCAL -> Outcome.ANSWERED
    DisconnectCause.REMOTE -> Outcome.ANSWERED
    DisconnectCause.ERROR -> Outcome.ERROR
    else -> Outcome.ERROR
}
