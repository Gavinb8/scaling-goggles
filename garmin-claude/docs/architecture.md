# Architecture

## What you asked for vs. what Connect IQ actually allows

The requested architecture was:

```
Venu 3 Connect IQ app -> Garmin Connect Mobile / Bluetooth -> Android companion -> Claude API -> back
```

That is, essentially, exactly what's implemented here. The one thing to be
precise about: **Garmin Connect Mobile (GCM) can't be removed from the
path.** It's not a "man in the middle" you interact with -- it's the
background service that owns the Bluetooth LE connection to the watch and
brokers all AppMessage traffic for every Connect IQ app on the phone. Every
Connect IQ companion app (Strava, etc.) works this way; there's no supported
way for a third-party Android app to open its own BLE channel to a Connect
IQ watch app. So the real, buildable pipeline is:

```
Venu 3 (Monkey C, Toybox.Communications)
        |  Bluetooth LE, AppMessage
        v
Garmin Connect Mobile (transport only, required, invisible to you day-to-day)
        |  IPC
        v
AskClaude Companion (Android, this repo's /android)
        |  HTTPS
        v
Claude API (api.anthropic.com) -- or your own /server bridge
        |
        v
... same path back to the watch
```

## Why the phone (not the watch) calls Claude

Two independent reasons converge on the same answer:

1. **Security.** You asked not to expose an API key on the watch. A
   Connect IQ `.prg`/`.iq` package is not meaningfully secret -- there's no
   secure enclave or code obfuscation guarantee -- so a key embedded in
   Monkey C source should be treated as public. The phone app is a much
   more reasonable place to hold a key (fully under your control, not
   distributed anywhere).
2. **Capability.** `Toybox.Communications.makeWebRequest()` *does* let the
   watch make an HTTPS request directly, but on a Venu 3 (no WiFi radio)
   that request is still physically carried out by Garmin Connect Mobile
   using the phone's own internet connection -- it doesn't reach the
   internet "from the watch" in any independent sense. So even the
   watch-native HTTP path still depends on the phone; using it wouldn't
   remove the phone dependency, it would just also require putting the key
   on the watch. There's no configuration that lets the watch talk to
   Claude without the phone being involved.

So: **AppMessage to a custom Android companion app is the correct,
supported way to do this**, and it's what's implemented.

## Message protocol (watch <-> phone)

Connect IQ AppMessage has no schema -- both sides just exchange
Dictionaries/Maps. The shared contract lives in two files that must stay in
sync:

- `watch/source/Protocol.mc`
- `android/app/src/main/java/com/gavinb8/askclaude/Protocol.kt`

Watch -> phone (`Communications.transmit`):

```json
{ "type": "prompt", "id": 123, "conv": "abc", "text": "...", "followUp": false }
{ "type": "cancel", "id": 123 }
```

Phone -> watch (`ConnectIQ.sendMessage`), text responses chunked because BLE
AppMessage throughput is slow (roughly 0.5-1KB/sec in practice) and payload
size limits are undocumented but reported around 10-16KB:

```json
{ "type": "chunk", "id": 123, "seq": 0, "total": 3, "text": "..." }
{ "type": "error", "id": 123, "message": "..." }
```

Image data (see "Images" below), also chunked, one row per message:

```json
{ "type": "image_start", "id": 123, "w": 180, "h": 180, "palette": [0,16777215,8092400,8421504] }
{ "type": "image_row", "id": 123, "row": 0, "pixels": [0,0,1,1,2,...] }
{ "type": "image_end", "id": 123 }
```

The watch (`ClaudeBridge.mc`) reassembles chunks by `id`/`seq`/`total`
before handing a complete response to whichever screen is showing.

## Conversation context

The Anthropic Messages API is stateless -- every call needs the full
transcript. The watch only tracks a `conversationId` (so it can mark a
question as "follow-up" vs. "new chat") and shows the latest answer; the
actual multi-turn history is kept phone-side in `ConversationStore.kt`
(bounded to the last 20 turns per conversation), since the phone has real
memory and the watch does not need to duplicate it.

## Two ways to ask a question

1. **From the watch**: tap "Ask", the on-device `TextPicker` keyboard opens
   (see limitations.md for why this, and not voice, is the input method),
   you type, it's sent as a `prompt` message.
2. **From the phone**: the companion app's main screen has its own text box
   ("Ask from your phone"). This exists because typing more than a couple
   of words on a 454x454 watch face is genuinely painful, and the task
   explicitly allowed a "companion-phone input mechanism." The answer is
   pushed to the watch either way, so the watch is always the display, even
   when the phone was where you typed.

## Images

Claude is a text model. It cannot generate a bitmap/photo of anything --
"show me a diagram of a transformer" cannot literally return pixels from
Claude itself. What this implements instead:

1. If the prompt looks like an image/diagram request (keyword heuristic in
   `ImagePipeline.kt`), the companion app appends instructions asking
   Claude to reply with a one-line caption plus a small structured JSON
   diagram spec (labelled boxes + arrows, not prose).
2. The phone renders that spec locally with a plain Android `Canvas` --
   this is real, on-device rendering, not a Claude-generated image.
3. The render is downscaled to 180x180 and colour-reduced to a fixed
   4-colour palette (`ImagePipeline.PALETTE`), because a full-colour bitmap
   would be far too large to move over AppMessage/BLE.
4. Pixels are sent to the watch one row at a time (`image_row` messages)
   and reassembled into a `Graphics.BufferedBitmap`, which is what actually
   gets drawn on-screen via `Dc.drawBitmap()`.

This is the "closest viable alternative" this project promised if literal
Claude-generated images turned out not to be possible -- and they aren't.
See `docs/limitations.md` for the untested-but-promising alternative
(`Communications.makeImageRequest` against a local loopback server on the
phone), which is worth trying once you have real hardware in hand.
