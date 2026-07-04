# Phone Notif

A system that surfaces Android phone-call events on a Linux desktop running
DankMaterialShell, via a Cloudflare Worker relay between the phone and a DMS
plugin.

## Language

**Call Event**:
A single state transition in a phone call's lifecycle, sent from the Android
app to the Worker. Either a Call Started event or a Call Ended event.
_Avoid_: notification, alert, message

**Call Started Event**:
The first event of a call, fired when the phone begins ringing. Carries the
caller display name, phone number, and a fresh `callId`. Only emitted for
incoming calls.
_Avoid_: ringing event, incoming event

**Call Ended Event**:
The final event of a call, fired on disconnect. Carries the same `callId`,
the disconnect cause, and end timestamp. Resolves the corresponding Call
Started Event on the desktop.
_Avoid_: disconnect event, summary event

**CallId**:
A UUID minted by the Android app on Call Started, reused on Call Ended. Used
by the Worker and DMS plugin to correlate the two events of a single call.
_Avoid_: call reference, session id

**Direction**:
Whether the call was `incoming` or `outgoing`. v1 forwards incoming calls
only; the field is kept in the schema for forward compatibility.
_Avoid_: type, side

**Outcome**:
The terminal state of an incoming call, derived from Android's
`DisconnectCause`: `answered`, `missed`, `declined`, or `error`. Only
present on Call Ended Events.
_Avoid_: result, status, disconnect cause

**Relay**:
The Cloudflare Worker that receives Call Events from the Android app and
serves them to the DMS plugin. Uses a Durable Object for strongly consistent
event storage.
_Avoid_: server, backend, broker

**Android App**:
The Kotlin app running on the phone. Owns call lifecycle detection via
`CallScreeningService` and emits Call Events to the Relay.
_Avoid_: client, sender, phone app

**DMS Plugin**:
The DankMaterialShell plugin running on the Linux desktop. Polls Call Events
from the Relay and surfaces them via `dms notify` (for tray history) plus a
transient Overlay Popup and Ringtone.
_Avoid_: shell plugin, widget, extension

**Overlay Popup**:
A transient `PanelWindow` on the `WlrLayershell.Overlay` layer, shown by the
DMS Plugin on Call Started and dismissed on Call Ended. Positioned
top-center. Auto-dismisses after 30s if no Call Ended Event arrives. Sits
above fullscreen windows, unlike DMS's standard notification popups.
_Avoid_: notification popup, banner, toast

**Ringtone**:
A sound file bundled with the DMS Plugin, played via QML `MediaPlayer` on
Call Started. Loops for up to 20s, then stops. Independent of DMS's global
notification sound settings.
_Avoid_: notification sound, alert tone
