package com.h.adblockbrowser

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun enableImmersiveMode() {
        // Ẩn CẢ thanh trạng thái (giờ/mạng/pin) LẪN thanh điều hướng hệ thống - kiểu "toàn màn
        // hình khi chơi game". Vuốt từ mép màn hình để hiện lại TẠM THỜI rồi tự ẩn lại sau đó
        // (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE), không phải hiện ra rồi ở nguyên đó như trước.
        // Dùng WindowInsetsControllerCompat của androidx để hoạt động đúng trên mọi phiên bản
        // Android (kể cả các máy Android cũ hơn không có API ẩn thanh điều hướng mới).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Chế độ ẩn toàn màn hình có thể bị huỷ khi bàn phím hiện lên, chuyển app đi rồi quay
        // lại... - áp dụng lại mỗi lần cửa sổ được lấy focus để luôn giữ đúng trạng thái ẩn.
        if (hasFocus) enableImmersiveMode()
    }

    private lateinit var webView: WebView
    private lateinit var edtUrl: EditText
    private lateinit var toolbarUrl: View
    private var progressBar: ProgressBar? = null
    private lateinit var homeOverlay: View
    private lateinit var imgWallpaper: android.widget.ImageView
    private var edtHomeSearch: EditText? = null
    private lateinit var homeScreenManager: HomeScreenManager

    // Lịch sử phiên làm việc (chỉ trong RAM, không lưu file -> tự mất khi thoát app)

    // Đánh dấu điều hướng do chính app gọi (từ thanh địa chỉ / menu đề xuất / mở lại tab)
    // để KHÔNG hỏi xác nhận, chỉ hỏi khi người dùng bấm link ngay trên trang.
    private var programmaticLoad = false

    // Bấm nút "🖥 Bản máy tính" nổi -> ép trang HIỆN TẠI sang UA máy tính tới khi tắt lại.
    private var forceDesktop = false

    // ── Cửa sổ nổi (Picture-in-Picture) khi đang phát video ──
    // true khi trang hiện có video HTML5 đang phát (cập nhật qua JS -> JavascriptInterface bên
    // dưới, xem VideoPlaybackTracker.JS). Dùng trong onUserLeaveHint() để tự động thu nhỏ thành
    // cửa sổ nổi NGAY KHI người dùng rời app (bấm Home / chuyển app khác) trong lúc video đang
    // chạy - đúng hành vi PiP chuẩn của Android, và vì PiP chỉ có DUY NHẤT 1 cửa sổ cho cả hệ
    // thống nên tự động đảm bảo "tối đa 1 cái" như yêu cầu.
    private var isVideoPlaying = false

    // ── Cửa sổ nổi TRONG APP (mini-player kéo/di chuyển được) - khác với PiP hệ thống ở trên:
    // cái này nổi NGAY TRÊN trang đang duyệt trong app (vd. trang chủ YouTube) để vừa xem video
    // đang phát vừa chọn video khác, không cần rời khỏi app.
    // QUAN TRỌNG (đã đổi cách làm): TRƯỚC ĐÂY dùng CHÍNH customView (video HTML5 toàn màn hình
    // thật của trang) làm nội dung cửa sổ nổi - nhưng cách đó khiến cửa sổ nổi BỊ PHỤ THUỘC vào
    // trang đang duyệt: hễ webView chính điều hướng sang trang khác (kể cả chỉ là back về trang
    // chủ YouTube) là trình duyệt COI NHƯ trang cũ đã đóng -> tự động huỷ luôn video/cửa sổ nổi
    // theo (gọi onHideCustomView), dù không ai bấm nút đóng cả - đúng lỗi user báo cáo. GIỜ dùng
    // 1 WebView THỨ HAI, HOÀN TOÀN RIÊNG BIỆT, không liên quan gì tới webView chính - tải cùng
    // video đó qua link nhúng (embed) rồi phát trong 1 khung nhỏ nổi lên trên. Nhờ vậy webView
    // chính muốn điều hướng đi đâu, back bao nhiêu lần trong nội bộ YouTube cũng KHÔNG ảnh hưởng
    // gì tới WebView nổi này - đúng yêu cầu "tách biệt hoàn toàn, độc lập với trang YouTube bên
    // dưới". Chỉ đóng hẳn khi: bấm nút ✕, hoặc thoát ra khỏi domain YouTube hoàn toàn. ──
    private var floatContainer: FrameLayout? = null
    private var floatingBackButtonHandle: FloatingBackButton.Handle? = null
    private var floatWebView: WebView? = null
    private val floatCheckHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Video/trang toàn màn hình HTML5 (xem onShowCustomView/onHideCustomView) - dùng chung với
    // logic chia 3 màn hình: khi đang ngang, customView (nếu có) sẽ là ô đầu tiên của chia 3
    // thay cho webView; khi đang dọc, customView hiện toàn màn hình bình thường trong
    // fullscreenContainer.
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var fullscreenContainer: FrameLayout

    companion object {
        const val REQ_PERMISSIONS = 101
        const val REQ_LOCK = 103
        const val DOWNLOAD_FOLDER = "AdBlockBrowser"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // FIX khoảng đen dư ở trên cùng: dù đã setDecorFitsSystemWindows(false), 1 số máy vẫn tự
        // đệm (padding) view gốc theo chiều cao thanh trạng thái/điều hướng theo cơ chế insets
        // kiểu cũ, tạo ra khoảng đen trống phía trên/dưới nội dung thật. Gắn listener KHÔNG áp
        // dụng padding gì cả (chỉ trả nguyên insets) để ép nội dung tràn hết viền màn hình.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootFrame)) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }
        enableImmersiveMode()

        // Khoá ứng dụng (PIN/Hình, đặt ở Cài đặt) - nếu đang bật, bắt buộc mở khoá đúng mới cho
        // vào màn chính. LockScreenActivity không cho back thoát ra (chỉ đưa app xuống nền).
        if (AppLockPrefs.isEnabled(this)) {
            startActivityForResult(Intent(this, LockScreenActivity::class.java), REQ_LOCK)
        } else {
            initAfterUnlock()
        }
    }

    private fun initAfterUnlock() {
        AdBlocker.init(applicationContext)
        AdBlocker.enabled = true // luôn bật, không cho tắt

        webView = findViewById(R.id.webView)
        webView.setBackgroundColor(android.graphics.Color.BLACK) // tránh WebView chớp trắng lúc mới vào/đang tải trang (bề mặt render riêng của WebView mặc định trắng, đặt màu nền XML không đủ)
        edtUrl = findViewById(R.id.edtUrl)
        toolbarUrl = findViewById(R.id.toolbarUrl)
        progressBar = null  // đã xoá khỏi layout
        homeOverlay = findViewById(R.id.homeOverlay)
        imgWallpaper = findViewById(R.id.imgWallpaper)
        edtHomeSearch = null  // đã xoá khỏi layout

        // Khởi tạo màn hình chính - chỉ còn 3 icon cố định (YouTube, Ẩn danh, YouTube+Ẩn danh)
        homeScreenManager = HomeScreenManager(
            this,
            onOpenShortcut = { item -> openShortcutByKey(item.key) },
            onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) }
        )
        val homeContainer = homeOverlay as android.widget.FrameLayout
        homeContainer.addView(homeScreenManager.build())
        val clockWidget = buildDraggableClock(homeContainer)
        homeContainer.addView(clockWidget)

        requestAllPermissions()
        setupWebView()
        fullscreenContainer = FrameLayout(this).apply { visibility = View.GONE }
        findViewById<FrameLayout>(R.id.rootFrame).addView(
            fullscreenContainer,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        addFloatingBackHomeButtons()
        loadWallpaper()

        findViewById<android.widget.ImageButton>(R.id.btnDesktopSite).setOnClickListener { toggleDesktopSite() }

        edtUrl.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                loadFromInput()
                true
            } else {
                false
            }
        }
        edtHomeSearch?.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                loadFromHomeSearch()
                true
            } else {
                false
            }
        }

        showHomeOverlay()
    }

    override fun onResume() {
        super.onResume()
        // Sau khi đổi hình nền ở Cài đặt rồi bấm Back quay lại đây, MainActivity không bị tạo lại
        // (onCreate không chạy lần nữa) nên hình nền cũ vẫn còn - phải tự tải lại ở đây thì ảnh
        // mới mới hiện ngay, không cần thoát hẳn app rồi mở lại như trước.
        if (::imgWallpaper.isInitialized) {
            loadWallpaper()
        }
        // Cho WebView chạy lại bình thường (đối xứng với onPause() ở dưới).
        if (::webView.isInitialized) webView.onResume()
        // Đọc lại vị trí nút Back nổi mới nhất (có thể vừa bị kéo sang chỗ khác ở màn hình
        // khác trong lúc màn hình này ở nền) - xem giải thích đồng bộ ở FloatingBackButton.kt.
        floatingBackButtonHandle?.resync()
    }

    private fun loadWallpaper() {
        val uriStr = WallpaperPrefs.get(this)
        if (uriStr != null) {
            try {
                imgWallpaper.setImageURI(Uri.parse(uriStr))
            } catch (e: Exception) { }
        }
    }

    private fun showHomeOverlay() {
        homeOverlay.visibility = View.VISIBLE
        toolbarUrl.visibility = View.GONE // trang chủ không phải trang web, không cần thanh địa chỉ
        // Về màn hình chính app = thoát khỏi YouTube -> tắt cửa sổ nổi (nếu đang có).
        if (floatWebView != null) closeFloatingVideoPlayer()
        // FIX: trước đây chỉ đóng cửa sổ nổi (customView) nếu có, còn video đang phát BÌNH
        // THƯỜNG (chưa fullscreen/chưa tách cửa sổ nổi) thì WebView vẫn nằm phía SAU
        // homeOverlay và tiếp tục chạy -> tiếng vẫn phát dù đã "thoát" về màn hình chính.
        // Dừng hẳn mọi video đang phát trên trang hiện tại mỗi khi rời về màn hình chính.
        pauseAllVideos()
    }

    /** Dừng phát TẤT CẢ thẻ <video> đang có trên trang hiện tại trong WebView (kể cả video
     *  YouTube dạng bình thường, chưa fullscreen) - dùng mỗi khi người dùng rời khỏi trang/rời
     *  khỏi app để tiếng không tiếp tục phát ngầm ngoài ý muốn. */
    private fun pauseAllVideos() {
        if (!::webView.isInitialized) return
        webView.evaluateJavascript(
            "(function(){" +
                "var vs=document.querySelectorAll('video');" +
                "for(var i=0;i<vs.length;i++){try{vs[i].pause();}catch(e){}}" +
                "})();",
            null
        )
    }

    private fun hideHomeOverlay() {
        homeOverlay.visibility = View.GONE
        // Đã ẩn hẳn thanh địa chỉ dưới cùng theo yêu cầu - không hiện lại kể cả khi đang xem
        // trang web. Điều hướng dùng trang chủ (icon/tìm kiếm) + nút Back tròn nổi.
    }

    private fun loadFromHomeSearch() {
        val search = edtHomeSearch ?: return
        var input = search.text.toString().trim()
        if (input.isEmpty()) return
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            input = if (input.contains(".") && !input.contains(" ")) "https://$input"
            else "https://www.google.com/search?q=" + Uri.encode(input)
        }
        search.setText("")
        navigateTo(input)
    }

    private fun toggleDesktopSite() {
        forceDesktop = !forceDesktop
        val btn = findViewById<android.widget.ImageButton>(R.id.btnDesktopSite)
        btn.setImageResource(
            if (forceDesktop) R.drawable.ic_desktop_mode else R.drawable.ic_mobile_mode
        )
        Toast.makeText(
            this,
            if (forceDesktop) "Đã chuyển sang bản máy tính" else "Đã chuyển về bản di động",
            Toast.LENGTH_SHORT
        ).show()
        webView.url?.let { navigateTo(it) }
    }

    // ---------- Quyền ----------

    private fun requestAllPermissions() {
        // Android 11+: xin quyền truy cập toàn bộ tệp (MANAGE_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    ))
                }
            }
        }

        val perms = ArrayList<String>()
        perms.add(Manifest.permission.CAMERA)
        perms.add(Manifest.permission.RECORD_AUDIO)
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT < 29) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        val need = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    // ---------- Điều hướng ----------

    /** Mở 1 tab từ màn Đa nhiệm: nếu có trạng thái đã lưu (state) thì PHỤC HỒI nguyên trang
     *  (giữ đúng vị trí cuộn, lịch sử điều hướng...) thay vì tải lại từ đầu - vì tab này không
     *  chạy nền, chỉ có dữ liệu trạng thái nhẹ được lưu lúc rời trang. */

    private fun navigateTo(url: String) {
        // Đặt User-Agent ĐÚNG cho từng trang TRƯỚC khi tải (Zalo luôn máy tính, nút nổi "🖥ản máy
        // tính" ép toàn bộ, còn lại dùng UA di động "sạch" - xem UserAgentManager.kt để biết vì
        // sao việc này giúp Gmail hiện đúng bản đầy đủ/cá nhân thay vì bản HTML rút gọn).
        val uri = try { Uri.parse(url) } catch (e: Exception) { null }
        webView.settings.userAgentString = UserAgentManager.uaFor(uri?.host, forceDesktop, uri?.path)
        hideHomeOverlay()
        programmaticLoad = true
        webView.loadUrl(url)
    }

    private fun loadFromInput() {
        var input = edtUrl.text.toString().trim()
        if (input.isEmpty()) return
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            input = if (input.contains(".") && !input.contains(" ")) {
                "https://$input"
            } else {
                "https://www.google.com/search?q=" + Uri.encode(input)
            }
        }
        navigateTo(input)
    }

    // ---------- Menu "đa nhiệm" (xem / xoá lịch sử phiên) ----------


    private fun buildDraggableClock(container: android.widget.FrameLayout): android.widget.FrameLayout {
        val prefs = getSharedPreferences("clock_widget_prefs", 0)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        val widget = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(0x88000000.toInt())
            }
        }

        val tvTime = android.widget.TextView(this).apply {
            textSize = 48f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = android.view.Gravity.CENTER
            setShadowLayer(8f, 0f, 2f, 0xFF000000.toInt())
        }
        val tvDate = android.widget.TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFDDDDDD.toInt())
            gravity = android.view.Gravity.CENTER
            setShadowLayer(4f, 0f, 1f, 0xFF000000.toInt())
        }
        widget.addView(tvTime)
        widget.addView(tvDate)

        // Cập nhật giờ mỗi giây
        val updateClock = object : Runnable {
            override fun run() {
                val now = java.util.Calendar.getInstance()
                tvTime.text = String.format("%02d:%02d", now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE))
                val days = arrayOf("CN", "T2", "T3", "T4", "T5", "T6", "T7")
                val day = days[now.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                tvDate.text = "$day, ${now.get(java.util.Calendar.DAY_OF_MONTH)}/${now.get(java.util.Calendar.MONTH)+1}/${now.get(java.util.Calendar.YEAR)}"
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateClock)

        val wrapper = android.widget.FrameLayout(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        wrapper.addView(widget, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Kéo widget đến vị trí bất kỳ
        var dX = 0f; var dY = 0f
        widget.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { dX = v.x - event.rawX; dY = v.y - event.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    val nx = (event.rawX + dX).coerceIn(0f, (container.width - v.width).toFloat())
                    val ny = (event.rawY + dY).coerceIn(0f, (container.height - v.height).toFloat())
                    v.x = nx; v.y = ny; true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit().putFloat("x", v.x).putFloat("y", v.y).apply(); true
                }
                else -> false
            }
        }

        // Phục hồi vị trí đã lưu
        widget.post {
            widget.x = prefs.getFloat("x", dp(16).toFloat())
            widget.y = prefs.getFloat("y", dp(80).toFloat())
        }

        return wrapper
    }

    private fun clearAllSessionData() {
        webView.clearHistory()
        webView.clearCache(true)
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        Toast.makeText(this, "Đã xoá toàn bộ lịch sử", Toast.LENGTH_SHORT).show()
    }

    // ---------- Menu đề xuất trang (tam giác) ----------

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_LOCK) {
            if (resultCode == RESULT_OK) {
                initAfterUnlock()
            }
            // resultCode khác OK (bị đưa xuống nền qua nút back của LockScreenActivity) -
            // không làm gì cả, app vẫn ở màn khoá lúc quay lại foreground lần sau.
            return
        }
    }

    private fun openShortcutByKey(key: String) {
        val item = ShortcutsRepository.ALL[key] ?: return
        if (item.type == ShortcutType.WEB) {
            navigateTo(item.target)
        } else {
            // "YouTube + Ẩn danh" (nếu có) mã hoá target dạng "IncognitoActivity:<url ban đầu>"
            // để mở luôn tab YouTube ngay khi vào Ẩn danh, thay vì phải tự gõ địa chỉ. Các
            // shortcut ACTIVITY khác chỉ cần đúng tên class, không có phần ":<url>".
            val parts = item.target.split(":", limit = 2)
            val activityName = parts[0]
            val initialUrl = parts.getOrNull(1)
            val activityClass = when (activityName) {
                "IncognitoActivity" -> IncognitoActivity::class.java
                "AccountsActivity" -> AccountsActivity::class.java
                "AppLockSetupActivity" -> AppLockSetupActivity::class.java
                "FilesActivity" -> FilesActivity::class.java
                else -> null
            }
            if (activityClass != null) {
                val intent = Intent(this, activityClass)
                if (initialUrl != null) intent.putExtra("initial_url", initialUrl)
                startActivity(intent)
            }
        }
    }

    // ---------- (Đã bỏ hộp thoại xác nhận mở liên kết - link bấm trong trang mở luôn) ----------

    // ---------- Tải video đang xem về máy ----------

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_").trim().take(50)
        return if (cleaned.isBlank()) "video_${System.currentTimeMillis()}" else cleaned
    }

    // ---------- Cửa sổ nổi (Picture-in-Picture) khi phát video ----------

    inner class PipStateBridge {
        /** Video YouTube VỪA bắt đầu phát trên trang XEM video (watch/Shorts - không phải trang
         *  chủ/feed nơi video chỉ là xem trước tự động khi lướt qua, xem isYoutubeWatchPage()) ->
         *  TỰ ĐỘNG mở cửa sổ nổi (WebView riêng, xem showFloatingVideoPlayer()) phát đúng video
         *  đó, đè lên trên nền YouTube. Đợi 150ms để lọc bỏ các lần play/pause chớp nhoáng lúc
         *  đang tua/qua quảng cáo trước khi mở. */
        @JavascriptInterface
        fun setPlaying(playing: Boolean) {
            val justStarted = playing && !isVideoPlaying
            isVideoPlaying = playing
            if (justStarted) {
                runOnUiThread {
                    val urlNow = webView.url
                    if (floatWebView == null && YoutubeAdSkipper.isYoutubeWatchPage(urlNow)) {
                        val check = Runnable {
                            if (isVideoPlaying && floatWebView == null &&
                                YoutubeAdSkipper.isYoutubeWatchPage(webView.url)
                            ) {
                                showFloatingVideoPlayer(webView.url)
                            }
                        }
                        floatCheckHandler.postDelayed(check, 150)
                    }
                }
            }
        }
    }

    inner class VideoDownloadBridge {
        @JavascriptInterface
        fun downloadVideo(url: String, title: String) {
            runOnUiThread {
                if (url.isBlank()) {
                    Toast.makeText(this@MainActivity, "Không tìm thấy video trên trang này", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                if (url.startsWith("blob:")) {
                    Toast.makeText(
                        this@MainActivity,
                        "Trang này (vd. YouTube) mã hoá luồng video, không thể tải trực tiếp kiểu này",
                        Toast.LENGTH_LONG
                    ).show()
                    return@runOnUiThread
                }
                try {
                    val fileName = sanitizeFileName(title) + ".mp4"
                    val request = DownloadManager.Request(Uri.parse(url))
                    request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                    request.addRequestHeader("User-Agent", webView.settings.userAgentString)
                    request.setTitle(fileName)
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_MOVIES, "$DOWNLOAD_FOLDER/$fileName"
                    )
                    val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                    dm.enqueue(request)
                    Toast.makeText(
                        this@MainActivity,
                        "Đang tải về thư mục Movies/$DOWNLOAD_FOLDER...",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Không tải được video này", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ---------- WebView ----------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.setSupportMultipleWindows(false)
        // Cho phép trang web (Google Maps...) xin vị trí thật của máy - mặc định WebView chặn
        // hoàn toàn API định vị của trình duyệt (navigator.geolocation) nếu không bật dòng này.
        webView.settings.setGeolocationEnabled(true)

        webView.addJavascriptInterface(VideoDownloadBridge(), "AndroidDownloader")
        webView.addJavascriptInterface(PipStateBridge(), "AndroidPip")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar?.progress = newProgress
                progressBar?.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            // Chặn popup / tab mới do quảng cáo tự mở (window.open)
            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?
            ): Boolean = false

            // ── Video/trang toàn màn hình HTML5 (nút phóng to của YouTube, hoặc YouTube TỰ
            // BẬT fullscreen khi xoay ngang) ── QUAN TRỌNG: nếu không bắt sự kiện này, trang sẽ
            // tự xử lý fullscreen theo kiểu riêng của nó (phóng to video trong khung WebView cũ),
            // hoàn toàn bỏ qua logic chia 3 màn hình của app. Bắt lấy customView này rồi đưa vào
            // refreshLayoutMode() để nó được xử lý CHUNG với webView chính (chia 3 nếu đang
            // ngang, toàn màn hình nếu đang dọc).
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                isVideoPlaying = true
                refreshLayoutMode()
            }

            override fun onHideCustomView() {
                if (customView == null) return
                (customView?.parent as? ViewGroup)?.removeView(customView)
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                refreshLayoutMode()
            }

            // Tự cấp quyền camera/mic cho WebView khi trang (Meet/Zoom...) yêu cầu,
            // vì quyền hệ thống đã được xin ở đầu app.
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

            // Tự cấp quyền VỊ TRÍ THẬT cho trang web (Google Maps...) khi trang yêu cầu qua
            // navigator.geolocation - chỉ cấp nếu bản thân app ĐÃ được cấp quyền vị trí hệ thống
            // (xin ở đầu app, xem requestAllPermissions()); nếu chưa có, từ chối an toàn thay vì
            // để WebView tự treo hộp thoại mà không có quyền hệ thống đứng sau.
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?, callback: android.webkit.GeolocationPermissions.Callback?
            ) {
                val granted = ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                callback?.invoke(origin, granted, false)
                if (!granted) {
                    Toast.makeText(
                        this@MainActivity,
                        "Chưa cấp quyền vị trí cho ứng dụng - vào Cài đặt máy để cấp quyền",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val host = request?.url?.host
                return if (AdBlocker.isAd(host)) {
                    AdBlocker.blockedResponse()
                } else {
                    super.shouldInterceptRequest(view, request)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                val scheme = request.url.scheme ?: ""

                // Điều hướng do chính app gọi (thanh địa chỉ, menu, mở lại tab...) -> cho qua luôn
                if (programmaticLoad) {
                    programmaticLoad = false
                    return if (scheme == "http" || scheme == "https") false else true
                }

                // Link không phải web (intent://, market://, tel:, mailto:, app riêng...) ->
                // MỞ THẬT bằng app tương ứng trên máy (Intent.ACTION_VIEW) thay vì chặn âm thầm
                // không làm gì cả như trước.
                if (scheme != "http" && scheme != "https") {
                    try {
                        val intent = if (scheme == "intent") {
                            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        } else {
                            Intent(Intent.ACTION_VIEW, request.url)
                        }
                        intent.addCategory(Intent.CATEGORY_BROWSABLE)
                        startActivity(intent)
                    } catch (e: Exception) {
                        // Không có app nào mở được link này -> bỏ qua, không làm gì thêm
                    }
                    return true
                }

                // Link do người dùng bấm trong trang -> mở thẳng luôn, KHÔNG hỏi xác nhận
                return false
            }

            // FIX "màn hình đen" khi mở YouTube (hoặc trang bất kỳ): trước đây KHÔNG bắt lỗi tải
            // trang nào cả -> nếu trang tải thất bại (mất mạng, DNS lỗi, timeout, lỗi chứng chỉ...)
            // WebView chỉ đứng im, không hiển thị gì, mà nền WebView lại bị đặt cứng màu ĐEN (xem
            // initAfterUnlock() - để tránh chớp trắng lúc mới vào) -> kết quả là 1 màn hình đen
            // tuyệt đối, không có bất kỳ thông báo lỗi nào cho người dùng biết chuyện gì đã xảy ra.
            // Giờ bắt lỗi ở KHUNG CHÍNH (isForMainFrame - bỏ qua lỗi của các tài nguyên phụ như
            // ảnh/quảng cáo bị chặn) và báo rõ nguyên nhân + tự thử tải lại 1 lần.
            private var lastErrorReloadAt = 0L

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame != true) return
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Không tải được trang (${error?.description ?: "lỗi mạng"}). Đang thử lại...",
                        Toast.LENGTH_LONG
                    ).show()
                }
                // Tự thử tải lại 1 lần (tối đa 1 lần mỗi 3 giây để tránh lặp vô hạn nếu mất mạng
                // hẳn) - nhiều trường hợp chỉ là lỗi mạng thoáng qua lúc mới bật WebView.
                val now = System.currentTimeMillis()
                if (now - lastErrorReloadAt > 3000) {
                    lastErrorReloadAt = now
                    view?.postDelayed({ view.reload() }, 800)
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                val code = errorResponse?.statusCode ?: return
                if (request?.isForMainFrame == true && code >= 400) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity, "Máy chủ trả lỗi $code khi tải trang", Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: android.webkit.SslErrorHandler?,
                error: android.net.http.SslError?
            ) {
                // KHÔNG tự ý bỏ qua lỗi chứng chỉ (mất an toàn) - chỉ báo rõ cho người dùng biết
                // vì sao trang không tải được thay vì để màn hình đen im lặng như trước.
                Toast.makeText(
                    this@MainActivity,
                    "Lỗi chứng chỉ bảo mật khi tải trang - đã chặn để an toàn",
                    Toast.LENGTH_LONG
                ).show()
                handler?.cancel()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isVideoPlaying = false
                edtUrl.setText(url)
                // Điều hướng sang trang KHÔNG phải YouTube = "ra khỏi YouTube" -> đóng hẳn cửa
                // sổ nổi (WebView riêng, độc lập hoàn toàn với trang chính - xem
                // showFloatingVideoPlayer()). Còn nếu vẫn đang loanh quanh trong YouTube (kênh,
                // tìm kiếm, video khác, trang chủ...) thì KHÔNG đụng gì tới cửa sổ nổi cả - nó cứ
                // tiếp tục phát riêng, không phụ thuộc trang chính điều hướng đi đâu.
                if (floatWebView != null && !YoutubeAdSkipper.isYoutube(url)) {
                    closeFloatingVideoPlayer()
                }
                view?.evaluateJavascript(AdOverlayBlocker.JS, null)
                view?.evaluateJavascript(VideoDownloadUI.JS, null)
                view?.evaluateJavascript(VideoPlaybackTracker.JS, null)
                if (YoutubeAdSkipper.isYoutube(url)) {
                    view?.evaluateJavascript(YoutubeAdSkipper.JS, null)
                }
                if (ZaloDesktopStyler.isZalo(try { Uri.parse(url).host } catch (e: Exception) { null })) {
                    view?.evaluateJavascript(ZaloDesktopStyler.JS, null)
                }
            }
        }
    }

    // Logic Back DÙNG CHUNG cho cả nút Back trên thanh điều hướng nổi VÀ nút/cử chỉ back vật lý
    // của điện thoại, để 2 nơi luôn nhất quán: lùi từng trang web đã xem -> hết thì về TRANG CHỦ
    // của app (không nhảy thẳng qua app khác) -> chỉ khi đã ở sẵn trang chủ rồi, bấm back thêm
    // lần nữa mới thực sự thoát app.
    //
    // RIÊNG YouTube: các trang con (xem video, kênh, tìm kiếm...) thường xếp chồng RẤT NHIỀU
    // trang trong lịch sử (mỗi lần chuyển video/kết quả tìm kiếm là 1 trang mới), lùi từng trang
    // một sẽ phải bấm Back rất nhiều lần mới ra khỏi YouTube. Nếu đang ở 1 trang con YouTube (và
    // KHÔNG ở sẵn trang chủ rồi) -> back NHẢY THẲNG về trang chủ YouTube (bỏ qua toàn bộ lịch sử
    // các trang con đã xem) thay vì lùi từng bước. NẾU đang có video phát -> trước khi chuyển,
    // THU NHỎ video đang xem thành cửa sổ nổi (mini-player) kéo/di chuyển được, để video vẫn
    // tiếp tục phát trong lúc duyệt trang chủ chọn video khác. Đã ở trang chủ YouTube rồi thì
    // back tiếp theo xử lý bình thường như các trang khác (lùi tiếp lịch sử trước khi vào
    // YouTube, hoặc về màn hình chính app).
    fun doBack() {
        // Đang ở fullscreen HTML5 THẬT (người dùng tự bấm nút fullscreen của YouTube, hoặc
        // trang tự bật khi xoay ngang) -> Back chỉ thoát fullscreen bình thường. KHÔNG đụng gì
        // tới cửa sổ nổi (WebView riêng, nếu đang có nó vẫn tiếp tục phát độc lập, không liên
        // quan gì tới việc bật/tắt fullscreen của webView chính).
        if (customView != null) {
            webView.webChromeClient?.onHideCustomView()
            return
        }
        val currentUrl = webView.url
        when {
            webView.canGoBack() && YoutubeAdSkipper.isYoutube(currentUrl) && !YoutubeAdSkipper.isYoutubeHome(currentUrl) -> {
                // Đang xem video (cửa sổ nổi CÓ THỂ đã tự mở sẵn qua PipStateBridge - hoặc CHƯA
                // kịp mở nếu vừa bấm Back ngay tức khắc sau khi bấm play) -> đảm bảo cửa sổ nổi
                // đang hiển thị đúng video này TRƯỚC KHI điều hướng trang chính về trang chủ
                // YouTube. Cửa sổ nổi là WebView RIÊNG BIỆT hoàn toàn nên việc điều hướng trang
                // chính này KHÔNG ảnh hưởng gì tới nó (đúng yêu cầu "độc lập với trang YouTube").
                if (isVideoPlaying) {
                    showFloatingVideoPlayer(currentUrl)
                }
                programmaticLoad = true
                webView.loadUrl("https://www.youtube.com")
            }
            // ĐÃ Ở SẴN trang chủ YouTube (vd. do back lần trước vừa nhảy về đây) -> back lần
            // NÀY = THOÁT HẲN khỏi YouTube, không chỉ lùi 1 bước lịch sử (lùi 1 bước dễ vẫn còn
            // là 1 trang con khác của YouTube, ví dụ trang kênh/tìm kiếm còn sót trong lịch sử,
            // khiến người dùng cảm giác "chưa ra khỏi YouTube"). Xem exitYoutube().
            YoutubeAdSkipper.isYoutube(currentUrl) && YoutubeAdSkipper.isYoutubeHome(currentUrl) -> {
                exitYoutube()
            }
            webView.canGoBack() -> {
                programmaticLoad = true
                webView.goBack()
            }
            homeOverlay.visibility != View.VISIBLE -> {
                showHomeOverlay()
            }
            else -> {
                super.onBackPressed()
            }
        }
    }

    /** Thoát hẳn khỏi YouTube khi đang đứng ở trang chủ YouTube: duyệt lịch sử NGƯỢC (không tải
     *  lại trang, chỉ tra danh sách có sẵn) để tìm trang GẦN NHẤT KHÔNG PHẢI YouTube - nếu có,
     *  nhảy thẳng về đúng trang đó (bỏ qua mọi trang con YouTube còn sót ở giữa: kênh, tìm
     *  kiếm, video khác đã xem...). Nếu TOÀN BỘ lịch sử phía trước đều là YouTube (vd. mở
     *  YouTube ngay từ trang chủ app, chưa từng duyệt trang nào khác trước đó) -> không còn gì
     *  để lùi về nữa, coi như "ra khỏi YouTube" = về màn hình chính app. */
    private fun exitYoutube() {
        // Thoát khỏi YouTube -> đóng hẳn cửa sổ nổi (nếu đang có) NGAY LẬP TỨC, không cần chờ
        // trang đích load xong (đúng yêu cầu "back thêm 1 cái thoát YouTube thì nó mới mất").
        if (floatWebView != null) closeFloatingVideoPlayer()
        val list = webView.copyBackForwardList()
        val currentIndex = list.currentIndex
        var targetIndex = -1
        for (i in currentIndex - 1 downTo 0) {
            val url = list.getItemAtIndex(i)?.url
            if (!YoutubeAdSkipper.isYoutube(url)) {
                targetIndex = i
                break
            }
        }
        if (targetIndex >= 0) {
            programmaticLoad = true
            webView.goBackOrForward(targetIndex - currentIndex)
        } else if (homeOverlay.visibility != View.VISIBLE) {
            showHomeOverlay()
        } else {
            super.onBackPressed()
        }
    }

    override fun onBackPressed() {
        doBack()
    }

    // Gọi TỰ ĐỘNG bởi Android ngay trước khi Activity bị đưa xuống nền do HÀNH ĐỘNG CỦA NGƯỜI
    // DÙNG (bấm nút Home, vuốt sang app khác, mở Recents...) - KHÔNG gọi khi bị xoay màn hình,
    // hiện hộp thoại hệ thống, hay bị hệ thống tự thu hồi bộ nhớ. Đây đúng là thời điểm chuẩn để
    // tự vào Picture-in-Picture: chỉ khi người dùng CHỦ ĐỘNG rời app trong lúc video đang phát.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isVideoPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
            } catch (e: Exception) {
                // Máy/ROM không hỗ trợ PiP -> KHÔNG vào được cửa sổ nổi thật, nên phải tự dừng
                // video ngay tại đây (nếu không, video/tiếng sẽ tiếp tục phát ngầm vô hạn ở nền
                // mà không có cửa sổ nào hiển thị cả - đúng lỗi "thoát app vẫn hát bình thường").
                pauseAllVideos()
            }
        }
    }

    // Gọi khi Activity bị đưa xuống nền vì BẤT KỲ lý do gì (Home, chuyển app khác, tắt màn
    // hình, mở app khác đè lên...). FIX: trước đây không có onPause()/onStop() nào dừng
    // WebView/video cả, nên hễ thoát khỏi app (mà không vào được đúng cửa sổ nổi PiP hệ thống)
    // là video/tiếng vẫn chạy tiếp trong nền như chưa hề thoát. Chỉ GIỮ tiếng phát khi đang ở
    // ĐÚNG chế độ Picture-in-Picture hệ thống thật (cửa sổ nổi thật sự đang hiển thị) - còn lại
    // mọi trường hợp khác coi như đã rời app -> dừng video ngay.
    override fun onPause() {
        super.onPause()
        val inRealSystemPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!inRealSystemPip) {
            pauseAllVideos()
            if (::webView.isInitialized) webView.onPause()
        }
    }

    override fun onStop() {
        super.onStop()
        val inRealSystemPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!inRealSystemPip) {
            pauseAllVideos()
        }
    }

    /** Bố cục "ô đầu tiên" (pane1) LUÔN LUÔN là customView (video HTML5 đang toàn màn hình,
     *  nếu có) HOẶC webView chính (bình thường). Hàm này XÂY LẠI layout cho khớp với việc có
     *  đang fullscreen video hay không, mỗi khi bật/tắt fullscreen video. */
    private fun refreshLayoutMode() {
        val root = findViewById<FrameLayout>(R.id.rootFrame)
        val pane1: View = customView ?: webView
        (pane1.parent as? ViewGroup)?.removeView(pane1)
        fullscreenContainer.removeAllViews()
        fullscreenContainer.visibility = View.GONE

        if (customView != null) {
            pane1.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            fullscreenContainer.addView(pane1, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            fullscreenContainer.visibility = View.VISIBLE
        } else {
            pane1.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            root.addView(pane1, 0)
        }
    }


    // ---------- Nút Back nổi kiểu nút Home iPhone đời cũ ----------
    // App ẩn thanh điều hướng hệ thống (enableImmersiveMode) để full màn hình, nên cần nút
    // điều hướng RIÊNG trong app. 1 nút tròn nổi DUY NHẤT, luôn hiện sẵn (không ẩn ngoài mép
    // /không cần vuốt để hiện như bản cũ), kéo đi đâu tuỳ ý, thả tay tự "hít" vào cạnh trái/
    // phải gần nhất. Bấm nhanh = lùi trang (doBack). Giữ tay = về màn hình chính app.
    @SuppressLint("ClickableViewAccessibility")
    private fun addFloatingBackHomeButtons() {
        val root = findViewById<FrameLayout>(R.id.rootFrame)
        floatingBackButtonHandle = FloatingBackButton.attach(
            activity = this,
            root = root,
            onTap = { doBack() },
            onLongPress = { if (homeOverlay.visibility != View.VISIBLE) showHomeOverlay() }
        )
    }

    // ---------- Cửa sổ nổi trong app (mini-player kéo/di chuyển được, ĐỘC LẬP với trang chính) ----------

    /** Lấy ID video YouTube từ URL (watch?v=, youtu.be/, /shorts/) - dùng để dựng link nhúng
     *  (embed) tải vào WebView nổi riêng. */
    private fun extractYoutubeVideoId(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        return try {
            val uri = Uri.parse(url)
            when {
                uri.host?.contains("youtu.be") == true -> uri.lastPathSegment
                uri.path?.startsWith("/shorts/") == true ->
                    uri.path?.removePrefix("/shorts/")?.substringBefore('/')
                else -> uri.getQueryParameter("v")
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Mở cửa sổ nổi phát ĐÚNG video đang xem, bằng 1 WebView HOÀN TOÀN RIÊNG (không phải
     *  customView mượn từ trang chính - xem giải thích ở khai báo floatWebView phía trên) - nhờ
     *  vậy trang chính (webView) muốn điều hướng/back đi đâu trong nội bộ YouTube cũng KHÔNG ảnh
     *  hưởng gì tới video đang phát ở đây. CHỈ 1 cửa sổ nổi tồn tại tại 1 thời điểm - nếu đã có
     *  sẵn 1 cửa sổ đang mở thì bỏ qua (giữ nguyên video đang phát, không tự thay bằng video mới).
     *  Lấy tạm thời điểm đang xem dở ở trang chính (currentTime) để phát tiếp GẦN ĐÚNG chỗ đang
     *  xem thay vì phát lại từ đầu (không thể liền mạch 100% vì đây là 1 phiên phát riêng, nhưng
     *  đủ gần để không bị khó chịu). */
    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingVideoPlayer(watchUrl: String?) {
        if (floatWebView != null) return
        val videoId = extractYoutubeVideoId(watchUrl) ?: return

        webView.evaluateJavascript(
            "(function(){var v=document.querySelector('video');return v?Math.floor(v.currentTime):0;})();"
        ) { result ->
            val startSeconds = result?.toIntOrNull() ?: 0
            createFloatingVideoWindow(videoId, startSeconds)
        }
    }

    private fun createFloatingVideoWindow(videoId: String, startSeconds: Int) {
        if (floatWebView != null) return
        val root = findViewById<FrameLayout>(R.id.rootFrame)
        val widthPx = dp(220)
        val heightPx = dp(124) // tỉ lệ 16:9

        val fWebView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.setSupportZoom(false)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?, request: WebResourceRequest?
                ): WebResourceResponse? {
                    val host = request?.url?.host
                    return if (AdBlocker.isAd(host)) AdBlocker.blockedResponse() else null
                }
            }
            // Không cần bắt onShowCustomView ở đây - cửa sổ nổi không cho fullscreen riêng (đã
            // đủ nhỏ gọn, fullscreen trong 1 khung 220dp không có ý nghĩa).
            webChromeClient = WebChromeClient()
            loadUrl(
                "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0" +
                    "&modestbranding=1&start=$startSeconds"
            )
        }

        // Tay cầm kéo RIÊNG (không đè lên video) - để video vẫn bấm play/pause/tua được bình
        // thường, chỉ khi chạm đúng vào tay cầm này mới kéo di chuyển cửa sổ.
        val handle = TextView(this).apply {
            text = "⠿"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xAA000000.toInt())
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(dp(26), dp(26)).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        }

        val btnClose = TextView(this).apply {
            text = "✕"
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xAA000000.toInt())
            setPadding(dp(6), dp(2), dp(6), dp(2))
            isClickable = true
            setOnClickListener { closeFloatingVideoPlayer() }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.END }
        }

        val container = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            elevation = dp(12).toFloat()
            layoutParams = FrameLayout.LayoutParams(widthPx, heightPx).apply {
                // Mặc định neo gần CẠNH TRÊN màn hình (đúng yêu cầu "bình thường nó nằm ở cạnh
                // trên"), lệch sang phải 1 chút. Kéo bằng tay cầm để dời đi bất kỳ đâu.
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(70)
                marginEnd = dp(10)
            }
            addView(fWebView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(handle)
            addView(btnClose)
        }

        // Kéo di chuyển tự do bằng ngón tay, CHỈ trên tay cầm (dùng translationX/Y - không đụng
        // layoutParams gốc nên không giật/nhảy khi kéo).
        var downX = 0f
        var downY = 0f
        var startTransX = 0f
        var startTransY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startTransX = container.translationX
                    startTransY = container.translationY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    container.translationX = startTransX + (event.rawX - downX)
                    container.translationY = startTransY + (event.rawY - downY)
                    true
                }
                else -> false
            }
        }

        root.addView(container)
        floatContainer = container
        floatWebView = fWebView

        // Hiệu ứng mờ dần xuất hiện, không giật/nhảy khựng như cách làm cũ (không còn phải đi
        // qua bước fullscreen gốc của trình duyệt nữa).
        container.alpha = 0f
        container.animate().alpha(1f).setDuration(220).start()
    }

    /** Đóng hẳn cửa sổ nổi (bấm ✕, hoặc thoát khỏi domain YouTube) - dừng và huỷ hẳn WebView
     *  riêng đó (không chỉ ẩn) vì đây là 1 phiên phát video độc lập, không có lý do giữ chạy
     *  ngầm khi người dùng đã không còn ở YouTube nữa. */
    private fun closeFloatingVideoPlayer() {
        floatContainer?.let { c -> (c.parent as? ViewGroup)?.removeView(c) }
        floatWebView?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        floatContainer = null
        floatWebView = null
    }

    // Thoát app -> xoá sạch mọi dấu vết phiên làm việc
    override fun onDestroy() {
        closeFloatingVideoPlayer()
        clearAllSessionData()
        floatingBackButtonHandle?.detach()
        super.onDestroy()
    }
}

