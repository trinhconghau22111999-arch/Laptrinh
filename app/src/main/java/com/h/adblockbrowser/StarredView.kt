package com.h.adblockbrowser

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

/**
 * Màn hình "đã gắn dấu" (star) - dựng THEO ĐÚNG bố cục lưới "Live Tile" 3 cột giống HỆT trang
 * "start" (xem HomeScreenManager.buildStartPage): mỗi trang web đã gắn dấu hiện thành 1 ô vuông
 * phẳng, màu accent xoay vòng đúng bảng màu Live Tile (ThemePrefs.PALETTE), icon ngôi sao trắng
 * góc trên-trái, nhãn là tên miền rút gọn của URL ở góc dưới-trái - cùng kiểu chữ/kích thước
 * dùng chung với tile trên "start" để 2 trang có cảm giác ĐỒNG BỘ tuyệt đối.
 *
 *   - Bấm vào tile  -> mở trang đó thành tab mới, tự đóng màn "đã gắn dấu".
 *   - NHẤN GIỮ tile -> hiện menu phẳng "Bỏ dấu trang" (bố cục PopupWindow giống hệt menu
 *     ghim/bỏ ghim của trang "start" - xem HomeScreenManager.showPinContextMenu), xoá xong lưới
 *     tự vẽ lại NGAY, không cần đóng/mở lại màn hình.
 *
 * DÙNG CHUNG cho cả 2 nơi có tính năng gắn dấu trang hiện có - Nhiều tài khoản
 * (AccountBrowserActivityBase, qua AccountStarredStore) và Ẩn danh (IncognitoActivity, qua
 * IncognitoStarredStore) - đúng theo cách TaskView.kt đang dùng chung cho Đa nhiệm ở 2 nơi đó.
 *
 * KHÔNG dùng cửa sổ hệ thống riêng (WindowManager) - chỉ là 1 View thường add vào FrameLayout
 * gốc (root = "outer"/"overlayRoot" của activity gọi), đè lên nội dung duyệt web bên dưới nhưng
 * vẫn NẰM DƯỚI thanh WpNavBar (cửa sổ hệ thống TYPE_APPLICATION_PANEL riêng, luôn nổi trên cùng)
 * - xem thêm giải thích chi tiết ở TaskView.kt. Các activity gọi hàm này cần tự kiểm tra
 * Handle.isShowing ở onBackPressed() để phím Back ĐÓNG màn "đã gắn dấu" trước, thay vì lùi
 * trang web, khi màn này đang hiển thị (giống hệt cách xử lý Đa nhiệm hiện có).
 */
object StarredView {

    class Handle internal constructor(
        private val root: ViewGroup,
        private val overlay: View,
        private val renderList: (List<String>) -> Unit
    ) {
        /** true nếu màn "đã gắn dấu" đang hiển thị (chưa bị dismiss). */
        val isShowing: Boolean get() = overlay.parent != null

        /** Vẽ lại lưới tile sau khi bỏ dấu 1 trang - KHÔNG tạo lại toàn bộ overlay, tránh
         *  giật/nháy màn hình, giống hệt cách TaskView.Handle.update() hoạt động. */
        fun update(urls: List<String>) {
            if (isShowing) renderList(urls)
        }

        fun dismiss() {
            if (overlay.parent != null) root.removeView(overlay)
        }
    }

    /** Rút gọn URL về tên miền để làm nhãn tile - VD "https://www.youtube.com/watch?v=xyz"
     *  -> "youtube.com" - ngắn gọn vừa khít 1 ô tile vuông, thay vì tràn cả đường link dài. */
    private fun domainLabel(url: String): String {
        val host = try { Uri.parse(url).host } catch (e: Exception) { null }
        return (host ?: url).removePrefix("www.")
    }

