package com.hanzdev.susunkata

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Seluruh tampilan digambar di satu View. Papan adalah data murni (Level + kata ditemukan);
 * tampilan hanya memutar ulang perubahan itu sebagai animasi. Arah desain ada di DESIGN.md.
 */
@Suppress("DEPRECATION")
class GameView(ctx: Context, private val host: MainActivity) : View(ctx) {

    private enum class Screen { MENU, LEVELS, PLAY, ABOUT }
    private enum class Overlay { NONE, SETTINGS, CONFIRM_RESET, WIN, FINISHED, BONUS }
    private enum class Style { PRIMARY, SECONDARY }

    private class Hit(val id: String, val r: RectF)

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val color: Int, val size: Float,
        val kind: Int, var rot: Float, val vr: Float, var delay: Float
    )

    private val prefs = host.prefs
    private val sfx = host.sfx
    private val rnd = Random()
    private val PIf = Math.PI.toFloat()

    private val GREEN = Color.rgb(0x1F, 0x7A, 0x3A)
    private val GREEN_EDGE = Color.rgb(0x12, 0x4D, 0x25)
    private val CREAM = Color.rgb(0xF5, 0xF0, 0xE1)
    private val CREAM_EDGE = Color.rgb(0xB9, 0xAF, 0x92)
    private val INK = Color.rgb(0x14, 0x21, 0x1A)
    private val INK_PANEL = Color.argb(235, 0x14, 0x21, 0x1A)
    private val GOLD = Color.rgb(0xF2, 0xB7, 0x05)
    private val GOLD_EDGE = Color.rgb(0x8A, 0x65, 0x00)
    private val WHITE = Color.WHITE

    private val COST_TARGET = 50
    private val COST_ONE = 25
    private val COST_FIVE = 100
    private val DAILY_GIFT = 50
    private val WIN_REWARD = 20
    private val BONUS_REWARD = 3

    private val bgIds = intArrayOf(
        R.drawable.bg_terasering, R.drawable.bg_pantai, R.drawable.bg_gunung,
        R.drawable.bg_hutan, R.drawable.bg_danau, R.drawable.bg_malam
    )

    private val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ln = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val tfBold: Typeface = loadFont(ctx, "fonts/Poppins-Bold.ttf", Typeface.DEFAULT_BOLD)
    private val tfMed: Typeface = loadFont(ctx, "fonts/Poppins-Medium.ttf", Typeface.DEFAULT)
    private val rect = RectF()
    private val path = Path()
    private val srcRect = Rect()
    private val dstRect = RectF()
    private val bgCache = HashMap<Int, Bitmap>()
    private val versionName: String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0"
    } catch (_: Exception) {
        "1.0.0"
    }
    private val systemAnimOff: Boolean = try {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (_: Exception) {
        false
    }

    private var loadState = 0 // 0 memuat, 1 siap, 2 gagal
    private var screen = Screen.MENU
    private var overlay = Overlay.NONE
    private var u = 1f
    private var cw = 720f
    private var ox = 0f
    private var insetTop = 0f
    private var insetBottom = 0f
    private val hits = ArrayList<Hit>()
    private val a11yHits = ArrayList<Hit>()
    private var inputOn = true
    private var pressedId: String? = null
    private var focusId: String? = null
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var moved = false
    private var now = SystemClock.uptimeMillis()
    private var lastFrame = now
    private var animUntil = 0L
    private var coinShown = prefs.coins.toFloat()
    private var coinShakeStart = 0L
    private var toastMsg: String? = null
    private var toastUntil = 0L
    private var overlayStart = 0L
    private var menuEnterAt = 0L
    private var scrollY = 0f
    private val parts = ArrayList<Particle>()

    private var lv: Level? = null
    private var levelIdx = 0
    private var letters = CharArray(0)
    private var slotOf = IntArray(0)
    private var prevSlot = IntArray(0)
    private var selStart = LongArray(0)
    private var shuffleStart = 0L
    private var gridCh = Array(0) { CharArray(0) }
    private var revealed = Array(0) { BooleanArray(0) }
    private var cellStart = Array(0) { LongArray(0) }
    private val found = HashSet<String>()
    private val bonusFound = LinkedHashSet<String>()
    private val sel = ArrayList<Int>()
    private var swiping = false
    private var fingerX = 0f
    private var fingerY = 0f
    private var previewText = ""
    private var previewKind = 0 // 0 kosong, 1 mengetik, 2 benar, 3 bonus, 4 salah
    private var previewUntil = 0L
    private var shakeStart = 0L
    private var pickMode = false
    private var levelDone = false
    private var winScheduled = false
    private var winReward = 0
    private var pulseWord: PlacedWord? = null
    private var pulseStart = 0L

    private var topY = 0f
    private var wheelCx = 0f
    private var wheelCy = 0f
    private var wheelR = 0f
    private var bottomRowY = 0f
    private var previewY = 0f
    private var gx0 = 0f
    private var gy0 = 0f
    private var cs = 0f
    private val panelRect = RectF()
    private val tmpP = PointF()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setOnApplyWindowInsetsListener { _, ins ->
            readInsets(ins)
            ins
        }
    }

    private fun loadFont(ctx: Context, file: String, fallback: Typeface): Typeface =
        try {
            Typeface.createFromAsset(ctx.assets, file)
        } catch (_: Exception) {
            fallback
        }

    private fun readInsets(ins: WindowInsets) {
        var t = ins.systemWindowInsetTop
        var b = ins.systemWindowInsetBottom
        if (Build.VERSION.SDK_INT >= 28) {
            val cut = ins.displayCutout
            if (cut != null) {
                t = max(t, cut.safeInsetTop)
                b = max(b, cut.safeInsetBottom)
            }
        }
        insetTop = t.toFloat()
        insetBottom = b.toFloat()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
        requestFocus()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Skala mengikuti sisi yang lebih sempit; layar lebar memusatkan konten 720 unit.
        u = min(w / 720f, h / 1100f)
        cw = 720f * u
        ox = (w - cw) / 2f
    }

    private fun d(v: Float) = v * u
    private fun ms() = SystemClock.uptimeMillis()
    private fun calm() = prefs.reduceFx || systemAnimOff

    private fun keepAnim(durMs: Long) {
        animUntil = max(animUntil, ms() + durMs)
        postInvalidateOnAnimation()
    }

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val x = t - 1f
        return 1f + c3 * x * x * x + c1 * x * x
    }

    private fun easeOutCubic(t: Float): Float {
        val x = 1f - t
        return 1f - x * x * x
    }

    fun onDataLoaded(ok: Boolean) {
        loadState = if (ok) 1 else 2
        if (ok) enterMenu()
        announceScreen()
        invalidate()
    }

    private fun enterMenu() {
        screen = Screen.MENU
        overlay = Overlay.NONE
        menuEnterAt = ms()
        keepAnim(1100)
    }

    private fun goMenu() {
        saveNow()
        pickMode = false
        enterMenu()
        invalidate()
    }

    private fun openLevels() {
        screen = Screen.LEVELS
        overlay = Overlay.NONE
        val done = min(prefs.maxLevel, Words.levels.size - 1)
        scrollY = max(0f, (done / 5) * d(128f) - d(300f))
        invalidate()
    }

    private fun startLevel(i: Int, restore: Boolean) {
        val idx = i.coerceIn(0, Words.levels.size - 1)
        levelIdx = idx
        val l = Words.levels[idx]
        lv = l
        letters = l.letters.toCharArray()
        val n = letters.size
        slotOf = IntArray(n) { it }
        prevSlot = IntArray(n) { it }
        selStart = LongArray(n)
        scrambleSlots(animate = false)
        gridCh = Array(l.rows) { CharArray(l.cols) }
        for (w in l.words) {
            for (k in w.text.indices) {
                val r = w.row + (if (w.horiz) 0 else k)
                val c = w.col + (if (w.horiz) k else 0)
                gridCh[r][c] = w.text[k]
            }
        }
        revealed = Array(l.rows) { BooleanArray(l.cols) }
        cellStart = Array(l.rows) { LongArray(l.cols) }
        found.clear()
        bonusFound.clear()
        sel.clear()
        swiping = false
        previewKind = 0
        previewText = ""
        pickMode = false
        levelDone = false
        winScheduled = false
        pulseWord = null
        if (restore) loadProgress()
        prefs.currentLevel = idx
        screen = Screen.PLAY
        overlay = Overlay.NONE
        announceScreen()
        invalidate()
    }

    private fun scrambleSlots(animate: Boolean) {
        val n = letters.size
        if (n < 2) return
        for (i in 0 until n) prevSlot[i] = slotOf[i]
        val before = slotOf.copyOf()
        var tries = 0
        do {
            val perm = IntArray(n) { it }
            for (i in n - 1 downTo 1) {
                val j = rnd.nextInt(i + 1)
                val tmp = perm[i]; perm[i] = perm[j]; perm[j] = tmp
            }
            for (i in 0 until n) slotOf[i] = perm[i]
            tries++
        } while (slotOf.contentEquals(before) && tries < 20)
        if (animate && !calm()) {
            shuffleStart = ms()
            keepAnim(450)
        } else {
            for (i in 0 until n) prevSlot[i] = slotOf[i]
            shuffleStart = 0L
        }
    }

    // Progres disimpan sebagai data murni (indeks, kata, sel terbuka); versi skema ada di Prefs.
    fun saveNow() {
        if (screen != Screen.PLAY || lv == null || levelDone) return
        try {
            val o = JSONObject()
            o.put("l", levelIdx)
            o.put("f", JSONArray(found.toList()))
            val rv = JSONArray()
            for (r in revealed.indices) for (c in revealed[r].indices) if (revealed[r][c]) rv.put(r * 100 + c)
            o.put("r", rv)
            o.put("b", JSONArray(bonusFound.toList()))
            prefs.progress = o.toString()
        } catch (_: Exception) {
        }
    }

    private fun loadProgress() {
        val s = prefs.progress ?: return
        try {
            val o = JSONObject(s)
            if (o.getInt("l") != levelIdx) return
            val fa = o.getJSONArray("f")
            for (i in 0 until fa.length()) found.add(fa.getString(i))
            val ra = o.getJSONArray("r")
            for (i in 0 until ra.length()) {
                val v = ra.getInt(i)
                val r = v / 100
                val c = v % 100
                if (r in revealed.indices && c in revealed[r].indices) revealed[r][c] = true
            }
            val ba = o.getJSONArray("b")
            for (i in 0 until ba.length()) bonusFound.add(ba.getString(i))
        } catch (_: Exception) {
            found.clear()
            bonusFound.clear()
            for (row in revealed) java.util.Arrays.fill(row, false)
        }
    }

    private fun submit(word: String) {
        val l = lv ?: return
        if (word.length < 3) {
            reject("Minimal 3 huruf")
            return
        }
        val placed = l.words.firstOrNull { it.text == word }
        if (placed != null) {
            if (found.contains(word)) {
                reject("Sudah ditemukan")
                pulseWord = placed
                pulseStart = ms()
                keepAnim(600)
            } else {
                var k = 0
                for (i in word.indices) {
                    val r = placed.row + (if (placed.horiz) 0 else i)
                    val c = placed.col + (if (placed.horiz) i else 0)
                    if (!revealed[r][c]) {
                        revealCell(r, c, k * 0.07f)
                        k++
                    }
                }
                previewText = word
                previewKind = 2
                previewUntil = ms() + 700
                keepAnim(900)
                sfx.play(R.raw.pop, 1f + 0.03f * min(found.size, 10))
                haptic(false)
                afterProgress()
            }
        } else if (Words.dict.contains(word)) {
            if (bonusFound.add(word)) {
                prefs.coins = prefs.coins + BONUS_REWARD
                prefs.totalBonus = prefs.totalBonus + 1
                previewText = word
                previewKind = 3
                previewUntil = ms() + 800
                showToast("Kata bonus, +$BONUS_REWARD koin")
                sfx.play(R.raw.bonus)
                sfx.play(R.raw.koin, 1f, 0.6f)
                haptic(false)
                burst(cw / 2f, previewY, 14, intArrayOf(GOLD, CREAM), d(300f), 0, 0f)
                keepAnim(900)
                saveNow()
            } else {
                reject("Sudah ditemukan")
            }
        } else {
            reject("Kata tidak ditemukan")
        }
        invalidate()
    }

    private fun reject(msg: String) {
        previewKind = 4
        previewUntil = ms() + 650
        shakeStart = ms()
        showToast(msg)
        sfx.play(R.raw.salah)
        haptic(true)
        keepAnim(750)
    }

    private fun revealCell(r: Int, c: Int, delaySec: Float) {
        if (revealed[r][c]) return
        revealed[r][c] = true
        cellStart[r][c] = if (calm()) 0L else ms() + (delaySec * 1000f).toLong()
        layoutPlay()
        val rc = cellRect(r, c)
        burst(rc.centerX(), rc.centerY(), 7, intArrayOf(GOLD, CREAM), d(240f), 0, delaySec)
        keepAnim((delaySec * 1000f).toLong() + 600)
    }

    private fun checkWords(): Int {
        val l = lv ?: return 0
        var n = 0
        for (w in l.words) {
            if (found.contains(w.text)) continue
            var all = true
            for (i in w.text.indices) {
                val r = w.row + (if (w.horiz) 0 else i)
                val c = w.col + (if (w.horiz) i else 0)
                if (!revealed[r][c]) {
                    all = false
                    break
                }
            }
            if (all) {
                found.add(w.text)
                n++
            }
        }
        return n
    }

    private fun afterProgress() {
        checkWords()
        saveNow()
        val l = lv ?: return
        if (found.size == l.words.size) scheduleWin()
    }

    private fun scheduleWin() {
        if (winScheduled) return
        winScheduled = true
        val idx = levelIdx
        postDelayed({
            winScheduled = false
            if (screen == Screen.PLAY && levelIdx == idx && !levelDone) showWin()
        }, if (calm()) 300L else 1000L)
    }

    private fun showWin() {
        levelDone = true
        val last = levelIdx >= Words.levels.size - 1
        winReward = 0
        if (levelIdx >= prefs.maxLevel) {
            winReward = WIN_REWARD
            prefs.coins = prefs.coins + winReward
            prefs.maxLevel = levelIdx + 1
        }
        prefs.progress = null
        overlay = if (last) Overlay.FINISHED else Overlay.WIN
        overlayStart = ms()
        sfx.play(R.raw.menang)
        haptic(true)
        for (k in 0 until 3) {
            burst(cw * (0.2f + 0.3f * k), height * 0.28f, 28, intArrayOf(GOLD, GREEN, CREAM), d(520f), 1, k * 0.12f)
        }
        keepAnim(2000)
        announceScreen()
        invalidate()
    }

    private fun spend(cost: Int): Boolean {
        if (prefs.coins >= cost) {
            prefs.coins = prefs.coins - cost
            return true
        }
        showToast("Koin tidak cukup")
        coinShakeStart = ms()
        sfx.play(R.raw.salah, 0.8f, 0.7f)
        keepAnim(600)
        return false
    }

    private fun hiddenCells(): ArrayList<IntArray> {
        val out = ArrayList<IntArray>()
        for (r in gridCh.indices) for (c in gridCh[r].indices) {
            if (gridCh[r][c] != '\u0000' && !revealed[r][c]) out.add(intArrayOf(r, c))
        }
        return out
    }

    private fun hintRandom(count: Int, cost: Int) {
        val cells = hiddenCells()
        if (cells.isEmpty()) {
            showToast("Semua huruf sudah terbuka")
            return
        }
        if (!spend(cost)) return
        var k = 0
        while (k < count && cells.isNotEmpty()) {
            val pick = cells.removeAt(rnd.nextInt(cells.size))
            revealCell(pick[0], pick[1], k * 0.12f)
            k++
        }
        sfx.play(R.raw.petunjuk)
        haptic(false)
        afterProgress()
    }

    private fun toggleTarget() {
        if (pickMode) {
            pickMode = false
            return
        }
        if (hiddenCells().isEmpty()) {
            showToast("Semua huruf sudah terbuka")
            return
        }
        if (prefs.coins < COST_TARGET) {
            spend(COST_TARGET)
            return
        }
        pickMode = true
        showToast("Ketuk kotak yang ingin dibuka")
        sfx.play(R.raw.klik)
    }

    private fun pickCell(r: Int, c: Int) {
        pickMode = false
        if (revealed[r][c]) return
        if (!spend(COST_TARGET)) return
        revealCell(r, c, 0f)
        sfx.play(R.raw.petunjuk)
        haptic(false)
        afterProgress()
    }

    private fun today(): Long = System.currentTimeMillis() / 86_400_000L

    private fun dailyAvailable(): Boolean = prefs.lastDaily != today()

    private fun claimDaily() {
        if (dailyAvailable()) {
            prefs.lastDaily = today()
            prefs.coins = prefs.coins + DAILY_GIFT
            showToast("Hadiah harian, +$DAILY_GIFT koin")
            sfx.play(R.raw.koin)
            haptic(false)
            burst(cw - d(72f), bottomRowY - d(300f), 16, intArrayOf(GOLD, CREAM), d(320f), 0, 0f)
            keepAnim(900)
        } else {
            showToast("Hadiah harian sudah diambil, kembali besok")
            sfx.play(R.raw.klik)
        }
    }

    private fun doShuffle() {
        if (letters.size < 2) return
        sel.clear()
        swiping = false
        scrambleSlots(animate = true)
        sfx.play(R.raw.kocok)
    }

    private fun haptic(strong: Boolean) {
        if (!prefs.vibrate) return
        performHapticFeedback(if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun showToast(msg: String) {
        toastMsg = msg
        toastUntil = ms() + 1800
        keepAnim(1900)
        announceForAccessibility(msg)
    }

    private fun burst(x: Float, y: Float, count: Int, colors: IntArray, speed: Float, kind: Int, delay: Float) {
        if (calm()) return
        for (i in 0 until count) {
            val a = rnd.nextFloat() * 2f * PIf
            val s = speed * (0.35f + rnd.nextFloat() * 0.65f)
            val vy = if (kind == 1) -abs(sin(a)) * s - d(260f) else sin(a) * s
            val life = if (kind == 1) 1.6f + rnd.nextFloat() * 0.8f else 0.5f + rnd.nextFloat() * 0.35f
            parts.add(
                Particle(
                    x, y, cos(a) * s, vy, life, life, colors[rnd.nextInt(colors.size)],
                    d(if (kind == 1) 9f + rnd.nextFloat() * 8f else 5f + rnd.nextFloat() * 5f),
                    kind, rnd.nextFloat() * 6f, (rnd.nextFloat() - 0.5f) * 14f, delay
                )
            )
        }
        postInvalidateOnAnimation()
    }

    private fun updateParticles(dt: Float) {
        val iter = parts.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            if (p.delay > 0f) {
                p.delay -= dt
                continue
            }
            p.life -= dt
            if (p.life <= 0f) {
                iter.remove()
                continue
            }
            p.vy += d(if (p.kind == 1) 780f else 520f) * dt
            p.vx *= (1f - 0.8f * dt)
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rot += p.vr * dt
        }
    }

    private fun drawParticles(c: Canvas) {
        for (p in parts) {
            if (p.delay > 0f) continue
            val a = (255f * (p.life / p.maxLife).coerceIn(0f, 1f)).toInt()
            fp.color = (p.color and 0x00FFFFFF) or (a shl 24)
            if (p.kind == 1) {
                c.save()
                c.rotate(p.rot * 57.29578f, p.x, p.y)
                c.drawRect(p.x - p.size, p.y - p.size * 0.5f, p.x + p.size, p.y + p.size * 0.5f, fp)
                c.restore()
            } else {
                c.drawCircle(p.x, p.y, p.size * (0.4f + 0.6f * p.life / p.maxLife), fp)
            }
        }
    }

    private fun updateCoins(dt: Float) {
        val target = prefs.coins.toFloat()
        if (coinShown != target) {
            val diff = target - coinShown
            val step = max(abs(diff) * min(1f, dt * 6f), d(60f) * dt)
            coinShown = if (abs(diff) <= step || calm()) target else coinShown + sign(diff) * step
        }
    }

    override fun onDraw(c: Canvas) {
        now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0L, 50L) / 1000f
        lastFrame = now
        hits.clear()
        updateParticles(dt)
        updateCoins(dt)

        if (loadState != 1) {
            fp.color = INK
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fp)
            c.save()
            c.translate(ox, 0f)
            drawLoadState(c)
            c.restore()
            publishHits()
            return
        }

        when (screen) {
            Screen.MENU -> drawBg(c, 0, 0.62f)
            Screen.LEVELS -> drawBg(c, 2, 0.62f)
            Screen.ABOUT -> drawBg(c, 3, 0.62f)
            Screen.PLAY -> drawBg(c, levelIdx / 3, 0.12f)
        }
        c.save()
        c.translate(ox, 0f)
        inputOn = overlay == Overlay.NONE
        when (screen) {
            Screen.MENU -> drawMenu(c)
            Screen.LEVELS -> drawLevels(c)
            Screen.PLAY -> drawPlay(c)
            Screen.ABOUT -> drawAbout(c)
        }
        inputOn = true
        when (overlay) {
            Overlay.SETTINGS -> drawSettings(c)
            Overlay.CONFIRM_RESET -> drawConfirm(c)
            Overlay.WIN -> drawWin(c)
            Overlay.FINISHED -> drawFinished(c)
            Overlay.BONUS -> drawBonus(c)
            Overlay.NONE -> {}
        }
        drawToast(c)
        drawParticles(c)
        drawFocusRing(c)
        c.restore()
        publishHits()

        if (now < animUntil || parts.isNotEmpty() || coinShown != prefs.coins.toFloat()) {
            postInvalidateOnAnimation()
        }
    }

    private fun publishHits() {
        a11yHits.clear()
        a11yHits.addAll(hits)
    }

    private fun hit(id: String, r: RectF) {
        if (inputOn) hits.add(Hit(id, RectF(r)))
    }

    private fun hitAt(x: Float, y: Float): String? {
        for (i in hits.indices.reversed()) {
            if (hits[i].r.contains(x, y)) return hits[i].id
        }
        return null
    }

    private fun bg(index: Int): Bitmap {
        val k = ((index % bgIds.size) + bgIds.size) % bgIds.size
        return bgCache.getOrPut(k) { BitmapFactory.decodeResource(resources, bgIds[k]) }
    }

    private fun drawBg(c: Canvas, index: Int, inkAlpha: Float) {
        val b = bg(index)
        val w = width.toFloat()
        val h = height.toFloat()
        val sc = max(w / b.width, h / b.height)
        val sw = w / sc
        val sh = h / sc
        srcRect.set(
            ((b.width - sw) / 2f).toInt(), ((b.height - sh) / 2f).toInt(),
            ((b.width + sw) / 2f).toInt(), ((b.height + sh) / 2f).toInt()
        )
        dstRect.set(0f, 0f, w, h)
        c.drawBitmap(b, srcRect, dstRect, bmpPaint)
        // Penutup rata: teks di atas foto selalu berada pada latar yang sudah dihitung kontrasnya.
        fp.color = Color.argb((inkAlpha * 255f).toInt(), 0x14, 0x21, 0x1A)
        c.drawRect(0f, 0f, w, h, fp)
    }

    private fun drawText(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        tf: Typeface = tfMed, align: Paint.Align = Paint.Align.CENTER
    ) {
        tp.typeface = tf
        tp.textSize = size
        tp.color = color
        tp.textAlign = align
        val fm = tp.fontMetrics
        c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, tp)
    }

    private fun wrap(s: String, size: Float, maxW: Float, tf: Typeface = tfMed): List<String> {
        tp.typeface = tf
        tp.textSize = size
        val out = ArrayList<String>()
        var cur = ""
        for (word in s.split(" ")) {
            val t = if (cur.isEmpty()) word else "$cur $word"
            if (cur.isEmpty() || tp.measureText(t) <= maxW) {
                cur = t
            } else {
                out.add(cur)
                cur = word
            }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float) {
        c.drawRoundRect(l, t, r, b, rad, rad, fp)
    }

    /** Ubin bertepi tebal: tepi bawah rata berwarna lebih gelap, tanpa bayangan buram. */
    private fun tile(c: Canvas, l: Float, t: Float, r: Float, b: Float, face: Int, edge: Int, pressed: Boolean = false) {
        val rad = min(d(14f), (b - t) * 0.22f)
        val e = max(d(4f), (b - t) * 0.07f)
        val dy = if (pressed) e else 0f
        if (!pressed) {
            fp.color = edge
            rr(c, l, t + e, r, b + e, rad)
        }
        fp.color = face
        rr(c, l, t + dy, r, b + dy, rad)
    }

    private fun button(c: Canvas, id: String, r: RectF, label: String, style: Style = Style.SECONDARY, size: Float = d(36f)) {
        val pr = pressedId == id
        val face = if (style == Style.PRIMARY) GREEN else CREAM
        val edge = if (style == Style.PRIMARY) GREEN_EDGE else CREAM_EDGE
        tile(c, r.left, r.top, r.right, r.bottom, face, edge, pr)
        val dy = if (pr) max(d(4f), r.height() * 0.07f) else 0f
        drawText(c, label, r.centerX(), r.centerY() + dy, size, if (style == Style.PRIMARY) WHITE else INK, tfBold)
        hit(id, r)
    }

    private fun coin(c: Canvas, cx: Float, cy: Float, r: Float) {
        fp.color = GOLD
        c.drawCircle(cx, cy, r, fp)
        ln.color = GOLD_EDGE
        ln.strokeWidth = r * 0.16f
        c.drawCircle(cx, cy, r * 0.9f, ln)
        ln.strokeWidth = r * 0.1f
        c.drawCircle(cx, cy, r * 0.58f, ln)
    }

    private fun bulb(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fp.color = color
        c.drawCircle(cx, cy - r * 0.2f, r * 0.55f, fp)
        rr(c, cx - r * 0.28f, cy + r * 0.34f, cx + r * 0.28f, cy + r * 0.68f, r * 0.1f)
        path.reset()
        path.moveTo(cx - r * 0.34f, cy + r * 0.1f)
        path.lineTo(cx + r * 0.34f, cy + r * 0.1f)
        path.lineTo(cx + r * 0.26f, cy + r * 0.38f)
        path.lineTo(cx - r * 0.26f, cy + r * 0.38f)
        path.close()
        c.drawPath(path, fp)
    }

    /** kind: 0 roda gigi, 1 panah kiri, 2 target, 3 bohlam, 4 tiga bohlam, 5 acak, 6 hadiah, 7 gembok, 8 centang */
    private fun icon(c: Canvas, kind: Int, cx: Float, cy: Float, r: Float, color: Int) {
        ln.color = color
        fp.color = color
        when (kind) {
            0 -> {
                ln.strokeWidth = r * 0.28f
                c.drawCircle(cx, cy, r * 0.5f, ln)
                ln.strokeWidth = r * 0.3f
                for (k in 0 until 8) {
                    val a = k * PIf / 4f
                    c.drawLine(cx + cos(a) * r * 0.72f, cy + sin(a) * r * 0.72f, cx + cos(a) * r * 0.98f, cy + sin(a) * r * 0.98f, ln)
                }
            }
            1 -> {
                ln.strokeWidth = r * 0.3f
                c.drawLine(cx + r * 0.7f, cy, cx - r * 0.7f, cy, ln)
                c.drawLine(cx - r * 0.7f, cy, cx - r * 0.1f, cy - r * 0.6f, ln)
                c.drawLine(cx - r * 0.7f, cy, cx - r * 0.1f, cy + r * 0.6f, ln)
            }
            2 -> {
                ln.strokeWidth = r * 0.17f
                c.drawCircle(cx, cy, r * 0.6f, ln)
                c.drawCircle(cx, cy, r * 0.16f, fp)
                c.drawLine(cx, cy - r * 0.35f, cx, cy - r * 0.98f, ln)
                c.drawLine(cx, cy + r * 0.35f, cx, cy + r * 0.98f, ln)
                c.drawLine(cx - r * 0.35f, cy, cx - r * 0.98f, cy, ln)
                c.drawLine(cx + r * 0.35f, cy, cx + r * 0.98f, cy, ln)
            }
            3 -> bulb(c, cx, cy, r * 1.15f, color)
            4 -> {
                bulb(c, cx - r * 0.62f, cy + r * 0.08f, r * 0.7f, color)
                bulb(c, cx + r * 0.62f, cy + r * 0.08f, r * 0.7f, color)
                bulb(c, cx, cy - r * 0.08f, r * 0.95f, color)
            }
            5 -> {
                ln.strokeWidth = r * 0.2f
                path.reset()
                path.moveTo(cx - r * 0.85f, cy - r * 0.45f)
                path.cubicTo(cx - r * 0.1f, cy - r * 0.45f, cx, cy + r * 0.45f, cx + r * 0.6f, cy + r * 0.45f)
                c.drawPath(path, ln)
                path.reset()
                path.moveTo(cx - r * 0.85f, cy + r * 0.45f)
                path.cubicTo(cx - r * 0.1f, cy + r * 0.45f, cx, cy - r * 0.45f, cx + r * 0.6f, cy - r * 0.45f)
                c.drawPath(path, ln)
                for (s in intArrayOf(-1, 1)) {
                    val y = cy + s * r * 0.45f
                    path.reset()
                    path.moveTo(cx + r * 1.0f, y)
                    path.lineTo(cx + r * 0.58f, y - r * 0.3f)
                    path.lineTo(cx + r * 0.58f, y + r * 0.3f)
                    path.close()
                    c.drawPath(path, fp)
                }
            }
            6 -> {
                rr(c, cx - r * 0.7f, cy - r * 0.02f, cx + r * 0.7f, cy + r * 0.78f, r * 0.1f)
                rr(c, cx - r * 0.82f, cy - r * 0.46f, cx + r * 0.82f, cy - r * 0.02f, r * 0.1f)
                fp.color = INK
                c.drawRect(cx - r * 0.1f, cy - r * 0.46f, cx + r * 0.1f, cy + r * 0.78f, fp)
                ln.strokeWidth = r * 0.14f
                ln.color = color
                c.drawCircle(cx - r * 0.3f, cy - r * 0.68f, r * 0.24f, ln)
                c.drawCircle(cx + r * 0.3f, cy - r * 0.68f, r * 0.24f, ln)
            }
            7 -> {
                rr(c, cx - r * 0.55f, cy - r * 0.05f, cx + r * 0.55f, cy + r * 0.72f, r * 0.12f)
                ln.strokeWidth = r * 0.17f
                rect.set(cx - r * 0.34f, cy - r * 0.78f, cx + r * 0.34f, cy + r * 0.2f)
                c.drawArc(rect, 180f, 180f, false, ln)
                c.drawLine(cx - r * 0.34f, cy - r * 0.3f, cx - r * 0.34f, cy, ln)
                c.drawLine(cx + r * 0.34f, cy - r * 0.3f, cx + r * 0.34f, cy, ln)
            }
            8 -> {
                ln.strokeWidth = r * 0.28f
                c.drawLine(cx - r * 0.6f, cy + r * 0.05f, cx - r * 0.15f, cy + r * 0.5f, ln)
                c.drawLine(cx - r * 0.15f, cy + r * 0.5f, cx + r * 0.65f, cy - r * 0.45f, ln)
            }
        }
    }

    private fun circleBtn(c: Canvas, id: String, cx: Float, cy: Float, r: Float, kind: Int, ring: Int = CREAM) {
        val sc = if (pressedId == id) 0.92f else 1f
        c.save()
        c.scale(sc, sc, cx, cy)
        fp.color = INK_PANEL
        c.drawCircle(cx, cy, r, fp)
        ln.color = ring
        ln.strokeWidth = d(4f)
        c.drawCircle(cx, cy, r, ln)
        icon(c, kind, cx, cy, r * 0.56f, CREAM)
        c.restore()
        rect.set(cx - r - d(10f), cy - r - d(10f), cx + r + d(10f), cy + r + d(10f))
        hit(id, rect)
    }

    private fun costPill(c: Canvas, cx: Float, cy: Float, label: String, w: Float) {
        val h = d(36f)
        fp.color = CREAM
        rr(c, cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, d(10f))
        coin(c, cx - w / 2f + d(20f), cy, d(13f))
        drawText(c, label, cx + d(14f), cy, d(24f), INK, tfBold)
    }

    private fun iconButtonWithCost(c: Canvas, id: String, cx: Float, cy: Float, kind: Int, label: String, highlight: Boolean = false) {
        val r = d(44f)
        circleBtn(c, id, cx, cy, r, kind, if (highlight) GOLD else CREAM)
        if (highlight) {
            ln.color = GOLD
            ln.strokeWidth = d(4f)
            c.drawCircle(cx, cy, r + d(6f), ln)
        }
        costPill(c, cx, cy + r + d(28f), label, d(108f))
        rect.set(cx - d(52f), cy - r - d(8f), cx + d(52f), cy + r + d(52f))
        hit(id, rect)
    }

    private fun drawCoinPill(c: Canvas) {
        val pw = d(220f)
        val ph = d(64f)
        var shake = 0f
        val t = (now - coinShakeStart) / 450f
        if (t in 0f..1f && !calm()) shake = sin(t * 40f) * d(8f) * (1f - t)
        val cx = cw - d(24f) - pw / 2f + shake
        val cy = topY
        fp.color = INK_PANEL
        rr(c, cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f, d(14f))
        coin(c, cx - pw / 2f + d(38f), cy, d(24f))
        drawText(c, coinShown.roundToInt().toString(), cx + d(26f), cy, d(34f), GOLD, tfBold)
    }

    private fun titlePanel(c: Canvas, s: String, cx: Float) {
        tp.typeface = tfBold
        tp.textSize = d(36f)
        val w = tp.measureText(s) + d(56f)
        fp.color = INK_PANEL
        rr(c, cx - w / 2f, topY - d(32f), cx + w / 2f, topY + d(32f), d(14f))
        drawText(c, s, cx, topY, d(36f), CREAM, tfBold)
    }

    private fun drawLoadState(c: Canvas) {
        val h = height.toFloat()
        if (loadState == 0) {
            drawText(c, "Memuat kata...", cw / 2f, h / 2f, d(40f), CREAM, tfBold)
        } else {
            drawText(c, "Data game gagal dimuat", cw / 2f, h * 0.38f, d(42f), CREAM, tfBold)
            var y = h * 0.38f + d(70f)
            for (s in wrap("Pasang ulang aplikasi, atau kirim email ke hanzzcodee@gmail.com bila masalah berlanjut.", d(30f), cw - d(120f))) {
                drawText(c, s, cw / 2f, y, d(30f), CREAM, tfMed)
                y += d(42f)
            }
            inputOn = true
            button(c, "r:retry", RectF(cw / 2f - d(200f), y + d(30f), cw / 2f + d(200f), y + d(30f) + d(96f)), "Coba lagi", Style.PRIMARY, d(36f))
        }
    }

    private fun tilesRow(c: Canvas, word: String, cx: Float, cy: Float, size: Float, green: Boolean, delayMs: Long) {
        val gap = size * 0.12f
        val total = word.length * size + (word.length - 1) * gap
        val x0 = cx - total / 2f
        for (k in word.indices) {
            var offY = 0f
            if (!calm()) {
                val t = ((now - menuEnterAt - delayMs - k * 70L) / 450f).coerceIn(0f, 1f)
                offY = -d(280f) * (1f - easeOutBack(t))
            }
            val l = x0 + k * (size + gap)
            val t0 = cy - size / 2f + offY
            tile(c, l, t0, l + size, t0 + size, if (green) GREEN else CREAM, if (green) GREEN_EDGE else CREAM_EDGE)
            drawText(c, word[k].toString(), l + size / 2f, t0 + size / 2f, size * 0.62f, if (green) WHITE else INK, tfBold)
        }
    }

    private fun drawMenu(c: Canvas) {
        val h = height.toFloat()
        topY = max(insetTop, d(20f)) + d(48f)
        drawCoinPill(c)
        val y1 = h * 0.21f
        val ts = min(d(100f), (cw - d(60f)) / 5.6f)
        tilesRow(c, "SUSUN", cw / 2f, y1, ts, true, 0L)
        tilesRow(c, "KATA", cw / 2f, y1 + ts * 1.3f, ts, false, 200L)
        drawText(c, "Geser huruf, susun kata.", cw / 2f, y1 + ts * 1.3f + ts * 1.1f, d(32f), CREAM, tfMed)

        val bw = min(d(520f), cw - d(80f))
        val done = prefs.maxLevel
        val cur = min(prefs.currentLevel, min(done, Words.levels.size - 1))
        val label = if (cur == 0 && prefs.progress == null) "Mulai bermain" else "Lanjut tingkat ${cur + 1}"
        var y = h * 0.5f
        button(c, "m:play", RectF((cw - bw) / 2f, y, (cw + bw) / 2f, y + d(120f)), label, Style.PRIMARY, d(40f))
        y += d(120f) + d(36f)
        button(c, "m:levels", RectF((cw - bw) / 2f, y, (cw + bw) / 2f, y + d(92f)), "Pilih tingkat", Style.SECONDARY, d(34f))
        y += d(92f) + d(30f)
        button(c, "m:settings", RectF((cw - bw) / 2f, y, (cw + bw) / 2f, y + d(92f)), "Pengaturan", Style.SECONDARY, d(34f))
        y += d(92f) + d(30f)
        button(c, "m:about", RectF((cw - bw) / 2f, y, (cw + bw) / 2f, y + d(92f)), "Tentang dan dukungan", Style.SECONDARY, d(34f))
        drawText(c, "Dikembangkan oleh Hanz Dev Official", cw / 2f, h - max(insetBottom, d(16f)) - d(36f), d(26f), CREAM, tfMed)
    }

    private fun drawLevels(c: Canvas) {
        val h = height.toFloat()
        topY = max(insetTop, d(20f)) + d(48f)
        circleBtn(c, "l:back", d(68f), topY, d(44f), 1)
        titlePanel(c, "Pilih tingkat", cw / 2f + d(10f))
        drawCoinPill(c)

        val total = Words.levels.size
        val cols = 5
        val cell = min(d(112f), (cw - d(48f) - 4 * d(16f)) / cols)
        val gap = d(16f)
        val x0 = (cw - (cols * cell + (cols - 1) * gap)) / 2f
        val areaTop = topY + d(80f)
        val areaBottom = h - max(insetBottom, d(16f)) - d(20f)
        val rowH = cell + gap + d(6f)
        val rows = (total + cols - 1) / cols
        val maxScroll = max(0f, rows * rowH - (areaBottom - areaTop))
        scrollY = scrollY.coerceIn(0f, maxScroll)
        val done = prefs.maxLevel
        c.save()
        c.clipRect(-ox, areaTop, cw + ox, areaBottom)
        for (i in 0 until total) {
            val l = x0 + (i % cols) * (cell + gap)
            val t = areaTop + (i / cols) * rowH - scrollY
            if (t + cell + d(8f) < areaTop || t > areaBottom) continue
            val unlocked = i <= done
            val id = "lv:$i"
            val pr = pressedId == id && !moved
            if (!unlocked) {
                fp.color = INK_PANEL
                rr(c, l, t, l + cell, t + cell, d(14f))
                icon(c, 7, l + cell / 2f, t + cell / 2f, d(28f), CREAM)
            } else if (i < done) {
                tile(c, l, t, l + cell, t + cell, GREEN, GREEN_EDGE, pr)
                drawText(c, (i + 1).toString(), l + cell / 2f, t + cell * 0.40f, d(40f), WHITE, tfBold)
                icon(c, 8, l + cell / 2f, t + cell * 0.76f, d(15f), WHITE)
            } else {
                tile(c, l, t, l + cell, t + cell, CREAM, CREAM_EDGE, pr)
                drawText(c, (i + 1).toString(), l + cell / 2f, t + cell / 2f, d(42f), INK, tfBold)
            }
            if (unlocked) {
                rect.set(l, max(t, areaTop), l + cell, min(t + cell, areaBottom))
                hit(id, rect)
            }
        }
        c.restore()
    }

    private fun drawAbout(c: Canvas) {
        val h = height.toFloat()
        topY = max(insetTop, d(20f)) + d(48f)
        circleBtn(c, "a:back", d(68f), topY, d(44f), 1)
        titlePanel(c, "Tentang", cw / 2f + d(10f))

        val cardW = min(d(630f), cw - d(48f))
        val top = topY + d(80f)
        val bottom = h - max(insetBottom, d(16f)) - d(24f)
        val card = RectF((cw - cardW) / 2f, top, (cw + cardW) / 2f, min(bottom, top + d(1000f)))
        fp.color = CREAM
        rr(c, card.left, card.top, card.right, card.bottom, d(22f))

        var y = card.top + d(82f)
        tilesRow(c, "KATA", cw / 2f, y, d(82f), true, 0L)
        y += d(88f)
        drawText(c, "Susun Kata", cw / 2f, y, d(46f), GREEN, tfBold)
        y += d(46f)
        drawText(c, "Versi $versionName", cw / 2f, y, d(26f), INK, tfMed)
        y += d(24f)
        val body = "Geser jari melewati huruf di roda untuk menyusun kata. Kata yang ada di kamus tapi tidak ada di kisi menjadi kata bonus dan menambah koin."
        for (s in wrap(body, d(28f), cardW - d(80f))) {
            y += d(38f)
            drawText(c, s, cw / 2f, y, d(28f), INK, tfMed)
        }
        y += d(64f)
        drawText(c, "Pengembang", cw / 2f, y, d(24f), INK, tfMed)
        y += d(44f)
        drawText(c, "Hanz Dev Official", cw / 2f, y, d(40f), GREEN, tfBold)
        y += d(40f)
        val bw = cardW - d(90f)
        button(c, "a:mail", RectF(cw / 2f - bw / 2f, y, cw / 2f + bw / 2f, y + d(90f)), "hanzzcodee@gmail.com", Style.PRIMARY, d(30f))
        y += d(90f) + d(30f)
        button(c, "a:saweria", RectF(cw / 2f - bw / 2f, y, cw / 2f + bw / 2f, y + d(90f)), "Dukung lewat Saweria", Style.SECONDARY, d(32f))
        y += d(90f) + d(44f)
        for (s in wrap("Game ini gratis dan tanpa iklan. Dukungan lewat Saweria membantu pengembang menambah tingkat baru.", d(26f), cardW - d(90f))) {
            drawText(c, s, cw / 2f, y, d(26f), INK, tfMed)
            y += d(34f)
        }
    }

    private fun layoutPlay() {
        val l = lv ?: return
        val h = height.toFloat()
        topY = max(insetTop, d(20f)) + d(48f)
        wheelR = min(d(236f), cw * 0.36f)
        bottomRowY = h - max(insetBottom, d(16f)) - d(56f)
        wheelCx = cw / 2f
        wheelCy = bottomRowY - wheelR + d(34f)
        previewY = wheelCy - wheelR - d(58f)
        val pad = d(16f)
        val areaTop = topY + d(44f) + d(24f) + pad
        val areaBottom = previewY - d(46f) - pad
        val areaW = cw - d(56f) - pad * 2f
        val areaH = max(areaBottom - areaTop, d(120f))
        cs = min(min(areaW / l.cols, areaH / l.rows), d(120f))
        gx0 = (cw - l.cols * cs) / 2f
        gy0 = areaTop + (areaH - l.rows * cs) / 2f
        panelRect.set(gx0 - pad, gy0 - pad, gx0 + l.cols * cs + pad, gy0 + l.rows * cs + pad)
    }

    private fun cellRect(r: Int, c: Int): RectF {
        val gap = cs * 0.09f
        return RectF(gx0 + c * cs + gap / 2f, gy0 + r * cs + gap / 2f, gx0 + (c + 1) * cs - gap / 2f, gy0 + (r + 1) * cs - gap / 2f - cs * 0.04f)
    }

    private fun letterPos(id: Int, out: PointF) {
        val n = letters.size
        val t = if (shuffleStart > 0L) ((now - shuffleStart) / 400f).coerceIn(0f, 1f) else 1f
        val e = easeOutCubic(t)
        val a0 = -PIf / 2f + 2f * PIf * prevSlot[id] / n
        val a1 = -PIf / 2f + 2f * PIf * slotOf[id] / n
        val rl = wheelR * 0.68f
        val x0 = wheelCx + cos(a0) * rl
        val y0 = wheelCy + sin(a0) * rl
        val x1 = wheelCx + cos(a1) * rl
        val y1 = wheelCy + sin(a1) * rl
        out.set(x0 + (x1 - x0) * e, y0 + (y1 - y0) * e)
    }

    private fun letterAt(x: Float, y: Float): Int {
        for (i in letters.indices) {
            letterPos(i, tmpP)
            val dx = x - tmpP.x
            val dy = y - tmpP.y
            if (dx * dx + dy * dy <= d(58f) * d(58f)) return i
        }
        return -1
    }

    private fun drawPlay(c: Canvas) {
        val l = lv ?: return
        layoutPlay()
        circleBtn(c, "p:settings", d(68f), topY, d(44f), 0)
        circleBtn(c, "p:back", d(168f), topY, d(44f), 1)
        titlePanel(c, "Tingkat ${levelIdx + 1}", cw / 2f + d(60f))
        drawCoinPill(c)

        fp.color = INK_PANEL
        rr(c, panelRect.left, panelRect.top, panelRect.right, panelRect.bottom, d(22f))

        for (r in 0 until l.rows) for (col in 0 until l.cols) {
            val ch = gridCh[r][col]
            if (ch == '\u0000') continue
            val rc = cellRect(r, col)
            val start = cellStart[r][col]
            val isRev = revealed[r][col] && now >= start
            var scale = 1f
            if (isRev && start > 0L && !calm()) {
                val k = (now - start) / 400f
                if (k < 1f) scale = 0.35f + 0.65f * easeOutBack(k.coerceIn(0f, 1f))
            }
            val pw = pulseWord
            if (pw != null && now - pulseStart < 600L && !calm()) {
                val inWord = if (pw.horiz) (r == pw.row && col >= pw.col && col < pw.col + pw.text.length)
                else (col == pw.col && r >= pw.row && r < pw.row + pw.text.length)
                if (inWord) scale *= 1f + 0.14f * sin((now - pulseStart) / 600f * PIf)
            }
            c.save()
            c.scale(scale, scale, rc.centerX(), rc.centerY())
            if (isRev) {
                tile(c, rc.left, rc.top, rc.right, rc.bottom, GREEN, GREEN_EDGE)
                drawText(c, ch.toString(), rc.centerX(), rc.centerY(), rc.width() * 0.62f, WHITE, tfBold)
            } else {
                tile(c, rc.left, rc.top, rc.right, rc.bottom, CREAM, CREAM_EDGE)
                if (pickMode) {
                    ln.color = GOLD
                    ln.strokeWidth = d(5f)
                    c.drawRoundRect(rc, d(10f), d(10f), ln)
                }
            }
            c.restore()
            if (pickMode && !revealed[r][col]) hit("cell:$r,$col", rc)
        }

        drawPreview(c)
        drawWheel(c)
        drawControls(c)
    }

    private fun drawPreview(c: Canvas) {
        val typing = sel.isNotEmpty()
        val txt = if (typing) sel.joinToString("") { letters[it].toString() } else previewText
        if (txt.isEmpty()) return
        if (!typing && now > previewUntil) return
        val n = txt.length
        val tile = min(d(78f), (cw - d(100f)) / n - d(10f))
        val gap = d(10f)
        val total = n * tile + (n - 1) * gap
        var shake = 0f
        val st = (now - shakeStart) / 350f
        if (previewKind == 4 && !typing && st in 0f..1f && !calm()) shake = sin(st * 38f) * d(14f) * (1f - st)
        val x0 = cw / 2f - total / 2f + shake
        fp.color = INK_PANEL
        rr(c, x0 - d(16f), previewY - tile / 2f - d(12f), x0 + total + d(16f), previewY + tile / 2f + d(18f), d(14f))
        val face: Int
        val edge: Int
        val fg: Int
        if (typing || previewKind == 2) {
            face = GREEN; edge = GREEN_EDGE; fg = WHITE
        } else if (previewKind == 3) {
            face = GOLD; edge = GOLD_EDGE; fg = INK
        } else {
            face = CREAM; edge = CREAM_EDGE; fg = INK
        }
        for (k in 0 until n) {
            val l = x0 + k * (tile + gap)
            val tt = previewY - tile / 2f
            var sc = 1f
            if (typing && k == n - 1 && k < sel.size && !calm()) {
                val a = (now - selStart[sel[k]]) / 200f
                if (a < 1f) sc = 0.6f + 0.4f * easeOutBack(a.coerceIn(0f, 1f))
            }
            c.save()
            c.scale(sc, sc, l + tile / 2f, previewY)
            tile(c, l, tt, l + tile, tt + tile, face, edge)
            drawText(c, txt[k].toString(), l + tile / 2f, previewY, tile * 0.64f, fg, tfBold)
            c.restore()
        }
    }

    private fun drawWheel(c: Canvas) {
        fp.color = Color.argb(245, 0xF5, 0xF0, 0xE1)
        c.drawCircle(wheelCx, wheelCy, wheelR, fp)
        ln.color = INK
        ln.strokeWidth = d(6f)
        c.drawCircle(wheelCx, wheelCy, wheelR, ln)

        if (sel.size >= 1) {
            path.reset()
            for (k in sel.indices) {
                letterPos(sel[k], tmpP)
                if (k == 0) path.moveTo(tmpP.x, tmpP.y) else path.lineTo(tmpP.x, tmpP.y)
            }
            if (swiping) path.lineTo(fingerX, fingerY)
            ln.color = GREEN
            ln.strokeWidth = d(22f)
            c.drawPath(path, ln)
        }
        for (i in letters.indices) {
            letterPos(i, tmpP)
            if (sel.contains(i)) {
                var sc = 1f
                val a = (now - selStart[i]) / 220f
                if (a < 1f && !calm()) sc = 0.8f + 0.2f * easeOutBack(a.coerceIn(0f, 1f))
                fp.color = GREEN
                c.drawCircle(tmpP.x, tmpP.y, d(48f) * sc, fp)
                drawText(c, letters[i].toString(), tmpP.x, tmpP.y, d(70f) * sc, WHITE, tfBold)
            } else {
                drawText(c, letters[i].toString(), tmpP.x, tmpP.y, d(72f), INK, tfBold)
            }
        }
    }

    private fun drawControls(c: Canvas) {
        val lx = d(72f)
        val rx = cw - d(72f)
        val y1 = wheelCy - wheelR * 0.64f
        val y2 = y1 + d(172f)
        iconButtonWithCost(c, "p:target", lx, y1, 2, COST_TARGET.toString(), pickMode)
        iconButtonWithCost(c, "p:five", lx, y2, 4, COST_FIVE.toString())
        iconButtonWithCost(c, "p:one", rx, y1, 3, COST_ONE.toString())
        val avail = dailyAvailable()
        iconButtonWithCost(c, "p:gift", rx, y2, 6, if (avail) "+$DAILY_GIFT" else "Besok", avail)

        val br = RectF(d(24f), bottomRowY - d(40f), d(214f), bottomRowY + d(40f))
        val pr = pressedId == "p:bonus"
        fp.color = INK_PANEL
        rr(c, br.left, br.top + (if (pr) d(3f) else 0f), br.right, br.bottom + (if (pr) d(3f) else 0f), d(14f))
        drawText(c, bonusFound.size.toString(), br.left + d(46f), br.centerY(), d(46f), GOLD, tfBold)
        drawText(c, "Kata", br.left + d(138f), br.centerY() - d(16f), d(26f), CREAM, tfBold)
        drawText(c, "bonus", br.left + d(138f), br.centerY() + d(16f), d(26f), CREAM, tfBold)
        hit("p:bonus", br)

        circleBtn(c, "p:shuffle", rx, bottomRowY, d(44f), 5)
    }

    private fun drawToast(c: Canvas) {
        val m = toastMsg ?: return
        if (now > toastUntil) {
            toastMsg = null
            return
        }
        val size = d(28f)
        tp.typeface = tfMed
        tp.textSize = size
        val tw = min(tp.measureText(m) + d(60f), cw - d(40f))
        val cx = cw / 2f
        val cy = topY + d(104f)
        fp.color = INK_PANEL
        rr(c, cx - tw / 2f, cy - d(30f), cx + tw / 2f, cy + d(30f), d(14f))
        drawText(c, m, cx, cy, size, CREAM, tfMed)
    }

    private fun drawFocusRing(c: Canvas) {
        val id = focusId ?: return
        val h = hits.lastOrNull { it.id == id } ?: return
        val r = RectF(h.r)
        r.inset(-d(6f), -d(6f))
        ln.color = INK
        ln.strokeWidth = d(9f)
        c.drawRoundRect(r, d(16f), d(16f), ln)
        ln.color = CREAM
        ln.strokeWidth = d(4f)
        c.drawRoundRect(r, d(16f), d(16f), ln)
    }

    private fun dim(c: Canvas) {
        fp.color = Color.argb(178, 0x14, 0x21, 0x1A)
        c.drawRect(-ox, 0f, cw + ox, height.toFloat(), fp)
    }

    private fun card(c: Canvas, cardW: Float, cardH: Float, scale: Float = 1f): RectF {
        val h = height.toFloat()
        val w = min(cardW, cw - d(40f))
        val r = RectF((cw - w) / 2f, (h - cardH) / 2f, (cw + w) / 2f, (h + cardH) / 2f)
        c.save()
        c.scale(scale, scale, cw / 2f, h / 2f)
        fp.color = CREAM
        rr(c, r.left, r.top, r.right, r.bottom, d(22f))
        c.restore()
        return r
    }

    private fun popScale(): Float {
        if (calm()) return 1f
        val t = ((now - overlayStart) / 320f).coerceIn(0f, 1f)
        return 0.82f + 0.18f * easeOutBack(t)
    }

    private fun toggleRow(c: Canvas, id: String, label: String, on: Boolean, card: RectF, y: Float) {
        val r = RectF(card.left + d(28f), y, card.right - d(28f), y + d(84f))
        hit(id, r)
        if (pressedId == id) {
            fp.color = Color.argb(34, 0x14, 0x21, 0x1A)
            rr(c, r.left, r.top, r.right, r.bottom, d(14f))
        }
        drawText(c, label, r.left + d(18f), r.centerY(), d(34f), INK, tfMed, Paint.Align.LEFT)
        val tw = d(108f)
        val th = d(56f)
        val tr = RectF(r.right - d(18f) - tw, r.centerY() - th / 2f, r.right - d(18f), r.centerY() + th / 2f)
        fp.color = if (on) GREEN else INK
        c.drawRoundRect(tr, th / 2f, th / 2f, fp)
        fp.color = CREAM
        val kx = if (on) tr.right - th / 2f else tr.left + th / 2f
        c.drawCircle(kx, tr.centerY(), th / 2f - d(7f), fp)
        if (on) icon(c, 8, kx, tr.centerY(), d(12f), GREEN)
    }

    private fun drawSettings(c: Canvas) {
        dim(c)
        val ch = d(110f) + 4 * d(92f) + d(100f) + d(110f) + d(70f)
        val cd = card(c, d(640f), ch)
        drawText(c, "Pengaturan", cd.centerX(), cd.top + d(62f), d(44f), GREEN, tfBold)
        var y = cd.top + d(110f)
        toggleRow(c, "t:sound", "Suara", prefs.sound, cd, y); y += d(92f)
        toggleRow(c, "t:music", "Musik", prefs.music, cd, y); y += d(92f)
        toggleRow(c, "t:vib", "Getar", prefs.vibrate, cd, y); y += d(92f)
        toggleRow(c, "t:fx", "Kurangi efek gerak", prefs.reduceFx, cd, y); y += d(92f)
        button(c, "s:reset", RectF(cd.left + d(60f), y + d(12f), cd.right - d(60f), y + d(12f) + d(80f)), "Reset progres", Style.SECONDARY, d(30f))
        val cb = RectF(cd.left + d(60f), y + d(12f) + d(80f) + d(28f), cd.right - d(60f), y + d(12f) + d(80f) + d(28f) + d(88f))
        button(c, "s:close", cb, "Tutup", Style.PRIMARY, d(34f))
    }

    private fun drawConfirm(c: Canvas) {
        dim(c)
        val cd = card(c, d(600f), d(430f))
        drawText(c, "Reset progres?", cd.centerX(), cd.top + d(70f), d(42f), INK, tfBold)
        var y = cd.top + d(130f)
        for (s in wrap("Semua tingkat, koin, dan pengaturan dihapus dan tidak bisa dikembalikan.", d(30f), cd.width() - d(80f))) {
            drawText(c, s, cd.centerX(), y, d(30f), INK, tfMed)
            y += d(42f)
        }
        val bw = (cd.width() - d(100f) - d(24f)) / 2f
        val by = cd.bottom - d(124f)
        button(c, "c:no", RectF(cd.left + d(50f), by, cd.left + d(50f) + bw, by + d(88f)), "Batal", Style.PRIMARY, d(32f))
        button(c, "c:yes", RectF(cd.right - d(50f) - bw, by, cd.right - d(50f), by + d(88f)), "Reset", Style.SECONDARY, d(32f))
    }

    private fun drawRootTiles(c: Canvas, cx: Float, cy: Float) {
        val word = lv?.letters ?: return
        val size = min(d(86f), (cw - d(160f)) / word.length - d(10f))
        val gap = size * 0.12f
        val total = word.length * size + (word.length - 1) * gap
        for (k in word.indices) {
            val t = if (calm()) 1f else ((now - overlayStart - 250L - k * 110L) / 420f).coerceIn(0f, 1f)
            if (t <= 0f) continue
            val sc = if (calm()) 1f else easeOutBack(t)
            val l = cx - total / 2f + k * (size + gap)
            c.save()
            c.scale(sc, sc, l + size / 2f, cy)
            tile(c, l, cy - size / 2f, l + size, cy + size / 2f, GREEN, GREEN_EDGE)
            drawText(c, word[k].toString(), l + size / 2f, cy, size * 0.62f, WHITE, tfBold)
            c.restore()
        }
    }

    private fun drawWin(c: Canvas) {
        dim(c)
        val cd = card(c, d(620f), d(660f), popScale())
        drawText(c, "Tingkat ${levelIdx + 1} selesai", cd.centerX(), cd.top + d(78f), d(46f), GREEN, tfBold)
        drawRootTiles(c, cd.centerX(), cd.top + d(215f))
        if (winReward > 0) {
            coin(c, cd.centerX() - d(112f), cd.top + d(352f), d(28f))
            drawText(c, "+$winReward koin", cd.centerX() + d(20f), cd.top + d(352f), d(42f), INK, tfBold)
        } else {
            drawText(c, "Tingkat ini sudah pernah selesai", cd.centerX(), cd.top + d(352f), d(28f), INK, tfMed)
        }
        drawText(c, "Kata bonus: ${bonusFound.size}", cd.centerX(), cd.top + d(414f), d(30f), INK, tfMed)
        button(c, "w:next", RectF(cd.left + d(60f), cd.bottom - d(206f), cd.right - d(60f), cd.bottom - d(206f) + d(98f)), "Tingkat berikutnya", Style.PRIMARY, d(36f))
        button(c, "w:menu", RectF(cd.left + d(130f), cd.bottom - d(92f), cd.right - d(130f), cd.bottom - d(92f) + d(70f)), "Menu", Style.SECONDARY, d(30f))
    }

    private fun drawFinished(c: Canvas) {
        dim(c)
        val cd = card(c, d(630f), d(780f), popScale())
        drawText(c, "Semua tingkat selesai", cd.centerX(), cd.top + d(82f), d(42f), GREEN, tfBold)
        drawRootTiles(c, cd.centerX(), cd.top + d(215f))
        var y = cd.top + d(345f)
        val msg = "Kamu sudah menamatkan ${Words.levels.size} tingkat. Tingkat baru belum tersedia."
        for (s in wrap(msg, d(30f), cd.width() - d(90f))) {
            drawText(c, s, cd.centerX(), y, d(30f), INK, tfMed)
            y += d(42f)
        }
        button(c, "f:support", RectF(cd.left + d(60f), cd.bottom - d(250f), cd.right - d(60f), cd.bottom - d(250f) + d(94f)), "Dukung lewat Saweria", Style.SECONDARY, d(32f))
        button(c, "f:menu", RectF(cd.left + d(60f), cd.bottom - d(132f), cd.right - d(60f), cd.bottom - d(132f) + d(94f)), "Ke menu", Style.PRIMARY, d(34f))
    }

    private fun drawBonus(c: Canvas) {
        dim(c)
        val cd = card(c, d(630f), d(700f))
        drawText(c, "Kata bonus", cd.centerX(), cd.top + d(64f), d(42f), GREEN, tfBold)
        drawText(c, "Tiap kata bonus memberi +$BONUS_REWARD koin", cd.centerX(), cd.top + d(114f), d(26f), INK, tfMed)
        if (bonusFound.isEmpty()) {
            var y = cd.top + d(230f)
            for (s in wrap("Belum ada. Rangkai kata lain dari huruf di roda. Kata yang ada di kamus tapi tidak ada di kisi menjadi kata bonus.", d(28f), cd.width() - d(100f))) {
                drawText(c, s, cd.centerX(), y, d(28f), INK, tfMed)
                y += d(40f)
            }
        } else {
            var x = cd.left + d(40f)
            var y = cd.top + d(190f)
            val maxX = cd.right - d(40f)
            for (wd in bonusFound) {
                tp.typeface = tfBold
                tp.textSize = d(32f)
                val tw = tp.measureText(wd) + d(40f)
                if (x + tw > maxX) {
                    x = cd.left + d(40f)
                    y += d(66f)
                }
                if (y > cd.bottom - d(190f)) break
                tile(c, x, y - d(26f), x + tw, y + d(26f), GOLD, GOLD_EDGE)
                drawText(c, wd, x + tw / 2f, y, d(32f), INK, tfBold)
                x += tw + d(14f)
            }
        }
        button(c, "b:close", RectF(cd.left + d(90f), cd.bottom - d(130f), cd.right - d(90f), cd.bottom - d(130f) + d(88f)), "Tutup", Style.PRIMARY, d(34f))
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x - ox
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                focusId = null
                downX = x
                downY = y
                lastY = y
                moved = false
                pressedId = hitAt(x, y)
                if (pressedId == null && screen == Screen.PLAY && overlay == Overlay.NONE && !pickMode && loadState == 1) {
                    layoutPlay()
                    val dx = x - wheelCx
                    val dy = y - wheelCy
                    if (sqrt(dx * dx + dy * dy) <= wheelR * 1.1f) beginSwipe(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(x - downX) > d(16f) || abs(y - downY) > d(16f)) moved = true
                if (swiping) {
                    fingerX = x
                    fingerY = y
                    updateSwipe(x, y)
                } else if (screen == Screen.LEVELS && overlay == Overlay.NONE && moved) {
                    scrollY -= (y - lastY)
                }
                lastY = y
            }
            MotionEvent.ACTION_UP -> {
                if (swiping) {
                    endSwipe()
                } else {
                    val id = hitAt(x, y)
                    val isLevelTap = id != null && id.startsWith("lv:")
                    if (id != null && id == pressedId && !(isLevelTap && moved)) onHit(id)
                }
                pressedId = null
            }
            MotionEvent.ACTION_CANCEL -> {
                if (swiping) {
                    sel.clear()
                    swiping = false
                }
                pressedId = null
            }
        }
        invalidate()
        return true
    }

    private fun beginSwipe(x: Float, y: Float) {
        swiping = true
        fingerX = x
        fingerY = y
        sel.clear()
        previewKind = 1
        updateSwipe(x, y)
    }

    private fun updateSwipe(x: Float, y: Float) {
        val id = letterAt(x, y)
        if (id < 0) return
        if (sel.size >= 2 && sel[sel.size - 2] == id) {
            sel.removeAt(sel.size - 1)
            sfx.play(R.raw.tick, 0.8f + 0.1f * sel.size, 0.6f)
        } else if (!sel.contains(id)) {
            addLetter(id)
        }
    }

    private fun addLetter(id: Int) {
        sel.add(id)
        selStart[id] = ms()
        previewKind = 1
        sfx.play(R.raw.tick, 0.85f + 0.1f * sel.size)
        haptic(false)
        keepAnim(300)
    }

    private fun endSwipe() {
        swiping = false
        val word = sel.joinToString("") { letters[it].toString() }
        sel.clear()
        previewKind = 0
        if (word.length >= 2) submit(word)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (loadState != 1) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_SPACE) {
                if (loadState == 2) onHit("r:retry")
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        val playing = screen == Screen.PLAY && overlay == Overlay.NONE && lv != null
        when (keyCode) {
            KeyEvent.KEYCODE_ESCAPE -> {
                handleBack()
                return true
            }
            KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                moveFocus(if (keyCode == KeyEvent.KEYCODE_TAB && event.isShiftPressed) -1 else 1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                moveFocus(-1)
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                if (playing && sel.isNotEmpty()) {
                    sel.removeAt(sel.size - 1)
                    invalidate()
                }
                return true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE -> {
                val f = focusId
                if (playing && sel.isNotEmpty() && keyCode != KeyEvent.KEYCODE_SPACE) {
                    endSwipe()
                } else if (f != null && hits.any { it.id == f }) {
                    onHit(f)
                }
                invalidate()
                return true
            }
        }
        if (playing) {
            val ch = Character.toUpperCase(event.unicodeChar.toChar())
            if (ch.isLetter()) {
                focusId = null
                var id = -1
                for (i in letters.indices) {
                    if (letters[i] == ch && !sel.contains(i)) {
                        id = i
                        break
                    }
                }
                if (id >= 0) addLetter(id)
                invalidate()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun moveFocus(dir: Int) {
        if (hits.isEmpty()) return
        val idx = hits.indexOfFirst { it.id == focusId }
        val next = if (idx < 0) (if (dir > 0) 0 else hits.size - 1) else Math.floorMod(idx + dir, hits.size)
        focusId = hits[next].id
        sfx.play(R.raw.klik, 1.2f, 0.5f)
        invalidate()
    }

    private fun onHit(id: String) {
        when {
            id.startsWith("lv:") -> {
                sfx.play(R.raw.klik)
                val i = id.substring(3).toInt()
                startLevel(i, restore = i == prefs.currentLevel)
            }
            id.startsWith("cell:") -> {
                val p = id.substring(5).split(",")
                pickCell(p[0].toInt(), p[1].toInt())
            }
            else -> when (id) {
                "r:retry" -> {
                    loadState = 0
                    host.loadData()
                }
                "m:play" -> {
                    sfx.play(R.raw.klik)
                    val cur = min(prefs.currentLevel, min(prefs.maxLevel, Words.levels.size - 1))
                    startLevel(cur, restore = true)
                }
                "m:levels" -> { sfx.play(R.raw.klik); openLevels() }
                "m:settings", "p:settings" -> { sfx.play(R.raw.klik); overlay = Overlay.SETTINGS; overlayStart = ms(); keepAnim(400) }
                "m:about" -> { sfx.play(R.raw.klik); screen = Screen.ABOUT }
                "a:back", "l:back", "p:back" -> { sfx.play(R.raw.klik); goMenu() }
                "a:mail" -> sendMail()
                "a:saweria", "f:support" -> openUrl("https://saweria.co/hanzreally")
                "p:target" -> toggleTarget()
                "p:one" -> hintRandom(1, COST_ONE)
                "p:five" -> hintRandom(5, COST_FIVE)
                "p:gift" -> claimDaily()
                "p:shuffle" -> doShuffle()
                "p:bonus" -> { sfx.play(R.raw.klik); overlay = Overlay.BONUS; overlayStart = ms(); keepAnim(400) }
                "b:close", "s:close" -> { sfx.play(R.raw.klik); overlay = Overlay.NONE }
                "t:sound" -> { prefs.sound = !prefs.sound; sfx.play(R.raw.klik) }
                "t:music" -> {
                    prefs.music = !prefs.music
                    if (prefs.music) sfx.startMusic() else sfx.pauseMusic()
                    sfx.play(R.raw.klik)
                }
                "t:vib" -> { prefs.vibrate = !prefs.vibrate; haptic(false); sfx.play(R.raw.klik) }
                "t:fx" -> { prefs.reduceFx = !prefs.reduceFx; sfx.play(R.raw.klik) }
                "s:reset" -> { sfx.play(R.raw.klik); overlay = Overlay.CONFIRM_RESET; overlayStart = ms(); keepAnim(400) }
                "c:no" -> overlay = Overlay.SETTINGS
                "c:yes" -> {
                    prefs.resetAll()
                    coinShown = prefs.coins.toFloat()
                    lv = null
                    enterMenu()
                    showToast("Progres direset")
                    sfx.startMusic()
                }
                "w:next" -> {
                    sfx.play(R.raw.klik)
                    overlay = Overlay.NONE
                    startLevel(levelIdx + 1, restore = false)
                }
                "w:menu", "f:menu" -> { sfx.play(R.raw.klik); overlay = Overlay.NONE; enterMenu() }
            }
        }
        announceScreen()
        invalidate()
    }

    private fun openUrl(url: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            host.startActivity(i)
        } catch (_: Exception) {
            showToast("Tidak bisa membuka tautan")
        }
    }

    private fun sendMail() {
        try {
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:hanzzcodee@gmail.com"))
            i.putExtra(Intent.EXTRA_SUBJECT, "Susun Kata, masukan")
            host.startActivity(i)
        } catch (_: Exception) {
            showToast("Aplikasi email tidak ditemukan")
        }
    }

    /** Dipanggil dari tombol kembali sistem. true berarti sudah ditangani. */
    fun handleBack(): Boolean {
        if (loadState != 1) return false
        if (overlay != Overlay.NONE) {
            when (overlay) {
                Overlay.CONFIRM_RESET -> overlay = Overlay.SETTINGS
                Overlay.WIN, Overlay.FINISHED -> enterMenu()
                else -> overlay = Overlay.NONE
            }
            announceScreen()
            invalidate()
            return true
        }
        if (pickMode) {
            pickMode = false
            invalidate()
            return true
        }
        if (screen != Screen.MENU) {
            goMenu()
            announceScreen()
            return true
        }
        return false
    }

    private fun labelFor(id: String): String = when {
        id.startsWith("lv:") -> "Tingkat ${id.substring(3).toInt() + 1}"
        id.startsWith("cell:") -> "Kotak huruf tertutup, ketuk untuk membuka"
        else -> when (id) {
            "r:retry" -> "Coba lagi"
            "m:play" -> "Main"
            "m:levels" -> "Pilih tingkat"
            "m:settings", "p:settings" -> "Pengaturan"
            "m:about" -> "Tentang dan dukungan"
            "a:back", "l:back", "p:back" -> "Kembali ke menu"
            "a:mail" -> "Kirim email ke hanzzcodee@gmail.com"
            "a:saweria", "f:support" -> "Dukung pengembang lewat Saweria"
            "p:target" -> "Buka kotak pilihan, $COST_TARGET koin"
            "p:one" -> "Buka 1 huruf, $COST_ONE koin"
            "p:five" -> "Buka 5 huruf, $COST_FIVE koin"
            "p:gift" -> if (dailyAvailable()) "Ambil hadiah harian, $DAILY_GIFT koin" else "Hadiah harian sudah diambil"
            "p:bonus" -> "Daftar kata bonus, ${bonusFound.size} ditemukan"
            "p:shuffle" -> "Acak huruf pada roda"
            "t:sound" -> "Suara, ${if (prefs.sound) "aktif" else "mati"}"
            "t:music" -> "Musik, ${if (prefs.music) "aktif" else "mati"}"
            "t:vib" -> "Getar, ${if (prefs.vibrate) "aktif" else "mati"}"
            "t:fx" -> "Kurangi efek gerak, ${if (prefs.reduceFx) "aktif" else "mati"}"
            "s:reset" -> "Reset progres"
            "s:close", "b:close" -> "Tutup"
            "c:yes" -> "Ya, reset progres"
            "c:no" -> "Batal"
            "w:next" -> "Tingkat berikutnya"
            "w:menu", "f:menu" -> "Ke menu"
            else -> id
        }
    }

    private fun screenSummary(): String {
        if (loadState == 0) return "Memuat kata"
        if (loadState == 2) return "Data game gagal dimuat"
        if (overlay != Overlay.NONE) {
            return when (overlay) {
                Overlay.SETTINGS -> "Pengaturan"
                Overlay.CONFIRM_RESET -> "Konfirmasi reset progres"
                Overlay.WIN -> "Tingkat ${levelIdx + 1} selesai"
                Overlay.FINISHED -> "Semua tingkat selesai"
                Overlay.BONUS -> "Kata bonus: " + (if (bonusFound.isEmpty()) "belum ada" else bonusFound.joinToString(", "))
                Overlay.NONE -> ""
            }
        }
        return when (screen) {
            Screen.MENU -> "Susun Kata, menu utama"
            Screen.LEVELS -> "Pilih tingkat"
            Screen.ABOUT -> "Tentang Susun Kata, pengembang Hanz Dev Official"
            Screen.PLAY -> {
                val l = lv
                if (l == null) "Bermain" else
                    "Tingkat ${levelIdx + 1}. Kata ditemukan ${found.size} dari ${l.words.size}. Huruf pada roda: ${letters.joinToString(" ")}. Ketik huruf dengan papan ketik lalu Enter untuk menyusun kata."
            }
        }
    }

    private fun announceScreen() {
        contentDescription = screenSummary()
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider? = a11yProvider

    private val a11yProvider = object : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
            if (virtualViewId == AccessibilityNodeProvider.HOST_VIEW_ID) {
                val info = AccessibilityNodeInfo.obtain(this@GameView)
                this@GameView.onInitializeAccessibilityNodeInfo(info)
                info.contentDescription = screenSummary()
                for (i in a11yHits.indices) info.addChild(this@GameView, i)
                return info
            }
            val h = a11yHits.getOrNull(virtualViewId) ?: return null
            val info = AccessibilityNodeInfo.obtain()
            info.setSource(this@GameView, virtualViewId)
            info.setParent(this@GameView)
            info.packageName = context.packageName
            info.className = "android.widget.Button"
            info.contentDescription = labelFor(h.id)
            info.isClickable = true
            info.isEnabled = true
            info.isVisibleToUser = true
            info.addAction(AccessibilityNodeInfo.ACTION_CLICK)
            val b = Rect(
                (h.r.left + ox).toInt(), h.r.top.toInt(),
                (h.r.right + ox).toInt(), h.r.bottom.toInt()
            )
            info.setBoundsInParent(b)
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            b.offset(loc[0], loc[1])
            info.setBoundsInScreen(b)
            return info
        }

        override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            if (virtualViewId == AccessibilityNodeProvider.HOST_VIEW_ID) return this@GameView.performAccessibilityAction(action, arguments)
            val h = a11yHits.getOrNull(virtualViewId) ?: return false
            if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                onHit(h.id)
                return true
            }
            return false
        }
    }
}
