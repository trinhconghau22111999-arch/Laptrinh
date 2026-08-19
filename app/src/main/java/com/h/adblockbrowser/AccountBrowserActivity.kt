package com.h.adblockbrowser

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Trình duyệt "Nhiều tài khoản" - mỗi hồ sơ (slot) chạy ở TIẾN TRÌNH RIÊNG (xem các lớp con
 *  AccountBrowserActivity1..10 bên dưới + android:process trong Manifest) nên mỗi hồ sơ có
 *  thư mục dữ liệu WebView (cookie, phiên đăng nhập Google, localStorage...) HOÀN TOÀN TÁCH
 *  BIỆT với các hồ sơ khác - có thể đăng nhập nhiều tài khoản Google KHÁC NHAU cùng lúc, mỗi
 *  tài khoản ở 1 hồ sơ riêng, không xung đột / không bị đăng xuất lẫn nhau.
 *  KHÁC với "Ẩn danh": ở đây KHÔNG xoá cookie/lịch sử - dữ liệu được GIỮ LẠI để tài khoản vẫn
 *  đăng nhập sau khi thoát app, và link trong trang được bấm/điều hướng BÌNH THƯỜNG (không bị
 *  chặn) để luồng đăng nhập Google (chuyển hướng nhiều bước, xác minh 2 lớp...) hoạt động đúng.
 *
 *  TÍNH NĂNG XEM VIDEO / ĐA MÀN HÌNH:
 *  - Video HTML5 toàn màn hình (nút phóng to của YouTube/trang web) tự động xoay ngang và che
 *    hết toolbar/tab-bar (xem onShowCustomView/onHideCustomView).
 *  - Nút "⊞3" ở góc TRÁI trên cùng - CHỈ hoạt động khi NHẤN ĐÚP (double-tap): bật/tắt xem
 *    NHIỀU TAB CÙNG LÚC trên 1 màn hình (chia đều theo chiều ngang - 2 tab -> 50/50, từ 3 tab
 *    trở lên -> chia 3), mỗi ô cuộn/tương tác ĐỘC LẬP - không cần chuyển qua lại giữa các tab
 *    nữa (xem toggleSplit3()). Nhấn đúp lại để tắt.
 *  - Bấm Back hết lịch sử trong 1 hồ sơ -> QUAY VỀ màn "Nhiều tài khoản" (đóng màn duyệt web
 *    của hồ sơ này, URL các tab đã được lưu lại nên mở lại hồ sơ sau vẫn khôi phục đúng).
 *  - Nút "✕" cạnh nút chia 3 màn hình: đóng ngay hồ sơ đang xem, về thẳng "Nhiều tài khoản"
 *    mà không cần lùi hết lịch sử từng trang như Back. */
abstract class AccountBrowserActivityBase : AppCompatActivity() {

    /** Số hồ sơ (1..MAX_PROFILES) - mỗi lớp con ghi đè giá trị cố định của riêng nó. */
    abstract val slot: Int

