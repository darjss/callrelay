package dev.darjs.callrelay

import org.json.JSONObject

/**
 * A single Call Event sent from the Android App to the Relay.
 *
 * Either a Call Started Event ([type] = "started") or a Call Ended Event
 * ([type] = "ended"). The [callId] is minted on Call Started and reused on
 * Call Ended so the Relay and DMS Plugin can correlate the two events of a
 * single call. [outcome] is only present on Call Ended Events.
 */
data class CallEvent(
    val callId: String,
    val type: String,
    val direction: String,
    val callerName: String,
    val callerNumber: String,
    val outcome: String? = null,
    val timestamp: Long
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("callId", callId)
        json.put("type", type)
        json.put("direction", direction)
        json.put("callerName", callerName)
        json.put("callerNumber", callerNumber)
        if (outcome != null) json.put("outcome", outcome)
        json.put("timestamp", timestamp)
        return json
    }
}
