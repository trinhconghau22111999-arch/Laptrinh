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
        // CHỈ ẩn thanh trạng thái (giờ/mạng/pin) để có thêm không gian màn hình. Thanh điều
        // hướng hệ thống (3 phím Back/Home/Recent hoặc gesture bar) được GIỮ NGUYÊN, LUÔN HIỆN
        // MẶC ĐỊNH, không còn bị ẩn đi như trước (trước đây dùng Type.systemBars() ẩn cả hai).
        // Dùng WindowInsetsControllerCompat của androidx để hoạt động đúng trên mọi phiên bản
        // Android (kể cả các máy Android cũ hơn không có API ẩn thanh điều hướng mới).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Chế độ ẩn toàn màn hình có thể bị huỷ khi bàn phím hiện lên, chuyển app đi rồi quay
        // lại... - áp dụng lại mỗi lần cửa sổ được lấy focus để luôn giữ đúng trạng thái ẩn.
        //
        // LỖI ĐÃ SỬA (lần 1): bấm vào ô địa chỉ (edtUrl) để gõ chữ thì bàn phím ảo KHÔNG bật lên
        // được, vì enableImmersiveMode() bị gọi lại đúng lúc IME đang hiện lên -> huỷ mất.
        // Sửa lần 1 bằng cách loại trừ theo LOẠI VIEW đang giữ focus (currentFocus !is EditText).
        //
        // LỖI ĐÃ SỬA (lần 2): cách loại trừ theo loại View ở lần 1 không bắt được trường hợp ô
        // nhập liệu NẰM TRONG WebView (currentFocus lúc đó là WebView, không phải EditText) ->
        // vẫn bị gọi enableImmersiveMode() giữa lúc bàn phím đang hiện cho web -> vẫn lỗi.
        //
        // LỖI ĐÃ SỬA (lần 3 - NGUYÊN NHÂN GỐC, đây mới là lý do lần 2 sửa xong vẫn không hết
        // lỗi): đoán qua LOẠI VIEW (currentFocus) là cách không đáng tin - còn có nút Back nổi
        // (FloatingBackButton, xem file đó) là 1 WINDOW RIÊNG luôn hiện sẵn, trước đây tranh
        // giành input focus với window chính, khiến chính bản thân hasFocus/currentFocus báo cáo
        // sai lệch. Đã sửa tận gốc ở FloatingBackButton.kt (thêm FLAG_NOT_FOCUSABLE). Ở ĐÂY sửa
        // thêm lớp bảo vệ thứ 2, ĐÁNG TIN CẬY HƠN NHIỀU: hỏi THẲNG hệ thống "bàn phím ảo (IME) có
        // đang thật sự hiển thị không" qua WindowInsetsCompat, thay vì đoán qua loại View đang
        // giữ focus - cách này đúng với MỌI trường hợp (EditText, ô nhập trong WebView, hay bất
        // kỳ ô nhập nào khác sau này), không cần liệt kê từng loại View một nữa.
        if (hasFocus) {
            val imeVisible = androidx.core.view.ViewCompat
                .getRootWindowInsets(window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (!imeVisible) enableImmersiveMode()
        }
    }

    private lateinit var webView: WebView
    private lateinit var edtUrl: EditText
    private lateinit var toolbarUrl: View
    private var progressBar: ProgressBar? = null
    private lateinit var homeOverlay: View
    private lateinit var imgWallpaper: android.widget.ImageView
    private var edtHomeSearch: EditText? = null
    private lateinit var homeScreenManager: HomeScreenManager

    // Đánh dấu điều hướng do chính app gọi (từ thanh địa chỉ / menu đề xuất / mở lại tab)
    // để KHÔNG hỏi xác nhận, chỉ hỏi khi người dùng bấm link ngay trên trang.
    private var programmaticLoad = false

    // Bấm nút "🖥 Bản máy tính" nổi -> ép trang HIỆN TẠI sang UA máy tính tới khi tắt lại.
    private var forceDesktop = false

    // ĐÃ BỎ (theo yêu cầu): tính năng "cửa sổ nổi trong app" (mini-player WebView riêng tự动
    // mở khi xem YouTube) - gây lỗi bàn phím ảo không bật lên được (WebView nổi tự cướp focus)
    // và lỗi "153 - Lỗi cấu hình trình phát video" (tải embed sai cách). Không đáng công sửa
    // tiếp vì tính năng "phát nền thật" khi thoát hẳn app (bấm Home vật lý) vốn không làm được
    // bằng WebView (xem giải thích trong hội thoại) - giữ app đơn giản, ổn định hơn.
    private var floatingBackButtonHandle: FloatingBackButton.Handle? = null

    // Nút "Off" nổi thứ 2, cùng kiểu nút tròn nổi kéo-thả với nút Back (xem FloatingBackButton),
    // nhưng bấm vào sẽ phủ màn hình "giả tắt" (FakeScreenOff) thay vì lùi trang - dùng khi đang
    // xem video (Youtube...) muốn "tắt màn hình" tạm thời (video/nhạc vẫn phát, chỉ chặn chạm
    // nhầm) mà không phải tắt màn hình thật của máy (tắt thật thì Youtube tự dừng video).
    private var floatingOffButtonHandle: FloatingBackButton.Handle? = null

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
        floatingOffButtonHandle?.resync()
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
        // FIX: trước đây chỉ đóng cửa sổ nổi (customView) nếu có, còn video đang phát BÌNH
        // THƯỜNG (chưa fullscreen/chưa tách cửa sổ nổi) thì WebView vẫn nằm phía SAU
        // homeOverlay và tiếp tục chạy -> tiếng vẫn phát dù đã "thoát" về màn hình chính.
        // Dừng hẳn mọi video đang phát trên trang hiện tại mỗi khi rời về màn hình chính.
        pauseAllVideos()
    }

    private val pauseRetryHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Dừng phát TẤT CẢ thẻ <video> VÀ <audio> đang có trên trang hiện tại trong WebView (kể cả
     *  video YouTube dạng bình thường chưa fullscreen, và các trang nghe nhạc dùng thẻ <audio>
     *  như Zing MP3, SoundCloud, NhacCuaTui...) - dùng mỗi khi người dùng rời khỏi trang/rời
     *  khỏi app để tiếng không tiếp tục phát ngầm ngoài ý muốn.
     *  FIX (lỗi #5): trước đây chỉ pause thẻ <video>, bỏ sót thẻ <audio>.
     *  FIX (YouTube vẫn phát sau khi rời app): evaluateJavascript() ở đây là lệnh "bắn rồi
     *  thôi" (bất đồng bộ) - nếu webView.onPause() được gọi ngay sau đó (ở onPause()/
     *  onUserLeaveHint()) làm WebView tạm ngưng xử lý ĐÚNG lúc lệnh JS này chưa kịp chạy tới,
     *  video/nhạc có thể lỡ không bị dừng dù code trông như đã gọi pause(). Gọi lại 1 lần nữa
     *  sau 150ms (khi WebView chắc chắn đã xử lý xong lượt đầu) để đảm bảo chắc chắn dừng hẳn. */
    private fun pauseAllVideos() {
        if (!::webView.isInitialized) return
        val js = "(function(){" +
            "var els=document.querySelectorAll('video,audio');" +
            "for(var i=0;i<els.length;i++){try{els[i].pause();}catch(e){}}" +
            "})();"
        webView.evaluateJavascript(js, null)
        pauseRetryHandler.postDelayed({
            if (::webView.isInitialized) webView.evaluateJavascript(js, null)
        }, 150)
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
                "CalendarActivity" -> CalendarActivity::class.java
                "CalculatorActivity" -> CalculatorActivity::class.java
                "ClockActivity" -> ClockActivity::class.java
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
                edtUrl.setText(url)
                view?.evaluateJavascript(AdOverlayBlocker.JS, null)
                if (YoutubeAdSkipper.isYoutube(url)) {
                    // AdOverlayBlocker KHÔNG chạy trên YouTube - nó dùng querySelectorAll('body *')
                    // quét toàn bộ DOM mỗi 700ms, YouTube có hàng nghìn element -> gây lag nặng.
                    // YoutubeAdSkipper đã xử lý overlay quảng cáo YouTube rồi, không cần thêm.
                    view?.evaluateJavascript(YoutubeAdSkipper.JS, null)
                } else {
                    // Nút "Tải về" (VideoDownloadUI) KHÔNG chèn trên YouTube - vì YouTube mã
                    // hoá luồng video dạng blob: nên nút này bấm vào không tải được gì cả (xem
                    // giải thích ở VideoDownloadUI), chỉ án ngữ giao diện vô ích. Các trang khác
                    // có video link file trực tiếp (mp4/webm...) vẫn hiện nút bình thường.
                    view?.evaluateJavascript(VideoDownloadUI.JS, null)
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
        // trang tự bật khi xoay ngang) -> Back chỉ thoát fullscreen bình thường.
        if (customView != null) {
            webView.webChromeClient?.onHideCustomView()
            return
        }
        val currentUrl = webView.url
        when {
            webView.canGoBack() && YoutubeAdSkipper.isYoutube(currentUrl) && !YoutubeAdSkipper.isYoutubeHome(currentUrl) -> {
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
    // DÙNG (bấm nút Home, vuốt sang app khác, mở Recents...).
    //
    // Ý ĐỊNH: nhạc/video đang phát PHẢI tiếp tục chạy nền như 1 app nghe nhạc bình thường khi
    // rời app (tắt màn hình, bấm Home, chuyển app khác) - CHỈ dừng khi người dùng tự bấm dừng
    // trên trang, hoặc khi tiến trình app bị dọn hẳn (vuốt xoá khỏi Recents). Vì vậy KHÔNG gọi
    // pauseAllVideos()/webView.onPause() ở đây - để mặc WebView tiếp tục xử lý JS/video/audio
    // bình thường dù activity không còn hiển thị.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
    }

    // Gọi khi Activity bị đưa xuống nền vì BẤT KỲ lý do gì (Home, chuyển app khác, tắt màn
    // hình, mở app khác đè lên...). KHÔNG dừng video/audio ở đây (xem giải thích ở
    // onUserLeaveHint() phía trên) - để nhạc/video tiếp tục phát nền đúng như yêu cầu.
    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
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
        // fixed = true: nút Back CỐ ĐỊNH ở góc DƯỚI-PHẢI, không kéo-thả được nữa và không đổi
        // vị trí dù xoay ngang/dọc màn hình (xem chi tiết ở FloatingBackButton.attach).
        floatingBackButtonHandle = FloatingBackButton.attach(
            activity = this,
            root = root,
            onTap = { doBack() },
            onLongPress = { if (homeOverlay.visibility != View.VISIBLE) showHomeOverlay() },
            defaultIsRight = true,
            fixed = true,
            doubleTapOnly = true
        )

        // Nút "Off" nổi - bấm để phủ màn hình giả tắt (xem FakeScreenOff). Icon "⏻" (nút nguồn)
        // để phân biệt rõ với mũi tên "◁" của nút Back. fixed = true: CỐ ĐỊNH ở góc DƯỚI-TRÁI
        // (defaultIsRight = false), đối xứng với nút Back ở góc dưới-phải, không kéo-thả được
        // và không đổi vị trí dù xoay màn hình.
        floatingOffButtonHandle = FloatingBackButton.attach(
            activity = this,
            root = root,
            // Truyền [webView] để FakeScreenOff tự hạ chất lượng video xuống thấp nhất lúc bật
            // (tiết kiệm CPU/GPU giải mã -> đỡ pin hơn khi không ai nhìn hình), và tự phục hồi
            // đúng chất lượng cũ lúc tắt lớp phủ - xem giải thích chi tiết ở FakeScreenOff.kt.
            onTap = { FakeScreenOff.show(this, webView) },
            id = "off",
            icon = "⏻",
            defaultIsRight = false,
            fixed = true,
            doubleTapOnly = true
        )
    }

    // Thoát app -> xoá sạch mọi dấu vết phiên làm việc
    override fun onDestroy() {
        clearAllSessionData()
        floatingBackButtonHandle?.detach()
        floatingOffButtonHandle?.detach()
        FakeScreenOff.hide()
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
            }, 500);
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
            setInterval(killOverlays, 2000);
        })();
    """
}

/**
 * Hiện thẻ xanh lá "Tải về" ở góc trên-trái khi trang có video, bấm vào sẽ gọi
 * xuống Kotlin (qua AndroidDownloader) để lưu video vào bộ nhớ máy.
 * Lưu ý: chỉ hoạt động với video có link file trực tiếp (mp4/webm...). Với các
 * trang mã hoá luồng video dạng blob: (ví dụ YouTube) sẽ không tải được, vì đó
 * là giới hạn kỹ thuật/điều khoản của các trang đó, không phải lỗi app - nên
 * KHÔNG chèn script này trên YouTube nữa (xem điều kiện gọi ở onPageFinished),
 * để tránh hiện 1 nút vô dụng (bấm không tải được gì) đè lên logo/UI YouTube.
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
            }, 2000);
        })();
    """
}
