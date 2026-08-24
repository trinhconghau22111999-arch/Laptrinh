package com.h.adblockbrowser

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceResponse
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

/**
 * ĐÃ VIẾT LẠI HOÀN TOÀN theo yêu cầu mới - MainActivity giờ CHỈ còn đúng 1 việc: hiện màn hình
 * "chọn 1 trong 3 app" mỗi khi mở app, KHÔNG còn là 1 trình duyệt/Start Screen kiểu Windows
 * Phone nữa. Cụ thể đã BỎ HẲN so với bản trước:
 *  - Xin quyền (Camera/Mic/Vị trí/Bộ nhớ/Thông báo...) lúc vào app - [requestAllPermissions]
 *    cũ đã xoá, KHÔNG còn hộp thoại xin quyền hệ thống nào hiện ra khi mở app nữa - NGOẠI TRỪ
 *    đúng 1 hộp thoại "Truy cập mọi tệp" hiện DUY NHẤT 1 LẦN ở lần mở app đầu tiên sau khi cài
 *    đặt (xem [maybeShowFirstLaunchAllFilesAccessPrompt]), thêm lại theo yêu cầu riêng.
 *  - Trang "DS Ứng dụng" (liệt kê toàn bộ app cài trên máy) - đã xoá cùng với cả hệ thống
 *    Start Screen kiểu Pivot 2 trang (xem HomeScreenManager.kt bản mới, đã viết lại hoàn toàn).
 *  - 2 nút nổi "Back / Start" (WpNavBar) từng thay thế thanh điều hướng hệ thống - đã xoá, vì
 *    giờ KHÔNG còn ẩn thanh điều hướng/trạng thái hệ thống nữa (xem bên dưới), đa nhiệm hệ
 *    thống (nút Recents/vuốt đa nhiệm thật của máy) luôn dùng được ở mọi màn hình.
 *  - Toàn bộ WebView/thanh địa chỉ/hình nền/widget/ghim tile/YouTube... của bản trình duyệt cũ -
 *    không còn cần thiết vì màn chính giờ không tự duyệt web nữa, chỉ mở 1 trong 3 Activity có
 *    sẵn (Ẩn danh / Nhiều tài khoản / Quản lý tệp).
 *
 * Hành vi "Back thoát app con = thoát hẳn app": xử lý ở TỪNG activity con (IncognitoActivity/
 * AccountsActivity/FilesActivity) - khi Back tới điểm "hết lịch sử, thoát màn gốc của app con
 * đó", các activity này gọi finishAffinity() thay vì finish() như trước, nên thoát LUÔN cả app
 * (không quay lại màn chọn 3 app này). Mở lại app từ đầu vì vậy luôn bắt đầu lại đúng ở màn
 * chọn 3 app này - đúng yêu cầu "vào lại là vào lại từ đầu", không cần lưu/khôi phục gì thêm.
 */
