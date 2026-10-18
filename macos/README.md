# macOS helper: vphone-bridge

A small dependency-free Swift CLI that:

1. launches `vphone-cli` with the automation API enabled (`--api-listen`) and a
   fixed token (`VPHONE_API_TOKEN`);
2. maintains `adb reverse tcp:<port> tcp:<port>` so the phone can reach the API
   on its own `127.0.0.1:<port>` over USB;
3. re-applies the tunnel when USB reconnects.

## Build

```sh
cd macos
swift build -c release
# binary: .build/release/vphone-bridge
```

## Run

```sh
# with vphone-cli on PATH
.build/release/vphone-bridge --vm iphone

# or point at the vphone-cli binary explicitly
.build/release/vphone-bridge --vm iphone --vphone /path/to/vphone-cli

# vphone already running separately: only manage adb reverse
.build/release/vphone-bridge --no-launch
```

All options: `vphone-bridge --help`.

The default token is `vphone-android-bridge` (matching the Android app's
default). If you change it with `--token`, set the same value in the app's
settings on the phone.

## Auto-start (launch agent)

```sh
# install the binary
cp .build/release/vphone-bridge /usr/local/bin/vphone-bridge

# adjust --vm and paths in the plist, then:
cp launchagent/com.vphone.bridge.plist ~/Library/LaunchAgents/
launchctl load ~/Library/LaunchAgents/com.vphone.bridge.plist
```

To stop it: `launchctl unload ~/Library/LaunchAgents/com.vphone.bridge.plist`.
