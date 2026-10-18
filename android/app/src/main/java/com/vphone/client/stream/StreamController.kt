package com.vphone.client.stream

import android.view.Surface
import com.vphone.client.input.InputClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Wires the screen stream, decoder, and input channel together. */
class StreamController(
    private val host: String,
    private val port: Int,
    private val token: String,
    private val codec: String = "h264",
    private val bitrate: Int = 16_000_000,
    private val fps: Int = 60,
    private val box: Int = 2796,
) {
    private val okhttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var decoder: VideoDecoder? = null
    private var screenClient: ScreenStreamClient? = null
    private var inputClient: InputClient? = null

    fun start(surface: Surface, onSize: (Int, Int) -> Unit, onStatus: (String) -> Unit) {
        stop()

        val dec = VideoDecoder(codec, onSize)
        dec.setSurface(surface)
        dec.start()
        decoder = dec

        val input = InputClient(okhttp, host, port, token)
        input.connect()
        inputClient = input

        val screen = ScreenStreamClient(okhttp, host, port, token, codec, bitrate, fps, box)
        screen.onFrame = { frame -> dec.feed(frame) }
        screen.onStatus = onStatus
        screen.connect()
        screenClient = screen
    }

    fun stop() {
        screenClient?.close()
        screenClient = null
        inputClient?.close()
        inputClient = null
        decoder?.stop()
        decoder = null
    }

    fun sendTouch(phase: String, x: Float, y: Float) {
        inputClient?.sendTouch(phase, x, y)
    }

    fun sendTouch2(phase: String, x1: Float, y1: Float, x2: Float, y2: Float) {
        inputClient?.sendTouch2(phase, x1, y1, x2, y2)
    }

    fun sendButton(name: String) {
        inputClient?.sendButton(name)
    }

    fun setRotation(orientation: String) {
        inputClient?.sendRotation(orientation)
    }
}
