# Daemon plugin with Overlay-layer popup, not dms notify alone

## Context

DMS's standard notification popups (via `dms notify` / DBus) do not appear
over fullscreen windows — they render below the fullscreen surface's z-order.
The user verified this on their compositor. DMS's `NotificationService` does
not suppress popups on fullscreen (only on DND and overlay-open), but the
compositor renders the popup behind the fullscreen window.

Additionally, the user has DMS's global notification sounds disabled, so
`dms notify` produces no audible alert.

## Decision

The DMS Plugin is a **daemon-type plugin** that:
1. Calls `dms notify` for tray history (DMS's `NotificationService` records
   it in `notification_history.json` regardless of popup visibility).
2. Shows a **transient `PanelWindow` on `WlrLayershell.Overlay`** for the
   call popup — the same layer DMS uses for modals, context menus, and the
   launcher. This sits above fullscreen windows.
3. Plays a **Ringtone via its own QML `MediaPlayer`**, independent of DMS's
   global `soundsEnabled` / `soundNewNotification` settings.

## Why

- **`dms notify` alone is insufficient**: popup invisible over fullscreen,
  no sound. Tray history works, but the live "phone is ringing" alert — the
  core feature — fails in the most common use case (watching fullscreen
  video / gaming when a call comes in).
- **Overlay layer is the proven mechanism**: DMS's own modals and launcher
  use `WlrLayershell.Overlay` to appear over fullscreen. A daemon plugin can
  create `PanelWindow` components (DMS widgets already do this for context
  menus, e.g. `ClipboardButton.qml`, `RunningApps.qml`).
- **Own sound via `MediaPlayer`**: DMS's `AudioService.playNormalNotification
  Sound()` is gated on global settings the user has disabled. A plugin-owned
  `MediaPlayer` plays regardless, giving the call alert an audible component
  the user controls per-plugin, not globally.
- **`desktop` plugin type rejected**: renders on `WlrLayer.Background`, below
  fullscreen windows — the wrong layer for a transient alert.

## Considered options

- **`dms notify` only**: rejected — invisible over fullscreen, no sound.
- **`desktop` plugin type**: rejected — Background layer sits below
  fullscreen.
- **Critical urgency via direct DBus**: rejected — DMS policy still respects
  DND and the popup z-order problem remains; `dms notify` CLI doesn't expose
  urgency anyway.
