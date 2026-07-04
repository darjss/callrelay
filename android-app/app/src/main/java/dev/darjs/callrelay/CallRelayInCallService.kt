package dev.darjs.callrelay

import android.telecom.Call
import android.telecom.InCallService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Observes the live call lifecycle and emits the Call Ended Event when a call
 * reaches [Call.STATE_DISCONNECTED].
 *
 * `CallScreeningService` only sees the call at ring time and cannot register a
 * [Call.Callback] (it has no [Call] object). This non-UI [InCallService] is
 * bound by Telecom for active calls, registers a [Call.Callback], and on
 * disconnect maps the cause to an Outcome and POSTs the Ended Event with the
 * same callId recovered from [PendingCalls].
 */
class CallRelayInCallService : InCallService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sender = EventSender()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
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
        val details = call.details
        val number = details.handle?.schemeSpecificPart ?: ""
        val callId = PendingCalls.take(number) ?: return

        val event = CallEvent(
            callId = callId,
            type = "ended",
            direction = "incoming",
            callerName = details.callerDisplayName ?: "",
            callerNumber = number,
            outcome = mapDisconnectCause(details.disconnectCause),
            timestamp = System.currentTimeMillis()
        )
        scope.launch { sender.sendEvent(event) }
    }
}
