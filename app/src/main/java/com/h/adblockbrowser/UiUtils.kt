package com.h.adblockbrowser

import android.app.Activity
import android.widget.TextView

fun Activity.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

/** Lấy chiều cao status bar thật (px) để tránh nội dung bị che.
 *  QUAN TRỌNG: resource "status_bar_height" là resource ẩn của hệ thống, KHÔNG đảm bảo đúng
 *  trên mọi ROM (đặc biệt MIUI) hoặc khi app đang chạy ở chế độ cửa sổ nổi/pop-up - có thể trả
 *  về giá trị SAI, rất lớn, khiến các thanh top bar trong app bị đẩy xuống giữa màn hình. Vì
 *  vậy LUÔN giới hạn (clamp) kết quả trong khoảng hợp lý của 1 status bar thật (tối đa 60dp -
 *  kể cả máy có tai thỏ/đục lỗ cũng không status bar nào cao hơn mức này). */
fun Activity.statusBarHeight(): Int {
    val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
    val raw = if (resId > 0) resources.getDimensionPixelSize(resId) else dp(24)
    val maxReasonable = dp(60)
    return if (raw in 1..maxReasonable) raw else dp(24)
}

/** Mũi tên back nhỏ (◀) dùng ở các màn phụ không có FloatingBackButton toàn app (vd. Quản lý
 *  tệp) - mặc định bấm vào sẽ finish() màn hiện tại, có thể truyền hành vi khác (vd. lùi 1 cấp
 *  thư mục thay vì thoát hẳn). */
fun Activity.buildBackArrow(onBack: () -> Unit = { finish() }): TextView =
    TextView(this).apply {
        text = "◀"
        textSize = 32f
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setBackgroundColor(0x66000000)
        isClickable = true
        isFocusable = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setOnClickListener { onBack() }
    }