/**
 * Chặn quảng cáo bằng cách so khớp host của request với danh sách domain trong assets/blocklist.txt.
 */
object AdBlocker {

    private var domains: HashSet<String> = HashSet()
    private var loaded = false
    var enabled: Boolean = true

    fun init(context: android.content.Context) {
        if (loaded) return
        try {
            context.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val d = line.trim().lowercase()
                    if (d.isNotEmpty() && !d.startsWith("#")) {
                        domains.add(d)
                    }
                }
            }
            loaded = true
        } catch (e: Exception) {
            loaded = true
        }
    }

    fun isAd(host: String?): Boolean {
        if (!enabled || host.isNullOrEmpty()) return false
        val h = host.lowercase()
        if (domains.contains(h)) return true
        var idx = h.indexOf('.')
        while (idx != -1) {
            val suffix = h.substring(idx + 1)
            if (domains.contains(suffix)) return true
            idx = h.indexOf('.', idx + 1)
        }
        return false
    }

    fun blockedResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    fun blockedCount(): Int = domains.size
}

/**
 * Tự động skip/tua qua quảng cáo trên YouTube bằng JS injection, và ẩn banner
 * "Mở trong ứng dụng YouTube".
 */
object YoutubeAdSkipper {

    const val JS = """
        (function() {
            if (window.__adSkipperRunning) return;
            window.__adSkipperRunning = true;
            setInterval(function() {
                try {
                    var skipBtn = document.querySelector(
                        '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .videoAdUiSkipButton'
                    );
                    if (skipBtn) { skipBtn.click(); }

                    var video = document.querySelector('video');
                    var adShowing = document.querySelector('.ad-showing, .ad-interrupting');
                    if (adShowing && video) {
                        video.muted = true;
                        if (video.duration && isFinite(video.duration)) {
                            video.currentTime = video.duration;
                        }
                        video.playbackRate = 16;
                    }

                    var overlays = document.querySelectorAll(
                        '.ytp-ad-overlay-container, .ytp-ad-text-overlay, .ytp-ad-image-overlay, ' +
                        '.video-ads, ytd-promoted-sparkles-web-renderer, ' +
                        'ytd-display-ad-renderer, ytd-in-feed-ad-layout-renderer, ytd-ad-slot-renderer, ' +
                        'ytd-banner-promo-renderer, ytd-mealbar-promo-renderer, #open-app, .app-promo, ' +
                        'tp-yt-paper-dialog.ytd-popup-container'
                    );
                    overlays.forEach(function(el) { el.style.display = 'none'; });
                } catch (e) {}
            }, 300);
        })();
    """

