package com.h.adblockbrowser

import android.content.Context

/** Danh sách các "icon" (app đã "Thêm vào Điện thoại" HOẶC ô đồng hồ, id đặc biệt
 *  [DesktopActivity.CLOCK_ID]) mà người dùng đã KÉO-THẢ TỪ vùng desktop tự do THẢ VÀO THANH DOCK
 *  DỌC cạnh phải trang Điện thoại (DesktopActivity) - TÁCH BIỆT với 5 lối tắt CỐ ĐỊNH của app
 *  (YouTube/Quản lý tệp/Cài đặt/Máy tính/Đồng hồ ở [ShortcutsRepository] - LUÔN có mặt trong
 *  dock, không kéo/gỡ được) - đây là các mục NGƯỜI DÙNG TỰ CHỌN thêm vào bằng cách kéo, xếp
 *  theo ĐÚNG THỨ TỰ đã thêm (thêm sau -> nằm dưới trong dock). Kéo icon từ vùng desktop tự do
 *  THẢ vào dock -> thêm vào đây (xem DesktopActivity.attachDrag) và icon đó biến mất khỏi vùng
 *  tự do, chỉ còn hiện trong dock; NHẤN GIỮ icon NGAY TRONG dock -> gỡ khỏi đây, icon trở lại
 *  vùng desktop tự do (giữ nguyên vị trí tự do đã lưu trước đó ở [DesktopIconStore], nếu có). */
object DesktopDockStore {
    private const val PREFS = "desktop_dock_items"
    private const val KEY = "ids"

    fun getAll(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    fun contains(context: Context, id: String): Boolean = getAll(context).contains(id)

    fun add(context: Context, id: String) {
        val list = getAll(context).toMutableList()
        if (!list.contains(id)) {
            list.add(id)
            save(context, list)
        }
    }

    fun remove(context: Context, id: String) {
        val list = getAll(context).toMutableList()
        if (list.remove(id)) save(context, list)
    }

    private fun save(context: Context, list: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, list.joinToString("\n")).apply()
    }
}
