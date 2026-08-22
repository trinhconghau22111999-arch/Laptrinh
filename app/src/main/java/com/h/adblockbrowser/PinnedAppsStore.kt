package com.h.adblockbrowser

import android.content.Context

/** Danh sách gói app người dùng đã "Ghim vào start" bằng cách NHẤN GIỮ trong trang "ứng dụng"
 *  (trang phải). Các app này hiện thêm dưới dạng Live Tile trên trang "start" (trang trái),
 *  ngay sau các ô cố định (YouTube, Ẩn danh, Nhiều T.khoản...). Lưu VĨNH VIỄN qua
 *  SharedPreferences theo thứ tự đã ghim (ghim thêm sẽ nối vào cuối danh sách).
 *
 *  Đây là danh sách RIÊNG của trang Start. */
object PinnedAppsStore {
    private const val PREFS = "pinned_apps_start"
    private const val KEY = "packages"

    fun getAll(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    fun isPinned(context: Context, pkgName: String): Boolean = getAll(context).contains(pkgName)

    fun pin(context: Context, pkgName: String) {
        val list = getAll(context).toMutableList()
        if (!list.contains(pkgName)) {
            list.add(pkgName)
            save(context, list)
        }
    }

    fun unpin(context: Context, pkgName: String) {
        val list = getAll(context).toMutableList()
        if (list.remove(pkgName)) save(context, list)
    }

    private fun save(context: Context, list: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, list.joinToString("\n")).apply()
    }
}
