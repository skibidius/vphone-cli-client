package com.vphone.client.stream

import android.os.Handler
import android.os.Looper
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Pulls frames from vphone's `/v1/host/screen` WebSocket.
 *
 * The client requests one frame per text message and receives a binary answer:
 * a 4-byte little-endian header length, then a JSON header, then the payload.
 * A small in-flight window keeps the pipeline full without buffering latency.
 */
class ScreenStreamClient(
    private val okhttp: OkHttpClient,
    private val host: String,
    private val port: Int,
    private val token: String,
    private val codec: String,
    private val bitrate: Int,
    private val fps: Int,
    private val box: Int,
) {
    var onFrame: ((VideoFrame) -> Unit)? = null
    var onStatus: ((String) -> Unit)? = null

    private val url = "ws://$host:$port/v1/host/screen?token=$token"
    private val handler = Handler(Looper.getMainLooper())
    private val inflight = AtomicInteger(0)
    private val idGen = AtomicLong(0)

    @Volatile private var ws: WebSocket? = null
    @Volatile private var closed = false
    @Volatile private var reconnectScheduled = false

    private val window = 2

    fun connect() {
        closed = false
        val request = Request.Builder().url(url).build()
        ws = okhttp.newWebSocket(request, listener)
    }

    fun close() {
        closed = true
        ws?.close(1000, null)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            postStatus("Streaming")
            requestFrame(key = true)
            requestFrame(key = false)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            inflight.decrementAndGet()
            handleBinary(bytes.toByteArray())
            requestFrame(key = false)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            postStatus("Connection error: ${t.message}")
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!closed) scheduleReconnect()
        }
    }

    private fun requestFrame(key: Boolean) {
        if (closed || ws == null) return
        if (inflight.get() >= window) return
        val id = idGen.incrementAndGet()
        inflight.incrementAndGet()
        val json = JSONObject()
            .put("id", id)
            .put("codec", codec)
            .put("box", JSONArray().put(box).put(box))
            .put("fps", fps)
            .put("bitrate", bitrate)
            .put("key", key)
            .toString()
        ws?.send(json)
    }

    private fun handleBinary(data: ByteArray) {
        if (data.size < 4) return
        val headerLen = readIntLE(data, 0)
        if (headerLen <= 0 || 4 + headerLen > data.size) return
        val header = String(data, 4, headerLen, Charsets.UTF_8)
        val obj = JSONObject(header)
        if (obj.has("error")) return
        if (obj.optString("status") != "frame") return // "unchanged" / "dropped"

        val payload = data.copyOfRange(4 + headerLen, data.size)
        val record: ByteArray? = if (obj.has("record") && !obj.isNull("record")) {
            Base64.decode(obj.getString("record"), Base64.DEFAULT)
        } else {
            null
        }
        onFrame?.invoke(
            VideoFrame(
                payload = payload,
                keyframe = obj.optBoolean("key", false),
                record = record,
                session = obj.optInt("session", 0),
                width = obj.optInt("width", 0),
                height = obj.optInt("height", 0),
            )
        )
    }

    private fun scheduleReconnect() {
        if (closed || reconnectScheduled) return
        reconnectScheduled = true
        postStatus("Reconnecting…")
        handler.postDelayed({
            reconnectScheduled = false
            if (!closed) connect()
        }, 1500L)
    }

    private fun postStatus(text: String) {
        handler.post { onStatus?.invoke(text) }
    }

    private fun readIntLE(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)
}
