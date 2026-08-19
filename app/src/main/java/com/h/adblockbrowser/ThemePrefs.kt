package com.h.adblockbrowser

import android.content.Context
import android.graphics.Color

/** Màu NHẤN (accent) dùng chung cho TOÀN app - người dùng chọn ở Cài đặt > Giao diện, thay vì
 *  từng màn hình tự hard-code cứng "0xFF0078D7" (Cobalt) rải rác như trước đây (khoá màn hình,
 *  lịch, đồng hồ, máy tính, quản lý tệp, ẩn danh, khoá ứng dụng, hình vẽ mở khoá, trang chủ...).
 *  Lưu vào 1 SharedPreferences riêng dùng chung (đọc từ file chung qua applicationContext, không
 *  phải bộ nhớ riêng từng Activity) để đổi màu ở Cài đặt là mọi màn hình MỞ SAU đó đều lên đúng
 *  màu mới ngay, không cần khởi động lại app.
 *
 *  [PALETTE]: đúng 20 màu Accent/Live Tile gốc của Windows Phone / Windows 10 Mobile, đúng thứ
 *  tự xuất hiện trong bảng chọn màu hệ thống thật (Settings > start+theme > màu nền/màu nhấn) -
 *  dùng chung cho CẢ lưới chọn màu ở màn Cài đặt LẪN vòng xoay màu của các ô Live Tile trên
 *  trang chủ (xem [HomeScreenManager]) để cả app chỉ có 1 bảng màu duy nhất, nhất quán, thay vì
 *  mỗi nơi tự bịa 1 bảng riêng như trước (trang chủ trước đây chỉ có 8 màu, không khớp bảng màu
 *  20 màu đã khai báo sẵn trong values.xml nhưng chưa nơi nào dùng tới). */
object ThemePrefs {
    private const val PREFS = "theme_prefs"
    private const val KEY_ACCENT = "accent_color"

    /** Cobalt - accent mặc định gốc của Windows Phone, giữ nguyên giá trị cũ đã hard-code khắp
     *  app từ trước để KHÔNG đổi giao diện của ai chưa từng vào Cài đặt đổi màu. */
    const val DEFAULT_ACCENT = 0xFF0078D7.toInt()

    val PALETTE: IntArray = intArrayOf(
        0xFFA4C400.toInt(), // Vàng chanh (Lime)
        0xFF60A917.toInt(), // Xanh lá (Green)
        0xFF008A00.toInt(), // Ngọc lục bảo (Emerald)
        0xFF00ABA9.toInt(), // Xanh ngọc (Teal)
        0xFF1BA1E2.toInt(), // Lam nhạt (Cyan)
        DEFAULT_ACCENT,      // Xanh coban (Cobalt) - mặc định
        0xFF6A00FF.toInt(), // Chàm (Indigo)
        0xFFAA00FF.toInt(), // Tím (Violet)
        0xFFF472D0.toInt(), // Hồng (Pink)
        0xFFD80073.toInt(), // Đỏ cánh sen (Magenta)
        0xFFA20025.toInt(), // Đỏ thẫm (Crimson)
        0xFFE51400.toInt(), // Đỏ (Red)
        0xFFFA6800.toInt(), // Cam (Orange)
        0xFFF0A30A.toInt(), // Hổ phách (Amber)
        0xFFE3C800.toInt(), // Vàng (Yellow)
        0xFF825A2C.toInt(), // Nâu (Brown)
        0xFF6D8764.toInt(), // Ô liu (Olive)
        0xFF647687.toInt(), // Xám thép (Steel)
        0xFF76608A.toInt(), // Mận (Mauve)
        0xFF87794E.toInt()  // Be xám (Taupe)
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Màu nhấn hiện tại (đã lưu), mặc định [DEFAULT_ACCENT] nếu người dùng chưa từng đổi. */
    fun accent(context: Context): Int =
        prefs(context).getInt(KEY_ACCENT, DEFAULT_ACCENT)

    fun setAccent(context: Context, color: Int) {
        prefs(context).edit().putInt(KEY_ACCENT, color).apply()
    }

    /** Màu nhấn hiện tại nhưng đổi độ mờ (dùng cho các mảng nền mờ/tô sáng nhẹ theo màu nhấn,
     *  ví dụ ô ngày hôm nay trong Lịch) - [alpha] từ 0 (trong suốt) đến 255 (đục hoàn toàn). */
    fun accentWithAlpha(context: Context, alpha: Int): Int {
        val c = accent(context)
        return Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c))
    }
}
