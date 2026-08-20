package com.h.adblockbrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** "App Bar" kiểu Windows Phone / Windows 10 Mobile thật: thanh phẳng bán trong suốt nằm sát
 *  đáy màn hình (ngay TRÊN 3 nút Back/Start/Search của [WpNavBar]) chứa TỐI ĐA 4 icon hành động
 *  của TRANG HIỆN TẠI (tải lại, đổi bản máy tính/di động, chia sẻ...) + 1 nút "..." bên phải.
 *
 *  Đây là mảnh còn thiếu so với WP thật: hầu hết app WP (kể cả Internet Explorer/Edge) đều có
 *  thanh này để chứa các thao tác theo NGỮ CẢNH TRANG đang xem, tách biệt hẳn với 3 nút phần
 *  cứng cố định (Back/Start/Search) vốn KHÔNG đổi theo trang. Trước đây app chỉ có 3 nút cứng
 *  nên phải giấu bớt chức năng (nút đổi bản máy tính/di động bị ẩn hẳn, xem MainActivity) vì
 *  không có chỗ nào đúng kiểu WP để đặt.
 *
 *  Hành vi ĐÚNG WP: bấm "..." -> thanh App Bar "bung" (mở rộng) LÊN TRÊN, hiện thêm danh sách
 *  chữ (icon nhỏ bên trái + tên hành động) cho các lệnh ít dùng hơn - bấm ra ngoài hoặc bấm lại
 *  "..." để thu gọn về lại chỉ còn hàng icon. */
object WpAppBar {

    /** [icon]: dự phòng bằng glyph chữ (dùng cho hàng chữ mở rộng, ít quan trọng về mặt hình
     *  ảnh vì đã có [label] đi kèm). [iconRes]: icon vector đơn sắc thật (0 = không có, dùng
     *  [icon] chữ thay thế) - LUÔN ưu tiên dùng cho 4 nút chính ở hàng dưới, vì đó là hàng CHỈ
     *  CÓ ICON không có chữ, nên icon vector chuẩn (không lệch tuỳ font emoji từng máy) quan
     *  trọng hơn nhiều so với hàng chữ mở rộng. */
    data class ActionItem(val icon: String, val label: String, val action: () -> Unit, val iconRes: Int = 0)

    class Handle internal constructor(
        private val wm: WindowManager,
        private val bar: View,
        private val lp: WindowManager.LayoutParams,
        private val root: ViewGroup,
        private val resyncCallback: () -> Unit,
        private val setVisibleCallback: (Boolean) -> Unit
    ) {
        fun resync() = resyncCallback()
        fun setVisible(visible: Boolean) = setVisibleCallback(visible)
        fun detach() {
            try { wm.removeViewImmediate(bar) } catch (e: Exception) { }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        activity: Activity,
        root: FrameLayout,
        /** Tối đa 4 nút icon LUÔN hiện ở hàng dưới (đúng giới hạn 4 icon của App Bar WP thật). */
        primaryActions: List<ActionItem>,
        /** Các dòng chữ hiện thêm khi bung "..." - không giới hạn số lượng, cuộn nếu quá dài. */
        secondaryActions: List<ActionItem>
    ): Handle {
        fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

        fun pressedBg(): StateListDrawable {
            val pressed = GradientDrawable().apply { setColor(0x33FFFFFF) }
            val normal = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            return StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressed)
                addState(intArrayOf(), normal)
            }
        }

        var expanded = false

