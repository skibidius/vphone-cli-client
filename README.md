# vphone-cli-client

Stream a virtual iPhone ([`vphone-cli`](https://github.com/Lakr233/vphone-cli))
from a Mac to an Android phone over USB in real time, sending screen touches and
buttons back. The end result: the Android device looks and feels like it is
running iOS.

```
[iOS VM → vphone-cli --api-listen 127.0.0.1:8765]   (hardware H.264/HEVC encoding)
              ▲ 127.0.0.1:8765
    [Mac: adb reverse tcp:8765 tcp:8765]  ────── USB ──────► Android
              │
    Android: ws://127.0.0.1:8765/v1/host/screen → MediaCodec → SurfaceView
    Android: ws://127.0.0.1:8765/v1/events      ← touch / volume / home / power
```

Latency is comparable to [scrcpy](https://github.com/Genymobile/scrcpy).

---

## Quick start — no building required

You need two files from the **latest GitHub Release**:

- `vphone-client.apk` — the Android app
- `vphone-bridge-macos-arm64.tar.gz` — the Mac helper

### Step 1 — Mac (one-time + every run)

Install `adb` once (the helper uses it to bridge USB):

```sh
brew install android-platform-tools
```

Extract the helper and put it on your PATH:

```sh
tar -xzf vphone-bridge-macos-arm64.tar.gz
sudo mv vphone-bridge /usr/local/bin/
sudo xattr -dr com.apple.quarantine /usr/local/bin/vphone-bridge 2>/dev/null || true
```

Run it, pointing at your existing vphone VM:

```sh
vphone-bridge --vm <your_vm_name>
```

It launches vphone with the streaming API on `127.0.0.1:8765` and waits for the
phone. Leave this window open.

### Step 2 — Phone

1. Enable **Developer options → USB debugging**.
2. Plug the phone into the Mac and accept the "Allow USB debugging?" prompt.
3. Install the APK:
   - copy `vphone-client.apk` to the phone and tap it (allow "install from
     unknown sources"), **or**
   - `adb install vphone-client.apk`
4. Open **vPhone Client**.

You should see the iOS screen, fullscreen and without any client UI. The
phone's physical volume keys are forwarded to iOS, and pinch works (zoom in
Photos, Maps, etc.).

- **Settings** are hidden: long-press the app icon in the launcher and tap
  **Settings** to edit host/port/token.
- The app blocks Android system gestures (back is swallowed, edge swipes go to
  the VM) and pins itself so the **home** gesture doesn't leave it. To fully
  block the home gesture, enable **Settings → Security → Screen pinning** on the
  phone once. To unpin: swipe up from the bottom and hold, then confirm.

> The host and port in the app default to `127.0.0.1:8765` — the adb-reverse
> tunnel makes that the phone's own loopback, so you normally don't need to
> change anything.

---

## Building from source (advanced)

### Android app

Requires JDK 17 and Gradle 8.7 (or open in Android Studio).

```sh
cd android
gradle :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### macOS helper

```sh
cd macos
swift build -c release
# binary: .build/release/vphone-bridge
```

All options: `vphone-bridge --help`.

---

## Releases and signing (maintainer)

GitHub Actions builds both artifacts on every `v*` tag push and publishes them
to Releases. To get a **signed** APK (not just the debug-signed fallback), set
up a signing key once:

```sh
keytool -genkeypair -v \
  -keystore vphone-release.jks \
  -alias vphone \
  -keyalg RSA -keysize 2048 -validity 10000

base64 -i vphone-release.jks | tr -d '\n' > vphone-release.jks.b64
```

Add these secrets in **GitHub → repo → Settings → Secrets and variables →
Actions**:

| Secret | Value |
| --- | --- |
| `VPHONE_KEYSTORE_BASE64` | contents of `vphone-release.jks.b64` |
| `VPHONE_KEYSTORE_PASSWORD` | keystore password |
| `VPHONE_KEY_ALIAS` | `vphone` |
| `VPHONE_KEY_PASSWORD` | key password |

Keep `vphone-release.jks` somewhere safe — you need the **same** key to ship
updates (Android refuses upgrades signed with a different key).

Then publish a release:

```sh
git tag v0.1.0
git push origin v0.1.0
```

Without the secrets, CI still publishes a debug-signed APK (fine for sideloading,
not for app-store-style updates).

---

## Repository layout

| Path | What it is |
| --- | --- |
| `macos/` | Swift helper `vphone-bridge`: launches vphone + `adb reverse` + auto-reconnect |
| `android/` | Android client (Kotlin + XML Views): stream, touch, buttons |
| `.github/workflows/release.yml` | CI: builds the APK and the helper, publishes to Releases |
| `docs/protocol.md` | The exact vphone API subset this project uses |

## How it works

The client connects to `ws://127.0.0.1:8765/v1/host/screen` (via `adb reverse`
this is the phone's own loopback) and *pulls* frames: for each text request
`{"id","codec","box","fps","bitrate","key"}` the server replies with one binary
frame — a 4-byte little-endian length + a JSON header + NAL units. `MediaCodec`
decodes straight into a `SurfaceView`. Touch and buttons go to `/v1/events` as
JSON-RPC (`input.touch` with normalized coordinates, `input.touch2` for pinch,
`input.button` for keys). See [docs/protocol.md](docs/protocol.md).

## Current limitations

- Up to two simultaneous touches (one finger + pinch); 3+ fingers are ignored.
- iPhone portrait only; iPadOS/landscape untested.
- No audio streaming (video + input only); audio can be added separately.

## License

MIT — see [LICENSE](LICENSE). Not affiliated with vphone-cli; vphone-cli is by
[Lakr233](https://github.com/Lakr233).
