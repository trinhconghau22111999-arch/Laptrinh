package com.h.adblockbrowser

import android.content.Context

/** Danh sách trang "gắn dấu trang" (bookmark) của "Nhiều tài khoản" - RIÊNG BIỆT cho TỪNG hồ sơ
 *  (theo slot 1..10, xem AccountBrowserActivityBase.slot), tài khoản này không thấy dấu trang
 *  của tài khoản khác. Lưu VĨNH VIỄN qua SharedPreferences, không mất khi đóng phiên. */
object AccountStarredStore {
    private const val PREFS_PREFIX = "starred_pages_account_"
    private const val KEY = "urls"

    fun getAll(context: Context, slot: Int): List<String> {
        val raw = context.getSharedPreferences(PREFS_PREFIX + slot, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    fun isStarred(context: Context, slot: Int, url: String): Boolean = getAll(context, slot).contains(url)

    fun toggle(context: Context, slot: Int, url: String): Boolean {
        val list = getAll(context, slot).toMutableList()
        val nowStarred: Boolean
        if (list.contains(url)) {
            list.remove(url); nowStarred = false
        } else {
            list.add(0, url); nowStarred = true
        }
        context.getSharedPreferences(PREFS_PREFIX + slot, Context.MODE_PRIVATE).edit()
            .putString(KEY, list.joinToString("\n")).apply()
        return nowStarred
    }
}
