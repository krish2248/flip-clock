package dev.flip.clock.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Flip "clack" player (spec §8.5). The sound is synthesised once into a tiny WAV in
 * cacheDir (a filtered noise transient + a low thump, 22.05 kHz, 45 ms) and played via
 * SoundPool — no binary asset is bundled.
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

    @Volatile private var soundId: Int = 0
    @Volatile private var ready: Boolean = false

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId == soundId) ready = true
        }
        runCatching { soundId = soundPool.load(ensureWavFile().absolutePath, 1) }
    }

    /** One clack, volume 0.35, ±4 % random rate so repeats don't sound robotic. */
    fun play() {
        if (!ready || soundId <= 0) return
        // Respect silent / vibrate ringer modes.
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val rate = 0.96f + Random.nextFloat() * 0.08f
        soundPool.play(soundId, 0.35f, 0.35f, 1, 0, rate)
    }

    private fun ensureWavFile(): File {
        val dir = File(appContext.cacheDir, "sound").apply { mkdirs() }
        val file = File(dir, "flip_clack_v2.wav")
        if (file.exists() && file.length() > 44L) return file
        file.writeBytes(synthesizeClackWav())
        return file
    }
}

/** 45 ms mono 16-bit PCM: band-limited noise burst (the flap) + 160 Hz thump (the stop). */
internal fun synthesizeClackWav(sampleRate: Int = 22050): ByteArray {
    val numSamples = sampleRate * 45 / 1000
    val data = ByteArray(numSamples * 2)
    val rng = Random(7) // deterministic: same sound every install
    var lp = 0.0
    var hp = 0.0
    var prev = 0.0
    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        // Noise through a simple one-pole low-pass then high-pass → "papery" mid band.
        val white = rng.nextDouble() * 2.0 - 1.0
        lp += 0.35 * (white - lp)
        hp = 0.92 * (hp + lp - prev)
        prev = lp
        val click = hp * exp(-t * 240.0)
        val thump = sin(2.0 * PI * 160.0 * t) * exp(-t * 90.0) * 0.55
        val attack = (i / (sampleRate * 0.0008)).coerceAtMost(1.0) // 0.8 ms fade-in, no pop
        val s = ((click * 1.6 + thump) * attack * 0.7).coerceIn(-1.0, 1.0)
        val v = (s * Short.MAX_VALUE).toInt()
        data[i * 2] = (v and 0xFF).toByte()
        data[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
    }
    return wavHeader(sampleRate, data.size) + data
}

private fun wavHeader(sampleRate: Int, dataSize: Int): ByteArray {
    val h = ByteArray(44)
    fun putInt(off: Int, v: Int) {
        h[off] = (v and 0xFF).toByte(); h[off + 1] = ((v shr 8) and 0xFF).toByte()
        h[off + 2] = ((v shr 16) and 0xFF).toByte(); h[off + 3] = ((v shr 24) and 0xFF).toByte()
    }
    fun putShort(off: Int, v: Int) {
        h[off] = (v and 0xFF).toByte(); h[off + 1] = ((v shr 8) and 0xFF).toByte()
    }
    fun putTag(off: Int, tag: String) = tag.forEachIndexed { i, c -> h[off + i] = c.code.toByte() }
    putTag(0, "RIFF"); putInt(4, 36 + dataSize); putTag(8, "WAVE")
    putTag(12, "fmt "); putInt(16, 16); putShort(20, 1); putShort(22, 1)
    putInt(24, sampleRate); putInt(28, sampleRate * 2); putShort(32, 2); putShort(34, 16)
    putTag(36, "data"); putInt(40, dataSize)
    return h
}
