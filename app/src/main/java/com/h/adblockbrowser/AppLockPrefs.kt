package com.h.adblockbrowser

import android.content.Context
import java.security.MessageDigest

/** Lưu đường dẫn ảnh nền cho trang chủ (trang đầu tiên khi mở app). */
object WallpaperPrefs {
    private const val PREFS = "wallpaper_prefs"
    private const val KEY_URI = "wallpaper_uri"

    fun get(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URI, null)

    fun set(context: Context, uri: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_URI, uri).apply()
    }
}

/** Lưu khoá mở app (PIN hoặc hình - pattern) - chỉ lưu HASH (SHA-256), không lưu PIN/pattern gốc. */
object AppLockPrefs {
    private const val PREFS = "app_lock_prefs"
    private const val KEY_TYPE = "lock_type"       // "none" | "pin" | "pattern"
    private const val KEY_HASH = "lock_hash"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lockType(context: Context): String = prefs(context).getString(KEY_TYPE, "none") ?: "none"

    fun isEnabled(context: Context): Boolean = lockType(context) != "none"

    fun setPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_TYPE, "pin").putString(KEY_HASH, hash(pin)).apply()
    }

    fun setPattern(context: Context, pattern: String) {
        prefs(context).edit().putString(KEY_TYPE, "pattern").putString(KEY_HASH, hash(pattern)).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().putString(KEY_TYPE, "none").remove(KEY_HASH).apply()
    }

    fun verify(context: Context, input: String): Boolean {
        val saved = prefs(context).getString(KEY_HASH, null) ?: return false
        return saved == hash(input)
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
