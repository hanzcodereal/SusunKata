package com.hanzdev.susunkata

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/** Efek suara (SoundPool) + musik latar (MediaPlayer). Semua mengikuti pengaturan di Prefs. */
class Sfx(private val ctx: Context, private val prefs: Prefs) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val ids = HashMap<Int, Int>()
    private var player: MediaPlayer? = null

    init {
        val all = intArrayOf(
            R.raw.tick, R.raw.klik, R.raw.pop, R.raw.salah, R.raw.bonus,
            R.raw.koin, R.raw.petunjuk, R.raw.kocok, R.raw.menang
        )
        for (r in all) ids[r] = pool.load(ctx, r, 1)
    }

    fun play(res: Int, rate: Float = 1f, vol: Float = 1f) {
        if (!prefs.sound) return
        val id = ids[res] ?: return
        pool.play(id, vol, vol, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun startMusic() {
        if (!prefs.music) return
        try {
            var p = player
            if (p == null) {
                p = MediaPlayer.create(ctx, R.raw.musik)
                if (p == null) return
                p.isLooping = true
                p.setVolume(0.30f, 0.30f)
                player = p
            }
            if (!p.isPlaying) p.start()
        } catch (_: Exception) {
        }
    }

    fun pauseMusic() {
        try {
            player?.let { if (it.isPlaying) it.pause() }
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        pool.release()
    }
}
