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

/**
 * THAY ĐỔI SO VỚI BẢN CŨ (để giống Win10 Mobile hơn):
 *
 * 1. NỀN ĐÚNG WIN10 MOBILE: nền đen ĐẶC HOÀN TOÀN (0xFF000000) thay vì bán trong suốt
 *    (0xCC000000). Thanh điều hướng phần mềm trên Windows 10 Mobile luôn có nền đen đặc,
 *    không trong suốt nhìn thấy nội dung bên dưới.
 *
 * 2. CHIỀU CAO ĐÚNG: 54dp thay vì 48dp. Trên máy thật Lumia, thanh điều hướng phần mềm
 *    cao hơn một chút so với Android thông thường, khoảng 54dp là đúng hơn.
 *
 * 3. KHÔNG CÓ NÚT SEARCH PHẦN MỀM: Windows 10 Mobile đời cuối (Threshold/Redstone) đã
 *    loại bỏ nút Search cứng/mềm khỏi thanh điều hướng - chỉ còn Back và Start (Windows).
 *    Nút Search trong bản cũ của app này sai với trải nghiệm Win10 Mobile thật.
 *    → Bỏ hẳn nút Search khỏi NavBar; tính năng tìm kiếm chuyển sang App Bar (···).
 *
 * 4. ICON BACK ĐÚNG HƯỚNG: icon Back (◁) của WP/W10M là mũi tên TRÁI ĐẦY (filled chevron),
 *    không phải outline. Giữ nguyên ic_wp_back nhưng lưu ý drawable phải là filled.
 *
 * 5. ICON START (⊞ Windows logo) ở CHÍNH GIỮA không đổi - đúng WP thật.
 *
 * 6. PADDING NÚT: tăng padding icon từ 13dp lên 15dp để icon trông thoáng hơn, đúng
 *    cảm giác nút điều hướng WP thật (icon không sát mép).
 *
 * 7. RIPPLE/PRESSED STATE: giữ nguyên hình chữ nhật phẳng (0x33FFFFFF) - đúng Metro,
 *    nhưng giờ áp dụng cho TOÀN Ô NÚT (không chỉ icon) để vùng bấm rõ ràng hơn.
 */
object WpNavBar {

    /** Chiều cao NavBar đúng Win10 Mobile (dùng để WpAppBar tính offset nằm ngay trên) */
    const val HEIGHT_DP = 54

