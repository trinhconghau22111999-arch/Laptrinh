package com.h.adblockbrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs

/** Nút Back nổi hình TRÒN, kiểu nút Home vật lý của iPhone đời cũ - LUÔN hiện sẵn trên màn
 *  hình. Chạm nhanh (không kéo) = [onTap] (mặc định dùng cho hành động Back). Giữ tay
 *  (long-press, không kéo) = [onLongPress] (dùng cho hành động Home/thoát) - mô phỏng đúng kiểu
 *  1 nút vật lý làm được nhiều việc của iPhone đời cũ, thay vì 2 nút Back+Home tách rời như
 *  trước. Dùng chung cho MainActivity / AccountBrowserActivity / IncognitoActivity / Accounts-
 *  Activity để khỏi lặp code nhiều lần.
 *
 *  2 CHẾ ĐỘ VỊ TRÍ (tham số [attach.fixed]):
 *  - fixed = false (mặc định, hành vi CŨ): KÉO được tới bất kỳ đâu bằng ngón tay, thả tay ra tự
 *    "hít" (snap) về cạnh trái/phải gần nhất cho gọn, không che nội dung giữa màn hình.
 *  - fixed = true (MỚI): nút CỐ ĐỊNH hẳn ở góc DƯỚI trái hoặc phải (theo [attach.defaultIsRight]),
 *    không kéo-thả được nữa, không đổi vị trí dù xoay ngang/dọc màn hình - xem chi tiết ở
 *    [applyFixedPosition] và tham số [attach.fixed].
 *
 *  ĐỒNG BỘ VỊ TRÍ GIỮA CÁC MÀN HÌNH (đúng như 1 nút duy nhất): vị trí (cạnh trái/phải + %
 *  chiều cao) được LƯU VÀO SharedPreferences DÙNG CHUNG (đọc từ file chung, không phải bộ nhớ
 *  riêng của từng tiến trình - quan trọng vì mỗi hồ sơ "Nhiều tài khoản" chạy 1 process riêng)
 *  mỗi khi thả tay. TRƯỚC ĐÂY: vị trí đã lưu chỉ được ĐỌC LẠI 1 LẦN DUY NHẤT lúc [attach] chạy
 *  (tức lúc Activity đó được TẠO MỚI - onCreate). Nếu người dùng chuyển qua màn hình khác rồi
 *  quay lại (ví dụ bấm Back hệ thống) mà Activity cũ KHÔNG bị huỷ/tạo lại (chỉ onResume), nút ở
 *  màn đó vẫn đứng yên ở vị trí NHỚ TRONG BỘ NHỚ cũ, không biết vị trí vừa đổi ở màn khác -> có
 *  cảm giác "lúc đồng bộ lúc không". GIỜ: mỗi lần [attach] trả về 1 [Handle], và mọi Activity
 *  dùng nút này phải gọi [Handle.resync] lại ở onResume() - đọc lại vị trí mới nhất từ file mỗi
 *  lần màn hình đó lên foreground, đảm bảo luôn khớp bất kể trước đó đã đổi từ màn nào.
 *
 *  LUÔN NỔI TRÊN VIDEO HTML5 TOÀN MÀN HÌNH (kể cả khi xoay ngang): TRƯỚC ĐÂY nút được add làm
 *  view con bình thường của `root` (FrameLayout của Activity), dù elevation cao cỡ nào cũng vô
 *  ích vì video fullscreen (onShowCustomView của WebView/Chromium khi bấm nút phóng to video
 *  hoặc khi trang tự bật fullscreen lúc xoay ngang) được vẽ bằng 1 SurfaceView đặt
 *  setZOrderOnTop(true) - loại surface này vẽ ĐÈ LÊN TOÀN BỘ nội dung "thường" của cả cửa sổ,
 *  không quan tâm thứ tự thêm view hay elevation trong cây view - nên nút bị video "nuốt mất".
 *  Cách duy nhất để nổi thật sự bất chấp video là add nút vào 1 WINDOW RIÊNG (panel con của
 *  chính Activity, KHÔNG cần quyền "hiển thị đè app khác"/SYSTEM_ALERT_WINDOW) bằng
 *  WindowManager - các Window khác nhau được hệ thống xếp lớp độc lập với chuyện SurfaceView
 *  zOrderOnTop bên trong 1 Window nào đó, nên panel này luôn nổi trên cùng dù video có fullscreen
 *  hay máy xoay hướng nào. */
