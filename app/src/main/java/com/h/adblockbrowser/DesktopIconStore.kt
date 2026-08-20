package com.h.adblockbrowser

import android.content.Context

/** Lưu vị trí (x, y dạng PHÂN SỐ 0f..1f theo chiều rộng/cao vùng desktop, để tự thích ứng mọi
 *  cỡ màn hình khi xoay máy hay đổi thiết bị) của từng icon ứng dụng người dùng đã KÉO-THẢ TỰ DO
 *  trên màn "Điện thoại" (DesktopActivity) - đúng kiểu Desktop Windows thật: icon nằm tự do bất
 *  kỳ đâu người dùng kéo tới, KHÔNG bị ép vào lưới cố định như Live Tile trang Start (đó là điểm
 *  khác biệt cốt lõi giữa 2 màn: Start = lưới Live Tile cố định kiểu WP, Desktop = icon tự do
 *  kiểu màn hình chính Android thường thấy). */
object DesktopIconStore {
    private const val PREFS = "desktop_icon_positions"

    fun getPosition(context: Context, pkgName: String): Pair<Float, Float>? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(pkgName, null) ?: return null
        val parts = raw.split(",")
        if (parts.size != 2) return null
        return try {
            Pair(parts[0].toFloat(), parts[1].toFloat())
        } catch (e: Exception) {
            null
        }
    }

    fun setPosition(context: Context, pkgName: String, xFrac: Float, yFrac: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(pkgName, "$xFrac,$yFrac").apply()
    }
}
