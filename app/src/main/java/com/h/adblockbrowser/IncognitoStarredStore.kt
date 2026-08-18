package com.h.adblockbrowser

import android.content.Context

/** Danh sách trang "gắn dấu sao" của Ẩn danh - LƯU VĨNH VIỄN (SharedPreferences), KHÔNG mất khi
 *  thoát/đóng phiên Ẩn danh (trước đây chỉ lưu trong RAM nên mất ngay khi đóng - nay đổi lại
 *  theo yêu cầu: lưu mãi mãi). */
object IncognitoStarredStore {
    private const val PREFS = "starred_pages_incognito"
    private const val KEY = "urls"

    fun getAll(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    fun isStarred(context: Context, url: String): Boolean = getAll(context).contains(url)

    fun toggle(context: Context, url: String): Boolean {
        val list = getAll(context).toMutableList()
        val nowStarred: Boolean
        if (list.contains(url)) {
            list.remove(url); nowStarred = false
        } else {
            list.add(0, url); nowStarred = true
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, list.joinToString("\n")).apply()
        return nowStarred
    }
}
