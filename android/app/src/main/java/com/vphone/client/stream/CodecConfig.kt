package com.vphone.client.stream

import java.io.ByteArrayOutputStream

/**
 * Parsed H.264 (avcC) / HEVC (hvcC) decoder configuration record carried in
 * the `record` field of a keyframe header from `/v1/host/screen`.
 */
class CodecConfig(
    val mime: String,
    /** Bytes per NAL length prefix in the frame payload (almost always 4). */
    val lengthSize: Int,
    /** SPS+PPS (AVC) or VPS+SPS+PPS (HEVC), in decode order. */
    val parameterSets: List<ByteArray>,
)

object CodecConfigParser {

    const val MIME_AVC = "video/avc"
    const val MIME_HEVC = "video/hevc"

    fun parse(mime: String, record: ByteArray): CodecConfig? = when (mime) {
        MIME_AVC -> parseAvc(record)
        MIME_HEVC -> parseHevc(record)
        else -> null
    }

    /** ISO/IEC 14496-15 AVCDecoderConfigurationRecord. */
    private fun parseAvc(r: ByteArray): CodecConfig? {
        if (r.size < 7) return null
        val lengthSize = (r[4].toInt() and 0x03) + 1
        val sets = ArrayList<ByteArray>()
        var off = 5
        val numSps = r[off].toInt() and 0x1F
        off += 1
        repeat(numSps) {
            if (off + 2 > r.size) return null
            val len = ((r[off].toInt() and 0xFF) shl 8) or (r[off + 1].toInt() and 0xFF)
            off += 2
            if (off + len > r.size) return null
            sets.add(r.copyOfRange(off, off + len))
            off += len
        }
        if (off >= r.size) return null
        val numPps = r[off].toInt() and 0xFF
        off += 1
        repeat(numPps) {
            if (off + 2 > r.size) return null
            val len = ((r[off].toInt() and 0xFF) shl 8) or (r[off + 1].toInt() and 0xFF)
            off += 2
            if (off + len > r.size) return null
            sets.add(r.copyOfRange(off, off + len))
            off += len
        }
        return CodecConfig(MIME_AVC, lengthSize, sets)
    }

    /** ISO/IEC 14496-15 HEVCDecoderConfigurationRecord. */
    private fun parseHevc(r: ByteArray): CodecConfig? {
        if (r.size < 23) return null
        val lengthSize = (r[21].toInt() and 0x03) + 1
        val numArrays = r[22].toInt() and 0xFF
        val sets = ArrayList<ByteArray>()
        var off = 23
        repeat(numArrays) {
            if (off + 3 > r.size) return null
            val nalType = r[off].toInt() and 0x3F
            off += 1
            val numNalus = ((r[off].toInt() and 0xFF) shl 8) or (r[off + 1].toInt() and 0xFF)
            off += 2
            repeat(numNalus) {
                if (off + 2 > r.size) return null
                val len = ((r[off].toInt() and 0xFF) shl 8) or (r[off + 1].toInt() and 0xFF)
                off += 2
                if (off + len > r.size) return null
                val nal = r.copyOfRange(off, off + len)
                off += len
                // VPS(32), SPS(33), PPS(34)
                if (nalType == 32 || nalType == 33 || nalType == 34) sets.add(nal)
            }
        }
        return CodecConfig(MIME_HEVC, lengthSize, sets)
    }

    /**
     * Splits a frame payload of big-endian length-prefixed NAL units into the
     * individual units. VideoToolbox emits AVCC/HVCC framing, so the length
     * prefix is big-endian (unlike the 4-byte little-endian outer header).
     */
    fun splitNals(payload: ByteArray, lengthSize: Int): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var off = 0
        while (off + lengthSize <= payload.size) {
            var len = 0
            for (i in 0 until lengthSize) {
                len = (len shl 8) or (payload[off + i].toInt() and 0xFF)
            }
            if (len <= 0 || off + lengthSize + len > payload.size) break
            out.add(payload.copyOfRange(off + lengthSize, off + lengthSize + len))
            off += lengthSize + len
        }
        return out
    }

    /** Builds an Annex-B byte stream: optional parameter sets, then the NALs. */
    fun toAnnexB(parameterSets: List<ByteArray>?, nals: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        parameterSets?.forEach { writeStartCode(out); out.write(it) }
        nals.forEach { writeStartCode(out); out.write(it) }
        return out.toByteArray()
    }

    private fun writeStartCode(out: ByteArrayOutputStream) {
        out.write(0)
        out.write(0)
        out.write(0)
        out.write(1)
    }
}