    /** Ghi đè finish() 1 LẦN ở lớp cơ sở để áp dụng cho CẢ 10 lớp con (AccountBrowserActivity1..10)
     *  - màn này luôn thoát kèm hiệu ứng "trượt ra bên phải" kiểu Windows Phone (xem [finishWp]
     *  ở UiUtils.kt), dù finish() được gọi từ đâu (nút Back nổi, phím Back cứng...). */
    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.wp_slide_in_left, R.anim.wp_slide_out_right)
    }

    private data class Tab(val webView: WebView, var title: String = "Tab mới")

    private val tabs = ArrayList<Tab>()
    private var activeIndex = 0

    // ── Chia 3 màn hình (xem nhiều tab cùng lúc) ──
    private var splitMode = false

    // ── Video/toàn màn hình HTML5 ──
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var orientationBeforeFullscreen = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    private lateinit var outer: FrameLayout
    private lateinit var browserRoot: LinearLayout
    private lateinit var fullscreenContainer: FrameLayout
    private var floatingBackButtonHandle: FloatingBackButton.Handle? = null
    private lateinit var tabBar: LinearLayout
    private lateinit var webArea: FrameLayout
    private lateinit var edtUrl: EditText
    private lateinit var btnStar: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSplit3: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        insetsController.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Cho phép cookie (kể cả cookie bên thứ ba, Google đăng nhập cần) được LƯU LẠI bình
        // thường - mỗi tiến trình :acctN đã có thư mục dữ liệu riêng nên việc bật ở đây chỉ
        // ảnh hưởng trong phạm vi hồ sơ hiện tại.
        CookieManager.getInstance().setAcceptCookie(true)

        browserRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(2))
        }
        // Nút "3" bọc vòng tròn - góc TRÁI trên cùng. CHỈ hoạt động khi NHẤN ĐÚP (double-tap),
        // nhấn 1 cái không có tác dụng gì - tránh bấm nhầm khi thao tác các nút khác gần đó.
        // Nhấn đúp lần nữa để tắt, quay lại xem 1 tab bình thường.
        // FIX "không nhạy": SimpleOnGestureListener mặc định onDown() trả về false khiến
        // GestureDetector đôi khi không theo dõi đúng chuỗi chạm -> double-tap bị bỏ sót. Ghi
        // đè onDown() trả về true để nhận diện double-tap ổn định. Vùng chạm cũng được PHÓNG TO
        // hẳn lên 44dp (thay vì chỉ vừa khít icon chữ nhỏ như trước) cho dễ bấm trúng hơn.
        val split3Detector = android.view.GestureDetector(
            this,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    toggleSplit3()
                    return true
                }
            }
        )
        btnSplit3 = TextView(this).apply {
            text = "3"
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFFCCCCCC.toInt())
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), 0xFFCCCCCC.toInt())
                setColor(Color.TRANSPARENT)
            }
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            isClickable = true
            setOnTouchListener { _, event -> split3Detector.onTouchEvent(event) }
        }
        header.addView(btnSplit3)

        // Nút "✕" kế bên nút chia 3 màn hình - ĐÓNG NGAY hồ sơ đang xem, quay thẳng về màn
        // "Nhiều tài khoản" (khác với Back: Back lùi từng trang lịch sử trong tab, còn nút này
        // thoát hẳn 1 phát không cần lùi hết lịch sử).
        val btnCloseProfile = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(0xFFCCCCCC.toInt())
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), 0xFFCCCCCC.toInt())
                setColor(Color.TRANSPARENT)
            }
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                marginStart = dp(10)
            }
            isClickable = true
            setOnClickListener {
                saveSession()
                finish()
            }
        }
        header.addView(btnCloseProfile)

        // Đã ẩn hoàn toàn dòng chữ "👤 <tên hồ sơ>" theo yêu cầu (trước đây hiển thị ở đây,
        // xem AccountProfileStore.load() để lấy tên hồ sơ nếu cần dùng lại sau này).
        browserRoot.addView(header)

        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(1), dp(8), dp(1))
        }
        tabScroll.addView(tabBar)
        browserRoot.addView(tabScroll)

        val urlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(6))
        }
        edtUrl = EditText(this).apply {
            hint = "Nhập địa chỉ web..."
            setHintTextColor(0xFF888888.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                    loadFromInput(); true
                } else false
            }
        }
        urlRow.addView(edtUrl)

        val btnGo = TextView(this).apply {
            text = "🔍"
            textSize = 18f
            setPadding(dp(10), dp(6), dp(4), dp(6))
            isClickable = true
            setOnClickListener { loadFromInput() }
        }
        urlRow.addView(btnGo)

        btnStar = TextView(this).apply {
            text = "☆"
            textSize = 18f
            setTextColor(0xFFCCCCCC.toInt())
            setPadding(dp(10), dp(6), dp(4), dp(6))
            isClickable = true
            setOnClickListener { toggleStarCurrent() }
        }
        urlRow.addView(btnStar)

        urlRow.addView(TextView(this).apply {
            text = "★"
            // Tăng cỡ chữ (12f -> 22f) và mở rộng vùng đệm chạm ra 44dp mỗi chiều (giống các
            // nút tròn khác trong app) để dễ bấm trúng hơn, tránh bấm nhầm nút "☆" đánh dấu
            // trang kế bên.
            textSize = 22f
            setTextColor(0xFF29B6F6.toInt())
            gravity = Gravity.CENTER
            minWidth = dp(44)
            minHeight = dp(44)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            isClickable = true
            setOnClickListener { openAllStarred() }
        })
        browserRoot.addView(urlRow)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF29B6F6.toInt())
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
        }
        browserRoot.addView(progressBar)

        // webArea: vùng chứa WebView của tab đang active (match_parent), xem layoutWebArea().
        webArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        browserRoot.addView(webArea)

        // fullscreenContainer: dùng riêng cho video HTML5 toàn màn hình (onShowCustomView) -
        // nằm ĐÈ LÊN TRÊN browserRoot, ẩn/hiện tương ứng lúc vào/ra chế độ toàn màn hình.
        fullscreenContainer = FrameLayout(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            visibility = View.GONE
        }

        outer = FrameLayout(this)
        outer.addView(browserRoot, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        outer.addView(fullscreenContainer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(outer)
        // FIX khoảng đen dư ở trên/dưới màn hình - xem giải thích chi tiết trong MainActivity.kt.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(outer) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }
        addFloatingBackHomeButtons(outer)

        val savedUrls = AccountSessionStore.load(this, slot)
        val startUrl = intent.getStringExtra("initial_url")
        if (startUrl != null) {
            newTab(startUrl)
        } else if (savedUrls.isNotEmpty()) {
            savedUrls.forEach { newTab(it) }
        } else {
            // Mặc định vào trang GOOGLE TÌM KIẾM (không phải trang đăng nhập) khi mở 1 hồ sơ
            // chưa có phiên nào trước đó - đăng nhập là tuỳ chọn (bấm nút 🔑), không ép ngay.
            newTab("https://www.google.com")
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- Nút Back nổi kiểu nút Home iPhone đời cũ ----------
    // App ẩn thanh điều hướng hệ thống để full màn hình - cần nút điều hướng riêng trong app.
    // 1 nút tròn nổi DUY NHẤT, luôn hiện sẵn (không ẩn/hiện theo cử chỉ vuốt cạnh nữa), kéo đi
    // đâu tuỳ ý trên màn hình, thả tay tự "hít" (snap) vào cạnh trái/phải gần nhất cho gọn.
    // Bấm (chạm nhanh, không kéo) = lùi trang trong tab hiện tại. Giữ (long-press) = thoát hồ
    // sơ này, quay về danh sách tài khoản - giống nút Home vật lý của iPhone đời cũ (bấm = về
    // trước, giữ/bấm đúp = tác vụ khác).
    @SuppressLint("ClickableViewAccessibility")
    private fun addFloatingBackHomeButtons(root: FrameLayout) {
        // fixed = true: nút Back CỐ ĐỊNH ở góc DƯỚI-PHẢI, không kéo-thả được nữa và không đổi
        // vị trí dù xoay ngang/dọc màn hình (xem chi tiết ở FloatingBackButton.attach).
        floatingBackButtonHandle = FloatingBackButton.attach(
            activity = this,
            root = root,
            onTap = { onBackPressed() },
            onLongPress = { finish() },
            defaultIsRight = true,
            fixed = true
        )
    }

    /** Nút "⊞3" góc trái - NHẤN ĐÚP mới bật/tắt chia màn hình xem NHIỀU TAB CÙNG LÚC, mỗi ô
     *  vẫn là 1 WebView riêng nên cuộn/bấm/nhập liệu ở ô này KHÔNG ảnh hưởng ô khác - hoàn toàn
     *  độc lập. Bật: cho phép xoay TỰ DO theo cảm biến (cả ngang lẫn dọc, xoay máy là màn hình
     *  xoay theo ngay - xem onConfigurationChanged()), chia theo ĐÚNG số tab đang có (2 tab ->
     *  chia đôi 50/50, từ 3 tab trở lên -> chia 3 đều nhau, tối đa 3 ô, không tự mở thêm tab
     *  trống). Tắt: quay lại xem 1 tab đang active, toàn màn hình, trả hướng xoay về như cũ.
     *  Cần ÍT NHẤT 2 tab đang mở thì mới có gì để chia. */
    private fun toggleSplit3() {
        if (!splitMode && tabs.size < 2) {
            Toast.makeText(this, "Cần mở ít nhất 2 tab để chia màn hình", Toast.LENGTH_SHORT).show()
            return
        }
        splitMode = !splitMode
        // SENSOR (không phải SENSOR_LANDSCAPE) -> xoay tự do theo CẢ 2 chiều ngang/dọc bám
        // đúng theo cảm biến vật lý của máy, không còn ép cứng mỗi hướng ngang như trước.
        requestedOrientation = if (splitMode) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        btnSplit3.setTextColor(if (splitMode) 0xFF29B6F6.toInt() else 0xFFCCCCCC.toInt())
        layoutWebArea()
    }

    /** Activity khai báo tự xử lý configChanges (orientation/screenSize...) trong Manifest nên
     *  KHÔNG bị huỷ/tạo lại mỗi lần xoay máy (giữ nguyên tab/WebView, không tải lại trang) -
     *  nhưng vì vậy phải TỰ bố trí lại các ô chia màn hình cho khớp hướng mới ở đây. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (splitMode) layoutWebArea()
    }

    /** Sắp xếp lại vùng hiển thị WebView.
     *  - Bình thường (splitMode=false): hiện ĐÚNG 1 tab đang active, toàn màn hình.
     *  - Chia 3 (splitMode=true): hiện tối đa 3 tab CÙNG LÚC, chia đều theo ĐÚNG hướng màn hình
     *    hiện tại - máy đang NGANG thì xếp CẠNH NHAU (trái-phải), máy đang DỌC thì xếp CHỒNG
     *    LÊN NHAU (trên-dưới) - xoay máy là tự đổi cách chia theo ngay (xem
     *    onConfigurationChanged()). Có đường kẻ mỏng phân cách giữa các ô. Các tab còn active
     *    nhưng không hiện lên (dư ngoài 3 ô) vẫn chạy NGẦM bình thường - chỉ đơn giản là không
     *    được gắn (attach) vào màn hình lúc này.
     *  Mỗi WebView chỉ được có 1 parent nên luôn phải gỡ khỏi parent cũ trước khi gắn lại. */
    private fun layoutWebArea() {
        for (t in tabs) (t.webView.parent as? ViewGroup)?.removeView(t.webView)
        webArea.removeAllViews()

        if (!splitMode || tabs.size < 2) {
            val active = tabs.getOrNull(activeIndex)?.webView
            if (active != null) {
                active.visibility = View.VISIBLE
                active.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                webArea.addView(active)
            }
            return
        }

        val paneCount = minOf(tabs.size, 3)
        val isLandscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val row = LinearLayout(this).apply {
            orientation = if (isLandscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        for (i in 0 until paneCount) {
            val wv = tabs[i].webView
            wv.visibility = View.VISIBLE
            wv.layoutParams = if (isLandscape) {
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            } else {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            row.addView(wv)
            if (i != paneCount - 1) {
                row.addView(View(this).apply {
                    setBackgroundColor(0xFF333333.toInt())
                    layoutParams = if (isLandscape) {
                        LinearLayout.LayoutParams(dp(2), ViewGroup.LayoutParams.MATCH_PARENT)
                    } else {
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2))
                    }
                })
            }
        }
        webArea.addView(row)
    }


    @SuppressLint("SetJavaScriptEnabled")
    private fun newTab(url: String) {
        val webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.userAgentString = UserAgentManager.MOBILE_UA
            // Hồ sơ tài khoản: CHO PHÉP lưu mật khẩu/form như trình duyệt bình thường, khác
            // hẳn "Ẩn danh" (mục đích ở đây là giữ đăng nhập lâu dài).
            settings.saveFormData = true
            // Cho phép video tự phát trong trang (không cần cử chỉ người dùng) - cần cho nút
            // "phóng to toàn màn hình" trên nhiều trình phát video hoạt động mượt.
            settings.mediaPlaybackRequiresUserGesture = false
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        val tab = Tab(webView)
        val index = tabs.size
        tabs.add(tab)
        setupWebViewCallbacks(webView, index)
        setupLongPress(webView)
        activeIndex = index
        webView.loadUrl(url)
        layoutWebArea()
        renderTabBar()
    }

    /** Nhấn giữ vào 1 link/ảnh trong trang -> hiện tuỳ chọn "Mở trong tab mới" */
    private fun setupLongPress(webView: WebView) {
        webView.setOnLongClickListener {
            val result = webView.hitTestResult
            when (result.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.IMAGE_TYPE -> {
                    val targetUrl = result.extra
                    if (targetUrl != null) showOpenInNewTabDialog(targetUrl)
                }
                // Ảnh nằm TRONG 1 link (<a href="..."><img/></a>): result.extra chỉ trả về URL
                // của ẢNH chứ không phải url của link bao quanh - phải dùng requestFocusNodeHref()
                // để lấy đúng href của thẻ <a>, kết quả trả về bất đồng bộ qua Handler/Message.
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    val handler = android.os.Handler(android.os.Looper.getMainLooper()) { msg ->
                        val hrefUrl = msg.data.getString("url")
                        if (hrefUrl != null) showOpenInNewTabDialog(hrefUrl)
                        true
                    }
                    webView.requestFocusNodeHref(handler.obtainMessage())
                }
                else -> return@setOnLongClickListener false
            }
            true
        }
    }

    private fun showOpenInNewTabDialog(targetUrl: String) {
        AlertDialog.Builder(this, R.style.Theme_WP_Dialog)
            .setTitle(targetUrl.take(60))
            .setItems(arrayOf("Mở trong tab mới", "Huỷ")) { _, which ->
                if (which == 0) newTab(targetUrl)
            }
            .show()
    }

    private fun setupWebViewCallbacks(webView: WebView, index: Int) {
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (tabs.getOrNull(activeIndex)?.webView === webView) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                }
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (index < tabs.size) {
                    tabs[index].title = title?.take(14) ?: "Tab mới"
                    renderTabBar()
                }
            }
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = false

            // Tự cấp quyền camera/mic cho WebView khi trang (YouTube tìm bằng giọng nói,
            // Meet/Zoom...) yêu cầu, vì quyền hệ thống đã được xin ở đầu app (MainActivity).
            // THIẾU đoạn này là lý do nút mic trong tab tài khoản cứ đòi cấp quyền mãi:
            // WebChromeClient mặc định KHÔNG trả lời PermissionRequest -> trang không bao giờ
            // nhận được phản hồi nên hiện lại y như chưa cấp quyền mỗi lần bấm.
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

            // ── Video/trang toàn màn hình (nút phóng to của YouTube, trình phát HTML5...) ──
            // Tự động MỞ TO NHẤT (che hết toolbar/tab-bar) và XOAY NGANG, khôi phục lại khi
            // người dùng bấm thoát toàn màn hình (nút X trên trình phát hoặc nút Back).
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                orientationBeforeFullscreen = requestedOrientation
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                browserRoot.visibility = View.GONE
                fullscreenContainer.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                fullscreenContainer.visibility = View.VISIBLE
            }

            override fun onHideCustomView() {
                exitFullscreenVideo()
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val host = request?.url?.host
                return if (AdBlocker.isAd(host)) AdBlocker.blockedResponse() else super.shouldInterceptRequest(view, request)
            }
            // Hồ sơ tài khoản: điều hướng BÌNH THƯỜNG như trình duyệt thật (không chặn bấm
            // link) để luồng đăng nhập Google (nhiều bước chuyển hướng, xác minh 2 lớp,
            // "Chọn tài khoản"...) hoạt động đúng như trên Chrome.
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme ?: return false
                return scheme != "http" && scheme != "https"
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (tabs.getOrNull(activeIndex)?.webView === webView) {
                    edtUrl.setText(url)
                    refreshStarIcon()
                }
                view?.evaluateJavascript(ZoomEnabler.JS, null)
                view?.evaluateJavascript(AdOverlayBlocker.JS, null)
                view?.evaluateJavascript(TranslateInjector.JS, null)
                if (YoutubeAdSkipper.isYoutube(url)) view?.evaluateJavascript(YoutubeAdSkipper.JS, null)
                CookieManager.getInstance().flush()
                saveSession()
            }
        }
    }

    private fun switchTab(index: Int) {
        if (index !in tabs.indices) return
        activeIndex = index
        edtUrl.setText(tabs[index].webView.url ?: "")
        refreshStarIcon()
        layoutWebArea()
        renderTabBar()
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val tab = tabs.removeAt(index)
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        tab.webView.destroy()
        if (tabs.isEmpty()) {
            saveSession()
            finish()
            return
        }
        val newActive = index.coerceAtMost(tabs.size - 1)
        activeIndex = newActive
        layoutWebArea()
        renderTabBar()
        saveSession()
    }

    private fun renderTabBar() {
        tabBar.removeAllViews()
        for ((i, tab) in tabs.withIndex()) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(if (i == activeIndex) 0xFF00344D.toInt() else 0xFF141414.toInt())
                setPadding(dp(10), dp(3), dp(6), dp(3))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = dp(4)
                layoutParams = lp
                isClickable = true
                setOnClickListener { switchTab(i) }
            }
            cell.addView(TextView(this).apply {
                text = tab.title
                textSize = 11f
                setTextColor(if (i == activeIndex) 0xFF29B6F6.toInt() else 0xFFAAAAAA.toInt())
            })
            cell.addView(TextView(this).apply {
                text = " ✕"
                textSize = 11f
                setTextColor(0xFF888888.toInt())
                isClickable = true
                setOnClickListener { closeTab(i) }
            })
            tabBar.addView(cell)
        }
        tabBar.addView(TextView(this).apply {
            text = "+ Tab"
            textSize = 12f
            setTextColor(0xFF29B6F6.toInt())
            setPadding(dp(10), dp(3), dp(10), dp(3))
            isClickable = true
            setOnClickListener { newTab("https://www.google.com") }
        })
    }

    // ── Gắn dấu trang (bookmark) - RIÊNG BIỆT cho TỪNG tài khoản (theo slot), lưu vĩnh viễn
    // qua AccountStarredStore, không mất khi đóng phiên. ──
    private fun refreshStarIcon() {
        val url = tabs.getOrNull(activeIndex)?.webView?.url ?: ""
        val starred = url.isNotBlank() && AccountStarredStore.isStarred(this, slot, url)
        btnStar.text = if (starred) "★" else "☆"
        btnStar.setTextColor(if (starred) 0xFFFFD700.toInt() else 0xFFCCCCCC.toInt())
    }

    private fun toggleStarCurrent() {
        val url = tabs.getOrNull(activeIndex)?.webView?.url ?: return
        if (url.isBlank()) return
        val nowStarred = AccountStarredStore.toggle(this, slot, url)
        refreshStarIcon()
        Toast.makeText(this, if (nowStarred) "Đã gắn dấu trang" else "Đã bỏ dấu trang", Toast.LENGTH_SHORT).show()
    }

    private fun openAllStarred() {
        val list = AccountStarredStore.getAll(this, slot)
        if (list.isEmpty()) {
            Toast.makeText(this, "Chưa có trang nào gắn dấu", Toast.LENGTH_SHORT).show()
            return
        }
        list.forEach { newTab(it) }
    }

    private fun loadFromInput() {
        var input = edtUrl.text.toString().trim()
        if (input.isEmpty()) return
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            input = if (input.contains(".") && !input.contains(" ")) "https://$input"
            else "https://www.google.com/search?q=" + Uri.encode(input)
        }
        tabs.getOrNull(activeIndex)?.webView?.loadUrl(input)
    }

    private fun saveSession() {
        AccountSessionStore.save(this, slot, tabs.mapNotNull { it.webView.url })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // FIX (giống MainActivity - xem giải thích đầy đủ 3 lần sửa ở
        // MainActivity.onWindowFocusChanged()): nguyên nhân gốc là nút Back nổi (FloatingBackButton,
        // đã sửa tận gốc bằng FLAG_NOT_FOCUSABLE) từng tranh giành input focus. Ở đây thêm lớp
        // bảo vệ đáng tin cậy hơn việc đoán qua loại View: hỏi thẳng hệ thống bàn phím có đang
        // hiển thị không, đúng cho mọi loại ô nhập (EditText, ô nhập trong WebView...).
        if (hasFocus) {
            val imeVisible = androidx.core.view.ViewCompat
                .getRootWindowInsets(window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            if (!imeVisible) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    /** Thoát chế độ video toàn màn hình - tách riêng thành hàm dùng chung cho cả
     *  onHideCustomView() (người dùng bấm nút thoát trên trình phát) LẪN onBackPressed()
     *  (người dùng bấm nút Back của hệ thống), KHÔNG gọi qua WebView.getWebChromeClient() vì
     *  API đó chỉ có từ Android 8.0 (API 26) trở lên, trong khi app hỗ trợ từ API 24. */
    private fun exitFullscreenVideo() {
        fullscreenContainer.removeAllViews()
        fullscreenContainer.visibility = View.GONE
        browserRoot.visibility = View.VISIBLE
        requestedOrientation = orientationBeforeFullscreen
        customViewCallback?.onCustomViewHidden()
        customView = null
    }

    override fun onBackPressed() {
        // Đang xem video toàn màn hình -> thoát toàn màn hình trước, không đóng tab/thoát app.
        if (customView != null) {
            exitFullscreenVideo()
            return
        }
        val current = tabs.getOrNull(activeIndex)?.webView
        if (current != null && current.canGoBack()) {
            current.goBack()
            return
        }
        // Hết lịch sử để lùi -> quay về màn "Nhiều tài khoản" (AccountsActivity), vì màn hình
        // này (AccountBrowserActivityN) nằm CHUNG 1 task với AccountsActivity (được mở bằng
        // startActivity() bình thường từ AccountsActivity, KHÔNG có cờ FLAG_ACTIVITY_NEW_TASK
        // nên android:taskAffinity riêng của từng hồ sơ không có tác dụng ở đây - nó chỉ ảnh
        // hưởng tới việc gom nhóm trong Recents/Đa nhiệm, không tách task lúc mở bình thường).
        // TRƯỚC ĐÂY dùng moveTaskToBack(true): lệnh này đưa CẢ TASK (gồm cả AccountsActivity
        // bên dưới) xuống nền cùng lúc -> cảm giác như bị "văng" ra khỏi app luôn (về màn hình
        // chính máy) thay vì quay lại danh sách hồ sơ bên trong app. finish() ở đây chỉ đóng
        // MÀN HÌNH DUYỆT WEB này, để lộ ra AccountsActivity đang có sẵn phía dưới trong cùng
        // task - đúng cảm giác "Back" thông thường. Phiên làm việc (URL các tab) đã được lưu
        // qua saveSession() nên mở lại hồ sơ này sau vẫn khôi phục đúng các tab đang mở.
        saveSession()
        finish()
    }

    // FIX: trước đây onPause() không dừng video nào cả -> thoát app (Home/chuyển app khác)
    // xong video (vd. YouTube trong hồ sơ tài khoản) vẫn tiếp tục phát tiếng như thường.
    override fun onPause() {
        super.onPause()
        saveSession()
        CookieManager.getInstance().flush()
        pauseAllVideosInAllTabs()
        for (t in tabs) t.webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        for (t in tabs) t.webView.onResume()
        // Đọc lại vị trí nút Back nổi mới nhất - xem giải thích đồng bộ ở FloatingBackButton.kt.
        floatingBackButtonHandle?.resync()
    }

    private fun pauseAllVideosInAllTabs() {
        val js = "(function(){" +
            "var vs=document.querySelectorAll('video');" +
            "for(var i=0;i<vs.length;i++){try{vs[i].pause();}catch(e){}}" +
            "})();"
        for (t in tabs) {
            t.webView.evaluateJavascript(js, null)
        }
    }

    override fun onDestroy() {
        saveSession()
        for (t in tabs) t.webView.destroy()
        floatingBackButtonHandle?.detach()
        super.onDestroy()
    }
}

// ── 10 lớp con ứng với 10 "slot" hồ sơ - mỗi lớp được khai báo với android:process RIÊNG
//    trong AndroidManifest.xml (":acct1".."acct10") để có thư mục dữ liệu WebView tách biệt
//    hoàn toàn ở cấp hệ điều hành. Việc chọn hồ sơ nào chỉ đơn giản là mở đúng lớp Activity
//    tương ứng (xem AccountsActivity.kt: classForSlot()). Vì mỗi hồ sơ là 1 TIẾN TRÌNH + 1
//    TASK riêng (android:taskAffinity khác nhau) nên có thể mở ĐỒNG THỜI nhiều hồ sơ cùng lúc
//    và chúng chạy SONG SONG thật sự (xem ghi chú trong AccountsActivity.kt). ──
class AccountBrowserActivity1 : AccountBrowserActivityBase() { override val slot = 1 }
class AccountBrowserActivity2 : AccountBrowserActivityBase() { override val slot = 2 }
class AccountBrowserActivity3 : AccountBrowserActivityBase() { override val slot = 3 }
class AccountBrowserActivity4 : AccountBrowserActivityBase() { override val slot = 4 }
class AccountBrowserActivity5 : AccountBrowserActivityBase() { override val slot = 5 }
class AccountBrowserActivity6 : AccountBrowserActivityBase() { override val slot = 6 }
class AccountBrowserActivity7 : AccountBrowserActivityBase() { override val slot = 7 }
class AccountBrowserActivity8 : AccountBrowserActivityBase() { override val slot = 8 }
class AccountBrowserActivity9 : AccountBrowserActivityBase() { override val slot = 9 }
class AccountBrowserActivity10 : AccountBrowserActivityBase() { override val slot = 10 }
