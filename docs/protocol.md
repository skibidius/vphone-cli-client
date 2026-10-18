# Protocol notes

The exact subset of the `vphone-cli` Automation API this project uses. Source of
truth is [Lakr233/vphone-cli](https://github.com/Lakr233/vphone-cli)
(`Research/vphoned_http_api.md`, `VPhoneDaemon/Daemon/GuestAPI*.swift`, and the
`owngoal-dev/icli` library). Pinned here so it does not have to be re-derived.

## Transport and authentication

- The VM is launched with `vphone-cli vm launch <name> --api-listen 127.0.0.1:8765`.
- Token: either fixed via `VPHONE_API_TOKEN` (16–256 chars from
  `A-Z a-z 0-9 - . _ ~`), or printed at startup (`[api] token: …`).
- Accepted as `Authorization: Bearer <token>`, `?token=<token>`, or
  `Sec-WebSocket-Protocol: vphone-token.<token>`.
- `adb reverse tcp:8765 tcp:8765` makes `127.0.0.1:8765` reachable on the phone;
  vphone sees the connection as loopback, so its Host check passes.

## Video: `GET /v1/host/screen` (WebSocket)

The client sends one text message per frame request:

```json
{"id":7,"codec":"h264","box":[2796,2796],"fps":60,"bitrate":4000000,"key":false}
```

- `codec`: `h264` | `hevc`
- `box`: [width, height] of the max frame size (1–8192, even sides; "no enlarging")
- `fps`: 1–240; `bitrate`: 1–100 000 000; `key`: request a keyframe

The reply is a binary message:

```
[4-byte LE: JSON header length][JSON header][payload]
```

The header has `id` and either `error {code,message}` or `status`:

| status | payload | meaning |
| --- | --- | --- |
| `frame` | access unit | NAL units with 4-byte length prefixes, **no** parameter sets |
| `unchanged` | none | screen did not change |
| `dropped` | none | rate controller skipped the frame |

Every `frame` header also carries `session` (changes when codec/size/orientation
changes; the first frame of a session is a keyframe), `width`/`height` (encoded
size), `sourceWidth`/`sourceHeight`, `scale`, `captureUs`/`encodeUs`. On a
keyframe there is an extra `record` — base64 `avcC` (H.264) or `hvcC` (HEVC).

**Decoder-relevant details:**

- The outer 4-byte header length is **little-endian**.
- The NAL length prefixes inside the payload are **big-endian** (AVCC/HVCC
  framing from VideoToolbox).
- Parameter sets (SPS/PPS, plus VPS for HEVC) exist **only** in `record`, so the
  client parses `record` and prepends them (Annex-B, `00 00 00 01` start codes)
  before a keyframe, converting the NALs themselves to Annex-B.

## Input: `/v1/events` WebSocket (JSON-RPC)

Requests are `{"id":N,"method":"…","params":{…}}`; responses can be ignored (the
host serializes touch/key sequences, so order is preserved).

### Live touch

```
input.touch {phase:"down"|"move"|"up", x, y, normalized:true}
```

`normalized:true` means x/y are 0…1 in the portrait digitizer space (the
cleanest option for a client — no need to know pixels vs points). Two-finger
pinch uses `input.touch2 {phase, x1, y1, x2, y2}` (normalized only).

### Canned gestures (coordinates are screen points, as `device.screen` reports)

```
input.tap {x, y}
input.double_tap {x, y, interval?}
input.long_press {x, y, seconds?}
input.swipe {x1, y1, x2, y2, seconds?, steps?}
input.drag {points:[[x,y],…], seconds?, hold?, steps?}
input.touch_sequence {events:[{phase,x,y,delay_ms}], normalized?}
```

### Buttons

```
input.button {name}
```

Names (from `icli` `pressButton`): `home`, `power`, `volume-up`, `volume-down`,
`mute`, `wake`.

### Keyboard

```
input.key {name}   # return, cmd+v, …
input.type {text, delay_ms?}
input.paste {text}
```

## Limits and behavior

- More than 16 pending frame requests → `too_many_requests`; keep the in-flight
  window at ~2 frames.
- Default codec is `h264` for maximum Android decoder compatibility; `hevc` is
  optional.
- The listener is loopback-only; do not expose it without encryption.
