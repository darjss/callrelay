import QtQuick
import QtMultimedia
import Quickshell
import Quickshell.Io
import Quickshell.Wayland
import qs.Common
import qs.Widgets

PluginComponent {
    id: root

    // Config from plugin settings (injected by DMS PluginService).
    property string workerUrl: pluginData.workerUrl || ""
    property string getToken: pluginData.getToken || ""

    // Polling cursor (highest seq successfully processed). Persisted to cursor.json.
    property int cursor: 0

    // Dedup set: keyed on "callId:type". Persisted to seen.json, capped at 100 (FIFO).
    property var seenKeys: []
    property var seenMap: ({})

    // Active calls keyed by callId: { popup, mediaPlayer, ringtoneTimer, dismissTimer }.
    property var activeCalls: ({})

    // State dir for cursor/seen files.
    readonly property string stateDir: StandardPaths.writableLocation(StandardPaths.GenericConfigLocation) + "/DankMaterialShell/plugins/CallRelay"
    readonly property string cursorPath: stateDir + "/cursor.json"
    readonly property string seenPath: stateDir + "/seen.json"


    Component.onCompleted: {
        ensureStateDir.running = true;
        cursorFile.path = cursorPath;
        seenFile.path = seenPath;
        console.info("CallRelay daemon loaded");
    }

    Component.onDestruction: {
        // Tear down any live popups/players so we don't leak windows on reload.
        var ids = Object.keys(activeCalls);
        for (var i = 0; i < ids.length; i++) {
            teardownCall(ids[i]);
        }
        pollTimer.stop();
    }

    // --- State dir bootstrap -------------------------------------------------

    Process {
        id: ensureStateDir
        command: ["mkdir", "-p", stateDir]
    }

    // --- Persisted state files ----------------------------------------------

    FileView {
        id: cursorFile
        atomicWrites: true
        blockWrites: true

        onLoaded: {
            try {
                var data = JSON.parse(text());
                if (data && typeof data.cursor === "number") {
                    root.cursor = data.cursor;
                }
            } catch (e) {
                console.warn("CallRelay: failed to parse cursor.json:", e);
            }
        }

        onLoadFailed: {
            // First run — no cursor yet. Leave at 0.
        }
    }

    FileView {
        id: seenFile
        atomicWrites: true
        blockWrites: true

        onLoaded: {
            try {
                var data = JSON.parse(text());
                if (data && Array.isArray(data.keys)) {
                    root.seenKeys = data.keys.slice();
                    root.seenMap = ({});
                    for (var i = 0; i < root.seenKeys.length; i++) {
                        root.seenMap[root.seenKeys[i]] = true;
                    }
                }
            } catch (e) {
                console.warn("CallRelay: failed to parse seen.json:", e);
            }
        }

        onLoadFailed: {
            // First run — empty dedup set.
        }
    }

    function saveCursor() {
        Qt.callLater(flushCursor);
    }

    function flushCursor() {
        flushPersist(cursorFile, { cursor: root.cursor });
    }

    function saveSeen() {
        Qt.callLater(flushSeen);
    }

    function flushSeen() {
        flushPersist(seenFile, { keys: root.seenKeys });
    }

    // Debounced write: Qt.callLater coalesces multiple calls, so the flush
    // functions always read the current property value at execution time.
    function flushPersist(fileView, data) {
        fileView.setText(JSON.stringify(data));
    }

    // --- Polling loop --------------------------------------------------------

    Timer {
        id: pollTimer
        interval: 2000
        repeat: true
        running: true
        triggeredOnStart: false

        onTriggered: root.poll()
    }

    function poll() {
        if (!workerUrl || !getToken) return;

        var xhr = new XMLHttpRequest();
        var url = workerUrl + "/events?since=" + cursor;
        xhr.open("GET", url);
        xhr.setRequestHeader("Authorization", "Bearer " + getToken);
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== XMLHttpRequest.DONE) return;
            if (xhr.status < 200 || xhr.status >= 300) {
                console.warn("CallRelay: poll failed status", xhr.status);
                return;
            }
            try {
                var payload = JSON.parse(xhr.responseText);
            } catch (e) {
                console.warn("CallRelay: poll JSON parse failed:", e);
                return;
            }
            if (!payload) return;

            var events = payload.events || [];
            var next = payload.nextCursor;
            for (var i = 0; i < events.length; i++) {
                handleEvent(events[i]);
            }
            if (typeof next === "number" && next > cursor) {
                cursor = next;
                saveCursor();
            }
        };
        xhr.send();
    }

    // --- Event dispatch ------------------------------------------------------

    function handleEvent(event) {
        if (!event || !event.callId || !event.type) return;

        var key = event.callId + ":" + event.type;
        if (seenMap[key]) return;
        markSeen(key);

        if (event.type === "started") {
            onCallStarted(event);
        } else if (event.type === "ended") {
            onCallEnded(event);
        }
    }

    function markSeen(key) {
        seenMap[key] = true;
        seenKeys.push(key);
        // FIFO cap at 100.
        while (seenKeys.length > 100) {
            var dropped = seenKeys.shift();
            delete seenMap[dropped];
        }
        saveSeen();
    }

    function displayName(event) {
        var name = event.callerName || "";
        return name !== "" ? name : (event.callerNumber || "Unknown");
    }

    function capitalize(s) {
        if (!s) return "";
        return s.charAt(0).toUpperCase() + s.slice(1);
    }

    function stopPlayer(player) {
        try { player.stop(); } catch (e) {}
    }

    // dms notify: the trailing "" arg is a body placeholder — without it,
    // dms treats the previous arg as the body instead of the summary.
    function notifyTray(label, name) {
        Quickshell.execDetached(["dms", "notify", "--app", "CallRelay", "--icon", "call", label + ": " + name, ""]);
    }

    // --- Call Started --------------------------------------------------------

    function onCallStarted(event) {
        var callId = event.callId;
        // If a popup is already live for this callId (duplicate start), tear it down first.
        if (activeCalls[callId]) teardownCall(callId);

        var name = displayName(event);

        // 1. Overlay popup.
        var popup = popupComponent.createObject(root, { callId: callId, callerName: name });

        // 2. Ringtone player.
        var player = ringtoneComponent.createObject(root);

        // 3. 20s ringtone cap.
        var ringTimer = ringtoneTimerComponent.createObject(root, { callId: callId });

        // 4. 30s auto-dismiss.
        var dismissTimer = dismissTimerComponent.createObject(root, { callId: callId });

        activeCalls[callId] = {
            popup: popup,
            mediaPlayer: player,
            ringtoneTimer: ringTimer,
            dismissTimer: dismissTimer
        };

        player.play();
        ringTimer.start();
        dismissTimer.start();

        // 5. Tray history (do NOT block on popup visibility — record the event).
        notifyTray("Incoming", name);
    }

    // --- Call Ended ----------------------------------------------------------

    function onCallEnded(event) {
        var callId = event.callId;
        var name = displayName(event);
        var label = capitalize(event.outcome || "ended");

        // 1. Tear down popup/ringtone/timers if still live.
        if (activeCalls[callId]) {
            teardownCall(callId);
        }
        // 2. Always fire tray history (even if popup already auto-dismissed).
        notifyTray(label, name);
    }

    // Destroy all resources for a callId and drop the entry.
    function teardownCall(callId) {
        var entry = activeCalls[callId];
        if (!entry) return;
        if (entry.ringtoneTimer) entry.ringtoneTimer.stop();
        if (entry.dismissTimer) entry.dismissTimer.stop();
        if (entry.mediaPlayer) {
            stopPlayer(entry.mediaPlayer);
            entry.mediaPlayer.destroy();
        }
        if (entry.popup) entry.popup.destroy();
        delete activeCalls[callId];
    }

    // --- Components ----------------------------------------------------------

    Component {
        id: popupComponent

        PanelWindow {
            id: popup
            property string callId: ""
            property string callerName: ""

            WlrLayershell.layer: WlrLayershell.Overlay
            WlrLayershell.namespace: "dms:callrelay-popup"
            WlrLayershell.exclusiveZone: -1
            WlrLayershell.keyboardFocus: WlrKeyboardFocus.None
            color: "transparent"

            anchors {
                top: true
                horizontalCenter: true
            }

            width: 300
            height: 80

            Rectangle {
                anchors.fill: parent
                color: Theme.surfaceContainer
                radius: Theme.cornerRadius
                border.color: Theme.primary
                border.width: 1

                Row {
                    anchors.centerIn: parent
                    spacing: Theme.spacingS

                    DankIcon {
                        name: "call"
                        size: Theme.iconSize
                        color: Theme.primary
                        anchors.verticalCenter: parent.verticalCenter
                    }

                    StyledText {
                        text: "Incoming: " + popup.callerName
                        color: Theme.surfaceText
                        font.pixelSize: Theme.fontSizeMedium
                        font.weight: Font.DemiBold
                        anchors.verticalCenter: parent.verticalCenter
                    }
                }
            }

        }
    }

    Component {
        id: ringtoneComponent

        MediaPlayer {
            source: Qt.resolvedUrl("sounds/ringtone.ogg")
            // Loop until the 20s cap or Call Ended stops us.
            loops: MediaPlayer.Infinite
            audioOutput: AudioOutput {
                volume: 1.0
            }
        }
    }

    Component {
        id: ringtoneTimerComponent

        Timer {
            property string callId: ""
            interval: 20000
            repeat: false
            onTriggered: {
                var entry = root.activeCalls[callId];
                if (entry && entry.mediaPlayer) {
                    stopPlayer(entry.mediaPlayer);
                }
            }
        }
    }

    Component {
        id: dismissTimerComponent

        Timer {
            property string callId: ""
            interval: 30000
            repeat: false
            onTriggered: {
                // Tear down WITHOUT firing dms notify — the ended event will
                // notify when it comes.
                root.teardownCall(callId);
            }
        }
    }
}
