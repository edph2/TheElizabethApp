package uk.elizabeth.aac.core.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The attention sound: a two-note bell ("ding-dong"). Generated in code so it works even if
 * speech is broken, with no sound files to go missing.
 */
object Chime {
    fun generate(sampleRate: Int = Wav.DEFAULT_SAMPLE_RATE): PcmAudio {
        val noteSamples = (sampleRate * 0.7).toInt()
        val samples = ShortArray(noteSamples * 2)
        for ((n, frequency) in listOf(659.25, 523.25).withIndex()) {
            for (i in 0 until noteSamples) {
                val t = i.toDouble() / sampleRate
                val envelope = exp(-4.0 * t) * minOf(1.0, i / (sampleRate * 0.005))
                val tone = sin(2 * PI * frequency * t) + 0.4 * sin(2 * PI * frequency * 2 * t) + 0.15 * sin(2 * PI * frequency * 3 * t)
                samples[n * noteSamples + i] = (tone / 1.55 * envelope * 26_000).toInt().toShort()
            }
        }
        return PcmAudio(samples, sampleRate)
    }
}