    fun isYoutube(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return url.contains("youtube.com") || url.contains("youtu.be")
    }

    /** Trang chủ YouTube (không có path/chỉ có "/") - dùng để biết khi nào KHÔNG cần nhảy về
     *  nữa (đã ở sẵn trang chủ rồi) trong logic Back thông minh của YouTube. */
    fun isYoutubeHome(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: return false
            val path = uri.path ?: ""
            host.endsWith("youtube.com") && (path.isEmpty() || path == "/")
        } catch (e: Exception) {
            false
        }
    }

    /** Đang ở đúng trang XEM 1 video cụ thể (watch/shorts/youtu.be) - KHÔNG phải trang chủ/feed
     *  (nơi video chỉ là xem trước tự động khi cuộn qua) - dùng để chỉ tự động tách video thành
     *  cửa sổ nổi khi người dùng THỰC SỰ đang xem 1 video, không phải mọi video preview lướt qua. */
    fun isYoutubeWatchPage(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        if (!isYoutube(url)) return false
        return url.contains("/watch") || url.contains("youtu.be/") || url.contains("/shorts/")
    }
}

/**
 * Theo dõi mọi thẻ <video> trên trang (YouTube, các trang khác...) - báo về Android mỗi khi có
 * video BẮT ĐẦU/DỪNG phát qua cầu nối AndroidPip.setPlaying(), để MainActivity biết lúc nào cần
 * tự động vào Picture-in-Picture (xem onUserLeaveHint()). Dùng "capture" listener gắn ở document
 * để bắt được sự kiện play/pause/ended của TẤT CẢ video kể cả những video được trang tạo ra SAU
 * này (video YouTube được thay thế liên tục mỗi khi chuyển bài).
 */
