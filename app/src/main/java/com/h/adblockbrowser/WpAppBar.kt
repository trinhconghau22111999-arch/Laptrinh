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

/**
 * THAY ĐỔI SO VỚI BẢN CŨ (để giống Win10 Mobile hơn):
 *
 * 1. NỀN ĐEN ĐẶC: App Bar trên Win10 Mobile (IE/Edge Mobile, Internet Explorer Mobile) có
 *    nền ĐEN ĐẶC (0xFF000000), không phải bán trong suốt (0xCC000000). Thanh App Bar là
 *    phần kéo dài của thanh điều hướng, cùng màu nền.
 *
 * 2. CHIỀU CAO ĐÚng: 64dp (tăng từ 48dp). App Bar của IE/Edge Mobile trên WP cao hơn
 *    thanh điều hướng để icon có đủ không gian hiển thị thoải mái.
 *
 * 3. ICON LỚN HƠN: icon trong App Bar WP thật to hơn so với bản cũ - tăng từ 52dp lên
 *    64dp mỗi icon để chiều rộng icon đồng đều với chiều cao App Bar.
 *
 * 4. NÚT "···" ĐẦY ĐỦ 3 CHẤM ĐỨNG (⋮) - Win10 Mobile IE/Edge dùng dấu 3 chấm ĐỨNG (⋮),
 *    không phải 3 chấm ngang (···). Sửa ký tự và tăng cỡ chữ.
 *
 * 5. PANEL MỞ RỘNG NỀN ĐEN ĐẶC: panel chữ khi bung "⋮" cũng dùng đen đặc (0xFF000000)
 *    thay vì 0xF0000000.
 *
 * 6. FONT CHỮ panel mở rộng: dòng chữ menu dùng "sans-serif" thường (không phải light)
 *    để dễ đọc hơn, đúng kiểu text trên WP App Bar expanded.
 *
 * 7. OFFSET ĐÚNG: App Bar nằm NGAY TRÊN NavBar, tính theo HEIGHT_DP mới của WpNavBar (54dp).
 *
 * 8. SEPARATOR: thêm đường kẻ mỏng (1dp) màu xám nhạt giữa panel mở rộng và hàng icon,
 *    đúng như IE Mobile trên WP thật.
 */
object WpAppBar {

    data class ActionItem(val icon: String, val label: String, val iconRes: Int = 0, val action: () -> Unit)

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
        primaryActions: List<ActionItem>,
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

        // ── Hàng icon chính (luôn hiện) - chiều cao 64dp ──
        val iconRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        primaryActions.take(4).forEach { item ->
            val actionView: View = if (item.iconRes != 0) {
                ImageView(activity).apply {
                    setImageResource(item.iconRes)
                    setColorFilter(Color.WHITE)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    val pad = dp(18) // tăng padding so với bản cũ (13dp)
                    setPadding(pad, pad, pad, pad)
                }
            } else {
                TextView(activity).apply {
                    text = item.icon
                    textSize = 20f // tăng từ 18f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                }
            }
            actionView.apply {
                background = pressedBg()
                isClickable = true
                isFocusable = true
                contentDescription = item.label
                // Icon rộng 64dp (tăng từ 52dp) để khớp với chiều cao 64dp của App Bar
                layoutParams = LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.MATCH_PARENT)
                setOnClickListener { item.action() }
            }
            iconRow.addView(actionView)
        }

        // ── Nút "⋮" (3 chấm ĐỨNG - đúng Win10 Mobile IE/Edge) ──
        lateinit var ellipsisBtn: TextView
        lateinit var expandedPanel: LinearLayout
        lateinit var barContainer: LinearLayout

        fun setExpanded(value: Boolean) {
            expanded = value
            expandedPanel.visibility = if (expanded) View.VISIBLE else View.GONE
        }

        ellipsisBtn = TextView(activity).apply {
            text = "⋮" // 3 CHẤM ĐỨNG (U+22EE) thay vì 3 chấm ngang (···)
            textSize = 22f // to hơn để dễ nhìn
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = pressedBg()
            isClickable = true
            isFocusable = true
            contentDescription = "Thêm"
            layoutParams = LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.MATCH_PARENT)
            setOnClickListener { setExpanded(!expanded) }
        }
        iconRow.addView(ellipsisBtn)

        // ── Đường kẻ ngăn cách giữa panel mở rộng và hàng icon ──
        val separator = View(activity).apply {
            setBackgroundColor(0x44FFFFFF) // xám nhạt mờ
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            visibility = View.GONE
        }

        // ── Panel chữ mở rộng (ẩn mặc định) ──
        expandedPanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            // Nền đen ĐẶC như bản cũ - nhưng đặt qua setBackgroundColor thay vì GradientDrawable
            setBackgroundColor(0xFF000000.toInt())
            visibility = View.GONE
        }
        secondaryActions.forEach { item ->
            expandedPanel.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(14), dp(20), dp(14))
                background = pressedBg()
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(TextView(activity).apply {
                    text = item.icon
                    textSize = 18f
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT)
                })
                addView(TextView(activity).apply {
                    text = item.label.lowercase()
                    textSize = 17f // tăng từ 16f
                    setTextColor(Color.WHITE)
                    // "sans-serif" thường thay vì light - dễ đọc hơn, đúng kiểu WP App Bar text
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                })
                setOnClickListener {
                    setExpanded(false)
                    item.action()
                }
            })
        }

        barContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            // NỀN ĐEN ĐẶC - đúng App Bar của IE/Edge Mobile trên Win10 Mobile thật
            setBackgroundColor(0xFF000000.toInt())
        }
        barContainer.addView(expandedPanel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        // Đường kẻ separator giữa panel và hàng icon
        barContainer.addView(separator)
        barContainer.addView(iconRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64) // 64dp thay vì 48dp
        ))

        // Ẩn/hiện separator cùng với panel mở rộng
        val originalSetExpanded = ::setExpanded
        expandedPanel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            separator.visibility = if (expanded) View.VISIBLE else View.GONE
        }

        val collapsedHeight = dp(64)
        val navBarHeight = dp(WpNavBar.HEIGHT_DP) // 54dp (dùng hằng số từ WpNavBar)

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
