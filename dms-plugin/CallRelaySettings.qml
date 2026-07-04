import QtQuick
import qs.Common
import qs.Widgets
import qs.Modules.Plugins

PluginSettings {
    id: root
    pluginId: "callRelay"

    StyledText {
        text: "CallRelay"
        font.pixelSize: Theme.fontSizeLarge
        font.weight: Font.Bold
        color: Theme.surfaceText
    }

    StyledText {
        text: "Polls the Cloudflare Worker relay for incoming call events from your Android phone and shows an overlay popup with a ringtone, plus tray history via dms notify."
        font.pixelSize: Theme.fontSizeSmall
        color: Theme.onSurfaceVariant
        wrapMode: Text.WordWrap
        width: parent.width
    }

    StringSetting {
        settingKey: "workerUrl"
        label: "Worker URL"
        description: "URL of the Cloudflare Worker relay, e.g. https://calls.darjs.dev"
        placeholder: "https://calls.darjs.dev"
        defaultValue: ""
    }

    StringSetting {
        settingKey: "getToken"
        label: "GET Token"
        description: "Bearer token used to authenticate GET /events requests to the relay."
        placeholder: "hex string"
        defaultValue: ""
    }
}
