package dev.darjs.callrelay

import android.telecom.CallScreeningService
import android.telecom.Call

/**
 * Pass-through role holder for ROLE_CALL_SCREENING.
 *
 * This service exists only to hold the default call-screening-app role so that
 * [CallRelayInCallService] receives `onCallAdded` for incoming calls. It always
 * allows the call through and emits no events — the full call lifecycle (Call
 * Started + Call Ended via [Call.Callback]) is owned by
 * [CallRelayInCallService], which receives the live [Call] object.
 */
class CallRelayScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val response = CallScreeningService.CallResponse.Builder()
            .setDisallowCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, response)
    }
}
