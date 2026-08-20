package com.h.adblockbrowser

import android.content.Context

/** Danh sách gói app người dùng đã ĐÁNH DẤU SAO bằng cách NHẤN GIỮ trong trang "ứng dụng" (trang
 *  phải) - KHÁC với [PinnedAppsStore] ("Ghim vào start", hiện thêm tile trên trang "start"). App
 *  đánh dấu sao vẫn nằm nguyên trong trang "ứng dụng", chỉ được XẾP LÊN 1 NHÓM RIÊNG "★ ĐÃ ĐÁNH
 *  DẤU SAO" ở ĐẦU trang (trước mọi danh mục khác) để tìm nhanh những app dùng thường xuyên mà
 *  không cần ghim hẳn lên "start" (ví dụ máy đã đủ tile trên start, hoặc chỉ muốn app đó dễ tìm
 *  hơn trong danh sách dài mà không chiếm chỗ trên Live Tile). Lưu VĨNH VIỄN qua SharedPreferences
 *  theo thứ tự đã đánh dấu (đánh dấu thêm sẽ nối vào cuối danh sách). */
object StarredAppsStore {
    private const val PREFS = "starred_apps_list"
    private const val KEY = "packages"

    fun getAll(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    fun isStarred(context: Context, pkgName: String): Boolean = getAll(context).contains(pkgName)

    fun star(context: Context, pkgName: String) {
        val list = getAll(context).toMutableList()
        if (!list.contains(pkgName)) {
            list.add(pkgName)
            save(context, list)
        }
    }

    fun unstar(context: Context, pkgName: String) {
        val list = getAll(context).toMutableList()
        if (list.remove(pkgName)) save(context, list)
    }

    private fun save(context: Context, list: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, list.joinToString("\n")).apply()
    }
}
