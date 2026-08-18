package com.h.adblockbrowser

import android.content.Context

/** Danh sách URL các tab đang mở của TỪNG hồ sơ tài khoản (theo slot), LƯU LÂU DÀI - thoát app
 *  vào lại thì mỗi tài khoản vẫn còn nguyên các tab đã mở, y hệt cách IncognitoSessionStore hoạt
 *  động nhưng tách riêng theo từng slot (mỗi slot 1 key SharedPreferences riêng). */
object AccountSessionStore {
    private const val PREFS = "account_session"
    private fun key(slot: Int) = "urls_slot_$slot"

    fun load(context: Context, slot: Int): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(slot), "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    fun save(context: Context, slot: Int, urls: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(slot), urls.joinToString("\n")).apply()
    }

    fun clear(context: Context, slot: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(slot)).apply()
    }
}
