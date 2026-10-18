package com.vphone.client.input

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * Sends input events to vphone's `/v1/events` WebSocket as JSON-RPC requests.
 * The host serializes touch and key sequences in order, so requests are
 * fire-and-forget (responses are ignored) for minimum latency.
 */
class InputClient(
    private val okhttp: OkHttpClient,
    host: String,
    port: Int,
    token: String,
) {
    private val url = "ws://$host:$port/v1/events?token=$token"
    private val idGen = AtomicLong(0)

    @Volatile private var ws: WebSocket? = null

    fun connect() {
        ws = okhttp.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {})
    }

    fun close() {
        ws?.close(1000, null)
        ws = null
    }

    /** Single-finger digitizer event. `x`/`y` are normalized 0..1. */
    fun sendTouch(phase: String, x: Float, y: Float) {
        send(
            "input.touch",
            JSONObject()
                .put("phase", phase)
                .put("x", x.toDouble())
                .put("y", y.toDouble())
                .put("normalized", true),
        )
    }

    /** Two-finger digitizer event (pinch). Coordinates are normalized 0..1. */
    fun sendTouch2(phase: String, x1: Float, y1: Float, x2: Float, y2: Float) {
        send(
            "input.touch2",
            JSONObject()
                .put("phase", phase)
                .put("x1", x1.toDouble())
                .put("y1", y1.toDouble())
                .put("x2", x2.toDouble())
                .put("y2", y2.toDouble()),
        )
    }

    /** Hardware button: "home", "power", "volume-up", "volume-down", "mute", "wake". */
    fun sendButton(name: String) {
        send("input.button", JSONObject().put("name", name))
    }

    /** Display rotation: "portrait", "landscape-left", "landscape-right", "upside-down". */
    fun sendRotation(orientation: String) {
        send("display.rotation", JSONObject().put("orientation", orientation))
    }

    private fun send(method: String, params: JSONObject) {
        val id = idGen.incrementAndGet()
        val msg = JSONObject()
            .put("id", id)
            .put("method", method)
            .put("params", params)
            .toString()
        ws?.send(msg)
    }
}
