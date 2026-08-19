package com.h.adblockbrowser

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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

/** Chế độ Ẩn danh - chạy ở TIẾN TRÌNH RIÊNG (xem android:process trong Manifest) nên dữ liệu
 *  (cookie, phiên đăng nhập, cache) hoàn toàn TÁCH BIỆT khỏi trình duyệt chính, không ảnh hưởng
 *  các tài khoản đang đăng nhập ở đó.
 *  THOÁT RA (đóng màn hình Ẩn danh): TẤT CẢ tab đang mở bị XOÁ SẠCH ngay, KHÔNG lưu lại - mở lại
 *  Ẩn danh lần sau luôn bắt đầu từ đầu (trống), đúng nghĩa duyệt web ẩn danh không để lại dấu vết.
 *  DẤU SAO: lưu VĨNH VIỄN qua IncognitoStarredStore, không mất khi đóng phiên.
 *  KHÔNG giới hạn số tab; TẤT CẢ các tab ẩn danh dùng CHUNG 1 phiên/cookie với nhau (đăng nhập ở
 *  tab này thì tab kia trong CÙNG phiên ẩn danh cũng thấy đã đăng nhập). */
class IncognitoActivity : AppCompatActivity() {

    /** Thoát màn này kèm hiệu ứng "trượt ra bên phải" kiểu Windows Phone (xem [finishWp] ở
     *  UiUtils.kt), dù finish() được gọi từ đâu (nút Back nổi, mũi tên ◀, phím Back cứng...). */
    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.wp_slide_in_left, R.anim.wp_slide_out_right)
    }


    private data class Tab(val webView: WebView, var title: String = "Tab mới")

    private val tabs = ArrayList<Tab>()
    private var activeIndex = 0
    private var programmaticLoad = false
    private var floatingBackButtonHandle: FloatingBackButton.Handle? = null
    // Ẩn danh: theo dõi xem lần load hiện tại có phải do code khởi tạo không
    // (true = load do code/newTab, false = load do user click link trong trang)
    private var isInitiatedLoad = false

    private lateinit var tabBar: LinearLayout
    private lateinit var webContainer: FrameLayout
    private lateinit var edtUrl: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnStar: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        insetsController.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }

        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }
        tabScroll.addView(tabBar)
        root.addView(tabScroll)

        val urlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(6))
        }
        edtUrl = EditText(this).apply {
            hint = "Hỏi google"
            setHintTextColor(0xFF888888.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setSingleLine(true)
            // Khai báo rõ kiểu URL để bàn phím chắc chắn hiện nút "Đi/Enter" đúng hành vi -
            // trước đây thiếu dòng này nên 1 số bàn phím (Samsung, SwiftKey...) không kích
            // hoạt được sự kiện Enter để tìm kiếm.
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

        // Nút tìm kiếm/đi tới - dự phòng cho trường hợp bàn phím không kích hoạt Enter được,
        // bấm trực tiếp vào đây luôn hoạt động.
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
            textSize = 20f
            setTextColor(0xFFCCCCCC.toInt())
            setPadding(dp(10), dp(6), dp(4), dp(6))
            isClickable = true
            setOnClickListener { toggleStarCurrent() }
        }
        urlRow.addView(btnStar)

        urlRow.addView(TextView(this).apply {
            text = "★ Mở hết"
            textSize = 12f
            setTextColor(ThemePrefs.accent(this@IncognitoActivity))
            setPadding(dp(8), dp(6), dp(4), dp(6))
            isClickable = true
            setOnClickListener { openAllStarred() }
        })
        root.addView(urlRow)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(ThemePrefs.accent(this@IncognitoActivity))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
        }
        root.addView(progressBar)

        webContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(webContainer)

        val outer = FrameLayout(this).apply {
            addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(outer)
        // FIX khoảng đen dư ở trên/dưới màn hình - xem giải thích chi tiết trong MainActivity.kt.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(outer) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }
        addFloatingBackHomeButtons(outer)

        // ── Thông báo giải thích vì sao KHÔNG cho chạm 1 lần vào link để chuyển trang ──
        // (xem thêm logic chặn thật ở shouldOverrideUrlLoading() bên dưới). Hiện MỖI LẦN mở Ẩn
        // danh (không lưu cờ "đã xem" - đúng tinh thần Ẩn danh không lưu lại gì), để người dùng
        // luôn được nhắc, tránh tưởng app bị lỗi khi bấm link không thấy chuyển trang.
        AlertDialog.Builder(this, R.style.Theme_WP_Dialog)
            .setTitle("Vì sao chạm vào link không chuyển trang?")
            .setMessage(
                "Vì mục đích của app là chặn quảng cáo, để tránh các quảng cáo ngầm trên " +
                "màn hình - khi người dùng chạm phải nó kéo qua trang khác không mong muốn - " +
                "nên nhà phát triển quyết định không cho phép chạm 1 lần để chuyển trang.\n\n" +
                "Nếu bạn muốn mở trang mới hoặc thêm tab, hãy nhấn giữ vào link rồi chọn " +
                "\"Mở trong tab mới\" nhé."
            )
            .setPositiveButton("Đã hiểu", null)
            .setCancelable(true)
            .show()

        // ĐÚNG NGHĨA Ẩn danh: KHÔNG khôi phục tab của lần trước nữa - mỗi lần mở Ẩn danh luôn
        // bắt đầu từ đầu (trống), và khi thoát (onDestroy) sẽ xoá sạch mọi tab đang mở, không
        // để lại dấu vết cho lần sau.
        val startUrl = intent.getStringExtra("initial_url")
        if (startUrl != null) {
            newTab(startUrl)
        } else {
            // Mở trang TRỐNG (nền đen), để người dùng tự gõ địa chỉ muốn vào, thanh địa chỉ
            // cũng để trống (không điền sẵn) - xem switchTab().
            newTab("about:blank")
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- Nút Back nổi kiểu nút Home iPhone đời cũ ----------
    // App ẩn thanh điều hướng hệ thống để full màn hình - cần nút điều hướng riêng trong app.
    // 1 nút tròn nổi DUY NHẤT, luôn hiện sẵn, kéo đi đâu tuỳ ý, thả tay tự "hít" vào cạnh gần
    // nhất. Bấm nhanh = lùi trang trong tab hiện tại. Giữ tay = thoát hẳn Ẩn danh, quay về màn
    // hình chính app (xoá sạch dữ liệu phiên Ẩn danh như trước).
    @SuppressLint("ClickableViewAccessibility")
    private fun addFloatingBackHomeButtons(root: FrameLayout) {
        // fixed = true: nút Back CỐ ĐỊNH ở góc DƯỚI-PHẢI, không kéo-thả được nữa và không đổi
        // vị trí dù xoay ngang/dọc màn hình (xem chi tiết ở FloatingBackButton.attach).
        floatingBackButtonHandle = FloatingBackButton.attach(
            activity = this,
            root = root,
            onTap = { onBackPressed() },
            onLongPress = { saveSession(); finish() },
            defaultIsRight = true,
            fixed = true
        )
    }

    // ── Quản lý tab (KHÔNG giới hạn số lượng) ──
    @SuppressLint("SetJavaScriptEnabled")
    private fun newTab(url: String) {
        val webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.userAgentString = UserAgentManager.MOBILE_UA
            // Ẩn danh: không lưu mật khẩu/form đã điền, không cho tự động điền lại
            settings.saveFormData = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            }
            visibility = View.INVISIBLE
            // Nền ĐEN cho WebView - khi tab đang trống (about:blank, chưa gõ địa chỉ) sẽ hiện
            // màu đen thay vì màu trắng mặc định của WebView, đúng yêu cầu "tối đen luôn".
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        // Xoá sạch mọi dữ liệu gợi ý tìm kiếm/form đã lưu trước đó (nếu có sót lại), để Ẩn danh
        // KHÔNG bao giờ nhớ lịch sử tìm kiếm cục bộ trên máy.
        @Suppress("DEPRECATION")
        android.webkit.WebViewDatabase.getInstance(this).clearFormData()
        @Suppress("DEPRECATION")
        android.webkit.WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword()
        val tab = Tab(webView)
        val index = tabs.size
        tabs.add(tab)
        webContainer.addView(webView)
        setupWebViewCallbacks(webView, index)
        setupLongPress(webView)
        if (index == 0) {
            // Tab ĐẦU TIÊN: chưa có trang nào khác đang xem, phải chuyển sang xem ngay.
            switchTab(index)
        }
        // Các tab mở SAU tab đầu tiên: chỉ tải NỀN, KHÔNG tự chuyển sang xem - trang hiện tại
        // đứng yên, người dùng tự bấm vào tab đó trên thanh tab khi nào muốn xem.
        loadInTab(index, url)
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
                // QUAN TRỌNG: khi ảnh nằm TRONG 1 link (<a href="..."><img/></a>), result.extra
                // ở trên chỉ trả về URL của ẢNH, KHÔNG PHẢI url của link bao quanh - đây là lý do
                // "nhấn giữ link chọn mở tab mới nhưng nó chỉ mở hình ảnh". Phải dùng
                // requestFocusNodeHref() (API chính thức của WebView cho đúng trường hợp này) để
                // lấy đúng href của thẻ <a>, kết quả trả về BẤT ĐỒNG BỘ qua Handler/Message.
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
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean = false

            // Tự cấp quyền camera/mic cho WebView khi trang (YouTube tìm bằng giọng nói...)
            // yêu cầu, vì quyền hệ thống đã được xin ở đầu app (MainActivity). THIẾU đoạn này
            // là lý do nút mic ở tab Ẩn danh cứ đòi cấp quyền mãi: WebChromeClient mặc định
            // KHÔNG trả lời PermissionRequest -> trang không bao giờ nhận phản hồi nên hiện
            // lại y như chưa cấp quyền mỗi lần bấm.
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val host = request?.url?.host
                return if (AdBlocker.isAd(host)) AdBlocker.blockedResponse() else super.shouldInterceptRequest(view, request)
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme ?: return false
                // Chặn scheme không phải http/https (tel:, intent:, v.v.)
                if (scheme != "http" && scheme != "https") return true

                // ── ẨN DANH: KHÔNG cho phép nhấp link thông thường mở trang ──
                // Chỉ cho load nếu lệnh đến từ code (loadInTab / goBack / newTab).
                // Nếu người dùng nhấp link trong trang -> chặn và nhắc nhở nhấn giữ.
                // QUAN TRỌNG: KHÔNG reset cờ ở đây - nếu trang đích có NHIỀU bước chuyển hướng
                // (ví dụ tìm kiếm Google hay redirect 2-3 lần), reset ngay sau lần đầu sẽ khiến
                // các bước chuyển hướng tiếp theo bị chặn nhầm (đây là nguyên nhân "bị chặn tìm
                // kiếm"). Cờ chỉ thật sự reset khi trang đã tải xong hẳn (onPageFinished).
                return !isInitiatedLoad
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Reset flag (phòng trường hợp redirect chuỗi)
                isInitiatedLoad = false
                if (tabs.getOrNull(activeIndex)?.webView === webView) {
                    edtUrl.setText(if (url == null || url == "about:blank") "" else url)
                    refreshStarIcon()
                }
                // ── ẨN DANH: XOÁ LỊCH SỬ duyệt web sau mỗi trang load xong ──
                // Người dùng không thể Back bằng lịch sử WebView (chỉ back trong tab hiện tại
                // vẫn hoạt động vì WebView giữ back-stack riêng; clearHistory() xoá lịch sử
                // TOÀN BỘ của WebView này, nên onBackPressed sẽ dùng canGoBack() = false
                // - ta giữ lại hành vi back bình thường bên trong tab nhưng không lưu lịch sử
                // bền vững nào ra ngoài).
                view?.clearHistory()
                view?.evaluateJavascript(ZoomEnabler.JS, null)
                view?.evaluateJavascript(AdOverlayBlocker.JS, null)
                view?.evaluateJavascript(TranslateInjector.JS, null)
                if (YoutubeAdSkipper.isYoutube(url)) view?.evaluateJavascript(YoutubeAdSkipper.JS, null)
                saveSession()
            }
        }
    }

    private fun loadInTab(index: Int, url: String) {
        isInitiatedLoad = true
        tabs.getOrNull(index)?.webView?.loadUrl(url)
    }

    private fun switchTab(index: Int) {
        if (index !in tabs.indices) return
        activeIndex = index
        // Dùng INVISIBLE thay vì GONE cho các tab không active: GONE gỡ hẳn WebView khỏi layout
        // pass, và trên một số máy (Samsung/MIUI...) WebView không kịp redraw khi bật GONE ->
        // VISIBLE trở lại - màn hình vẫn đứng hình ở nội dung tab TRƯỚC đó dù activeIndex trong
        // code đã đổi (tạo cảm giác "mở tab mới nhưng vẫn xem tab hiện tại"). INVISIBLE vẫn giữ
        // WebView trong layout (các WebView đều match_parent chồng lên nhau trong webContainer
        // nên không lệch layout của view khác) nhưng tránh được lỗi redraw này.
        for ((i, t) in tabs.withIndex()) {
            t.webView.visibility = if (i == index) View.VISIBLE else View.INVISIBLE
        }
        // Trang trống (about:blank, null) -> để thanh địa chỉ TRỐNG, không điền sẵn gì cả,
        // đúng yêu cầu "thanh địa chỉ đừng điền sẵn để người dùng điền".
        val shownUrl = tabs[index].webView.url
        edtUrl.setText(if (shownUrl == null || shownUrl == "about:blank") "" else shownUrl)
        refreshStarIcon()
        renderTabBar()
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val tab = tabs.removeAt(index)
        webContainer.removeView(tab.webView)
        tab.webView.destroy()
        if (tabs.isEmpty()) {
            saveSession()
            finish()
            return
        }
        val newActive = index.coerceAtMost(tabs.size - 1)
        switchTab(newActive)
        renderTabBar()
        saveSession()
    }

    private fun renderTabBar() {
        tabBar.removeAllViews()
        for ((i, tab) in tabs.withIndex()) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(if (i == activeIndex) 0xFF2A0033.toInt() else 0xFF141414.toInt())
                setPadding(dp(12), dp(8), dp(8), dp(8))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = dp(6)
                layoutParams = lp
                isClickable = true
                setOnClickListener { switchTab(i) }
            }
            cell.addView(TextView(this).apply {
                text = tab.title
                textSize = 12f
                setTextColor(if (i == activeIndex) ThemePrefs.accent(this@IncognitoActivity) else 0xFFAAAAAA.toInt())
            })
            cell.addView(TextView(this).apply {
                text = " ✕"
                textSize = 12f
                setTextColor(0xFF888888.toInt())
                isClickable = true
                setOnClickListener { closeTab(i) }
            })
            tabBar.addView(cell)
        }
        // KHÔNG còn giới hạn số tab - nút "+ Tab" luôn hiện
        tabBar.addView(TextView(this).apply {
            text = "+ Tab"
            textSize = 13f
            setTextColor(ThemePrefs.accent(this@IncognitoActivity))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = true
            setOnClickListener { newTab("about:blank") }
        })
    }

    // ── Gắn dấu sao (LƯU VĨNH VIỄN qua IncognitoStarredStore, không mất khi đóng phiên) ──
    private fun refreshStarIcon() {
        val url = tabs.getOrNull(activeIndex)?.webView?.url ?: ""
        val starred = url.isNotBlank() && IncognitoStarredStore.isStarred(this, url)
        btnStar.text = if (starred) "★" else "☆"
        btnStar.setTextColor(if (starred) 0xFFFFD700.toInt() else 0xFFCCCCCC.toInt())
    }

    private fun toggleStarCurrent() {
        val url = tabs.getOrNull(activeIndex)?.webView?.url ?: return
        if (url.isBlank()) return
        val nowStarred = IncognitoStarredStore.toggle(this, url)
        refreshStarIcon()
        Toast.makeText(this, if (nowStarred) "Đã gắn dấu sao" else "Đã bỏ dấu sao", Toast.LENGTH_SHORT).show()
    }

    private fun openAllStarred() {
        val list = IncognitoStarredStore.getAll(this)
        if (list.isEmpty()) {
            Toast.makeText(this, "Chưa có trang nào gắn dấu sao", Toast.LENGTH_SHORT).show()
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
        programmaticLoad = true
        loadInTab(activeIndex, input)
    }

    // ĐÚNG NGHĨA Ẩn danh: không lưu lại danh sách tab cho lần mở sau nữa (trước đây hàm này lưu
    // URL các tab để khôi phục, giờ đảm bảo dữ liệu cũ - nếu còn sót từ bản trước - cũng bị xoá
    // sạch, không để lại dấu vết gì khi thoát Ẩn danh).
    private fun saveSession() {
        IncognitoSessionStore.clear(this)
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

    override fun onBackPressed() {
        val current = tabs.getOrNull(activeIndex)?.webView
        if (current != null && current.canGoBack()) {
            programmaticLoad = true
            isInitiatedLoad = true
            current.goBack()
        } else {
            saveSession()
            finish()
        }
    }

    // FIX: trước đây onPause() chỉ lưu phiên làm việc, không hề dừng video đang phát ở tab
    // nào cả -> thoát app (Home/chuyển app khác) xong video (vd. YouTube ẩn danh) vẫn tiếp tục
    // phát tiếng bình thường như chưa hề rời khỏi app. Dừng hẳn video ở TẤT CẢ tab khi rời app.
    override fun onPause() {
        super.onPause()
        saveSession()
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
