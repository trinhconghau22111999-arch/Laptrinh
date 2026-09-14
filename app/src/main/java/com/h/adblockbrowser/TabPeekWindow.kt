package com.h.adblockbrowser

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Cửa sổ "xem trước" 1 tab, nổi RIÊNG trên màn hình hiện tại - đúng cách bong bóng chat của
 *  Messenger/Zalo hoạt động: bấm bong bóng -> hiện thêm 1 cửa sổ nhỏ NẰM NGAY DƯỚI bong bóng đó
 *  chứa nội dung tab, mà KHÔNG động gì tới tab đang hiển thị chính trong app (webArea của
 *  AccountBrowserActivityBase) - vì đây là 1 WINDOW HỆ THỐNG RIÊNG (WindowManager, giống cách
 *  FloatingBackButton nổi trên cùng), hoàn toàn tách biệt khỏi cây view của Activity, KHÔNG cần
 *  đổi activeIndex/gọi layoutWebArea() như chuyển tab bình thường.
 *
 *  KHÁC với FloatingBackButton (nút nổi CHỈ để bấm, không có nội dung tương tác được): cửa sổ
 *  này chứa 1 WebView THẬT (cuộn/gõ chữ/bấm link bình thường) nên KHÔNG dùng cờ
 *  FLAG_NOT_FOCUSABLE như nút nổi - CHỦ ĐỘNG cho phép cửa sổ này nhận focus bàn phím/input
 *  trong lúc đang mở (đúng ý định: lúc này người dùng đang thao tác với tab TRONG cửa sổ nổi,
 *  không phải với tab chính phía sau) - đóng cửa sổ lại thì focus tự trả về cho activity chính
 *  như bình thường, không cần code gì thêm.
 *
 *  QUAN TRỌNG VỀ VÒNG ĐỜI WebView: hàm [show] REPARENT (gỡ khỏi parent cũ rồi add vào đây) ĐÚNG
 *  WebView instance được truyền vào - KHÔNG tạo WebView mới - để giữ nguyên trạng thái đăng
 *  nhập/lịch sử/vị trí cuộn của tab đó. Nơi gọi [show] PHẢI tự đảm bảo WebView truyền vào đang
 *  KHÔNG có parent nào khác (chưa được hiển thị ở webArea chính) trước khi gọi, nếu không sẽ có
 *  2 nơi cùng tranh giành 1 View. Khi [Handle.dismiss] được gọi, WebView được GỠ RA khỏi cửa sổ
 *  này (KHÔNG destroy) rồi trả lại quyền quyết định "đặt nó về đâu tiếp theo" cho nơi gọi. */
object TabPeekWindow {

    class Handle internal constructor(
        private val wm: WindowManager,
        private val panel: View,
        val webView: WebView
    ) {
        /** true nếu cửa sổ peek này vẫn đang hiển thị (chưa bị dismiss). */
        val isShowing: Boolean get() = panel.parent != null

        /** Gỡ cửa sổ nổi này khỏi màn hình. WebView bên trong được gỡ khỏi panel TRƯỚC (không
         *  destroy) để nơi gọi có thể dùng lại ngay (ví dụ add về webArea nếu cần) - gọi hàm
         *  này AN TOÀN dù gọi nhiều lần liên tiếp hoặc panel đã bị gỡ từ trước. */
        fun dismiss() {
            (webView.parent as? ViewGroup)?.removeView(webView)
            if (panel.parent != null) {
                try {
                    wm.removeViewImmediate(panel)
                } catch (e: Exception) {
                    // Có thể panel đã bị gỡ từ trước (race condition) - bỏ qua an toàn.
                }
            }
        }
    }

