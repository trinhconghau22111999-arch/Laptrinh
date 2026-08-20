package com.h.adblockbrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Thanh điều hướng 3 NÚT CỐ ĐỊNH kiểu Windows Phone / Windows 10 Mobile thật: ◁ Back / ⊞ Start
 *  / 🔍 Search, LUÔN nằm giữa cạnh dưới màn hình - đúng đúng 3 nút cứng/cảm ứng vật lý của điện
 *  thoại WP thật, thay cho [FloatingBackButton] (1 nút tròn nổi kéo-thả tự do khắp màn hình,
 *  vốn mô phỏng nút Home vật lý của iPhone đời cũ - SAI hẳn ẩn dụ điều hướng của WP).
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

        fun navButton(icon: String, desc: String, action: () -> Unit): TextView = TextView(activity).apply {
            text = icon
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            contentDescription = desc
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.MATCH_PARENT)
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
        bar.addView(navButton("◁", "Quay lại", onBack))
        bar.addView(navButton("⊞", "Start", onStart))
        if (onSearch != null) bar.addView(navButton("🔍", "Tìm kiếm", onSearch))

        val barHeight = dp(48)
        val barWidth = dp(64) * (if (onSearch != null) 3 else 2)

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

        // Luôn ở CHÍNH GIỮA cạnh dưới màn hình - không kéo-thả (đúng vị trí cố định của 3 nút
        // cứng/cảm ứng WP thật), tự tính lại mỗi khi kích thước root đổi (xoay máy).
        val resyncCallback = {
            ensureWindowAdded()
            if (root.width > 0 && root.height > 0) {
                lp.x = ((root.width - barWidth) / 2).coerceAtLeast(0)
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
