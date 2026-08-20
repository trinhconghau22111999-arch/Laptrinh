package com.h.adblockbrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/** Thanh điều hướng 3 NÚT CỐ ĐỊNH kiểu Windows Phone / Windows 10 Mobile thật: ◁ Back / ⊞ Start
 *  / 🔍 Search, thay cho [FloatingBackButton] (1 nút tròn nổi kéo-thả tự do khắp màn hình, vốn
 *  mô phỏng nút Home vật lý của iPhone đời cũ - SAI hẳn ẩn dụ điều hướng của WP).
 *
 *  SỬA ĐỂ ĐÚNG WP THẬT (khác bản trước): thanh nút phần mềm on-screen của Windows 10 Mobile trải
 *  dài HẾT CHIỀU NGANG màn hình, 3 nút chia đều theo tỉ lệ 1/3 - 1/3 - 1/3 (Back luôn ở SÁT MÉP
 *  TRÁI, Search luôn ở SÁT MÉP PHẢI, Start ở giữa) - KHÔNG phải 1 cụm nhỏ 3 nút dính sát nhau nổi
 *  giữa màn hình như trước (nhìn giống 1 "viên thuốc" nổi kiểu iOS/gesture-pill hơn là 3 nút
 *  cứng thật của WP). Icon cũng đổi từ glyph Unicode (◁ ⊞ 🔍 - hiển thị lệch cỡ/kiểu, có màu tuỳ
 *  bộ font emoji từng máy) sang vector đơn sắc trắng phẳng (xem ic_wp_back/start/search.xml) để
 *  luôn đúng 1 kiểu, mọi máy như nhau, đúng tinh thần "flat design" của Metro UI.
 *
 *  [onSearch] = null -> chỉ hiện 2 nút Back/Start (WP cũng cho phép cấu hình thiếu nút Search ở
 *  vài máy/thiết lập, nên bỏ bớt 1 nút không hỏng cảm giác chung).
 *
 *  Kỹ thuật giống hệt [FloatingBackButton]: add vào 1 WINDOW RIÊNG bằng WindowManager (không
 *  phải view con bình thường của Activity) để LUÔN nổi trên cả video HTML5 toàn màn hình (xem
 *  giải thích chi tiết ở đầu FloatingBackButton.kt) - và KHÔNG focusable để không tranh giành
 *  bàn phím với ô nhập liệu của trang web/WebView. */
object WpNavBar {

    class Handle internal constructor(
        private val wm: WindowManager,
        private val bar: View,
        private val lp: WindowManager.LayoutParams,
        private val root: ViewGroup,
        private val resyncCallback: () -> Unit
    ) {
        fun resync() {
            resyncCallback()
        }

        fun detach() {
            try {
                wm.removeViewImmediate(bar)
            } catch (e: Exception) {
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        activity: Activity,
        root: FrameLayout,
        onBack: () -> Unit,
        onStart: () -> Unit,
        onSearch: (() -> Unit)? = null
    ): Handle {
        fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

        // Icon vector 24dp đơn sắc trắng, thay cho glyph Unicode - căn giữa trong ô 1/3 chiều
        // rộng của mỗi nút bằng layout_weight=1f để 3 nút LUÔN chia đều bất kể màn hình rộng
        // bao nhiêu (đúng cách thanh nút phần mềm on-screen thật của WP tự giãn theo màn hình).
        fun navButton(iconRes: Int, desc: String, action: () -> Unit): ImageView = ImageView(activity).apply {
            setImageResource(iconRes)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dp(13)
            setPadding(pad, pad, pad, pad)
            contentDescription = desc
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT
            ).also { it.weight = 1f }
            // Chạm tối nhẹ hình chữ nhật khi bấm - đúng cảm giác "bấm phẳng" Metro, không ripple
            // tròn Material mặc định (xem lý giải tương tự ở HomeScreenManager.pressedOverlay).
            val pressed = GradientDrawable().apply { setColor(0x33FFFFFF) }
            val normal = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            background = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressed)
                addState(intArrayOf(), normal)
            }
            setOnClickListener { action() }
        }

        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(0xCC000000.toInt()) }
        }
        bar.addView(navButton(R.drawable.ic_wp_back, "Quay lại", onBack))
        bar.addView(navButton(R.drawable.ic_wp_start, "Start", onStart))
        if (onSearch != null) bar.addView(navButton(R.drawable.ic_wp_search, "Tìm kiếm", onSearch))

        val barHeight = dp(48)
        // Trải HẾT CHIỀU NGANG màn hình (không còn là 1 cụm nhỏ nổi giữa) - đúng thanh nút phần
        // mềm on-screen thật của Windows 10 Mobile. Đặt tạm = chiều rộng root lúc khởi tạo,
        // resyncCallback bên dưới sẽ cập nhật lại đúng theo thực tế mỗi khi màn hình đổi kích
        // thước (xoay máy).
        var barWidth = root.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels

        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = WindowManager.LayoutParams(
            barWidth, barHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            // Xem giải thích đầy đủ ở FloatingBackButton.kt - panel KHÔNG BAO GIỜ được nhận
            // input/window focus, chỉ nhận sự kiện chạm nút, để không làm mất bàn phím ảo khi
            // đang gõ vào ô nhập liệu của trang web.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        var windowAdded = false
        fun ensureWindowAdded() {
            if (windowAdded) return
            val token = activity.window?.decorView?.windowToken ?: return
            lp.token = token
            try {
                wm.addView(bar, lp)
                windowAdded = true
            } catch (e: Exception) {
                // Token chưa sẵn sàng - resync() gọi lại sau (root.post / onResume) sẽ tự thử lại.
            }
        }

        // Luôn TRẢI HẾT CHIỀU NGANG, sát cạnh dưới màn hình - không kéo-thả (đúng vị trí cố định
        // của thanh nút phần mềm on-screen WP thật), tự tính lại mỗi khi kích thước root đổi
        // (xoay máy) để 3 nút luôn chia đều theo đúng bề ngang MỚI.
        val resyncCallback = {
            ensureWindowAdded()
            if (root.width > 0 && root.height > 0) {
                barWidth = root.width
                lp.width = barWidth
                lp.x = 0
                lp.y = (root.height - barHeight).coerceAtLeast(0)
                try {
                    wm.updateViewLayout(bar, lp)
                } catch (e: Exception) {
                }
            }
        }

        root.post { resyncCallback() }
        // Activity của app khai báo configChanges nên KHÔNG bị huỷ/tạo lại lúc xoay máy - phải
        // tự lắng nghe root đổi kích thước để canh lại giữa cạnh dưới theo kích thước MỚI (xem
        // lý giải tương tự ở FloatingBackButton.applyFixedPosition).
        root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val sizeChanged = (right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)
            if (sizeChanged) resyncCallback()
        }

        return Handle(wm, bar, lp, root, resyncCallback)
    }
}