    /** Hiệu ứng khi bấm: tối nhẹ toàn ô hình chữ nhật, KHÔNG dùng ripple tròn Material mặc
     *  định - đúng cảm giác "bấm phẳng" Metro UI, giống hệt pressedOverlay() ở HomeScreenManager. */
    private fun pressedOverlay(): Drawable {
        val pressedState = GradientDrawable().apply { setColor(0x33FFFFFF) }
        val normalState = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedState)
            addState(intArrayOf(), normalState)
        }
    }

    fun show(
        activity: Activity,
        root: FrameLayout,
        urls: List<String>,
        onOpen: (String) -> Unit,
        onRemove: (String) -> Unit
    ): Handle {
        fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()
        val tilePalette = ThemePrefs.PALETTE

        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(0xFF000000.toInt())
            // Chặn chạm xuyên qua xuống nội dung duyệt web bên dưới trong lúc màn này hiển thị.
            isClickable = true
        }

        val scrollView = ScrollView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Đáy chừa thêm đúng chiều cao WpNavBar - thanh điều hướng nổi là cửa sổ hệ thống
            // riêng luôn đè lên trên cùng, không chừa chỗ thì tile cuối cùng của lưới bị che.
            setPadding(dp(20), dp(40), dp(20), dp(24) + dp(WpNavBar.HEIGHT_DP))
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        // Tiêu đề kiểu Hub/Pivot header của WP - ĐÚNG kiểu chữ/cỡ/màu với tiêu đề "start" (xem
        // HomeScreenManager.sectionHeader) để 2 trang có cảm giác đồng bộ tuyệt đối.
        content.addView(TextView(activity).apply {
            text = "đã gắn dấu"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(dp(2), dp(4), dp(2), dp(10))
        })

        val emptyLabel = TextView(activity).apply {
            text = "Chưa có trang nào gắn dấu"
            textSize = 15f
            setTextColor(0xFF999999.toInt())
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(dp(2), dp(20), dp(2), dp(2))
        }

        val tilesContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        content.addView(tilesContainer)
        content.addView(emptyLabel)

        fun showRemoveMenu(anchor: View, url: String) {
            lateinit var popup: PopupWindow
            val item = TextView(activity).apply {
                text = "Bỏ dấu trang"
                textSize = 16f
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                setPadding(dp(22), dp(16), dp(22), dp(16))
                minWidth = dp(200)
                isClickable = true
                isFocusable = true
                background = pressedOverlay()
                setOnClickListener {
                    onRemove(url)
                    popup.dismiss()
                }
            }
            val menuBox = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                // Nền đen tuyệt đối phẳng, viền mảnh xám đậm thay vì bóng đổ - đúng cảm giác
                // context menu WP thật, giống hệt showPinContextMenu() ở HomeScreenManager.
                background = GradientDrawable().apply {
                    setColor(0xFF1A1A1A.toInt())
                    setStroke(dp(1), 0xFF3A3A3A.toInt())
                }
                addView(item)
            }
            popup = PopupWindow(
                menuBox, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true
            ).apply {
                elevation = 0f
                animationStyle = 0
                isOutsideTouchable = true
            }
            popup.showAsDropDown(anchor, 0, dp(4))
        }

        // TỰ XẾP HÀNG THỦ CÔNG bằng LinearLayout lồng nhau, đúng kỹ thuật addTileToGrid() ở
        // HomeScreenManager.buildStartPage() - lưới 3 cột, mỗi tile luôn vuông 1 đơn vị.
        fun renderList(currentUrls: List<String>) {
            tilesContainer.removeAllViews()
            emptyLabel.visibility = if (currentUrls.isEmpty()) View.VISIBLE else View.GONE

            val tileUnitsPerRow = 3
            var currentRow: LinearLayout? = null
            var unitsInRow = 0
            fun addTileToGrid(tileView: View) {
                if (currentRow == null || unitsInRow + 1 > tileUnitsPerRow) {
                    currentRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                    tilesContainer.addView(currentRow, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(96)
                    ))
                    unitsInRow = 0
                }
                currentRow!!.addView(tileView, LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
                ).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) })
                unitsInRow += 1
            }

            currentUrls.forEachIndexed { index, url ->
                val tileColor = tilePalette[index % tilePalette.size]
                val tile = FrameLayout(activity).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(tileColor)
                    }
                    isClickable = true
                    isFocusable = true
                    isLongClickable = true
                    foreground = pressedOverlay()
                }
                tile.addView(ImageView(activity).apply {
                    setImageResource(R.drawable.ic_wp_star_filled)
                    imageTintList = ColorStateList.valueOf(Color.WHITE)
                    layoutParams = FrameLayout.LayoutParams(dp(28), dp(28)).also {
                        it.gravity = Gravity.TOP or Gravity.START
                        it.leftMargin = dp(10); it.topMargin = dp(10)
                    }
                })
                tile.addView(TextView(activity).apply {
                    text = domainLabel(url)
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also {
                        it.gravity = Gravity.BOTTOM or Gravity.START
                        it.leftMargin = dp(8); it.bottomMargin = dp(6); it.rightMargin = dp(8)
                    }
                })
                tile.setOnClickListener { onOpen(url) }
                tile.setOnLongClickListener { anchor -> showRemoveMenu(anchor, url); true }
                addTileToGrid(tile)
            }
        }

        renderList(urls)
        scrollView.addView(content)
        overlay.addView(scrollView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        return Handle(root, overlay, ::renderList)
    }
}