object FloatingBackButton {

    private const val PREFS = "floating_back_btn_prefs"
    // NHIỀU NÚT ĐỘC LẬP: mỗi nút (Back, Off...) có [id] riêng -> lưu vị trí riêng bằng key có
    // hậu tố [id], để nút này kéo đi đâu không ảnh hưởng tới vị trí của nút kia. [id] mặc định
    // "back" giữ NGUYÊN key cũ (không hậu tố) để tương thích ngược với vị trí đã lưu từ trước
    // khi tính năng nhiều nút này chưa tồn tại (người dùng đang dùng bản cũ nâng cấp lên không
    // bị mất vị trí nút Back đã kéo).
    private fun keyIsRight(id: String) = if (id == "back") "is_right" else "is_right_$id"
    private fun keyYFraction(id: String) = if (id == "back") "y_fraction" else "y_fraction_$id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Các nút đang "sống" TRONG CÙNG TIẾN TRÌNH tự đăng ký ở đây khi attach() để, nếu có, cập
    // nhật NGAY LẬP TỨC cho nhau khi 1 nút bị kéo (không cần đợi màn kia onResume) - chỉ là hỗ
    // trợ thêm cho trường hợp cùng process (MainActivity/IncognitoActivity/AccountsActivity);
    // giữa các hồ sơ "Nhiều tài khoản" (khác process) thì bắt buộc phải đợi onResume đọc lại từ
    // SharedPreferences như mô tả ở trên vì bộ nhớ trong không dùng chung được giữa các process.
    // MỖI [id] có danh sách callback riêng để nút Back kéo không làm nút Off (khác id) bị gọi
    // resync nhầm và ngược lại.
    private val liveResyncCallbacks = mutableMapOf<String, MutableList<() -> Unit>>()
    private fun callbacksFor(id: String) = liveResyncCallbacks.getOrPut(id) { mutableListOf() }

    // [excluding]: callback của chính nút vừa kéo, KHÔNG gọi lại nó ở đây - nút đó đang tự chạy
    // animation "hít cạnh" mượt riêng (animateSnapX), gọi resync đồng bộ ngay lúc này sẽ làm nó
    // bị "nhảy giật" tới đích tức thì thay vì trượt mượt. Chỉ các nút KHÁC (đang mở ở Activity
    // khác cùng tiến trình, CÙNG [id]) mới cần cập nhật ngay lập tức ở đây.
    private fun savePosition(context: Context, id: String, isRight: Boolean, yFraction: Float, excluding: (() -> Unit)?) {
        prefs(context).edit()
            .putBoolean(keyIsRight(id), isRight)
            .putFloat(keyYFraction(id), yFraction.coerceIn(0f, 1f))
            .apply()
        callbacksFor(id).toList().forEach { if (it !== excluding) it() }
    }

    private fun applyPosition(
        context: Context,
        id: String,
        defaultIsRight: Boolean,
        defaultYFraction: Float,
        btn: View,
        lp: WindowManager.LayoutParams,
        wm: WindowManager,
        root: ViewGroup
    ) {
        if (root.width == 0 || root.height == 0) return
        val p = prefs(context)
        val isRight = p.getBoolean(keyIsRight(id), defaultIsRight)
        val yFraction = p.getFloat(keyYFraction(id), defaultYFraction)
        val btnSize = if (lp.width > 0) lp.width else btn.width
        lp.x = if (isRight) (root.width - btnSize).coerceAtLeast(0) else 0
        val maxY = (root.height - btnSize).coerceAtLeast(0)
        lp.y = (yFraction * maxY).toInt().coerceIn(0, maxY)
        try {
            wm.updateViewLayout(btn, lp)
        } catch (e: Exception) {
            // Có thể view chưa kịp add vào WindowManager (chưa có windowToken) - applyPosition
            // sẽ được gọi lại ngay khi add xong nên bỏ qua an toàn.
        }
    }

