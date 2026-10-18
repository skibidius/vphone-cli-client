package com.vphone.client.stream

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import java.util.concurrent.LinkedBlockingQueue

/** One decoded-able frame from the screen stream. */
class VideoFrame(
    val payload: ByteArray,
    val keyframe: Boolean,
    /** Base64-decoded avcC/hvcC record, present on keyframes. */
    val record: ByteArray?,
    /** Changes when codec, size or orientation changes; a new decoder is made. */
    val session: Int,
    val width: Int,
    val height: Int,
)

/**
 * Feeds H.264/HEVC access units to MediaCodec and renders to a Surface.
 * The stream's NAL units are length-prefixed (AVCC/HVCC) and carry no parameter
 * sets, so parameter sets are parsed from the keyframe `record` and prepended
 * in Annex-B form on each keyframe. A dedicated thread runs the MediaCodec
 * drain loop.
 */
class VideoDecoder(
    private val codec: String, // "h264" | "hevc"
    private val onSizeChanged: (Int, Int) -> Unit,
) {
    private val queue = LinkedBlockingQueue<VideoFrame>()

    @Volatile private var surface: Surface? = null
    private var mediaCodec: MediaCodec? = null
    private var config: CodecConfig? = null
    private var currentSession = -1
    @Volatile private var running = false
    private var thread: Thread? = null
    private var ptsCounter = 0L

    private val mime: String
        get() = if (codec == "hevc") MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC

    fun setSurface(s: Surface?) {
        surface = s
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "vphone-decoder").also { it.start() }
    }

    fun stop() {
        if (!running) return
        running = false
        thread?.interrupt()
        // Wake the loop so it observes `running == false` and exits. The codec
        // is released by the decoder thread itself on exit (MediaCodec is not
        // thread-safe, so stop() must not touch it directly).
        queue.offer(VideoFrame(ByteArray(0), false, null, -1, 0, 0))
    }

    fun feed(frame: VideoFrame) {
        queue.offer(frame)
    }

    private fun loop() {
        while (running) {
            val frame = try {
                queue.take()
            } catch (e: InterruptedException) {
                break
            }
            if (frame.session == -1) break
            process(frame)
        }
        releaseCodec()
    }

    private fun process(frame: VideoFrame) {
        if (frame.session != currentSession) {
            releaseCodec()
            currentSession = -1
            config = frame.record?.let { CodecConfigParser.parse(mime, it) }
            if (configure(frame)) {
                currentSession = frame.session
            }
        }
        feedFrame(frame)
    }

    private fun configure(frame: VideoFrame): Boolean {
        val s = surface ?: return false
        val format = MediaFormat.createVideoFormat(mime, frame.width, frame.height)
        return try {
            val mc = MediaCodec.createDecoderByType(mime)
            mc.configure(format, s, null, 0)
            mc.start()
            mediaCodec = mc
            onSizeChanged(frame.width, frame.height)
            true
        } catch (e: Exception) {
            mediaCodec = null
            false
        }
    }

    private fun feedFrame(frame: VideoFrame) {
        val mc = mediaCodec ?: return
        val lengthSize = config?.lengthSize ?: 4
        val nals = CodecConfigParser.splitNals(frame.payload, lengthSize)
        val parameterSets = if (frame.keyframe) config?.parameterSets else null
        val annexB = CodecConfigParser.toAnnexB(parameterSets, nals)
        if (annexB.isEmpty()) return

        val inIndex = mc.dequeueInputBuffer(20_000)
        if (inIndex >= 0) {
            val buf = mc.getInputBuffer(inIndex)
            if (buf != null) {
                buf.clear()
                buf.put(annexB)
                mc.queueInputBuffer(inIndex, 0, annexB.size, ptsCounter++, 0)
            }
        }
        drainOutput()
    }

    private fun drainOutput() {
        val mc = mediaCodec ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIndex = mc.dequeueOutputBuffer(info, 0)
            when {
                outIndex >= 0 -> {
                    mc.releaseOutputBuffer(outIndex, info.size > 0)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> break
            }
        }
    }

    private fun releaseCodec() {
        mediaCodec?.let {
            try { it.stop() } catch (_: Throwable) {}
            try { it.release() } catch (_: Throwable) {}
        }
        mediaCodec = null
    }
}