object VideoPlaybackTracker {
    const val JS = """
        (function() {
            if (window.__pipTrackerRunning) return;
            window.__pipTrackerRunning = true;
            function report() {
                try {
                    var playing = false;
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        if (!v.paused && !v.ended && v.readyState > 2) { playing = true; break; }
                    }
                    if (window.AndroidPip) window.AndroidPip.setPlaying(playing);
                } catch (e) {}
            }
            document.addEventListener('play', report, true);
            document.addEventListener('pause', report, true);
            document.addEventListener('ended', report, true);
            setInterval(report, 1000);
        })();
    """
}

/**
 * Nhiều trang tự đặt thẻ <meta viewport> với user-scalable=no để chặn zoom trên
 * mobile. Ghi đè lại để cho phép pinch-to-zoom, kể cả khi đang xem video.
 */
object ZoomEnabler {
    const val JS = """
        (function() {
            try {
                var content = 'width=device-width, initial-scale=1.0, maximum-scale=6.0, user-scalable=yes';
                var meta = document.querySelector('meta[name=viewport]');
                if (meta) {
                    meta.setAttribute('content', content);
                } else {
                    var m = document.createElement('meta');
                    m.name = 'viewport';
                    m.content = content;
                    document.head.appendChild(m);
                }
            } catch (e) {}
        })();
    """
}

