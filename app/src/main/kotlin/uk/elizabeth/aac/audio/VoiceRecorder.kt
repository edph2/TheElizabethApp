package uk.elizabeth.aac.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import uk.elizabeth.aac.core.audio.PcmAudio
import uk.elizabeth.aac.core.audio.Wav
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Records her voice as 16-bit mono PCM at 22.05 kHz, the format used for voice training.
 * Audio stays in memory until it is saved encrypted.
 */
class VoiceRecorder {
    @Volatile private var stopRequested = false

    fun requestStop() {
        stopRequested = true
    }

    /**
     * Records until [requestStop] is called, the coroutine is cancelled or [maxMs] passes.
     * [onLevel] receives the input level (0..1) for a level meter. The caller must hold RECORD_AUDIO.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(maxMs: Long = 30_000, onLevel: (Float) -> Unit = {}): PcmAudio = withContext(Dispatchers.IO) {
        stopRequested = false
        val rate = Wav.DEFAULT_SAMPLE_RATE
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, // no automatic gain control: truer to her voice
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, rate),
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "The microphone is not available" }
        val maxSamples = (rate * maxMs / 1000).toInt()
        val out = ShortArray(maxSamples)
        var count = 0
        val chunk = ShortArray(rate / 20)
        try {
            recorder.startRecording()
            while (coroutineContext.isActive && !stopRequested && count < maxSamples) {
                val n = recorder.read(chunk, 0, minOf(chunk.size, maxSamples - count))
                if (n <= 0) break
                chunk.copyInto(out, count, 0, n)
                count += n
                var peak = 0
                for (i in 0 until n) peak = maxOf(peak, abs(chunk[i].toInt()))
                onLevel(peak / 32768f)
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        PcmAudio(out.copyOf(count), rate)
    }
}
