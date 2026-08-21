package com.h.adblockbrowser

import android.content.Context

/** Kích cỡ 1 ô Live Tile trên trang "start" - đúng 4 cỡ chuẩn của Windows Phone/Windows 10
 *  Mobile thật (Small/Medium.../Wide/Large), tính theo "đơn vị" ô vuông trong lưới 3 cột:
 *   - NHO  (Nhỏ): 1x1 - vuông, cỡ mặc định.
 *   - RONG (Rộng): 2x1 - rộng gấp đôi bề ngang, cao như bình thường.
 *   - CAO  (Cao): 1x2 - cao gấp đôi bề dọc, rộng như bình thường.
 *   - TO   (To): 2x2 - gấp đôi CẢ bề ngang lẫn bề dọc. */
enum class TileSize(val w: Int, val h: Int, val label: String) {
    NHO(1, 1, "Nhỏ"),
    RONG(2, 1, "Rộng"),
    CAO(1, 2, "Cao"),
    TO(2, 2, "To")
}

/** Lưu kích cỡ người dùng đã chọn cho TỪNG ô tile trên trang "start" - cả ô CỐ ĐỊNH (YouTube,
 *  Ẩn danh, Cài đặt...) LẪN app người dùng tự ghim - qua NHẤN GIỮ tile rồi chọn "Đổi kích cỡ".
 *  Lưu VĨNH VIỄN qua SharedPreferences theo "khoá tile":
 *   - Ô cố định: dùng đúng [ShortcutItem.key] (vd "youtube", "settings"...).
 *   - App người dùng ghim: dùng "pkg:<packageName>" - thêm tiền tố "pkg:" để KHÔNG BAO GIỜ
 *     trùng với khoá ô cố định ở trên (khoá ô cố định không chứa dấu ":"). */
object TileSizeStore {
    private const val PREFS = "tile_sizes"

    private fun keyForPackage(pkgName: String) = "pkg:$pkgName"

    /** [default]: cỡ dùng khi người dùng CHƯA từng đổi kích cỡ tile này (vd YouTube/Nhiều
     *  T.khoản mặc định "Rộng" ngay từ đầu, các ô còn lại mặc định "Nhỏ" - xem [wideKeys] ở
     *  [HomeScreenManager.buildStartPage]). */
    fun get(context: Context, key: String, default: TileSize): TileSize {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)
            ?: return default
        return try { TileSize.valueOf(saved) } catch (e: Exception) { default }
    }

    fun getForPackage(context: Context, pkgName: String, default: TileSize): TileSize =
        get(context, keyForPackage(pkgName), default)

    fun set(context: Context, key: String, size: TileSize) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key, size.name).apply()
    }

    fun setForPackage(context: Context, pkgName: String, size: TileSize) {
        set(context, keyForPackage(pkgName), size)
    }
}
