package dev.darjs.callrelay

import android.telecom.Call
import android.telecom.CallResponse
import android.telecom.CallScreeningService
import android.telecom.DisconnectCause
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Screens incoming calls: mints a [callId], emits a Call Started Event to the
 * Relay, and always passes the call through (never blocks).
 *
 * `CallScreeningService.onScreenCall` only receives a [Call.Details] — it does
 * not expose the live [Call] object, so the Call Ended Event (observed via
 * [Call.Callback] on `STATE_DISCONNECTED`) is emitted by
 * [CallRelayInCallService]. The two services correlate via [PendingCalls],
 * keyed by the caller number.
 */
class CallRelayScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sender = EventSender()

    override fun onScreenCall(callDetails: Call.Details) {
        // Skip outgoing calls (the user is dialing). CallScreeningService fires
        // for both directions; v1 forwards incoming calls only.
        if (callDetails.callDirection == Call.Details.DIRECTION_OUTGOING) {
            allowCall(callDetails)
            return
        }

        val callId = UUID.randomUUID().toString()
        val callerName = callDetails.callerDisplayName ?: ""
        val callerNumber = callDetails.handle?.schemeSpecificPart ?: ""

        PendingCalls.put(callId, callerNumber)

        val event = CallEvent(
            callId = callId,
            type = "started",
            direction = "incoming",
            callerName = callerName,
            callerNumber = callerNumber,
            timestamp = System.currentTimeMillis()
        )
        scope.launch { sender.sendEvent(event) }

        allowCall(callDetails)
    }

    private fun allowCall(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setAllowCall(true)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, response)
    }
}

/**
 * Maps an Android [DisconnectCause] code to the Relay's Outcome enum.
 *
 * - MISSED → "missed"
 * - REJECTED → "declined"
 * - ANSWERED_ELSEWHERE / LOCAL / REMOTE → "answered"
 * - ERROR or anything else → "error"
 */
fun mapDisconnectCause(cause: Int): String = when (cause) {
    DisconnectCause.MISSED -> "missed"
    DisconnectCause.REJECTED -> "declined"
    DisconnectCause.ANSWERED_ELSEWHERE -> "answered"
    DisconnectCause.LOCAL -> "answered"
    DisconnectCause.REMOTE -> "answered"
    DisconnectCause.ERROR -> "error"
    else -> "error"
}

/**
 * Correlates the callId minted in [CallRelayScreeningService] with the call
 * observed by [CallRelayInCallService], keyed by the normalized caller number.
 * Entries expire after [TTL_MS] so abandoned calls don't leak.
 */
object PendingCalls {
    private const val TTL_MS = 60_000L
    private val map = ConcurrentHashMap<String, Entry>()

    private data class Entry(val callId: String, val ts: Long)

    fun put(callId: String, number: String) {
        map[normalize(number)] = Entry(callId, System.currentTimeMillis())
        evict()
    }

    fun take(number: String): String? {
        val e = map.remove(normalize(number)) ?: return null
        return if (System.currentTimeMillis() - e.ts > TTL_MS) null else e.callId
    }

    private fun normalize(s: String) = s.filter { it.isDigit() }

    private fun evict() {
        val now = System.currentTimeMillis()
        map.entries.removeAll { now - it.value.ts > TTL_MS }
    }
}
