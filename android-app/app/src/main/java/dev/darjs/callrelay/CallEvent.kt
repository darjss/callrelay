package dev.darjs.callrelay

import org.json.JSONObject

/** Wire type for [CallEvent.type]. */
enum class EventType(val wire: String) {
    STARTED("started"),
    ENDED("ended"),
}

/** Terminal state of an incoming call, derived from Android's `DisconnectCause`. */
enum class Outcome(val wire: String) {
    ANSWERED("answered"),
    MISSED("missed"),
    DECLINED("declined"),
    ERROR("error"),
}

/**
 * A single Call Event sent from the Android App to the Relay.
 *
 * Either a Call Started Event ([type] = [EventType.STARTED]) or a Call Ended
 * Event ([type] = [EventType.ENDED]). The [callId] is minted on Call Started
 * and reused on Call Ended so the Relay and DMS Plugin can correlate the two
 * events of a single call. [outcome] is only present on Call Ended Events.
 */
data class CallEvent(
    val callId: String,
    val type: EventType,
    val direction: String? = null,
    val callerName: String? = null,
    val callerNumber: String? = null,
    val outcome: Outcome? = null,
    val timestamp: Long,
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("callId", callId)
        json.put("type", type.wire)
        if (direction != null) json.put("direction", direction)
        if (callerName != null) json.put("callerName", callerName)
        if (callerNumber != null) json.put("callerNumber", callerNumber)
        if (outcome != null) json.put("outcome", outcome.wire)
        json.put("timestamp", timestamp)
        return json
    }
}
