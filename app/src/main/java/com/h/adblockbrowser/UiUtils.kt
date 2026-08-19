package com.h.adblockbrowser

import android.app.Activity
import android.content.Intent
import android.widget.TextView

/** Mở 1 màn hình MỚI trong app kèm hiệu ứng chuyển màn kiểu Windows Phone (màn mới trượt vào từ
 *  bên phải, màn cũ trượt ra bên trái - xem wp_slide_in_right.xml/wp_slide_out_left.xml) thay vì
 *  hiệu ứng mặc định của hệ thống (mờ dần trên hầu hết máy, không giống WP). Dùng hàm này thay
 *  cho startActivity() thường ở MỌI nơi điều hướng sang màn hình KHÁC CỦA CHÍNH APP (không dùng
 *  cho intent mở app/màn hình ngoài như Cài đặt hệ thống, trình xem tệp, trình duyệt... vì những
 *  màn đó không thuộc app này, để hệ thống tự quyết định hiệu ứng của nó). */
fun Activity.startActivityWp(intent: Intent) {
    startActivity(intent)
    @Suppress("DEPRECATION")
    overridePendingTransition(R.anim.wp_slide_in_right, R.anim.wp_slide_out_left)
}

/** Giống [startActivityWp] nhưng dùng khi cần nhận kết quả trả về (startActivityForResult). */
fun Activity.startActivityForResultWp(intent: Intent, requestCode: Int) {
    @Suppress("DEPRECATION")
    startActivityForResult(intent, requestCode)
    @Suppress("DEPRECATION")
    overridePendingTransition(R.anim.wp_slide_in_right, R.anim.wp_slide_out_left)
}

/** Đóng màn hình hiện tại kèm hiệu ứng LÙI LẠI kiểu Windows Phone (màn hiện tại trượt ra bên
 *  phải, màn phía sau trượt vào lại từ bên trái - hướng NGƯỢC với [startActivityWp]) - dùng thay
 *  cho finish() thường ở các Activity phụ của app (nút Back nổi, mũi tên ◀, phím Back cứng...). */
fun Activity.finishWp() {
    finish()
    @Suppress("DEPRECATION")
    overridePendingTransition(R.anim.wp_slide_in_left, R.anim.wp_slide_out_right)
}

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

/** Ẩn thanh trạng thái hệ thống (giờ/mạng/pin) CHO 1 MÀN CỤ THỂ - dùng ở các màn phụ (vd. Quản
 *  lý tệp) muốn dùng trọn vẹn phần không gian phía trên thay vì để trống/đè lên nội dung phía
 *  sau (ví dụ video đang mở ở app khác dạng cửa sổ nổi lộ ra phía sau vùng status bar trong suốt
 *  của app này). Thanh điều hướng hệ thống (Back/Home/Recent) GIỮ NGUYÊN, không bị ẩn - giống
 *  cách MainActivity.enableImmersiveMode() làm cho màn chính. */
fun Activity.hideStatusBar() {
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
    controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
    controller.systemBarsBehavior =
        androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}
