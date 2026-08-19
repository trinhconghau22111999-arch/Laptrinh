package com.h.adblockbrowser

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

/** Màn hình "giả tắt" - bấm nút Off nổi lúc đang xem video (Youtube...) sẽ phủ 1 lớp ĐEN TOÀN
 *  MÀN HÌNH lên trên mọi thứ, ở giữa chỉ hiện đồng hồ giờ:phút + thứ,ngày/tháng/năm giống hệt
 *  kiểu đồng hồ ở màn hình chính (xem MainActivity.buildDraggableClock) - nhìn như điện thoại
 *  đã tắt màn hình thật (kiểu màn khoá), NHƯNG video/nhạc phía dưới (WebView) vẫn tiếp tục phát
 *  bình thường vì đây chỉ là 1 lớp phủ hình ảnh, không thật sự tắt gì cả.
 *
 *  CHẶN CHẠM: lớp phủ này chặn TOÀN BỘ sự kiện chạm (return true ở mọi nơi trừ vùng đồng hồ) để
 *  không vô tình bấm trúng nút trên trang web/app phía dưới trong lúc "màn hình" đang "tắt".
 *  CHỈ 1 cách duy nhất để quay lại: chạm 2 LẦN LIÊN TIẾP (double-tap) đúng vào khối đồng hồ ở
 *  giữa - giống thao tác "nhấn đúp mở khoá" quen thuộc, tránh việc lỡ tay chạm 1 phát ở mép
 *  ngoài đồng hồ mà tắt mất lớp phủ.
 *
 *  Cài đặt như 1 WINDOW RIÊNG (panel con của Activity qua WindowManager, không cần quyền
 *  "hiển thị đè app khác"/SYSTEM_ALERT_WINDOW) - giống cách làm của FloatingBackButton (xem
 *  file đó để biết lý do bắt buộc dùng panel riêng thay vì add thẳng vào view cây bình thường:
 *  video HTML5 fullscreen dùng SurfaceView zOrderOnTop sẽ "nuốt" mọi view thường bất kể
 *  elevation). Panel toàn màn hình này được add SAU (nên nổi trên) các nút tròn nổi khác, nên
 *  khi đang hiện, nó che kín và chặn chạm luôn cả 2 nút Back/Off nổi bên dưới - đúng ý đồ "giả
 *  tắt màn hình" (màn thật tắt thì làm gì bấm được nút nào). */
object FakeScreenOff {

    private var wm: WindowManager? = null
    private var overlay: View? = null
    private var clockHandler: Handler? = null
    private var clockRunnable: Runnable? = null

    fun isShowing(): Boolean = overlay != null

    fun show(activity: Activity) {
        if (overlay != null) return // đang hiện rồi thì thôi, tránh add trùng window

        fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

        val wmLocal = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = wmLocal

        val container = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
        }

        // Khối đồng hồ giữa màn hình - CÙNG STYLE với đồng hồ ở màn hình chính (chữ giờ:phút to,
        // dòng thứ/ngày/tháng/năm nhỏ bên dưới, màu trắng, có đổ bóng nhẹ cho dễ đọc trên nền
        // đen tuyệt đối) để giống hệt cảm giác màn khoá thật của máy.
        val clockLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Vùng chạm rộng rãi hơn phần chữ 1 chút cho dễ nhấn đúp trúng, không cần chính xác
            // tuyệt đối vào từng con số.
            setPadding(dp(24), dp(20), dp(24), dp(20))
        }

        val tvTime = TextView(activity).apply {
            textSize = 56f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setShadowLayer(10f, 0f, 2f, 0xFF000000.toInt())
        }
        val tvDate = TextView(activity).apply {
            textSize = 16f
            setTextColor(0xFFAAAAAA.toInt())
            gravity = Gravity.CENTER
            setShadowLayer(6f, 0f, 1f, 0xFF000000.toInt())
            val top = 6
            setPadding(0, dp(top), 0, 0)
        }
        clockLayout.addView(tvTime)
        clockLayout.addView(tvDate)

        // Cập nhật đồng hồ mỗi giây, y hệt cách làm ở buildDraggableClock trong MainActivity.
        val handler = Handler(Looper.getMainLooper())
        val updateClock = object : Runnable {
            override fun run() {
                val now = Calendar.getInstance()
                tvTime.text = String.format(
                    "%02d:%02d",
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE)
                )
                val days = arrayOf("CN", "T2", "T3", "T4", "T5", "T6", "T7")
                val day = days[now.get(Calendar.DAY_OF_WEEK) - 1]
                tvDate.text = "$day, ${now.get(Calendar.DAY_OF_MONTH)}/" +
                    "${now.get(Calendar.MONTH) + 1}/${now.get(Calendar.YEAR)}"
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateClock)
        clockHandler = handler
        clockRunnable = updateClock

        container.addView(
            clockLayout,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        // Nhận diện đúp-chạm CHỈ trong vùng đồng hồ (clockLayout) - GestureDetector đáng tin cậy
        // hơn tự đo khoảng cách thời gian giữa 2 lần ACTION_DOWN thủ công (xử lý đúng cả
        // khoảng cách 2 điểm chạm, tránh nhận nhầm 2 lần chạm ở 2 vị trí xa nhau là double-tap).
        val gestureDetector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                hide()
                return true
            }
        })
        clockLayout.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true // luôn chặn, không cho "lọt" xuống dưới dù có phải double-tap hay không
        }

        // Toàn bộ vùng CÒN LẠI ngoài đồng hồ: chặn chạm tuyệt đối, không có hành động gì (đúng
        // như màn hình thật sự đã tắt - chạm vào không có phản ứng).
        container.setOnTouchListener { _, _ -> true }

        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            // FLAG_NOT_FOCUSABLE: giống FloatingBackButton - panel nổi này KHÔNG BAO GIỜ được
            // chiếm input/window focus của Activity chính (tránh lỗi bàn phím ảo không bật lên
            // được nếu sau này có ô nhập liệu nào đó đang giữ focus).
            // KHÔNG dùng FLAG_LAYOUT_NO_LIMITS (khác với nút tròn) vì panel này cần đúng kích
            // thước MATCH_PARENT để phủ kín, không cần vẽ tràn ra ngoài biên như nút tròn.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            token = activity.window?.decorView?.windowToken
        }

        try {
            wmLocal.addView(container, lp)
            overlay = container
        } catch (e: Exception) {
            // Token chưa sẵn sàng (activity chưa attach xong cửa sổ) - trường hợp rất hiếm vì
            // nút Off chỉ bấm được sau khi màn hình đã hiện hoàn chỉnh; bỏ qua an toàn.
        }
    }

    /** Tắt lớp phủ, quay lại đúng trang đang xem (video Youtube... vẫn đang phát y nguyên vì
     *  suốt lúc "tắt" WebView phía dưới chưa từng bị pause). */
    fun hide() {
        clockRunnable?.let { clockHandler?.removeCallbacks(it) }
        clockHandler = null
        clockRunnable = null
        overlay?.let {
            try {
                wm?.removeViewImmediate(it)
            } catch (e: Exception) {
            }
        }
        overlay = null
    }
}
