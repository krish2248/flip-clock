package dev.flip.clock.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import java.io.File
import kotlin.random.Random

/**
 * Flip "tick" player (spec §8.5). The click is synthesised at first use as a tiny
 * WAV in cacheDir (8 kHz, 40 ms decaying square wave) and played via SoundPool —
 * no binary asset needed.
 */
class FlipSoundPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var soundId: Int = 0

    @Volatile
    private var ready: Boolean = false

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId == soundId) ready = true
        }
        runCatching {
            soundId = soundPool.load(ensureWavFile().absolutePath, 1)
        }
    }

    fun release() = soundPool.release()

    /**
     * One click, volume 0.35, ±4 % random rate so repeats don't sound robotic.
     * Skipped while the ringer is silent.
     */
    fun play() {
        if (!ready || soundId <= 0) return
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val rate = 0.96f + Random.nextFloat() * 0.08f
        soundPool.play(soundId, 0.35f, 0.35f, 1, 0, rate)
    }

    private fun ensureWavFile(): File {
        val dir = File(appContext.cacheDir, "sound").apply { mkdirs() }
        val file = File(dir, "flip_tick.wav")
        if (file.exists() && file.length() > 44L) return file
        file.writeBytes(synthesizeTickWav())
        return file
    }
}

/** 40 ms, 8 kHz, mono 16-bit PCM square wave with exponential decay. */
internal fun synthesizeTickWav(): ByteArray {
    val sampleRate = 8000
    val durationMs = 40
    val numSamples = sampleRate * durationMs / 1000 // 320
    val data = ByteArray(numSamples * 2)
    var phase = 0.0
    for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples
        val envelope = Math.exp(-t * 9.0)
        phase += 1800.0 * 2.0 * Math.PI / sampleRate
        if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI
        val sample = ((if (Math.sin(phase) >= 0) 1 else -1).toDouble() * envelope * 0.6 * Short.MAX_VALUE).toInt()
        data[i * 2] = (sample and 0xFF).toByte()
        data[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
    }

    val header = ByteArray(44)
    val dataSize = data.size
    val fileSize = 36 + dataSize
    fun putInt(dst: ByteArray, off: Int, v: Int) {
        dst[off] = (v and 0xFF).toByte()
        dst[off + 1] = ((v shr 8) and 0xFF).toByte()
        dst[off + 2] = ((v shr 16) and 0xFF).toByte()
        dst[off + 3] = ((v shr 24) and 0xFF).toByte()
    }
    fun putShort(dst: ByteArray, off: Int, v: Int) {
        dst[off] = (v and 0xFF).toByte()
        dst[off + 1] = ((v shr 8) and 0xFF).toByte()
    }
    // RIFF header
    header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
    putInt(header, 4, fileSize)
    header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
    header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
    putInt(header, 16, 16)          // PCM chunk size
    putShort(header, 20, 1)         // PCM format
    putShort(header, 22, 1)         // mono
    putInt(header, 24, sampleRate)
    putInt(header, 28, sampleRate * 2) // byte rate
    putShort(header, 32, 2)         // block align
    putShort(header, 34, 16)        // bits per sample
    header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
    putInt(header, 40, dataSize)

    return header + data
}