/**
 * Chặn kiểu quảng cáo "phủ toàn màn hình vô hình" hay dùng để bẫy người dùng
 * bấm vào đâu cũng bị điều hướng sang trang khác.
 */
object AdOverlayBlocker {
    const val JS = """
        (function() {
            if (window.__overlayBlockerRunning) return;
            window.__overlayBlockerRunning = true;
            function killOverlays() {
                try {
                    var all = document.querySelectorAll('body *');
                    for (var i = 0; i < all.length; i++) {
                        var el = all[i];
                        var tag = el.tagName;
                        if (tag === 'VIDEO' || tag === 'SCRIPT' || tag === 'STYLE') continue;
                        var style = window.getComputedStyle(el);
                        if (style.position !== 'fixed' && style.position !== 'absolute') continue;
                        var z = parseInt(style.zIndex) || 0;
                        if (z < 999) continue;
                        var rect = el.getBoundingClientRect();
                        var coversScreen = rect.width >= window.innerWidth * 0.85 &&
                                            rect.height >= window.innerHeight * 0.85;
                        if (coversScreen) {
                            el.style.setProperty('display', 'none', 'important');
                            el.style.setProperty('pointer-events', 'none', 'important');
                        }
                    }
                } catch (e) {}
            }
            setInterval(killOverlays, 700);
        })();
    """
}

