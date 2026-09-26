package uk.elizabeth.aac.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import uk.elizabeth.aac.core.audio.PcmAudio

/** Plays her recorded phrases and the attention chime straight from memory (no unencrypted files). */
class AudioPlayer {
    private var track: AudioTrack? = null

    val isPlaying: Boolean get() = track?.playState == AudioTrack.PLAYSTATE_PLAYING

    fun play(audio: PcmAudio, loop: Boolean = false, onDone: () -> Unit = {}) {
        stop()
        if (audio.samples.isEmpty()) return onDone()
        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(audio.samples.size * 2)
            .build()
        newTrack.write(audio.samples, 0, audio.samples.size)
        if (loop) {
            newTrack.setLoopPoints(0, audio.samples.size, -1)
        } else {
            newTrack.notificationMarkerPosition = audio.samples.size
            newTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) {
                    if (track === t) stop()
                    onDone()
                }

                override fun onPeriodicNotification(t: AudioTrack) = Unit
            })
        }
        track = newTrack
        newTrack.play()
    }

    fun stop() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }
}
