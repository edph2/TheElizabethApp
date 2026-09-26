package uk.elizabeth.aac.core.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

enum class QualityIssue(val advice: String) {
    TOO_SHORT("The recording is very short. Try again and say the whole phrase."),
    SILENT("Nothing was heard. Check the microphone and try again."),
    TOO_QUIET("The recording is quiet. Move the microphone closer, or speak up a little if you can."),
    CLIPPING("The recording is distorted because it is too loud. Move the microphone a little further away."),
    NOISY("There is a lot of background noise. Try somewhere quieter, with the television and fans off."),
}

data class QualityReport(
    val issues: List<QualityIssue>,
    val peakDbfs: Double,
    val speechDbfs: Double,
    val noiseFloorDbfs: Double,
) {
    val isGood: Boolean get() = issues.isEmpty()
}

/**
 * Checks a recording on the tablet before it is kept, so problems are caught while she can
 * still re-record. Used for message banking and voice banking.
 */
object RecordingQuality {
    private const val FRAME_MS = 20

    fun check(audio: PcmAudio): QualityReport {
        val frame = audio.sampleRate * FRAME_MS / 1000
        val levels = (0 until audio.samples.size / frame).map { rmsDbfs(audio.samples, it * frame, frame) }.sorted()
        val peak = audio.samples.maxOfOrNull { abs(it.toInt()) } ?: 0
        val peakDbfs = toDbfs(peak.toDouble())
        val clipped = audio.samples.count { abs(it.toInt()) >= 32_000 }

        if (levels.isEmpty()) return QualityReport(listOf(QualityIssue.TOO_SHORT), peakDbfs, -120.0, -120.0)
        val noiseFloor = levels[levels.size / 10]            // quietest 10%: pauses between words
        val speech = levels[(levels.size * 9) / 10]          // loudest 10%: speech

        val issues = mutableListOf<QualityIssue>()
        if (audio.durationMs < 400) issues += QualityIssue.TOO_SHORT
        if (speech < -50) {
            issues += QualityIssue.SILENT
        } else {
            if (speech < -32) issues += QualityIssue.TOO_QUIET
            if (clipped > audio.sampleRate / 1000) issues += QualityIssue.CLIPPING
            if (speech - noiseFloor < 20) issues += QualityIssue.NOISY
        }
        return QualityReport(issues, peakDbfs, speech, noiseFloor)
    }

    /** Removes silence at the start and end, keeping a short margin. */
    fun trimSilence(audio: PcmAudio, marginMs: Int = 150): PcmAudio {
        val threshold = 32767 * 0.02
        val first = audio.samples.indexOfFirst { abs(it.toInt()) > threshold }
        if (first < 0) return audio
        val last = audio.samples.indexOfLast { abs(it.toInt()) > threshold }
        val margin = audio.sampleRate * marginMs / 1000
        val from = (first - margin).coerceAtLeast(0)
        val to = (last + margin).coerceAtMost(audio.samples.size - 1)
        return PcmAudio(audio.samples.copyOfRange(from, to + 1), audio.sampleRate)
    }

    private fun rmsDbfs(samples: ShortArray, from: Int, length: Int): Double {
        var sumSquares = 0.0
        for (i in from until from + length) sumSquares += samples[i].toDouble() * samples[i]
        return toDbfs(sqrt(sumSquares / length))
    }

    private fun toDbfs(amplitude: Double): Double =
        if (amplitude <= 0) -120.0 else 20 * log10(amplitude / 32767.0)
}
