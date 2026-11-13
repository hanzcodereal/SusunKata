package com.hanzdev.susunkata

import android.content.Context
import android.content.SharedPreferences

/** Penyimpanan progres & pengaturan. Versi skema disimpan agar update di masa depan bisa bermigrasi. */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("susunkata", Context.MODE_PRIVATE)

    init {
        if (sp.getInt("versi", 0) == 0) {
            sp.edit().putInt("versi", SCHEMA).putInt("koin", 150).apply()
        }
    }

    var coins: Int
        get() = sp.getInt("koin", 0)
        set(v) { sp.edit().putInt("koin", v.coerceAtLeast(0)).apply() }

    /** Indeks level tertinggi yang terbuka (0-based). */
    var maxLevel: Int
        get() = sp.getInt("level_maks", 0)
        set(v) { sp.edit().putInt("level_maks", v.coerceAtLeast(0)).apply() }

    var currentLevel: Int
        get() = sp.getInt("level_kini", 0)
        set(v) { sp.edit().putInt("level_kini", v.coerceAtLeast(0)).apply() }

    var sound: Boolean
        get() = sp.getBoolean("suara", true)
        set(v) { sp.edit().putBoolean("suara", v).apply() }

    var music: Boolean
        get() = sp.getBoolean("musik", true)
        set(v) { sp.edit().putBoolean("musik", v).apply() }

    var vibrate: Boolean
        get() = sp.getBoolean("getar", true)
        set(v) { sp.edit().putBoolean("getar", v).apply() }

    var reduceFx: Boolean
        get() = sp.getBoolean("kurangi_efek", false)
        set(v) { sp.edit().putBoolean("kurangi_efek", v).apply() }

    /** Hari (epoch day) terakhir hadiah harian diambil. */
    var lastDaily: Long
        get() = sp.getLong("hadiah_hari", -1L)
        set(v) { sp.edit().putLong("hadiah_hari", v).apply() }

    var totalBonus: Int
        get() = sp.getInt("total_bonus", 0)
        set(v) { sp.edit().putInt("total_bonus", v).apply() }

    /** Progres level yang sedang dimainkan (JSON). */
    var progress: String?
        get() = sp.getString("progres", null)
        set(v) { sp.edit().putString("progres", v).apply() }

    fun resetAll() {
        sp.edit().clear().putInt("versi", SCHEMA).putInt("koin", 150).apply()
    }

    companion object {
        const val SCHEMA = 1
    }
}