        // ── Hàng icon (luôn hiện khi App Bar đang hiển thị) ──
        val iconRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        primaryActions.take(4).forEach { item ->
            // Icon vector đơn sắc thật (đúng kiểu App Bar WP: icon trắng phẳng trong khung tròn
            // ẩn/hiện theo trạng thái bấm) khi có iconRes; nếu không, dự phòng bằng glyph chữ.
            val actionView: View = if (item.iconRes != 0) {
                ImageView(activity).apply {
                    setImageResource(item.iconRes)
                    setColorFilter(Color.WHITE)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    val pad = dp(13)
                    setPadding(pad, pad, pad, pad)
                }
            } else {
                TextView(activity).apply {
                    text = item.icon
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                }
            }
            actionView.apply {
                background = pressedBg()
                isClickable = true
                isFocusable = true
                contentDescription = item.label
                layoutParams = LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT)
                setOnClickListener { item.action() }
            }
            iconRow.addView(actionView)
        }

        // ── Nút "..." (dấu ba chấm mở rộng) - bên phải hàng icon ──
        lateinit var ellipsisBtn: TextView
        lateinit var expandedPanel: LinearLayout
        lateinit var barContainer: LinearLayout

        fun setExpanded(value: Boolean) {
            expanded = value
            expandedPanel.visibility = if (expanded) View.VISIBLE else View.GONE
            // WP thật: dấu "..." GIỮ NGUYÊN dạng ngang dù bung hay thu gọn (không tự xoay dọc
            // thành "⋮" như trước) - chỉ có bảng chữ phía trên hiện/ẩn.
        }

        ellipsisBtn = TextView(activity).apply {
            text = "···"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = pressedBg()
            isClickable = true
            isFocusable = true
            contentDescription = "Thêm"
            layoutParams = LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT)
            setOnClickListener { setExpanded(!expanded) }
        }
        iconRow.addView(ellipsisBtn)

        // ── Panel chữ mở rộng (ẩn mặc định) - nằm PHÍA TRÊN hàng icon, nền đen đặc hơn ──
        expandedPanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF0000000.toInt())
            visibility = View.GONE
        }
        secondaryActions.forEach { item ->
            expandedPanel.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(12), dp(20), dp(12))
                background = pressedBg()
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(TextView(activity).apply {
                    text = item.icon
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT)
                })
                addView(TextView(activity).apply {
                    text = item.label.lowercase()
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                })
                setOnClickListener {
                    setExpanded(false)
                    item.action()
                }
            })
        }

        barContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(0xCC000000.toInt()) }
        }
        barContainer.addView(expandedPanel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        barContainer.addView(iconRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ))

        val collapsedHeight = dp(48)

        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = WindowManager.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        var windowAdded = false
        var isVisible = false
        val navBarHeight = dp(48) // chiều cao của WpNavBar phía dưới - App Bar phải nằm NGAY TRÊN nó

        fun ensureWindowAdded() {
            if (windowAdded) return
            val token = activity.window?.decorView?.windowToken ?: return
            lp.token = token
            try {
                wm.addView(barContainer, lp)
                windowAdded = true
            } catch (e: Exception) { }
        }

        val resyncCallback = {
            ensureWindowAdded()
            if (root.width > 0 && root.height > 0 && isVisible) {
                lp.width = root.width
                lp.x = 0
                lp.y = (root.height - navBarHeight - collapsedHeight -
                    (if (expanded) expandedPanel.height.coerceAtLeast(0) else 0)).coerceAtLeast(0)
                try { wm.updateViewLayout(barContainer, lp) } catch (e: Exception) { }
            }
        }

        val setVisibleCallback: (Boolean) -> Unit = { visible ->
            isVisible = visible
            if (!visible) setExpanded(false)
            barContainer.visibility = if (visible) View.VISIBLE else View.GONE
            root.post { resyncCallback() }
        }

        // Bung panel làm đổi chiều cao cả thanh -> phải tính lại vị trí (y) mỗi lần bung/thu gọn
        // để App Bar luôn "dính" sát trên nút Back/Start/Search, không đè hay hở ra.
        expandedPanel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> root.post { resyncCallback() } }

        barContainer.visibility = View.GONE
        root.post { resyncCallback() }
        root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val sizeChanged = (right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)
            if (sizeChanged) resyncCallback()
        }

        return Handle(wm, barContainer, lp, root, resyncCallback, setVisibleCallback)
    }
}