/**
 * Hiện thẻ xanh lá "Tải về" ở góc trên-trái khi trang có video, bấm vào sẽ gọi
 * xuống Kotlin (qua AndroidDownloader) để lưu video vào bộ nhớ máy.
 * Lưu ý: chỉ hoạt động với video có link file trực tiếp (mp4/webm...). Với các
 * trang mã hoá luồng video dạng blob: (ví dụ YouTube) sẽ không tải được, vì đó
 * là giới hạn kỹ thuật/điều khoản của các trang đó, không phải lỗi app.
 */
object VideoDownloadUI {
    const val JS = """
        (function() {
            if (window.__downloadUIRunning) return;
            window.__downloadUIRunning = true;
            var btn = null;
            function ensureButton() {
                if (btn) return;
                btn = document.createElement('div');
                btn.innerText = '⬇ Tải về';
                btn.style.position = 'fixed';
                btn.style.top = '10px';
                btn.style.left = '10px';
                btn.style.zIndex = '2147483647';
                btn.style.background = '#22c55e';
                btn.style.color = '#ffffff';
                btn.style.padding = '6px 14px';
                btn.style.borderRadius = '6px';
                btn.style.fontFamily = 'sans-serif';
                btn.style.fontSize = '13px';
                btn.style.fontWeight = 'bold';
                btn.style.boxShadow = '0 2px 6px rgba(0,0,0,0.5)';
                btn.style.cursor = 'pointer';
                document.body.appendChild(btn);
                btn.addEventListener('click', function(e) {
                    e.stopPropagation();
                    var v = document.querySelector('video');
                    var src = v ? (v.currentSrc || v.src || '') : '';
                    if (window.AndroidDownloader) {
                        window.AndroidDownloader.downloadVideo(src, document.title || 'video');
                    }
                });
            }
            setInterval(function() {
                try {
                    var v = document.querySelector('video');
                    if (v) {
                        ensureButton();
                        btn.style.display = 'block';
                    } else if (btn) {
                        btn.style.display = 'none';
                    }
                } catch (e) {}
            }, 800);
        })();
    """
}
