package com.h.adblockbrowser

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Calendar

/** Ô hiển thị GIỜ/NGÀY hiện tại (tự cập nhật mỗi giây) - giờ được coi là 1 Live Tile BÌNH THƯỜNG
 *  trong lưới trang "start" (xem [HomeScreenManager.buildStartPage]), tham gia ĐẦY ĐỦ hệ thống
 *  chung: [HomeScreenManager.GridPlacer] xếp vị trí, NHẤN GIỮ rồi KÉO tay cầm để đổi cỡ giống
 *  HỆT các tile khác (xem [HomeScreenManager.enterResizeMode]) - KHÔNG còn là widget đặc biệt
 *  nằm riêng phía trên lưới với kiểu resize khác (chụm/dãn 2 ngón đổi cỡ CHỮ) như bản cũ.
 *
 *  Vì giờ có thể bị thu nhỏ xuống tận cỡ "Nhỏ" (1x1, bằng 1 tile vuông bình thường) nên nội dung
 *  PHẢI tự đổi cỡ chữ + ẩn/hiện dòng ngày tuỳ theo [TileSize] hiện tại - xem [applySize] - giống
 *  hệt cách Live Tile Lịch/Đồng hồ thật của Windows Phone hiện ít/nhiều chi tiết hơn tuỳ cỡ ô.
 *
 *  DÙNG FrameLayout (ghim GIỜ ở GÓC TRÊN-TRÁI, NGÀY ở GÓC DƯỚI-TRÁI) THAY VÌ LinearLayout xếp
 *  2 dòng liền sát nhau như bản đầu: với LinearLayout, ở cỡ "To"/"Cao" (tile cao gấp đôi bình
 *  thường) 2 dòng chữ dồn hết lên trên để lại 1 khoảng trống rất lớn, rất kì ở nửa dưới tile -
 *  KHÔNG giống cách các tile khác (icon ghim trên-trái, nhãn ghim dưới-trái, lấp đầy cả khối)
 *  lấp đầy chiều cao tile. Ghim 2 đầu như vậy tự động lấp đầy khoảng trống ở MỌI cỡ tile, luôn
 *  nhất quán với các tile icon khác bất kể tile cao bao nhiêu. */
class ClockWidgetView(context: Context) : FrameLayout(context) {

    private val tvTime = TextView(context).apply {
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.START
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        includeFontPadding = false
        isClickable = true
        isFocusable = true
    }
    private val tvDate = TextView(context).apply {
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.START
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        isClickable = true
        isFocusable = true
    }
    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false

    init {
        val padH = dp(14)
        val padV = dp(10)
        setPadding(padH, padV, padH, padV)
        // Khung nền phẳng kiểu Live Tile, màu accent người dùng đã chọn - y hệt các tile khác.
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(ThemePrefs.accent(context))
        }
        // Giờ ghim GÓC TRÊN-TRÁI (giống vị trí icon của các tile khác).
        addView(tvTime, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.TOP or Gravity.START })
        // Ngày ghim GÓC DƯỚI-TRÁI (giống vị trí nhãn tên app của các tile khác) - luôn dính đáy
        // tile bất kể tile cao bao nhiêu, không còn để trống khoảng lớn ở giữa/dưới như trước.
        addView(tvDate, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.BOTTOM or Gravity.START })
        applySize(TileSize.TO) // mặc định "To" - giữ đúng cảm giác nổi bật như thiết kế gốc
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Bấm vào giờ/phút -> hành động tuỳ nơi gọi (thường là mở trang Đồng hồ). */
    fun setOnTimeClick(onTap: () -> Unit) { tvTime.setOnClickListener { onTap() } }

    /** Bấm vào ngày/tháng -> hành động tuỳ nơi gọi (thường là mở trang Lịch). Không có tác dụng
     *  khi đang ở cỡ "Nhỏ" vì dòng ngày bị ẩn hẳn lúc đó (xem [applySize]). */
    fun setOnDateClick(onTap: () -> Unit) { tvDate.setOnClickListener { onTap() } }

    /** Đổi cỡ chữ + ẩn/hiện dòng ngày tuỳ [TileSize] hiện tại của tile này - gọi lại mỗi khi
     *  người dùng đổi cỡ (kéo tay cầm) hoặc mỗi lần trang "start" được dựng lại. "To" giữ đúng
     *  cỡ chữ như thiết kế gốc trước đây (90sp/18sp) để KHÔNG đổi gì với người chưa từng chỉnh
     *  cỡ; các cỡ nhỏ hơn thu gọn dần, "Nhỏ" chỉ còn hiện mỗi giờ:phút (ẩn hẳn dòng ngày vì
     *  không đủ chỗ) - đúng tinh thần tile nhỏ hiện ít chi tiết hơn của Windows Phone thật. */
    fun applySize(size: TileSize) {
        when (size) {
            TileSize.NHO -> {
                tvTime.textSize = 22f
                tvDate.visibility = GONE
            }
            TileSize.RONG -> {
                tvTime.textSize = 40f
                tvDate.textSize = 13f
                tvDate.visibility = VISIBLE
            }
            TileSize.CAO -> {
                tvTime.textSize = 46f
                tvDate.textSize = 15f
                tvDate.visibility = VISIBLE
            }
            TileSize.TO -> {
                tvTime.textSize = 90f
                tvDate.textSize = 18f
                tvDate.visibility = VISIBLE
            }
        }
    }

    /** Bắt đầu đếm giờ mỗi giây - gọi 1 LẦN DUY NHẤT lúc tạo widget (idempotent nhờ cờ [ticking],
     *  an toàn nếu lỡ gọi lại nhiều lần do widget được TÁI SỬ DỤNG qua nhiều lần dựng lại trang
     *  "start" - xem [MainActivity] giữ 1 tham chiếu widget DUY NHẤT suốt vòng đời Activity). */
    fun startTicking() {
        if (ticking) return
        ticking = true
        val update = object : Runnable {
            override fun run() {
                val now = Calendar.getInstance()
                tvTime.text = String.format(
                    "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)
                )
                // Định dạng đúng WP thật: "thứ tư, 20 tháng 8 2026" (viết đầy đủ, chữ thường)
                val days = arrayOf("chủ nhật", "thứ hai", "thứ ba", "thứ tư", "thứ năm", "thứ sáu", "thứ bảy")
                val day = days[now.get(Calendar.DAY_OF_WEEK) - 1]
                val dom = now.get(Calendar.DAY_OF_MONTH)
                val month = now.get(Calendar.MONTH) + 1
                val year = now.get(Calendar.YEAR)
                tvDate.text = "$day, $dom tháng $month $year"
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(update)
    }
}
