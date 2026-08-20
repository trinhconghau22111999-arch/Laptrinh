package com.h.adblockbrowser

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Màn hình "Đa nhiệm" (Task View) kiểu Windows 10 Mobile thật: liệt kê MỌI tab đang mở của
 * màn hình đang gọi thành các card TOÀN CHIỀU RỘNG, xếp dọc, cuộn được. Đây là widget DÙNG
 * CHUNG cho 2 màn hình DUY NHẤT hiện có nhiều tab để chuyển qua lại - Ẩn danh
 * (IncognitoActivity) và 1 hồ sơ Nhiều tài khoản (AccountBrowserActivityBase). Các màn hình
 * khác (Trang chủ, Cài đặt, Lịch, Đồng hồ, Máy tính, danh sách Nhiều tài khoản...) không có
 * khái niệm "nhiều tab" nên không gọi tới file này.
 *
 *   - Bấm vào card  -> chuyển sang xem tab đó, tự đóng Đa nhiệm.
 *   - Bấm nút ✕ trên card -> đóng NGAY tab đó tại chỗ, danh sách card tự cập nhật lại - không
 *     cần mở tab lên xem rồi mới đóng như thanh tab thu nhỏ phía trên URL trước đây.
 *
 * KHÔNG dùng cửa sổ hệ thống riêng (WindowManager) như WpNavBar - đây chỉ là 1 View thường
 * được add vào FrameLayout gốc (root, chính là "outer" của activity gọi nó) - ĐÈ LÊN
 * webContainer/tabBar hiện có nhưng vẫn NẰM DƯỚI thanh WpNavBar (thanh đó là cửa sổ hệ thống
 * TYPE_APPLICATION_PANEL riêng, luôn nổi trên cùng - xem WpNavBar.kt) nên trong lúc xem Đa
 * nhiệm người dùng vẫn bấm được Back/Start/Đa nhiệm bình thường. Các activity gọi hàm này cần
 * tự kiểm tra Handle.isShowing ở onBackPressed() để phím Back ĐÓNG Đa nhiệm trước, thay vì lùi
 * trang web, khi Đa nhiệm đang hiển thị (xem cách IncognitoActivity/AccountBrowserActivityBase
 * xử lý).
 */
object TaskView {

    /** Dữ liệu hiển thị cho 1 card - CHỈ chứa text hiển thị (tiêu đề + URL), KHÔNG giữ tham
     *  chiếu WebView trực tiếp để tránh rò rỉ bộ nhớ nếu Handle bị giữ lại lâu hơn cần thiết. */
    data class Item(val title: String, val url: String)

    class Handle internal constructor(
        private val root: ViewGroup,
        private val overlay: View,
        private val renderList: (List<Item>, Int) -> Unit
    ) {
        /** true nếu Đa nhiệm đang hiển thị (chưa bị dismiss). */
        val isShowing: Boolean get() = overlay.parent != null

        /** Vẽ lại danh sách card sau khi 1 tab bị đóng (✕) hoặc chuyển tab - KHÔNG tạo lại
         *  toàn bộ overlay, tránh giật/nháy màn hình. */
        fun update(items: List<Item>, activeIndex: Int) {
            if (isShowing) renderList(items, activeIndex)
        }

        fun dismiss() {
            if (overlay.parent != null) root.removeView(overlay)
        }
    }

    fun show(
        activity: Activity,
        root: FrameLayout,
        items: List<Item>,
        activeIndex: Int,
        onSelect: (Int) -> Unit,
        onCloseTab: (Int) -> Unit
    ): Handle {
        fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(0xFF000000.toInt())
            // Chặn chạm xuyên qua xuống webContainer/tabBar bên dưới trong lúc Đa nhiệm hiển thị.
            isClickable = true
        }

        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        column.addView(TextView(activity).apply {
            text = "Đa nhiệm"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(20), dp(16), dp(12))
        })

        val listHolder = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(
                listHolder,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        column.addView(scroll)

        // Chừa khoảng trống đáy đúng bằng chiều cao WpNavBar (xem WpNavBar.HEIGHT_DP) để card
        // cuối cùng không bị thanh điều hướng nổi che mất, KHÔNG hard-code lại số 54 ở đây.
        column.setPadding(0, 0, 0, dp(WpNavBar.HEIGHT_DP))
        overlay.addView(
            column,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        fun renderList(currentItems: List<Item>, currentActive: Int) {
            listHolder.removeAllViews()
            for ((i, item) in currentItems.withIndex()) {
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setBackgroundColor(if (i == currentActive) 0xFF1F1F1F.toInt() else 0xFF141414.toInt())
                    setPadding(dp(16), dp(18), dp(8), dp(18))
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.bottomMargin = dp(2)
                    layoutParams = lp
                    isClickable = true
                    // "i" ở đây lấy từ currentItems tại thời điểm renderList() được GỌI - danh
                    // sách được render lại (renderList) mỗi khi update() chạy nên chỉ số này
                    // luôn khớp đúng với danh sách tab thật ở phía activity gọi.
                    setOnClickListener { onSelect(i) }
                }

                val texts = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                texts.addView(TextView(activity).apply {
                    text = item.title
                    textSize = 15f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(if (i == currentActive) ThemePrefs.accent(activity) else Color.WHITE)
                })
                texts.addView(TextView(activity).apply {
                    text = item.url
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(0xFF999999.toInt())
                })
                card.addView(texts)

                card.addView(ImageView(activity).apply {
                    setImageResource(R.drawable.ic_wp_close)
                    imageTintList = ColorStateList.valueOf(0xFFCCCCCC.toInt())
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    val pad = dp(10)
                    setPadding(pad, pad, pad, pad)
                    contentDescription = "Đóng tab"
                    isClickable = true
                    setOnClickListener { onCloseTab(i) }
                }, LinearLayout.LayoutParams(dp(40), dp(40)))

                listHolder.addView(card)
            }
        }

        renderList(items, activeIndex)
        root.addView(
            overlay,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        return Handle(root, overlay, ::renderList)
    }
}
