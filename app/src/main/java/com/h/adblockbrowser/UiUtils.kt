package com.h.adblockbrowser

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
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

/** Ẩn CẢ thanh trạng thái (giờ/mạng/pin) LẪN thanh điều hướng hệ thống (3 phím Back/Home/Recent
 *  hoặc gesture bar) - toàn màn hình thật sự, đúng tinh thần Windows Phone (bản thân WP không hề
 *  có thanh điều hướng phần mềm của Android). Đa nhiệm trong app này giờ CHỈ dùng nút "Đa nhiệm"
 *  của WpNavBar (xem WpNavBar.kt/TaskView.kt) - KHÔNG còn dựa vào nút Recent/Overview của hệ
 *  thống Android nữa, nên thanh điều hướng hệ thống không cần (và không nên) hiện thường trực.
 *
 *  TRƯỚC ĐÂY hàm này (hideStatusBar) CHỈ ẩn thanh trạng thái, CỐ Ý giữ nguyên thanh điều hướng -
 *  đã đổi lại theo yêu cầu (3 phím điều hướng Android vẫn lộ ra phá vỡ giao diện WP).
 *
 *  Vẫn dùng BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE (không phải ẩn tuyệt đối, không lối thoát) -
 *  vuốt nhẹ từ mép màn hình vẫn hiện TẠM thời thanh điều hướng thật, làm lối thoát khẩn cấp an
 *  toàn (ví dụ 1 màn hình lỗi/treo không có nút Back nào dùng được) - đây là hành vi immersive
 *  tiêu chuẩn Google khuyến nghị, không phải để dùng làm cách đa nhiệm chính. */
/** ĐÃ ĐỔI THEO YÊU CẦU MỚI: KHÔNG còn ẩn thanh trạng thái/thanh điều hướng hệ thống ở bất kỳ màn
 *  hình nào nữa - để đa nhiệm (Recents) THẬT của hệ thống luôn dùng được ở mọi lúc, mọi màn hình
 *  ("luôn bật đa nhiệm của hệ thống"). Giữ lại hàm rỗng (thay vì xoá + sửa lại mọi nơi gọi nó)
 *  để không phải sửa từng Activity đang gọi [hideStatusBar()]. */
fun Activity.hideStatusBar() {
    // Cố ý để trống.
}

/** Toggle bật/tắt kiểu Windows Phone/Windows 10 Mobile thật - hình viên thuốc (pill) bo tròn 2
 *  đầu (1 trong SỐ RẤT ÍT chỗ WP dùng bo góc, khác hẳn phần còn lại của Metro vốn vuông sắc 100%):
 *  rãnh (track) RỖNG viền màu accent khi TẮT, ĐẶC màu accent khi BẬT; núm (thumb) tròn trắng
 *  nhô cao hơn rãnh 1 chút, KHÔNG đổ bóng/elevation, KHÔNG ripple tròn Material khi bấm - trượt
 *  bằng animate() đơn giản thay effect Material - dùng thay cho android.widget.Switch mặc định
 *  (Switch gốc của Android luôn có ripple + shadow quanh núm, không đúng cảm giác "phẳng" WP). */
fun Activity.buildWpToggle(initialChecked: Boolean, onToggle: (Boolean) -> Unit): FrameLayout {
    var checked = initialChecked
    val trackW = dp(46)
    val trackH = dp(20)
    val thumbD = dp(28)
    val containerW = trackW + dp(6)
    val containerH = thumbD

    val trackBg = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = trackH / 2f
    }
    fun applyTrackColor() {
        if (checked) {
            trackBg.setColor(ThemePrefs.accent(this))
            trackBg.setStroke(0, Color.TRANSPARENT)
        } else {
            trackBg.setColor(Color.TRANSPARENT)
            trackBg.setStroke(dp(2), 0xFF767676.toInt())
        }
    }
    applyTrackColor()

    val track = View(this).apply { background = trackBg }
    val thumb = View(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
    }

    val offX = 0f
    val onX = (containerW - thumbD).toFloat()
    thumb.translationX = if (checked) onX else offX

    // KHÔNG gán cứng `layoutParams = FrameLayout.LayoutParams(...)` ở đây - view này sẽ được
    // addView() vào NHIỀU LOẠI cha khác nhau tuỳ nơi gọi (LinearLayout, FrameLayout...), mà
    // layoutParams của 1 view PHẢI đúng loại của CHA nó, nếu không sẽ crash ClassCastException
    // ngay khi cha đó layout. Dùng minimumWidth/minimumHeight để tự báo kích thước mong muốn -
    // cha sẽ tự sinh đúng loại LayoutParams (kiểu WRAP_CONTENT) phù hợp với chính nó.
    val container = FrameLayout(this).apply {
        minimumWidth = containerW
        minimumHeight = containerH
        isClickable = true
        isFocusable = true
    }
    container.addView(track, FrameLayout.LayoutParams(trackW, trackH).also {
        it.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        it.leftMargin = dp(3)
    })
    container.addView(thumb, FrameLayout.LayoutParams(thumbD, thumbD).also {
        it.gravity = Gravity.CENTER_VERTICAL or Gravity.START
    })
    container.setOnClickListener {
        checked = !checked
        applyTrackColor()
        track.invalidate()
        thumb.animate().translationX(if (checked) onX else offX).setDuration(120).start()
        onToggle(checked)
    }
    return container
}