    /** [anchorScreenX]/[anchorScreenY]: toạ độ MÀN HÌNH (không phải toạ độ trong 1 View nào cụ
     *  thể) của góc trên-trái bong bóng đang bấm - lấy qua FloatingBackButton.Handle.screenLocation().
     *  [anchorSize]: chiều rộng/cao (hình vuông) của bong bóng đó, dùng để tính điểm NGAY DƯỚI
     *  nó - xem [FloatingBackButton.Handle.size]. */
    fun show(
        activity: Activity,
        webView: WebView,
        anchorScreenX: Int,
        anchorScreenY: Int,
        anchorSize: Int,
        title: String,
        onCollapseTapped: () -> Unit
    ): Handle {
        fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()
        val dm = activity.resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        // Kích thước cửa sổ peek: gần hết chiều rộng màn hình (chừa lề 2 bên) nhưng không quá
        // to trên máy tính bảng/màn hình lớn; chiều cao ~60% màn hình - đủ xem nội dung 1 trang
        // web mà vẫn còn thấy 1 phần màn hình chính phía sau/xung quanh, đúng cảm giác "cửa sổ
        // nổi" chứ không chiếm hết màn hình như mở tab bình thường.
        val panelWidth = (screenW - dp(24)).coerceAtMost(dp(420))
        val panelHeight = (screenH * 0.62f).toInt()

        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = WindowManager.LayoutParams(
            panelWidth, panelHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            // CỐ Ý không có FLAG_NOT_FOCUSABLE (khác FloatingBackButton) - xem giải thích ở đầu
            // file: cửa sổ này cần nhận input thật (gõ chữ, cuộn, bấm link) trong lúc đang mở.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            token = activity.window?.decorView?.windowToken
            // CĂN theo cạnh bong bóng đang đứng (trái/phải màn hình) để cửa sổ không tràn ra
            // ngoài mép màn hình: bong bóng đang ở nửa PHẢI -> canh mép PHẢI cửa sổ trùng mép
            // phải bong bóng (cửa sổ "mọc" sang trái); ngược lại canh mép TRÁI trùng nhau.
            val bubbleCenterX = anchorScreenX + anchorSize / 2
            x = if (bubbleCenterX >= screenW / 2) {
                (anchorScreenX + anchorSize - panelWidth).coerceAtLeast(dp(4))
            } else {
                anchorScreenX.coerceIn(dp(4), (screenW - panelWidth - dp(4)).coerceAtLeast(dp(4)))
            }
            // NGAY DƯỚI bong bóng + đệm nhỏ - nếu không đủ chỗ phía dưới (bong bóng đang neo gần
            // đáy màn hình) thì đẩy lên trên cho vừa, không để cửa sổ bị tràn xuống ngoài màn.
            y = (anchorScreenY + anchorSize + dp(8)).coerceIn(dp(4), (screenH - panelHeight - dp(4)).coerceAtLeast(dp(4)))
        }

        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF141414.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0x33FFFFFF)
            }
            // Cửa sổ này CÓ focus bàn phím thật (khác FloatingBackButton, xem giải thích ở đầu
            // file) nên khi đang mở, phím Back hệ thống sẽ được gửi ĐẾN ĐÂY thay vì tới Activity
            // chính - nếu không tự bắt, Back coi như "biến mất" (không đóng được cửa sổ này,
            // cũng không lùi được trang chính) vì đây chỉ là 1 View thường (không phải Activity/
            // Dialog nên không tự có hành vi "Back = đóng/thoát" mặc định). Cần isFocusableInTouchMode
            // + requestFocus() thì mới NHẬN được sự kiện phím ở view thường như thế này.
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                    onCollapseTapped()
                    true
                } else {
                    false
                }
            }
            clipToOutline = true
            // Chặn chạm "xuyên" xuống dưới trong đúng phạm vi hình chữ nhật của panel - ngoài
            // phạm vi đó (FLAG_NOT_TOUCH_MODAL) vẫn chạm được bình thường xuống màn hình chính.
            isClickable = true
        }

        // Thanh tiêu đề nhỏ - tên tab + nút "×" thu gọn. Có thêm nút này NGOÀI cách bấm lại
        // đúng bong bóng, vì bong bóng có thể bị panel che khuất một phần tuỳ vị trí neo.
        panel.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(6), dp(8))
            addView(TextView(activity).apply {
                text = title
                textSize = 13f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(0xFFCCCCCC.toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_wp_close)
                imageTintList = ColorStateList.valueOf(0xFFCCCCCC.toInt())
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = "Thu gọn"
                val pad = dp(6)
                setPadding(pad, pad, pad, pad)
                isClickable = true
                setOnClickListener { onCollapseTapped() }
            }, LinearLayout.LayoutParams(dp(30), dp(30)))
        })

        panel.addView(View(activity).apply {
            setBackgroundColor(0x22FFFFFF)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        })

        // Reparent ĐÚNG webView được truyền vào (không tạo mới) - xem giải thích ở đầu file về
        // vòng đời WebView. removeView phòng trường hợp (không nên xảy ra, nơi gọi phải tự đảm
        // bảo) webView vẫn còn dính parent cũ nào đó.
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val webHolder = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        webHolder.addView(webView)
        panel.addView(webHolder)

        wm.addView(panel, lp)
        panel.requestFocus()
        return Handle(wm, panel, webView)
    }
}
