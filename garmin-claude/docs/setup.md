# Setup: Windows 11 + OnePlus 12 + Garmin Venu 3

This was developed in a sandboxed Linux environment with no Garmin/Android
SDKs and no access to `developer.garmin.com` (see limitations.md), so
**none of this has been compiled or run yet.** These are the exact steps to
build and run it for real on your machine. Work through Phase 1-6 in order;
each phase is independently testable.

## 0. One-time installs (Windows 11, Windows Terminal)

**Garmin Connect IQ SDK**

1. Install the SDK Manager:
   https://developer.garmin.com/connect-iq/sdk/ -> download the SDK Manager
   for Windows, run the installer.
2. Install VS Code (https://code.visualstudio.com/) and the "Monkey C"
   extension (publisher: Garmin) from the VS Code marketplace. The
   extension bundles the ability to drive the SDK Manager and simulator
   from inside VS Code, which is the officially supported workflow (the
   raw `monkeyc`/`monkeydo` CLI still exists if you prefer the terminal).
3. Open the SDK Manager (via the VS Code command palette:
   `Monkey C: Show Device List`, or the standalone SDK Manager app), accept
   the license, and install the latest SDK. Confirm the device list
   includes **Venu 3** and **Venu 3S** (Garmin added these to the SDK
   Manager after the watch launched; if you have an old SDK install,
   update it).
4. Generate a developer signing key (needed to sideload onto a real
   watch): VS Code command palette -> `Monkey C: Generate Developer Key`,
   save it somewhere outside this repo (e.g.
   `%USERPROFILE%\garmin\developer_key.der`). **Do not commit this file.**

**Android**

1. Install Android Studio (Ladybird/Koala or newer):
   https://developer.android.com/studio
2. During setup, let it install the Android SDK (API 34) and platform
   tools (`adb`).
3. On your OnePlus 12: Settings -> About device -> tap "Build number" 7
   times to enable Developer Options, then Settings -> System -> Developer
   options -> enable **USB debugging**.
4. Install the **Garmin Connect** app from the Play Store on the OnePlus
   12, sign in, and pair your Venu 3 with it (this is required regardless
   of anything in this repo -- it's the transport every Connect IQ
   companion app relies on).

**Claude API key**

1. Create a key at https://console.anthropic.com/ (Settings -> API Keys).
2. Keep it somewhere private. You'll paste it into
   `android/secrets.properties` (Phase 3) or the app's in-app Settings
   screen -- never into any file under `watch/`.

## Phase 1: Watch app skeleton on the simulator

```powershell
cd garmin-claude\watch
```

Open this folder in VS Code. VS Code Monkey C extension workflow:

1. Command palette -> `Monkey C: Build Current Project`, target device
   `venu3`.
2. Command palette -> `Monkey C: Run` (or `F5`) to launch the simulator
   with the built app.

If you'd rather use the CLI directly (SDK Manager installs these under
`%APPDATA%\Garmin\ConnectIQ\Sdks\<version>\bin`):

```powershell
monkeyc -f monkey.jungle -d venu3 -o bin\AskClaude.prg -y <path-to-developer_key.der>
monkeydo bin\AskClaude.prg venu3
```

**Verify**: the simulator shows the "CLAUDE / tap to ask a question" home
screen at 454x454. Tapping opens the on-device text keyboard (TextPicker).
Typing something and confirming will move to a "Thinking..." screen and
then time out with an error after ~45s, since there's no phone app running
yet -- that's expected at this phase.

## Phase 2: Watch <-> phone communication

