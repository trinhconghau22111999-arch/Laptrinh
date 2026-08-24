package com.h.adblockbrowser

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
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
import android.content.res.ColorStateList
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

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
    // Màn hình gốc (chứa webContainer/tabBar) - lưu lại thành field để showTaskView() có nơi
    // add overlay Đa nhiệm vào, thay vì chỉ là biến cục bộ "outer" trong onCreate() như trước.
    private lateinit var overlayRoot: FrameLayout
    private var taskViewHandle: TaskView.Handle? = null
    // Ẩn danh: theo dõi xem lần load hiện tại có phải do code khởi tạo không
    // (true = load do code/newTab, false = load do user click link trong trang)
    private var isInitiatedLoad = false

    private lateinit var tabBar: LinearLayout
    private lateinit var webContainer: FrameLayout
    private lateinit var edtUrl: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnStar: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // KHÔNG còn ẩn status bar/thanh điều hướng hệ thống nữa (khác bản cũ) - để đa nhiệm
        // (Recents) thật của hệ thống luôn dùng được ở màn này, đúng yêu cầu mới.

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
        val btnGo = ImageView(this).apply {
            setImageResource(R.drawable.ic_wp_search)
            imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(6), dp(4), dp(6))
            isClickable = true
            setOnClickListener { loadFromInput() }
        }
        urlRow.addView(btnGo, LinearLayout.LayoutParams(dp(34), dp(34)))

        btnStar = ImageView(this).apply {
            setImageResource(R.drawable.ic_wp_star_outline)
            imageTintList = ColorStateList.valueOf(0xFFCCCCCC.toInt())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(6), dp(4), dp(6))
            isClickable = true
            setOnClickListener { toggleStarCurrent() }
        }
        urlRow.addView(btnStar, LinearLayout.LayoutParams(dp(34), dp(34)))

        urlRow.addView(TextView(this).apply {
            text = "Mở hết"
            textSize = 12f
            setTextColor(ThemePrefs.accent(this@IncognitoActivity))
            setPadding(dp(8), dp(6), dp(4), dp(6))
            // Icon sao dạng vector thay cho ký tự "★", đặt bên trái chữ, cùng màu accent.
            val star = ContextCompat.getDrawable(this@IncognitoActivity, R.drawable.ic_wp_star_filled)
            star?.setTintList(ColorStateList.valueOf(ThemePrefs.accent(this@IncognitoActivity)))
            star?.setBounds(0, 0, dp(16), dp(16))
            setCompoundDrawables(star, null, null, null)
            compoundDrawablePadding = dp(4)
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
        overlayRoot = outer
        setContentView(outer)
        // FIX khoảng đen dư ở trên/dưới màn hình - xem giải thích chi tiết trong MainActivity.kt.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(outer) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }
        // ĐÃ XOÁ HẲN: thanh điều hướng nổi (WpNavBar) Back/Start/Đa nhiệm theo yêu cầu - dùng
        // đúng nút Back thật của hệ thống (luôn hiện sẵn); chuyển tab vẫn dùng dải tab ở trên.

        // ĐÃ XOÁ HẲN: thông báo giải thích vì sao không cho chạm 1 lần vào link để chuyển
        // trang (trước đây tự bật mỗi lần mở Ẩn danh) - theo yêu cầu, không hiện nữa. Hành vi
        // chặn chạm 1 lần vẫn giữ nguyên ở shouldOverrideUrlLoading() bên dưới, chỉ bỏ dialog.

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

    // ---------- Đa nhiệm (Task View) - xem TaskView.kt để biết chi tiết cách overlay này gắn
    // vào màn hình và vì sao KHÔNG dùng cửa sổ hệ thống riêng như WpNavBar. ----------
    private fun taskViewItems() = tabs.map { TaskView.Item(it.title, it.webView.url ?: "") }

    private fun toggleTaskView() {
        val existing = taskViewHandle
        if (existing != null && existing.isShowing) {
            existing.dismiss()
            return
        }
        taskViewHandle = TaskView.show(
            activity = this,
            root = overlayRoot,
            items = taskViewItems(),
            activeIndex = activeIndex,
            onSelect = { i ->
                taskViewHandle?.dismiss()
                switchTab(i)
            },
            onCloseTab = { i ->
                closeTab(i)
                // closeTab() tự finish() activity nếu đó là tab CUỐI CÙNG - kiểm tra tabs còn
                // rỗng hay không trước khi vẽ lại danh sách card, tránh thao tác lên overlay
                // của 1 activity sắp/đã bị huỷ.
                if (tabs.isNotEmpty()) {
                    taskViewHandle?.update(taskViewItems(), activeIndex)
                }
            }
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

            // FIX (lý do "bấm mở mic là tắt liền"): trước đây CẤP LUÔN mọi quyền WebView yêu
            // cầu mà không kiểm tra quyền HỆ THỐNG (RECORD_AUDIO/CAMERA) có thực sự đã được
            // người dùng đồng ý hay chưa (xin ở MainActivity lúc mở app lần đầu) - nếu người
            // dùng từng bấm "Từ chối" hoặc thu hồi quyền sau đó trong Cài đặt, WebView vẫn
            // được báo "đã cấp" -> trang hiện UI ghi âm lên nhưng phần cứng mic bị hệ điều
            // hành CHẶN THẬT NGAY LẬP TỨC vì thiếu quyền hệ thống -> luồng ghi âm kết thúc tức
            // khắc, nhìn như "bấm mở là tắt liền". Giờ CHỈ cấp đúng resource nào có quyền hệ
            // thống tương ứng đã thực sự được cấp; thiếu quyền nào thì từ chối riêng resource
            // đó và báo cho người dùng biết.
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val granted = request.resources.filter { resource ->
                    when (resource) {
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                            ContextCompat.checkSelfPermission(this@IncognitoActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                            ContextCompat.checkSelfPermission(this@IncognitoActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        else -> true
                    }
                }
                if (granted.isNotEmpty()) {
                    request.grant(granted.toTypedArray())
                } else {
                    request.deny()
                }
                if (granted.size < request.resources.size) {
                    Toast.makeText(
                        this@IncognitoActivity,
                        "Chưa cấp quyền micro/camera cho ứng dụng - vào Cài đặt máy để cấp quyền",
                        Toast.LENGTH_LONG
                    ).show()
                }
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
            cell.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_wp_close)
                imageTintList = ColorStateList.valueOf(0xFF888888.toInt())
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(4), dp(4), dp(4), dp(4))
                contentDescription = "Đóng tab"
                isClickable = true
                setOnClickListener { closeTab(i) }
            }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginStart = dp(2) })
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
        btnStar.setImageResource(if (starred) R.drawable.ic_wp_star_filled else R.drawable.ic_wp_star_outline)
        btnStar.imageTintList = ColorStateList.valueOf(if (starred) 0xFFFFD700.toInt() else 0xFFCCCCCC.toInt())
    }

    private fun toggleStarCurrent() {
        val url = tabs.getOrNull(activeIndex)?.webView?.url ?: return
        if (url.isBlank()) return
        val nowStarred = IncognitoStarredStore.toggle(this, url)
        refreshStarIcon()
        Toast.makeText(this, if (nowStarred) "Đã gắn dấu sao" else "Đã bỏ dấu sao", Toast.LENGTH_SHORT).show()
    }

    /** Bấm "Mở hết" -> mở LUÔN tất cả trang đã gắn dấu sao thành tab mới, KHÔNG hiện màn lưới
     *  chọn trước nữa (đúng theo yêu cầu: bấm là mở trang luôn, không phải vào xem danh sách). */
    private fun openAllStarred() {
        val urls = IncognitoStarredStore.getAll(this)
        if (urls.isEmpty()) {
            Toast.makeText(this, "Chưa có trang nào được đánh dấu sao", Toast.LENGTH_SHORT).show()
            return
        }
        urls.forEach { newTab(it) }
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

    override fun onBackPressed() {
        // Đang mở Đa nhiệm -> Back chỉ ĐÓNG Đa nhiệm, KHÔNG lùi trang/thoát Ẩn danh.
        val tv = taskViewHandle
        if (tv != null && tv.isShowing) {
            tv.dismiss()
            return
        }
        val current = tabs.getOrNull(activeIndex)?.webView
        if (current != null && current.canGoBack()) {
            programmaticLoad = true
            isInitiatedLoad = true
            current.goBack()
        } else {
            // Hết trang để lùi trong tab hiện tại, không còn tab khác -> Back ở đây THOÁT LUÔN
            // CẢ APP (không quay lại màn chọn 3 app) theo yêu cầu mới.
            saveSession()
            finishAffinity()
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
        taskViewHandle?.dismiss()
        super.onDestroy()
    }
}
