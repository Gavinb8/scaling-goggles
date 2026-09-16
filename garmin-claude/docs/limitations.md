# Limitations, and what I could and couldn't verify

## A research caveat up front

`developer.garmin.com` and `forums.garmin.com` were both blocked by this
environment's network egress policy while building this project, so the
Connect IQ facts below come from Garmin's own GitHub repos, Maven Central,
and cached/indexed forum text via web search -- not from directly reading
the current API reference. Everything here is written in good faith from
that evidence plus well-established, years-stable Connect IQ conventions,
but **you should sanity-check the exact method signatures in
`ClaudeBridgeService.kt` and `ClaudeBridge.mc` against the current official
docs the first time you build**, since I could not load
`developer.garmin.com/connect-iq/api-docs/...` to confirm them character
for character. Nothing here was faked to look more finished than it is --
where I'm not sure, it says so.

## Confirmed: no microphone / voice API for third-party apps

The Venu 3 has a microphone and supports a voice assistant (Siri / Google
Assistant / Bixby), but that's a phone-side OS feature the watch merely
relays to over Bluetooth -- it is **not** exposed to Connect IQ apps.
Garmin's own developer forum has an open feature request ("Include
microphone access in the SDK") confirming there is currently no
speech-to-text or raw microphone API for third-party Monkey C apps. So:

- **Voice input is not implemented, and can't be, on current Connect IQ.**
- The watch-side input is `Toybox.WatchUi.TextPicker`, the standard
  on-device text entry widget (touch keyboard/grid, look differs by
  device but is present on all touchscreen Connect IQ devices including
  the Venu 3).
- The phone-typed input path in the companion app exists specifically to
  make this less painful in practice.

## Communications module: AppMessage vs. direct web requests

`Toybox.Communications` exposes both:

- `transmit()` / `registerForPhoneAppMessages()` -- generic bidirectional
  messaging with whatever companion app is registered for this app's ID.
  This is what the watch app uses.
- `makeWebRequest()` / `makeImageRequest()` -- lets the watch make an HTTP(S)
  request "directly." On a Venu 3, which has no WiFi radio, this request is
  actually carried out by Garmin Connect Mobile using the phone's
  connection; it is not independent of the phone. Using this for the
  Claude call itself would mean putting the API key in the watch binary,
  which the task explicitly ruled out, so it isn't used for that. See
  architecture.md for the full reasoning.

**Payload size / throughput**: I could not confirm an exact documented
limit. Community reports (Garmin developer forum threads, via search)
describe BLE AppMessage throughput around 0.5-1KB/sec and suggest staying
well under a reported ~10-16KB ceiling. This project treats that as a hard
design constraint: text responses are chunked into ~400-character pieces
(`Protocol.MAX_CHUNK_CHARS`) and reassembled on the watch, regardless of
what the real limit turns out to be.

## Background execution

Not used. Connect IQ's `Background` module (periodic/temporal delegates for
running code while the app isn't in the foreground) exists for things like
syncing a watch face complication, but this app's whole interaction model
is synchronous request/response while the app is open, so there's no
"ask Claude something and check back after closing the app" feature. That
would be a reasonable v2 addition (see improvements.md-style list in the
README) but adds real complexity (background CPU/network budgets are
tightly rationed by Garmin) and wasn't necessary for a working prototype.

## Persistent storage

`Toybox.Application.Storage` is used for exactly two small values (the next
request id counter, and the current conversation id) so a relaunch of the
watch app doesn't immediately lose track of an in-flight conversation. Full
multi-turn history is intentionally **not** stored on the watch -- it lives
in the phone app's `ConversationStore`, which has effectively unlimited
storage/RAM by comparison and is the right place for it.

## Images: what's implemented vs. what's aspirational

Implemented and should work as designed:

- Claude (a text model) cannot generate a bitmap. The pipeline instead has
  Claude return a tiny structured "diagram spec" (boxes, labels, arrows)
  which the **phone** renders with a normal Android `Canvas`, downsamples
  to 180x180, and colour-reduces to 4 colours.
- That image is sent to the watch as a sequence of `image_row` AppMessage
  dictionaries (one Monkey C `Array` of small integers per row) and
  reassembled into a `Graphics.BufferedBitmap`, then drawn with
  `Dc.drawBitmap()`. `BufferedBitmap` and `Dc.drawBitmap` are long-standing,
  broadly-supported Connect IQ APIs, so this path only depends on
  well-established primitives.
- This will be **slow** -- 180 rows x 180 one-byte pixels is 32,400 small
  integers, sent as ~180 separate AppMessage payloads. Expect several
  seconds to tens of seconds for an image to fully arrive. Reducing
  `ImagePipeline.TARGET_SIZE` (in the Android app) trades image size for
  speed if that's not acceptable.

Worth trying next, not implemented (because I couldn't verify the exact API
against current docs, and didn't want to ship unverified code as if it were
tested):

- `Communications.makeImageRequest(url, params, options, callback)` can
  decode a PNG/JPEG straight into a `Graphics.BitmapReference` on-device,
  with `:maxWidth`/`:maxHeight`/`:palette` options to control size and
  colour depth -- this is clearly the API Garmin intends for this use case,
  and would very likely be both faster and simpler than the row-chunking
  fallback above. The open question is reachability: on a Venu 3 (no WiFi),
  this request is executed by Garmin Connect Mobile using the phone's own
  network stack. Since GCM and the AskClaude companion app run on the
  *same* phone, a tiny local HTTP server inside the companion app (e.g.
  NanoHTTPD, already a dependency) bound to `127.0.0.1` should be reachable
  by GCM's request the same way any other localhost service is reachable
  from another process on the same device -- but this is my best technical
  reasoning, not something I was able to confirm against Garmin's docs or
  test on hardware. If you want to pursue this, it's a genuinely promising
  next step; treat it as an experiment, not a guarantee.

## What was not built at all

- **Publishing to the Connect IQ Store.** Not requested, and would require
  a paid/verified Garmin developer account, app review, and a permanent
  signing key -- out of scope for a personal prototype. Sideloading (see
  setup.md) needs none of that.
- **Multi-device support beyond "your one paired watch."** The companion
  app just uses the first connected/known Connect IQ device; fine for a
  single Venu 3, not written to juggle multiple watches.
- **Encryption/auth on the optional `/server` bridge** beyond an optional
  shared-secret header -- it's meant to run on your own machine/LAN, not be
  exposed to the internet. Don't port-forward it without adding real auth
  and TLS.
