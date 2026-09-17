# AskClaude for Garmin Venu 3

Ask Claude a question from your Garmin Venu 3, get the answer back on your
wrist -- bridged through an Android companion app on your phone, which is
the only supported way to reach an LLM API from a Connect IQ watch without
putting an API key on the watch itself.

```
Venu 3 (Connect IQ / Monkey C)
   <-- Bluetooth LE, AppMessage -->
Garmin Connect Mobile (required transport, not something you interact with)
   <-- IPC -->
AskClaude Companion (Android, OnePlus 12)
   <-- HTTPS -->
Claude API (or your own optional /server bridge)
```

See `docs/architecture.md` for the full reasoning, `docs/limitations.md`
for what's confirmed vs. what needs validating on real hardware, and
`docs/setup.md` for exact Windows 11 + OnePlus 12 + Venu 3 build/install
steps.

## Status

This was originally built in a sandboxed environment with no Garmin/Android
SDKs and no access to `developer.garmin.com`, so it shipped uncompiled. That
is no longer true of the watch side.

**Watch app: builds and runs.** Verified 2026-09-16 against Connect IQ SDK
9.2.0, built for both `venu3` and `venu3s`, running in the Venu 3 simulator.
The home screen renders and the on-watch keyboard opens. Two type errors had
to be fixed first (see the git history); the Monkey C type checker requires
explicit `as Void` annotations on callbacks handed to the SDK.

**Bridge server: verified.** `server/` boots, `/health` responds, and a real
request reaches the Anthropic API.

**Android companion app: builds.** Verified 2026-09-17. `./gradlew
:app:assembleDebug` produces a 6.5 MB debug APK against Android SDK 34 and
JDK 17, with two warnings and no errors. `docs/limitations.md` expected
signature fixes here; none were needed. Every Connect IQ SDK call in
`ClaudeBridgeService.kt` was checked against the real
`ciq-companion-app-sdk:2.4.0` AAR (`getInstance`, `initialize`,
`getConnectedDevices`, `registerForAppEvents`, `sendMessage`, `IQApp(String)`,
`IQDevice.getFriendlyName`) and all of them match.

The project had no Gradle wrapper; one is now committed, so `./gradlew` works
from a clean clone. You still need `android/local.properties` with
`sdk.dir=/path/to/android-sdk` (gitignored), and `android/secrets.properties`
with your API key before the app can reach Claude.

Note on Android 11+ package visibility: the app binds to Garmin Connect
Mobile, which normally needs a `<queries>` declaration. The Connect IQ AAR
already declares it and it merges in automatically, so nothing is needed in
this app's manifest.

**Not yet verified on real hardware.** Everything above is the simulator. In
particular the simulator draws system UI (the `TextPicker` keyboard) as a
crude off-center approximation; that is the simulator, not this app, and it
reproduces identically in a stub app containing no layout code. How it looks
on an actual Venu 3 is still unconfirmed.

## Project layout

```
garmin-claude/
  watch/     Connect IQ project (Monkey C) -- the Venu 3 app
  android/   Android companion app (Kotlin) -- the Claude bridge
  server/    Optional Node bridge server, if you'd rather not keep the
             API key on your phone at all
  docs/      architecture.md, setup.md, limitations.md
```

## What works (by design, pending your first real build/test pass)

- Ask a question from the watch (on-device `TextPicker` keyboard -- there
  is no microphone/voice API available to Connect IQ apps, see
  limitations.md) or from the phone app (easier for longer questions).
- Loading state, scrollable response, readable error states (phone
  unreachable, API error, timeout).
- Follow-up questions with real conversation context (kept phone-side,
  since the Anthropic API is stateless and the watch has nowhere to
  usefully store a growing transcript).
- A configurable model (`CLAUDE_MODEL`, no hardcoded model name) and no
  hardcoded API key anywhere in the repo.
- A genuine, if intentionally simple, image pipeline: Claude returns a
  small structured diagram spec instead of prose, the phone renders it,
  downsamples and colour-reduces it, and streams it to the watch as a
  `Graphics.BufferedBitmap`.

## What doesn't (and can't, on current Connect IQ)

- Voice input on the watch -- no microphone API exists for third-party
  Connect IQ apps.
- Literal Claude-generated images -- Claude is a text model; "a diagram of
  a transformer" is rendered locally on the phone from a structured spec,
  not generated as a bitmap by Claude.
- Background/closed-app usage -- this is a foreground, open-the-app,
  ask-a-question tool, not a watch face complication.

Full detail on both lists, including exactly what I could and couldn't
verify given a blocked network path to Garmin's docs, is in
`docs/limitations.md`.

## Quick start

Read `docs/setup.md`. Short version:

1. Install the Connect IQ SDK + VS Code Monkey C extension, build
   `watch/` for `venu3`, run it in the simulator.
2. Open `android/` in Android Studio, copy
   `android/secrets.properties.example` to `android/secrets.properties`,
   add your Anthropic API key, run the app on your OnePlus 12.
3. Ask something from the watch.

## Security

- No API key is ever placed in `watch/`. The manifest only requests the
  `Communications` permission needed for AppMessage.
- `android/secrets.properties` and `server/.env` are gitignored; only
  `*.example` templates are committed.
- The optional `/server` bridge exists for people who don't want the key
  on their phone at all; it's meant for localhost/LAN use, not the public
  internet (see limitations.md).

## Next improvements

Roughly in the order I'd tackle them:

1. Try `Communications.makeImageRequest` against a local loopback server
   in the companion app (see limitations.md) -- likely faster and simpler
   than the current row-by-row AppMessage image transfer, but unverified
   without real hardware.
2. Add a proper "New chat" vs. "Follow-up" menu on the watch (currently: a
   fresh tap from Home always starts new, a tap from the response screen
   always follows up) once you know which you actually want more often.
3. Stream partial text back to the watch as Claude generates it (Anthropic
   supports streaming responses) instead of waiting for the full answer,
   for snappier perceived latency.
4. Add retry-with-backoff on AppMessage send failures instead of just
   surfacing an error.
5. Multi-watch support in the companion app, if you ever pair a second
   Garmin device.
6. Real auth + TLS on `/server` if you ever want to run it somewhere
   other than your own LAN.