1. Build/install the watch app onto your **real Venu 3** (see "Installing
   on a real Venu 3" below) or keep using the simulator -- the Android app
   can talk to either (the simulator exposes a local TCP bridge Garmin's
   tooling uses automatically; a real device needs actual BLE pairing).
2. Open `garmin-claude\android` in Android Studio, let Gradle sync
   (it will pull `com.garmin.connectiq:ciq-companion-app-sdk` from Maven
   Central automatically).
3. Copy `android\secrets.properties.example` to
   `android\secrets.properties` and fill in `ANTHROPIC_API_KEY` (Phase 3
   needs it; for this phase alone you can leave it blank).
4. Run the app on the OnePlus 12 (USB debugging, `Run` in Android Studio,
   or `adb install`).
5. Grant the notification permission prompt (needed for the foreground
   service).

**Verify**: the app's status line should read "Connected: <your watch
name>" within a few seconds. Tap "Ask" on the watch, type "test", submit --
the Android app's log should show `Watch asked: "test"`, and (once Phase 3
is wired up) the watch should show a real answer instead of erroring out.

## Phase 3: Real Claude responses

1. Make sure `android/secrets.properties` has a real
   `ANTHROPIC_API_KEY` and, if you want a specific model,
   `CLAUDE_MODEL=claude-sonnet-5` (or whatever current model you prefer --
   it's fully configurable, not hardcoded).
2. Rebuild/reinstall the Android app so the new `BuildConfig` values take
   effect (or set them at runtime via the app's Settings button instead of
   rebuilding).
3. Ask a real question from the watch.

**Verify**: within a few seconds the watch shows "Thinking..." with a
"receiving N/M" progress label, then the actual Claude response,
scrollable.

## Phase 4: Scrolling + follow-ups

Already implemented -- swipe up/down on the response screen to scroll long
answers; tap anywhere on the response screen to ask a follow-up (context is
preserved via the phone-side `ConversationStore`); back button/gesture
returns to the home screen and a fresh tap there starts a brand-new
conversation.

**Verify**: ask something with a long answer ("explain photosynthesis in
detail"), confirm you can swipe through all of it. Then tap to ask "what
about at night?" and confirm the answer makes sense as a follow-up (proves
history is being sent to the API).

## Phase 5: Images

Ask something like "show me a diagram of a transformer neural network."

**Verify**: watch should show "Receiving image N/180" progress, then a
small 4-colour box-and-arrow diagram. This will take noticeably longer than
a text answer -- see limitations.md for why, and for the untested
`makeImageRequest` alternative worth trying if this is too slow for you in
practice.

## Phase 6: Reliability pass

Things worth manually testing before you trust this day-to-day:

- Turn off the OnePlus 12's Bluetooth mid-question -- the watch should time
  out with a readable error, not hang forever.
- Force-stop the Android app, then ask from the watch -- same.
- Ask two questions back-to-back quickly -- the `id`-based
  request-tracking in `ClaudeBridge.mc` should ignore a stale response for
  a question you already backed out of.

## Installing on a real Venu 3

Once you have a signed `.prg`:

1. Connect the Venu 3 to your PC via USB (it mounts as a mass storage
   device, "GARMIN").
2. Copy `bin\AskClaude.prg` into `GARMIN\APPS\` on the watch drive.
3. Safely eject; the watch will prompt to install the new app on next
   reboot/detection (Settings -> System -> Restart if it doesn't pick it
   up automatically).

No Garmin developer *account* purchase or app-store submission is required
for this -- that's only needed if you want to publish to the Connect IQ
Store. Sideloading with a self-generated developer key is free and is the
normal way to run your own apps on your own watch.

## Installing the Android app on the OnePlus 12 without Android Studio

```powershell
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

(Build the APK first via Android Studio's Build menu, or
`gradlew.bat assembleDebug` from the `android` folder once you've run
Android Studio at least once so the Gradle wrapper/SDK paths exist.)

## Optional: running the /server bridge instead of calling Anthropic from the phone

```powershell
cd garmin-claude\server
copy .env.example .env
notepad .env   # fill in ANTHROPIC_API_KEY
npm install
npm start
```

Then in the Android app's Settings screen, set "Bridge server URL" to
`http://<your-PC's-LAN-IP>:8787` (find the IP with `ipconfig`). Leave the
in-app API key field blank -- the phone will use the bridge instead.
