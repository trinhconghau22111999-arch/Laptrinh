package com.h.adblockbrowser

import android.content.Context

/** Lưu THỨ TỰ các tile trên trang \"start\" sau khi người dùng KÉO-THẢ để đổi vị trí.
 *  Lưu dưới dạng chuỗi key cách nhau bằng newline, đúng thứ tự từ trên-trái xuống dưới-phải.
 *  Nếu chưa có dữ liệu (lần đầu cài / sau khi reset) → trả về danh sách mặc định ban đầu. */
object PinnedOrderStore {
    private const val PREFS = "pinned_order"
    private const val KEY_FIXED = "fixed_keys"
    private const val KEY_USER = "user_keys"

    /** Thứ tự các tile CỐ ĐỊNH (youtube, settings, incognito...) */
    fun getFixedOrder(context: Context, default: List<String>): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FIXED, null)
            ?: return default
        val saved = raw.split("\n").filter { it.isNotBlank() }
        // Đảm bảo không mất key nếu có key mới được thêm vào default
        val missing = default.filter { it !in saved }
        return saved + missing
    }

    fun saveFixedOrder(context: Context, keys: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FIXED, keys.joinToString("\n")).apply()
    }

    /** Thứ tự các tile USER GHIM (pkgName) */
    fun getUserOrder(context: Context, default: List<String>): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USER, null)
            ?: return default
        val saved = raw.split("\n").filter { it.isNotBlank() }
        val missing = default.filter { it !in saved }
        return (saved.filter { it in default }) + missing
    }

    fun saveUserOrder(context: Context, keys: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_USER, keys.joinToString("\n")).apply()
    }
}
