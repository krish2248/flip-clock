package dev.flip.clock.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Timer-finished chime: a soft two-note bell, synthesised once into cacheDir (no bundled
 * asset) and looped by SoundPool on the ALARM stream until [stop] — like any clock app.
 */
class ChimePlayer(context: Context) {

    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    @Volatile private var soundId = 0
    @Volatile private var ready = false
    @Volatile private var pendingPlay = false
    @Volatile private var streamId = 0

    init {
        soundPool.setOnLoadCompleteListener { _, id, status ->
            if (status == 0 && id == soundId) {
                ready = true
                if (pendingPlay) start()
            }
        }
        runCatching { soundId = soundPool.load(ensureWav().absolutePath, 1) }
    }

    fun start() {
        if (!ready) {
            pendingPlay = true
            return
        }
        pendingPlay = false
        if (streamId != 0) return
        streamId = soundPool.play(soundId, 0.8f, 0.8f, 1, -1, 1f)
    }

    fun stop() {
        pendingPlay = false
        val id = streamId
        streamId = 0
        if (id != 0) soundPool.stop(id)
    }

    private fun ensureWav(): File {
        val dir = File(appContext.cacheDir, "sound").apply { mkdirs() }
        val file = File(dir, "timer_chime_v1.wav")
        if (file.exists() && file.length() > 44L) return file
        file.writeBytes(synthesizeChimeWav())
        return file
    }
}

/**
 * 1.6 s loop: E6 then C6 bell strikes (fundamental + inharmonic partials, exponential
 * decay), then silence. Loops seamlessly because each strike has decayed by the end.
 */
internal fun synthesizeChimeWav(sampleRate: Int = 22050): ByteArray {
    val total = sampleRate * 1600 / 1000
    val pcm = DoubleArray(total)
    fun strike(startMs: Int, freq: Double, gain: Double) {
        val start = sampleRate * startMs / 1000
        for (i in start until total) {
            val t = (i - start).toDouble() / sampleRate
            val attack = (t / 0.004).coerceAtMost(1.0)
            val v = sin(2 * PI * freq * t) * exp(-t * 4.2) +
                0.45 * sin(2 * PI * freq * 2.76 * t) * exp(-t * 7.5) +
                0.22 * sin(2 * PI * freq * 5.4 * t) * exp(-t * 12.0)
            pcm[i] += v * attack * gain
        }
    }
    strike(0, 1318.5, 0.42)
    strike(260, 1046.5, 0.42)
    // Fade the tail so the loop point is click-free.
    val fade = sampleRate * 120 / 1000
    for (k in 0 until fade) pcm[total - 1 - k] *= k.toDouble() / fade

    val data = ByteArray(total * 2)
    for (i in 0 until total) {
        val v = (pcm[i].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()
        data[i * 2] = (v and 0xFF).toByte()
        data[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
    }
    return pcmWavHeader(sampleRate, data.size) + data
}
