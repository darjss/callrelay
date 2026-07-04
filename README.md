# callrelay

Surface incoming call notifications from your Android phone on your Linux desktop — even when an app is fullscreen.

A three-piece system: a Kotlin Android app detects incoming calls via `CallScreeningService`, a Cloudflare Worker (Hono + Durable Object) relays the events, and a DankMaterialShell plugin renders an overlay-layer popup with a ringtone on your desktop.

```
Android phone (CallScreeningService)
    │  POST /events (Bearer POST_TOKEN)
    ▼
Cloudflare Worker (Hono + Durable Object)
    │  GET /events?since=<cursor> (Bearer GET_TOKEN)
    ▼
Linux desktop (DMS daemon plugin)
    ├── PanelWindow on WlrLayershell.Overlay  (popup over fullscreen)
    ├── MediaPlayer                             (ringtone, 20s loop cap)
    └── dms notify                              (tray history)
```

## Why

I use a Bigme Hibreak Pro (e-ink Android phone) as my daily phone. When I'm on my Linux desktop — often in a fullscreen app — I miss calls because the phone is across the room and DMS's standard notification popups render behind fullscreen windows. This project bridges that gap.

## How it works

1. **Android app** registers a `CallScreeningService` that fires on every incoming call. On `STATE_NEW` (ringing), it sends a **Call Started Event** to the Worker. On `STATE_DISCONNECTED`, it sends a **Call Ended Event** with the outcome (`answered`, `missed`, `declined`, `error`). Call Started is best-effort (2 quick retries); Call Ended is queued persistently via Room + WorkManager.

2. **Cloudflare Worker** stores events in a Durable Object (SQLite, strongly consistent). Events expire after 7 days. The Worker exposes `POST /events` (auth via `POST_TOKEN`) and `GET /events?since=<cursor>` (auth via `GET_TOKEN`).

3. **DMS plugin** polls the Worker every 2 seconds. On a Call Started Event, it shows a top-center popup on the `WlrLayershell.Overlay` layer (above fullscreen windows) and plays a bundled ringtone via QML `MediaPlayer` (independent of DMS's global sound settings). On a Call Ended Event, it dismisses the popup and fires `dms notify` for tray history. If no Ended Event arrives within 30s, the popup auto-dismisses.

## Architecture decisions

Key decisions are recorded as ADRs in [`docs/adr/`](./docs/adr/):

- [ADR-0001](./docs/adr/0001-durable-object-polling.md) — Durable Object + 2s polling (not KV, not WebSocket)
- [ADR-0002](./docs/adr/0002-daemon-overlay-popup.md) — Daemon plugin with Overlay-layer popup (not `dms notify` alone)
- [ADR-0003](./docs/adr/0003-callscreeningservice.md) — `CallScreeningService` for call detection (not `PhoneStateListener`)

Domain language is in [`CONTEXT.md`](./CONTEXT.md).

## Requirements

- **Phone:** Android 14+ (API 34). Tested on Bigme Hibreak Pro.
- **Desktop:** Linux running [DankMaterialShell](https://github.com/AvengeMedia/DankMaterialShell) ≥ 1.5.0 on a wlroots-compatible compositor (Hyprland, Niri, Sway, etc.)
- **Worker:** Cloudflare Workers account with Durable Objects enabled

## Setup

### 1. Worker

```bash
cd web-server
cp .dev.vars.example .dev.vars    # add POST_TOKEN and GET_TOKEN
pnpm install
wrangler secret put POST_TOKEN    # paste a random 32-byte hex string
wrangler secret put GET_TOKEN     # paste another
wrangler deploy
```

### 2. Android app

```bash
cd android-app
cp .env.example .env              # add WORKER_URL and POST_TOKEN
./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

Open the app, grant the call screening permission (Settings → Default apps → Call screening).

### 3. DMS plugin

```bash
cp -r dms-plugin ~/.config/DankMaterialShell/plugins/CallRelay
dms restart
```

Open DMS Settings → Plugins → Scan for Plugins → enable CallRelay. Enter the Worker URL and GET_TOKEN in the plugin settings.

## Project structure

```
callrelay/
├── CONTEXT.md              # domain glossary
├── docs/
│   ├── adr/                # architecture decision records
│   └── agents/             # skill configuration
├── web-server/             # Cloudflare Worker (Hono + Durable Object)
├── android-app/            # Kotlin Android app (CallScreeningService)
└── dms-plugin/             # DankMaterialShell plugin (QML daemon)
```

## License

MIT
