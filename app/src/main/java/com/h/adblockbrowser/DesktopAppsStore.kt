package com.h.adblockbrowser

import android.content.Context

/** Danh sách gói app người dùng đã "Thêm vào Điện thoại" bằng cách NHẤN GIỮ (trong trang "ứng
 *  dụng" ở Start, hoặc nhấn giữ ngay icon trên trang Điện thoại để gỡ). Các app này hiện thành
 *  icon TỰ DO kéo-thả trên trang "Điện thoại" (DesktopActivity).
 *
 *  QUAN TRỌNG: đây là danh sách TÁCH BIỆT HOÀN TOÀN với [PinnedAppsStore] (danh sách "Ghim vào
 *  start" - Live Tile trên trang Start). 2 trang trước đây dùng CHUNG 1 danh sách khiến ghim 1
 *  app luôn tự xuất hiện ở CẢ 2 nơi - không đúng ý muốn "1 app có thể chỉ ở Start, chỉ ở Điện
 *  thoại, hoặc ở cả 2 nếu người dùng chủ động thêm riêng từng nơi". Vị trí kéo-thả tự do trên
 *  trang Điện thoại vẫn lưu riêng ở [DesktopIconStore] như cũ. */
object DesktopAppsStore {
    private const val PREFS = "pinned_apps_desktop"
    private const val KEY = "packages"

    fun getAll(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    fun isAdded(context: Context, pkgName: String): Boolean = getAll(context).contains(pkgName)

    fun add(context: Context, pkgName: String) {
        val list = getAll(context).toMutableList()
        if (!list.contains(pkgName)) {
            list.add(pkgName)
            save(context, list)
        }
    }

    fun remove(context: Context, pkgName: String) {
        val list = getAll(context).toMutableList()
        if (list.remove(pkgName)) save(context, list)
    }

    private fun save(context: Context, list: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, list.joinToString("\n")).apply()
    }
}
