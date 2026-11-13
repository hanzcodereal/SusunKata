package com.hanzdev.susunkata

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager

/**
 * Susun Kata, game susun kata bahasa Indonesia.
 * Pengembang: Hanz Dev Official (hanzzcodee@gmail.com)
 */
class MainActivity : Activity() {
    lateinit var prefs: Prefs
        private set
    lateinit var sfx: Sfx
        private set
    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        sfx = Sfx(this, prefs)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        gameView = GameView(this, this)
        setContentView(gameView)
        applyImmersive()
        loadData()
    }

    /** Memuat kamus dan level di thread terpisah; GameView menampilkan keadaan memuat atau gagal. */
    fun loadData() {
        val app = applicationContext
        Thread {
            val ok = try {
                Words.load(app)
                Words.levels.isNotEmpty() && Words.dict.isNotEmpty()
            } catch (_: Exception) {
                false
            }
            runOnUiThread { gameView.onDataLoaded(ok) }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun applyImmersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    override fun onResume() {
        super.onResume()
        sfx.startMusic()
        gameView.invalidate()
    }

    override fun onPause() {
        sfx.pauseMusic()
        gameView.saveNow()
        super.onPause()
    }

    override fun onDestroy() {
        sfx.release()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!gameView.handleBack()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
