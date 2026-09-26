package uk.elizabeth.aac.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16-bit mono PCM audio, the format used for her recordings and for voice training. */
class PcmAudio(val samples: ShortArray, val sampleRate: Int) {
    val durationMs: Long get() = samples.size * 1000L / sampleRate
}

/** Minimal WAV (RIFF, 16-bit PCM, mono) reader and writer. */
object Wav {
    const val DEFAULT_SAMPLE_RATE = 22_050

    fun encode(audio: PcmAudio): ByteArray {
        val dataBytes = audio.samples.size * 2
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(audio.sampleRate).putInt(audio.sampleRate * 2).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(dataBytes)
        audio.samples.forEach { buffer.putShort(it) }
        return buffer.array()
    }

    /** Decodes a 16-bit mono PCM WAV. Throws [IllegalArgumentException] for anything else. */
    fun decode(bytes: ByteArray): PcmAudio {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 12 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "Not a WAV file" }
        var pos = 12
        var sampleRate = 0
        var formatOk = false
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val size = b.getInt(pos + 4)
            require(size >= 0 && pos + 8 + size <= bytes.size) { "Corrupt WAV chunk" }
            when (id) {
                "fmt " -> {
                    val format = b.getShort(pos + 8).toInt()
                    val channels = b.getShort(pos + 10).toInt()
                    sampleRate = b.getInt(pos + 12)
                    val bits = b.getShort(pos + 22).toInt()
                    formatOk = format == 1 && channels == 1 && bits == 16 && sampleRate in 8_000..96_000
                }
                "data" -> {
                    require(formatOk) { "Only 16-bit mono PCM WAV is supported" }
                    val samples = ShortArray(size / 2) { b.getShort(pos + 8 + it * 2) }
                    return PcmAudio(samples, sampleRate)
                }
            }
            pos += 8 + size + (size and 1)
        }
        throw IllegalArgumentException("WAV has no audio data")
    }
}
