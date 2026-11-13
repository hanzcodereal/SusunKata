package com.hanzdev.susunkata

import android.content.Context
import org.json.JSONArray
import java.util.Locale

class PlacedWord(val text: String, val row: Int, val col: Int, val horiz: Boolean)

class Level(val letters: String, val rows: Int, val cols: Int, val words: List<PlacedWord>)

/** Kamus bahasa Indonesia + daftar level (dibuat & diverifikasi oleh tools/buat_level.py). */
object Words {
    val dict = HashSet<String>(4096)
    var levels: List<Level> = emptyList()
        private set
    private var loaded = false

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        dict.clear()
        ctx.assets.open("kata.txt").bufferedReader().useLines { seq ->
            for (line in seq) {
                val s = line.trim().uppercase(Locale.ROOT)
                if (s.length >= 3) dict.add(s)
            }
        }
        val text = ctx.assets.open("levels.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        val out = ArrayList<Level>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val wa = o.getJSONArray("words")
            val ws = ArrayList<PlacedWord>(wa.length())
            for (k in 0 until wa.length()) {
                val w = wa.getJSONObject(k)
                ws.add(PlacedWord(w.getString("w"), w.getInt("r"), w.getInt("c"), w.getInt("h") == 1))
            }
            out.add(Level(o.getString("letters"), o.getInt("rows"), o.getInt("cols"), ws))
        }
        levels = out
        loaded = true
    }
}