class MainActivity : AppCompatActivity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // CHỦ Ý: không gọi bất kỳ hàm ẩn status bar/thanh điều hướng hệ thống nào ở đây (khác
        // hẳn bản cũ) - để thanh điều hướng/status bar + đa nhiệm (Recents) THẬT của hệ thống
        // luôn hiện, luôn dùng được ở mọi lúc, đúng yêu cầu "luôn bật đa nhiệm của hệ thống".

        // Khoá ứng dụng (PIN/Hình, đặt ở Cài đặt trước đây) - giữ nguyên tính năng này, KHÔNG
        // thuộc diện "yêu cầu quyền" (đây là khoá riêng của app, không phải hộp thoại quyền hệ
        // thống) nên không bị xoá theo yêu cầu "xoá yêu cầu quyền khi vào app".
        if (AppLockPrefs.isEnabled(this)) {
            startActivityForResult(Intent(this, LockScreenActivity::class.java), REQ_LOCK)
        } else {
            showChooser()
            maybeShowFirstLaunchAllFilesAccessPrompt()
        }
    }

    companion object {
        const val REQ_LOCK = 103
        private const val PREFS_FIRST_RUN = "first_run"
        private const val KEY_ASKED_ALL_FILES_ACCESS = "asked_all_files_access"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_LOCK) {
            if (resultCode == RESULT_OK) {
                showChooser()
                maybeShowFirstLaunchAllFilesAccessPrompt()
            }
            // Khác RESULT_OK (bị đưa xuống nền qua nút back của màn khoá) - không làm gì, app
            // vẫn ở màn khoá lúc quay lại foreground lần sau.
        }
    }

    /** Hộp thoại xin quyền "Truy cập mọi tệp" (MANAGE_EXTERNAL_STORAGE) - CHỈ hiện đúng 1 LẦN
     *  DUY NHẤT, ngay lần đầu mở app sau khi cài đặt. Đánh dấu "đã hỏi" vào SharedPreferences
     *  NGAY LẬP TỨC (trước khi biết người dùng bấm gì) nên hộp thoại không hiện lại ở các lần mở
     *  sau nữa, kể cả khi người dùng bấm "Để sau" hoặc rời app mà chưa cấp quyền ở Cài đặt.
     *  Quyền này KHÔNG xin được qua hộp thoại quyền runtime thông thường (như Camera/Vị trí...),
     *  phải mở đúng màn Cài đặt hệ thống để người dùng tự bật.
     *  LƯU Ý: màn "Quản lý tệp" (xem FilesActivity.checkAllFilesAccess()) vẫn TỰ nhắc lại quyền
     *  này mỗi lần mở nếu còn thiếu - đó là nhắc lại theo NGỮ CẢNH (đúng lúc cần dùng tệp), tách
     *  biệt hoàn toàn với lần hỏi MỘT LẦN DUY NHẤT lúc khởi động app ở đây. */
    private fun maybeShowFirstLaunchAllFilesAccessPrompt() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return
        val prefs = getSharedPreferences(PREFS_FIRST_RUN, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED_ALL_FILES_ACCESS, false)) return
        prefs.edit().putBoolean(KEY_ASKED_ALL_FILES_ACCESS, true).apply()
        if (android.os.Environment.isExternalStorageManager()) return

        AlertDialog.Builder(this, R.style.Theme_WP_Dialog)
            .setTitle("Cần quyền truy cập tệp")
            .setMessage("Để ứng dụng xem/xoá/chia sẻ được mọi tệp trên máy (mục Quản lý tệp), hãy cấp quyền \"Truy cập mọi tệp\" ở Cài đặt hệ thống.")
            .setPositiveButton("Mở Cài đặt") { _, _ ->
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    } catch (e2: Exception) { }
                }
            }
            .setNegativeButton("Để sau", null)
            .show()
    }

    /** Dựng màn "chọn 1 trong 3 app": nền đen tuyệt đối phủ toàn màn hình, CHỈ có 3 icon (đã
     *  phóng to) xếp thành 1 CỘT DỌC, nằm giữa màn hình cả theo chiều ngang lẫn chiều dọc -
     *  ngoài 3 icon này ra không còn gì khác (không chữ tiêu đề, không thanh trạng thái riêng,
     *  không nút phụ). Chạm vào 1 icon sẽ mở đúng app con tương ứng. */
    private fun showChooser() {
        val root = findViewById<FrameLayout>(R.id.rootFrame)
        root.removeAllViews()
        root.setBackgroundColor(Color.BLACK)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val items = listOf(
            Triple("Ẩn danh", R.drawable.ic_shortcut_incognito, IncognitoActivity::class.java),
            Triple("Nhiều tài khoản", R.drawable.ic_shortcut_accounts, AccountsActivity::class.java),
            Triple("Quản lý tệp", R.drawable.ic_shortcut_files, FilesActivity::class.java)
        )

        val iconSize = dp(120)
        items.forEachIndexed { index, (label, iconRes, activityClass) ->
            val itemContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                isFocusable = true
                val outValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setPadding(dp(24), dp(20), dp(24), dp(20))
                setOnClickListener { startActivity(Intent(this@MainActivity, activityClass)) }
            }

            itemContainer.addView(ImageView(this).apply {
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            })

            itemContainer.addView(TextView(this).apply {
                text = label
                textSize = 20f
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(12), 0, 0)
            })

            val itemLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) itemLp.topMargin = dp(28)
            column.addView(itemContainer, itemLp)
        }

        root.addView(
            column,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
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
                        'tp-yt-paper-dialog.ytd-popup-container, ' +
                        'ytm-open-in-app-button, ytm-app-promo-banner-renderer, ' +
                        '.mobile-topbar-header-open-app-button-container, ' +
                        'yt-open-in-app-button-renderer, [id*="open-in-app" i], [class*="open-in-app" i]'
                    );
                    overlays.forEach(function(el) { el.style.display = 'none'; });

                    // Nút/thẻ "Mở ứng dụng" (mời cài/mở app YouTube thật) ở đầu trang - selector
                    // CSS của YouTube hay đổi tên class nên KHÔNG đáng tin cậy 100%, dò thêm theo
                    // NỘI DUNG CHỮ (đa ngôn ngữ) để chắc chắn bắt được, dù YouTube đổi class.
                    var promoTexts = ['mở ứng dụng', 'open app', 'open the app', 'open in app'];
                    var candidates = document.querySelectorAll('a, button, ytd-button-renderer, tp-yt-paper-button');
                    for (var j = 0; j < candidates.length; j++) {
                        var elx = candidates[j];
                        var txt = (elx.innerText || elx.textContent || '').trim().toLowerCase();
                        if (promoTexts.indexOf(txt) !== -1) {
                            // Ẩn cả khối cha gần nhất (thường là 1 thanh/khung chứa icon + chữ)
                            // thay vì chỉ ẩn mỗi chữ, để không để lại khoảng trống/icon mồ côi.
                            var target = elx.closest('ytm-open-in-app-button, .mobile-topbar-header-open-app-button-container, ytm-app-promo-banner-renderer') || elx;
                            target.style.display = 'none';
                        }
                    }
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