    class Handle internal constructor(
        private val wm: WindowManager,
        private val bar: View,
        private val lp: WindowManager.LayoutParams,
        private val root: ViewGroup,
        private val resyncCallback: () -> Unit
    ) {
        fun resync() = resyncCallback()

        /** ẨN thanh Back/Start/Đa nhiệm - dùng khi xem video HTML5 toàn màn hình (xem
         *  onShowCustomView() ở MainActivity/AccountBrowserActivityBase) để thanh (vốn là 1
         *  CỬA SỔ HỆ THỐNG riêng, luôn nổi trên cùng - xem attach() bên dưới) không đè lên video.
         *  GONE thay vì removeView hẳn: giữ nguyên cửa sổ đã cấp token, [show] lại tức thì không
         *  cần cấp token/addView lại (addView 2 lần trên cùng 1 token dễ lỗi WindowManager). */
        fun hide() {
            bar.visibility = View.GONE
        }

        /** HIỆN lại thanh - vuốt lên từ cạnh đáy trong lúc xem video toàn màn hình sẽ gọi hàm
         *  này (xem setupFullscreenSwipeReveal() ở MainActivity/AccountBrowserActivityBase). */
        fun show() {
            bar.visibility = View.VISIBLE
        }

        /** true nếu thanh đang bị ẩn (đang xem video toàn màn hình) - dùng để quyết định có nên
         *  tự ẩn lại sau vài giây khi người dùng vuốt lên xem thoáng qua hay không. */
        val isHidden: Boolean get() = bar.visibility != View.VISIBLE

        fun detach() {
            try { wm.removeViewImmediate(bar) } catch (e: Exception) { }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        activity: Activity,
        root: FrameLayout,
        onBack: () -> Unit,
        onStart: () -> Unit,
        /** ĐÃ BỎ nút Search khỏi NavBar (xem lý do ở comment đầu file).
         *  Tham số này giữ lại để TƯƠNG THÍCH ngược với code gọi ở MainActivity,
         *  nhưng giá trị truyền vào sẽ bị BỎ QUA - không hiện nút Search nữa. */
        @Suppress("UNUSED_PARAMETER") onSearch: (() -> Unit)? = null,
        /** Nút thứ 3 "Đa nhiệm" (Task View, xem TaskView.kt) - CHỈ hiện khi tham số này khác
         *  null. Các màn hình không có nhiều tab để chuyển qua lại (Trang chủ, Cài đặt, Lịch,
         *  Đồng hồ, Máy tính, danh sách Nhiều tài khoản...) không truyền tham số này nên
         *  NavBar vẫn chỉ có đúng 2 nút Back/Start như trước. Hiện tại chỉ 2 màn hình có
         *  nhiều tab thật sự truyền vào: Ẩn danh (IncognitoActivity) và 1 hồ sơ Nhiều tài
         *  khoản (AccountBrowserActivityBase). */
        onTaskView: (() -> Unit)? = null
    ): Handle {
        fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

        fun navButton(iconRes: Int, desc: String, action: () -> Unit): ImageView =
            ImageView(activity).apply {
                setImageResource(iconRes)
                setColorFilter(Color.WHITE)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                // Padding 15dp (tăng từ 13dp) - icon thoáng hơn, đúng cảm giác nút WP thật
                val pad = dp(15)
                setPadding(pad, pad, pad, pad)
                contentDescription = desc
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT
                ).also { it.weight = 1f }
                // Pressed state hình chữ nhật phẳng - đúng Metro, không ripple tròn Material
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
            // NỀN ĐEN ĐẶC HOÀN TOÀN - đúng thanh điều hướng phần mềm Win10 Mobile thật
            // (không phải bán trong suốt 0xCC000000 như bản cũ)
            setBackgroundColor(0xFF000000.toInt())
        }

        // MẶC ĐỊNH 2 NÚT: Back (trái) và Start/Windows (giữa/phải)
        // Win10 Mobile Threshold/Redstone đã bỏ nút Search cứng/mềm
        bar.addView(navButton(R.drawable.ic_wp_back, "Quay lại", onBack))
        bar.addView(navButton(R.drawable.ic_wp_start, "Start", onStart))
        // NÚT THỨ 3 "Đa nhiệm": chỉ thêm khi màn hình gọi có truyền onTaskView (xem doc ở tham
        // số bên trên) - tự động chia đều 3 ô nhờ weight=1f có sẵn trong navButton(), không
        // cần chỉnh gì thêm ở layout.
        if (onTaskView != null) {
            bar.addView(navButton(R.drawable.ic_wp_taskview, "Đa nhiệm", onTaskView))
        }

        val barHeight = dp(HEIGHT_DP) // 54dp thay vì 48dp
        var barWidth = root.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels

        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = WindowManager.LayoutParams(
            barWidth, barHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
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
            } catch (e: Exception) { }
        }

        val resyncCallback = {
            ensureWindowAdded()
            if (root.width > 0 && root.height > 0) {
                barWidth = root.width
                lp.width = barWidth
                lp.x = 0
                lp.y = (root.height - barHeight).coerceAtLeast(0)
                try { wm.updateViewLayout(bar, lp) } catch (e: Exception) { }
            }
        }

        root.post { resyncCallback() }
        root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val sizeChanged = (right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)
            if (sizeChanged) resyncCallback()
        }

        return Handle(wm, bar, lp, root, resyncCallback)
    }
}