    /** Tính vị trí GÓC DƯỚI trái/phải (theo [isRight]) cách mép [marginPx] - dùng cho nút CỐ
     *  ĐỊNH ([fixed] = true ở [attach]): không đọc/ghi SharedPreferences như [applyPosition]
     *  (nút kéo-thả) vì vị trí không đổi theo thao tác người dùng, LUÔN ở đúng góc dưới đã chọn.
     *  Hàm này được gọi lại mỗi khi kích thước [root] đổi (ví dụ xoay ngang/dọc màn hình) để nút
     *  luôn bám đúng góc đó, không bị lệch hay giữ nguyên toạ độ pixel cũ của chiều trước. */
    private fun applyFixedPosition(
        isRight: Boolean,
        btn: View,
        lp: WindowManager.LayoutParams,
        wm: WindowManager,
        root: ViewGroup,
        marginPx: Int
    ) {
        if (root.width == 0 || root.height == 0) return
        val btnSize = if (lp.width > 0) lp.width else btn.width
        lp.x = if (isRight) (root.width - btnSize - marginPx).coerceAtLeast(0) else marginPx
        lp.y = (root.height - btnSize - marginPx).coerceAtLeast(0)
        try {
            wm.updateViewLayout(btn, lp)
        } catch (e: Exception) {
            // Xem giải thích ở applyPosition - bỏ qua an toàn, sẽ được gọi lại sau.
        }
    }

