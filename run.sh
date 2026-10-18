#!/usr/bin/env zsh
set -euo pipefail

# Launch vphone-cli-client streaming.
#
# Starts vphone-bridge, which boots the iPhone VM with the automation API
# (127.0.0.1:8765) and keeps `adb reverse` alive so the Android client can
# reach it over USB.
#
# Usage:  ./run.sh            # uses defaults (VM "iphone")
#         VPHONE_VM=myvm ./run.sh

ROOT="$(cd "$(dirname "$0")" && pwd)"
BRIDGE="$ROOT/macos/.build/release/vphone-bridge"
VM="${VPHONE_VM:-iphone}"
VPHONE_CLI="${VPHONE_CLI:-$HOME/.local/bin/vphone-cli}"

if [[ ! -x "$BRIDGE" ]]; then
    echo "vphone-bridge not found. Build it first:" >&2
    echo "  cd '$ROOT/macos' && swift build -c release" >&2
    exit 1
fi

echo "[run] starting vphone-bridge (VM: $VM, port 8765)…"
echo "[run] keep this window open; Ctrl-C stops the VM and the tunnel."
exec "$BRIDGE" --vm "$VM" --vphone "$VPHONE_CLI"
