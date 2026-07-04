# POST_TOKEN baked into the APK

## Context

The Android App authenticates POST requests to the CallRelay Worker with a
shared `POST_TOKEN`. The token is injected at build time via `BuildConfig`
from `gradle.properties` (or a CI secret), so it lands in the compiled APK.

## Decision

Keep `POST_TOKEN` in `BuildConfig` rather than implementing a runtime login
flow or on-device token entry.

## Tradeoff

The token is extractable from the APK by anyone with physical access to the
device or the built artifact (e.g. via `apktool`). This is acceptable because
CallRelay is a single-user personal app on a single owned device, the token
only authorizes appending call events to a personal log, and rotating the
token is a one-line `gradle.properties` change plus a redeploy. A runtime
login flow would add complexity disproportionate to the threat model.
