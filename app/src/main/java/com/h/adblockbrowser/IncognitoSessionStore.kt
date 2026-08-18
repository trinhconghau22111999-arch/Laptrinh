package com.h.adblockbrowser

import android.content.Context

/** Danh sách URL các tab đang mở của "Ẩn danh" - LƯU LÂU DÀI, để thoát app vào lại các tab cũ
 *  vẫn còn nguyên (không tự xoá nữa). */
object IncognitoSessionStore {
    private const val PREFS = "incognito_session"
    private const val KEY = "urls"

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    fun save(context: Context, urls: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, urls.joinToString("\n")).apply()
    }

    /** Xoá sạch danh sách tab đã lưu - dùng khi thoát Ẩn danh để KHÔNG để lại dấu vết cho lần
     *  mở sau (đúng nghĩa ẩn danh: mỗi phiên độc lập, không lưu lại gì). */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