    /** Handle đại diện cho 1 nút đang gắn trên 1 Activity cụ thể - Activity đó PHẢI gọi
     *  [resync] lại ở onResume() để luôn khớp vị trí mới nhất (xem giải thích ở đầu file), và
     *  NÊN gọi [detach] ở onDestroy() để gỡ view khỏi WindowManager, tránh rò rỉ (leak) window
     *  khi Activity đóng. */
    class Handle internal constructor(
        private val id: String,
        private val wm: WindowManager,
        private val btn: View,
        private val lp: WindowManager.LayoutParams,
        private val root: ViewGroup,
        private val resyncCallback: () -> Unit,
        // [fixed]: true = nút cố định, không đăng ký vào callbacksFor(id) (nút cố định không
        // bao giờ kéo-thả nên không cần đẩy vị trí sang các Activity khác cùng tiến trình).
        private val fixed: Boolean
    ) {
        fun resync() {
            resyncCallback()
        }

        fun detach() {
            if (!fixed) callbacksFor(id).remove(resyncCallback)
            try {
                wm.removeViewImmediate(btn)
            } catch (e: Exception) {
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        activity: Activity,
        root: FrameLayout,
        onTap: () -> Unit,
        onLongPress: (() -> Unit)? = null,
        // [id]: định danh riêng cho từng nút nổi độc lập (vị trí lưu riêng, xem giải thích ở
        // đầu object) - mặc định "back" để các chỗ gọi cũ (nút Back) không cần sửa gì, tự động
        // giữ đúng key vị trí đã lưu từ trước. Nút mới (vd nút "Off" giả tắt màn hình) truyền
        // id khác, ví dụ "off".
        id: String = "back",
        // Icon hiển thị trên nút - mặc định mũi tên lùi trang giống nút Back gốc.
        icon: String = "◁",
        // Vị trí mặc định LẦN ĐẦU (trước khi người dùng tự kéo đi chỗ khác, chỉ áp dụng khi
        // [fixed] = false) - cho nút mới xuất hiện ở 1 vị trí khác nút Back để 2 nút không đè
        // lên nhau ngay từ đầu. Khi [fixed] = true, [defaultIsRight] quyết định LUÔN LUÔN là
        // góc trái hay phải (không còn ý nghĩa "mặc định lần đầu" nữa vì không kéo được).
        defaultIsRight: Boolean = true,
        defaultYFraction: Float = 0.5f,
        // [fixed]: true = nút CỐ ĐỊNH ở góc DƯỚI trái/phải (theo [defaultIsRight]) - không kéo-
        // thả được nữa (bỏ toàn bộ logic kéo/snap/lưu SharedPreferences ở dưới), luôn nằm đúng
        // góc đó kể cả khi xoay ngang/dọc màn hình. Cần tự cập nhật lại vị trí mỗi khi kích
        // thước [root] đổi (xem addOnLayoutChangeListener bên dưới) vì các Activity dùng nút
        // này khai báo android:configChanges="orientation|..." trong Manifest nên KHÔNG bị huỷ/
        // tạo lại lúc xoay máy - chỉ trông chờ [Handle.resync] ở onResume() là không đủ, vì
        // xoay máy không tự gọi lại onResume. false (mặc định) = giữ nguyên hành vi kéo-thả +
        // tự nhớ vị trí đã kéo như trước.
        fixed: Boolean = false
    ): Handle {
        fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()
        val size = dp(56)
        val fixedMargin = dp(12)

        val btn = TextView(activity).apply {
            text = icon
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xAA1C1C1E.toInt())
                setStroke(dp(1), 0x33FFFFFF)
            }
        }

        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            // LỖI ĐÃ SỬA (nguyên nhân THẬT SỰ khiến bàn phím không bật lên khi gõ vào trang web):
            // panel này là 1 WINDOW RIÊNG (add bằng WindowManager, xem giải thích ở đầu file),
            // LUÔN hiện sẵn trên mọi màn hình/mọi trang. Trước đây KHÔNG có cờ FLAG_NOT_FOCUSABLE
            // -> panel này được phép NHẬN INPUT FOCUS của cửa sổ (window focus), tranh giành với
            // window chính của Activity (nơi chứa WebView/EditText). Ô địa chỉ (EditText gốc) ít
            // bị lộ ra ngoài vì Android có cơ chế TỰ ĐỘNG hiện lại bàn phím khi window chính lấy
            // lại focus và view đang giữ focus là 1 EditText - nhưng ô nhập liệu BÊN TRONG trang
            // web (do Chromium/WebView tự quản lý, không qua cơ chế "tự hiện lại" đó của Android)
            // thì không có cơ chế tự phục hồi này, nên hễ panel nổi giành mất window focus dù chỉ
            // trong chốc lát là yêu cầu hiện IME của WebView bị bỏ luôn, không tự thử lại - kết
            // quả đúng như user báo cáo: gõ vào trang web không bao giờ bật được bàn phím, trong
            // khi gõ vào ô địa chỉ vẫn bình thường. Thêm FLAG_NOT_FOCUSABLE để panel này CHỈ nhận
            // sự kiện chạm (kéo thả nút) mà KHÔNG BAO GIỜ được nhận input/window focus - focus
            // luôn thuộc về window chính của Activity, giống hệt cách 1 overlay nổi (nút chat
            // Messenger, nút Home iPhone ảo...) không bao giờ được phép chiếm bàn phím.
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
                wm.addView(btn, lp)
                windowAdded = true
            } catch (e: Exception) {
                // Token chưa sẵn sàng hoặc Activity đã đóng - root.post bên dưới sẽ không thử
                // lại nữa trong trường hợp này nhưng resync() ở onResume sẽ tự thử add lại.
            }
        }

        val resyncCallback = {
            ensureWindowAdded()
            if (fixed) {
                applyFixedPosition(defaultIsRight, btn, lp, wm, root, fixedMargin)
            } else {
                applyPosition(activity, id, defaultIsRight, defaultYFraction, btn, lp, wm, root)
            }
        }
        if (!fixed) callbacksFor(id).add(resyncCallback)

        // Đợi layout xong (post) mới có windowToken hợp lệ (Activity đã thật sự attach vào cửa
        // sổ hệ thống) + kích thước root thật để tính vị trí ban đầu theo % đã lưu (hoặc góc cố
        // định, nếu [fixed]).
        root.post { resyncCallback() }

        if (fixed) {
            // Nút cố định: KHÔNG được huỷ/tạo lại Activity lúc xoay máy (xem giải thích ở tham
            // số [fixed]), nên phải tự lắng nghe root đổi kích thước (xảy ra ngay khi xoay máy,
            // dù Activity không recreate) để tính lại đúng góc dưới trái/phải theo kích thước
            // MỚI - nếu không, nút sẽ đứng yên ở toạ độ pixel cũ của chiều trước, nhìn như bị
            // "trôi" ra giữa màn hình hoặc lệch khỏi góc sau khi xoay.
            root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val sizeChanged = (right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)
                if (sizeChanged) resyncCallback()
            }
        }

        val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var longPressRunnable: Runnable? = null
        var downRawX = 0f
        var downRawY = 0f
        var startLpX = 0
        var startLpY = 0
        var isDragging = false
        var longPressFired = false
        val dragSlop = dp(8)
        val longPressDelay = 500L

        fun animateSnapX(fromX: Int, toX: Int) {
            val animator = android.animation.ValueAnimator.ofInt(fromX, toX)
            animator.duration = 200
            animator.addUpdateListener {
                lp.x = it.animatedValue as Int
                try {
                    wm.updateViewLayout(btn, lp)
                } catch (e: Exception) {
                }
            }
            animator.start()
        }

        if (fixed) {
            // Nút cố định: không kéo-thả, chỉ cần chạm nhanh = [onTap] và (nếu có) giữ tay =
            // [onLongPress] - dùng thẳng OnClickListener/OnLongClickListener chuẩn của View
            // thay vì tự bắt MotionEvent như nút kéo-thả, đơn giản và đủ dùng vì không cần theo
            // dõi toạ độ ngón tay để tính kéo nữa.
            btn.setOnClickListener { onTap() }
            if (onLongPress != null) {
                btn.setOnLongClickListener {
                    it.animate().scaleX(1.15f).scaleY(1.15f).setDuration(80)
                        .withEndAction { it.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }
                        .start()
                    onLongPress()
                    true
                }
            }
        } else {
            btn.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        startLpX = lp.x
                        startLpY = lp.y
                        isDragging = false
                        longPressFired = false
                        if (onLongPress != null) {
                            val r = Runnable {
                                longPressFired = true
                                v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(80)
                                    .withEndAction { v.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }
                                    .start()
                                onLongPress()
                            }
                            longPressRunnable = r
                            longPressHandler.postDelayed(r, longPressDelay)
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!isDragging && (abs(dx) > dragSlop || abs(dy) > dragSlop)) {
                            isDragging = true
                            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        }
                        if (isDragging) {
                            val maxX = (root.width - lp.width).coerceAtLeast(0)
                            val maxY = (root.height - lp.height).coerceAtLeast(0)
                            lp.x = (startLpX + dx.toInt()).coerceIn(0, maxX)
                            lp.y = (startLpY + dy.toInt()).coerceIn(0, maxY)
                            try {
                                wm.updateViewLayout(v, lp)
                            } catch (e: Exception) {
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        if (isDragging) {
                            // Thả tay -> "hít" về cạnh trái/phải gần nhất, giữ nguyên độ cao - và
                            // LƯU LẠI vị trí này để các màn hình khác đồng bộ theo (đọc lại lúc
                            // resync() ở onResume, hoặc ngay lập tức nếu cùng tiến trình).
                            val maxX = (root.width - lp.width).coerceAtLeast(0)
                            val isRight = lp.x + lp.width / 2 >= root.width / 2
                            val targetX = if (isRight) maxX else 0
                            animateSnapX(lp.x, targetX)
                            val maxY = (root.height - lp.height).coerceAtLeast(1)
                            val yFraction = (lp.y.toFloat() / maxY).coerceIn(0f, 1f)
                            savePosition(activity, id, isRight, yFraction, excluding = resyncCallback)
                        } else if (!longPressFired && event.actionMasked == MotionEvent.ACTION_UP) {
                            onTap()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        return Handle(id, wm, btn, lp, root, resyncCallback, fixed)
    }
}
