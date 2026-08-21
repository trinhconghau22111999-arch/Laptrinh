package com.h.adblockbrowser

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/** Màn "Điện thoại" (tile "phone" trên trang Start) - mô phỏng 1 MÀN HÌNH CHÍNH ANDROID THẬT
 *  (background full-screen + icon ứng dụng ĐẶT TỰ DO kéo-thả được, không xếp lưới cố định như
 *  Live Tile) THAY VÌ tiếp tục phong cách Live Tile WP của trang Start - đúng như yêu cầu: "1
 *  trang giống desktop trên máy tính, na ná như 1 trang màn hình chính android... phía trên có
 *  tiện ích thời gian, rồi 1 cạnh ứng dụng như android nhưng nằm dọc theo cạnh phải" - tức
 *  KHÔNG dùng lại thẩm mỹ WP ở màn này (đây là màn DUY NHẤT trong app cố tình phá cách, đóng vai
 *  "phần Android thật" nằm cạnh "phần WP giả lập" - giống việc 1 máy Windows Phone thật KHÔNG
 *  BAO GIỜ có thể trông giống Android, nhưng app này cố tình dựng thêm 1 "cửa sổ" kiểu Android
 *  để dùng như 1 desktop phụ, tương tự khái niệm "This PC/Desktop" bên Windows).
 *
 *  3 phần, từ trên xuống:
 *   1) Widget giờ/ngày kiểu WIDGET ANDROID THẬT (chữ trắng đổ bóng nổi trực tiếp trên ảnh nền,
 *      KHÔNG có khung nền màu như Live Tile của trang Start - khác biệt thẩm mỹ CỐ Ý, xem giải
 *      thích ở trên).
 *   2) Vùng "desktop" - icon các app đã "Thêm vào Điện thoại" qua menu nhấn giữ (lưu ở
 *      [DesktopAppsStore] - TÁCH BIỆT HOÀN TOÀN với [PinnedAppsStore] của trang Start).
 *      Ghim vào Start KHÔNG tự thêm vào đây, và ngược lại - người dùng chủ động chọn
 *      riêng từng nơi. Kéo-thả tự do bất kỳ đâu, vị trí lưu qua [DesktopIconStore]. Ô đồng hồ
 *      ở trên cũng được coi NHƯ 1 ICON ỨNG DỤNG BÌNH THƯỜNG trong vùng này (id đặc biệt
 *      [CLOCK_ID]) - kéo-thả được y hệt icon app, KHÔNG còn cố định 1 chỗ như trước.
 *   3) Dock dọc cạnh phải - lối tắt CỐ ĐỊNH tới các chức năng riêng của app (không phải app
 *      ngoài) để luôn có sẵn dù danh sách ghim trống - giống vai trò 1 "taskbar" thu nhỏ. KÉO
 *      1 icon (app HOẶC ô đồng hồ) từ vùng desktop THẢ VÀO dải này (chạm cuối nằm trong vùng
 *      dock) sẽ THÊM icon đó vào dock luôn (lưu ở [DesktopDockStore]), biến mất khỏi vùng tự
 *      do - NHẤN GIỮ 1 icon loại này NGAY TRONG dock để gỡ trở lại vùng tự do.
 */
class DesktopActivity : AppCompatActivity() {

    companion object {
        /** Id đặc biệt đại diện cho ô đồng hồ/ngày trong các store dùng chung với app thật
         *  ([DesktopIconStore] lưu vị trí tự do, [DesktopDockStore] đánh dấu đã kéo vào dock) -
         *  không trùng bất kỳ tên package Android thật nào (luôn có dạng "a.b.c"). */
        const val CLOCK_ID = "clock_widget"
    }


    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.wp_slide_in_left, R.anim.wp_slide_out_right)
    }

    private var navBarHandle: WpNavBar.Handle? = null
    private lateinit var desktopArea: FrameLayout
    private lateinit var dock: LinearLayout
    private lateinit var clockWidget: LinearLayout
    // Dấu ✕ (loại bỏ app khỏi Điện thoại) đang hiện, nếu có - xem showRemoveBadge(). Giữ tham
    // chiếu để: (1) tự dọn dấu ✕ CŨ khi nhấn giữ 1 icon KHÁC (chỉ 1 dấu ✕ hiện tại 1 lúc), (2)
    // dọn khi chạm ra khoảng trống (xem setOnClickListener của desktopArea bên dưới).
    private var activeRemoveBadge: View? = null
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView

    // Dòng chữ hướng dẫn "Giữ 1 ứng dụng... để nó xuất hiện ở đây" CHỈ hiện trong 10 giây ĐẦU
    // TIÊN của LẦN MỞ trang Điện thoại ĐẦU TIÊN sau khi cài app (không hiện lại ở những lần mở
    // sau, kể cả khi trang vẫn đang trống) - đúng ý người dùng: hướng dẫn 1 lần cho biết cách
    // dùng, không lặp lại gây rối mắt mỗi lần vào trang trống. Cờ [showEmptyHint] chỉ đặt true
    // ở onCreate() (không đặt lại ở layoutPinnedIcons() - hàm đó có thể gọi lại nhiều lần mỗi
    // khi onResume/ghim thêm app), và cờ "đã từng mở" lưu VĨNH VIỄN qua SharedPreferences nên
    // dù đóng app hẳn rồi mở lại cũng không hiện lại nữa.
    private var showEmptyHint = false
    private val hideHintRunnable = Runnable {
        showEmptyHint = false
        layoutPinnedIcons()
    }

    /** true nếu đây là lần ĐẦU TIÊN trang Điện thoại được mở kể từ khi cài app (hoặc từ khi cài
     *  đè bản mới - dữ liệu SharedPreferences không mất khi cập nhật app, chỉ mất khi gỡ cài).
     *  Đánh dấu "đã mở" NGAY LẬP TỨC (không đợi hết 10 giây) để dù người dùng thoát app giữa
     *  chừng trong 10 giây đó, lần mở KẾ TIẾP vẫn không tính là "lần đầu" nữa. */
    private fun consumeFirstOpenFlag(): Boolean {
        val prefs = getSharedPreferences("desktop_prefs", MODE_PRIVATE)
        val isFirst = !prefs.getBoolean("opened_once", false)
        if (isFirst) prefs.edit().putBoolean("opened_once", true).apply()
        return isFirst
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideStatusBar()

        val outer = FrameLayout(this)

        // ── Ảnh nền full-screen - DÙNG CHUNG 1 ảnh với trang Start (WallpaperPrefs) để 2 màn
        // nhìn như "cùng 1 chiếc điện thoại", không lệch tông dù đổi hẳn phong cách UI phía trên. ──
        val imgWallpaper = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.default_wallpaper)
        }
        outer.addView(imgWallpaper, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val wallpaperUri = WallpaperPrefs.get(this)
        if (wallpaperUri != null) {
            try {
                imgWallpaper.setImageURI(Uri.parse(wallpaperUri))
            } catch (e: Exception) {
            }
        }

        // ── Widget giờ/ngày kiểu Android thật: chữ trắng đổ bóng đè trực tiếp lên ảnh nền,
        // KHÔNG khung màu (khác Live Tile của Start - xem class doc ở trên). GIỜ ĐÂY được coi
        // NHƯ 1 ICON ỨNG DỤNG BÌNH THƯỜNG trong vùng desktop tự do (xem layoutPinnedIcons() -
        // gắn vào [desktopArea] + [attachDrag] y hệt icon app khác) - KHÔNG còn gắn cố định 1
        // vị trí thẳng vào [outer] như trước, KHÔNG còn 2 vùng chạm riêng (giờ mở Đồng hồ, ngày
        // mở Lịch) - chạm/kéo xử lý CHUNG cho cả khối qua [attachDrag], chạm (không kéo) LUÔN mở
        // Đồng hồ, đúng ý "xem như 1 icon ứng dụng luôn".
        clockWidget = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        tvTime = TextView(this).apply {
            textSize = 56f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setShadowLayer(dp(6).toFloat(), 0f, dp(2).toFloat(), 0x99000000.toInt())
        }
        tvDate = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFFEEEEEE.toInt())
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), 0x99000000.toInt())
        }
        clockWidget.addView(tvTime)
        clockWidget.addView(tvDate)
        updateClock()

        // ── Vùng desktop: icon tự do kéo-thả. Chừa lề phải cho dock, lề trên cho widget giờ. ──
        desktopArea = FrameLayout(this)
        // Chạm vào khoảng TRỐNG (không trúng icon nào) trên trang - nếu đang hiện dấu ✕ (vừa
        // nhấn giữ 1 icon) thì huỷ nó đi, coi như "bấm ra ngoài để thoát chế độ xoá".
        desktopArea.setOnClickListener {
            activeRemoveBadge?.let { badge -> desktopArea.removeView(badge) }
            activeRemoveBadge = null
        }
        outer.addView(desktopArea, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).also {
            // 72dp (không phải 56dp trước đây) - PHẢI khớp ĐÚNG bề ngang dock bên dưới (xem
            // outer.addView(dock,...)) - dock cũ chỉ 56dp trong khi icon bên trong to 64dp,
            // khiến icon bị TRÀN RA NGOÀI khung dock và bị CẮT MẤT 2 bên (mỗi bên ~4dp), các
            // icon trông không đều/khuất góc - giờ dock đủ rộng để chứa TRỌN VẸN icon 64dp
            // (dư thêm ~8dp làm khoảng đệm 2 bên) nên không còn bị cắt nữa.
            it.rightMargin = dp(72)
        })

        // ── Dock dọc cạnh phải - lối tắt cố định tới chức năng riêng của app + các mục người
        // dùng đã KÉO THẢ vào đây ([DesktopDockStore]) - nội dung được dựng trong [rebuildDock]
        // (tách hàm riêng để gọi lại được mỗi khi thêm/gỡ mục kéo-thả, không chỉ dựng 1 lần lúc
        // mở trang). ──
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // ĐÁY (Gravity.BOTTOM) + GIỮA THEO CHIỀU NGANG (CENTER_HORIZONTAL) - trước đây các
            // icon lơ lửng giữa màn hình, chừa khoảng trống rỗng phía trên LẪN phía dưới rất
            // lãng phí - giờ dính SÁT ĐÁY dock, chỉ còn khoảng trống phía trên (đúng yêu cầu
            // "nằm sát cạnh dưới"), đồng thời chừa đúng khoảng bằng chiều cao [WpNavBar] (thanh
            // điều hướng nổi, cửa sổ hệ thống riêng LUÔN đè lên trên cùng ở mép dưới màn hình)
            // để icon cuối cùng (nút "Về Start") không bị thanh đó che khuất mất 1 phần.
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(WpNavBar.HEIGHT_DP) + dp(8))
            background = ColorDrawable(0x66000000)
        }
        rebuildDock()
        outer.addView(dock, FrameLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.MATCH_PARENT).also {
            it.gravity = Gravity.END
        })

        setContentView(outer)
        navBarHandle = WpNavBar.attach(
            activity = this,
            root = outer,
            onBack = { onBackPressed() },
            onStart = { onBackPressed() }
        )

        showEmptyHint = consumeFirstOpenFlag()
        desktopArea.post { layoutPinnedIcons() }
        if (showEmptyHint) {
            // Sau đúng 10 giây kể từ lúc mở trang này lần đầu tiên - tắt hẳn dòng hướng dẫn (kể
            // cả khi người dùng vẫn đang đứng yên ở trang trống xem nó), vẽ lại layoutPinnedIcons()
            // để dòng chữ biến mất, không cần người dùng thao tác gì thêm.
            desktopArea.postDelayed(hideHintRunnable, 10000)
        }
    }

    override fun onResume() {
        super.onResume()
        navBarHandle?.resync()
        updateClock()
        // Re-vẽ mỗi lần quay lại màn này - danh sách "Ghim vào Start" có thể vừa đổi (ghim/gỡ
        // ghim) ở trang Start trong lúc màn này đang ở nền.
        desktopArea.post { layoutPinnedIcons() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Áp lại ẩn thanh trạng thái/điều hướng mỗi lần cửa sổ lấy lại focus - xem giải thích
        // chi tiết ở MainActivity.onWindowFocusChanged(). Màn này không có ô nhập chữ nào trực
        // tiếp trên cửa sổ chính nên không cần kiểm tra IME.
        if (hasFocus) hideStatusBar()
    }

    override fun onDestroy() {
        desktopArea.removeCallbacks(hideHintRunnable)
        navBarHandle?.detach()
        super.onDestroy()
    }

    /** Dựng lại TOÀN BỘ nội dung [dock]: 5 lối tắt CỐ ĐỊNH (youtube/files/settings/calculator/
     *  clock, không đổi được) rồi tới các mục NGƯỜI DÙNG đã kéo-thả vào đây ([DesktopDockStore],
     *  app hoặc ô đồng hồ [CLOCK_ID]), cuối cùng luôn là nút "Về Start". Gọi lại hàm này MỖI KHI
     *  danh sách [DesktopDockStore] đổi (thêm/gỡ 1 mục) để dock hiện đúng nội dung mới ngay,
     *  không cần thoát vào lại trang. */
    private fun rebuildDock() {
        dock.removeAllViews()
        // ── Icon app trong dock (youtube/files/settings/calculator/clock) - GIỐNG ĐÚNG kiểu ô
        // vuông màu accent của nút "Start" bên dưới (trước đây các icon này chỉ là icon trắng
        // trơn không nền, lạc lõng, không rõ là 1 nút bấm được so với nút "Start" nổi bật) -
        // xoay vòng màu qua [ThemePrefs.PALETTE] giống hệt cách các ô Live Tile trên trang
        // "start" xoay màu (xem [HomeScreenManager]), để mỗi icon 1 màu khác nhau, đồng bộ
        // cảm giác Live Tile xuyên suốt toàn app thay vì chỉ riêng nút "Start" có nền màu.
        val dockTilePalette = ThemePrefs.PALETTE
        var dockColorIndex = 0
        val dockKeys = listOf("youtube", "files", "settings", "calculator", "clock")
        dockKeys.forEach { key ->
            ShortcutsRepository.ALL[key]?.let { item ->
                // Icon "YouTube" trong dock: LUÔN nền đỏ cố định (khớp icon "youtube" trên
                // trang start, xem HomeScreenManager) - không xoay vòng palette, không chiếm
                // 1 lượt màu để các icon dock còn lại (files/settings/calculator/clock) không
                // bị lệch màu so với trước.
                val tileColor = if (key == "youtube") {
                    ThemePrefs.PALETTE[11] // 0xFFE51400 - "Đỏ (Red)"
                } else {
                    dockTilePalette[dockColorIndex % dockTilePalette.size].also { dockColorIndex++ }
                }
                dock.addView(FrameLayout(this).apply {
                    background = GradientDrawable().apply {
                        setColor(tileColor)
                    }
                    isClickable = true
                    isFocusable = true
                    contentDescription = item.label
                    setOnClickListener { openShortcut(key) }
                    addView(ImageView(this@DesktopActivity).apply {
                        setImageResource(item.iconRes)
                        val pad = dp(14)
                        setPadding(pad, pad, pad, pad)
                    })
                }, LinearLayout.LayoutParams(dp(64), dp(64)).also { it.topMargin = dp(2) })
            }
        }

        // ── Mục NGƯỜI DÙNG đã KÉO-THẢ vào dock (app đã "Thêm vào Điện thoại", hoặc ô đồng hồ) -
        // xem [DesktopDockStore] - thêm vào bằng cách kéo icon từ vùng desktop tự do thả vào
        // dải dock này (xem [attachDrag]). NHẤN GIỮ 1 mục ở đây -> gỡ khỏi dock, icon quay lại
        // vùng desktop tự do (giữ nguyên vị trí tự do cũ nếu có). ──
        val dockedIds = DesktopDockStore.getAll(this)
        if (dockedIds.isNotEmpty()) {
            dock.addView(View(this).apply {
                setBackgroundColor(0x33FFFFFF)
            }, LinearLayout.LayoutParams(dp(32), dp(1)).also { it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = dp(4); it.bottomMargin = dp(4) })
        }
        val pm = packageManager
        dockedIds.forEach { id ->
            val tileColor = dockTilePalette[dockColorIndex % dockTilePalette.size].also { dockColorIndex++ }
            val tile = FrameLayout(this).apply {
                background = GradientDrawable().apply { setColor(tileColor) }
                isClickable = true
                isFocusable = true
            }
            if (id == CLOCK_ID) {
                tile.contentDescription = "Đồng hồ"
                tile.addView(ImageView(this).apply {
                    setImageResource(R.drawable.ic_shortcut_clock)
                    val pad = dp(14); setPadding(pad, pad, pad, pad)
                })
                tile.setOnClickListener { openShortcut("clock") }
            } else {
                val icon = try { pm.getApplicationIcon(id) } catch (e: Exception) { null }
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(id, 0)).toString()
                } catch (e: Exception) { id }
                tile.contentDescription = label
                tile.addView(ImageView(this).apply {
                    if (icon != null) setImageDrawable(icon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val pad = dp(8); setPadding(pad, pad, pad, pad)
                })
                tile.setOnClickListener {
                    val launch = pm.getLaunchIntentForPackage(id)
                    if (launch != null) startActivity(launch)
                }
            }
            tile.setOnLongClickListener {
                tile.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                DesktopDockStore.remove(this, id)
                rebuildDock()
                layoutPinnedIcons()
                true
            }
            dock.addView(tile, LinearLayout.LayoutParams(dp(64), dp(64)).also { it.topMargin = dp(2) })
        }

        dock.addView(View(this).apply {
            setBackgroundColor(0x33FFFFFF)
        }, LinearLayout.LayoutParams(dp(32), dp(1)).also { it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = dp(4); it.bottomMargin = dp(4) })
        dock.addView(FrameLayout(this).apply {
            // Ô VUÔNG MÀU (accent người dùng đã chọn ở Cài đặt > Giao diện - mặc định Cobalt,
            // có thể là Tím/Chàm nếu người dùng chọn màu đó) đúng kiểu "Live Tile Start" thật
            // của Windows Phone, thay vì icon trắng trơn không nền như trước - để nút này thật
            // sự NỔI BẬT, rõ ràng là 1 nút bấm được, không bị lẫn vào nền tối của dock.
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ThemePrefs.accent(this@DesktopActivity))
            }
            isClickable = true
            isFocusable = true
            contentDescription = "Về Start"
            setOnClickListener { finish() }
            addView(ImageView(this@DesktopActivity).apply {
                setImageResource(R.drawable.ic_wp_start)
                val pad = dp(14)
                setPadding(pad, pad, pad, pad)
            })
        }, LinearLayout.LayoutParams(dp(64), dp(64)).also { it.topMargin = dp(2) })
    }

    private fun updateClock() {
        val cal = Calendar.getInstance()
        tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
        // Định dạng đúng WP: "thứ sáu, 21 tháng 8 2026" (chữ thường, viết đầy đủ)
        val dayNames = arrayOf("chủ nhật", "thứ hai", "thứ ba", "thứ tư", "thứ năm", "thứ sáu", "thứ bảy")
        val dayName = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val dom = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        tvDate.text = "$dayName, $dom tháng $month $year"
    }

    private fun openShortcut(key: String) {
        val item = ShortcutsRepository.ALL[key] ?: return
        if (item.type == ShortcutType.WEB) {
            // "from_phone": báo cho MainActivity biết nó được mở TỪ trang Điện thoại, để nút
            // Back/Start (Home) của WpNavBar bên đó biết đường quay VỀ ĐÂY (trang Điện thoại)
            // thay vì mặc định về Start - xem MainActivity.openedFromPhone.
            startActivityWp(Intent(this, MainActivity::class.java)
                .putExtra("initial_url", item.target)
                .putExtra("from_phone", true))
        } else {
            val activityClass = when (item.target.split(":", limit = 2)[0]) {
                "FilesActivity" -> FilesActivity::class.java
                "CalendarActivity" -> CalendarActivity::class.java
                "CalculatorActivity" -> CalculatorActivity::class.java
                "ClockActivity" -> ClockActivity::class.java
                "SettingsActivity" -> SettingsActivity::class.java
                else -> null
            }
            if (activityClass != null) startActivityWp(Intent(this, activityClass))
        }
    }

    /** Vẽ icon ứng dụng đã "Thêm vào Điện thoại" ([DesktopAppsStore] - danh sách RIÊNG, độc lập
     *  hoàn toàn với "Ghim vào start" của trang Start, xem [DesktopAppsStore]) dạng TỰ DO
     *  kéo-thả trong [desktopArea]. Chưa thêm app nào -> hiện dòng chữ hướng dẫn thay vì để
     *  trống trơn khó hiểu. Nhấn giữ 1 icon đã có ở đây để gỡ khỏi trang này. */
    private fun layoutPinnedIcons() {
        desktopArea.removeAllViews()
        activeRemoveBadge = null  // removeAllViews() vừa xoá luôn dấu ✕ nếu đang hiện

        // ── Ô đồng hồ/ngày - coi NHƯ 1 ICON ỨNG DỤNG BÌNH THƯỜNG trong vùng tự do này (xem
        // class doc + CLOCK_ID) - CHỈ hiện ở đây nếu CHƯA bị kéo vào dock (xem
        // [DesktopDockStore], nếu đã ở dock thì rebuildDock() lo phần hiển thị, không hiện lại
        // ở đây nữa kẻo trùng 2 nơi cùng lúc). Vị trí mặc định = giữa màn hình theo chiều ngang,
        // ngay dưới thanh trạng thái (giữ đúng cảm giác widget giờ gốc), NGƯỜI DÙNG kéo đi đâu
        // thì lưu lại y hệt icon app qua [DesktopIconStore] (dùng chung, khoá bằng [CLOCK_ID]). ──
        if (!DesktopDockStore.contains(this, CLOCK_ID)) {
            (clockWidget.parent as? ViewGroup)?.removeView(clockWidget)
            desktopArea.addView(
                clockWidget,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            clockWidget.post {
                val areaWNow = desktopArea.width.toFloat()
                val areaHNow = desktopArea.height.toFloat()
                val cw = clockWidget.width.toFloat().takeIf { it > 0f } ?: dp(200).toFloat()
                val ch = clockWidget.height.toFloat().takeIf { it > 0f } ?: dp(80).toFloat()
                val saved = DesktopIconStore.getPosition(this, CLOCK_ID)
                val x = if (saved != null) saved.first * areaWNow else (areaWNow - cw) / 2f
                val y = if (saved != null) saved.second * areaHNow else (statusBarHeight() + dp(28)).toFloat()
                clockWidget.x = x.coerceIn(0f, (areaWNow - cw).coerceAtLeast(0f))
                clockWidget.y = y.coerceIn(0f, (areaHNow - ch).coerceAtLeast(0f))
            }
            attachDrag(clockWidget, CLOCK_ID,
                onTap = { openShortcut("clock") },
                onLongPress = { /* Đồng hồ không có "gỡ khỏi trang" - chỉ kéo vào dock được */ },
                onDroppedInDock = {
                    DesktopDockStore.add(this, CLOCK_ID)
                    layoutPinnedIcons()
                    rebuildDock()
                },
                minYPx = statusBarHeight() + dp(4)
            )
        } else {
            (clockWidget.parent as? ViewGroup)?.removeView(clockWidget)
        }

        val pm = packageManager
        // App đã "Thêm vào Điện thoại" NHƯNG đã bị kéo vào dock thì KHÔNG hiện lại ở vùng tự do
        // nữa (xem [DesktopDockStore]) - chỉ còn hiện trong dock (xem [rebuildDock]).
        val pinned = DesktopAppsStore.getAll(this).filter { !DesktopDockStore.contains(this, it) }
        if (pinned.isEmpty()) {
            if (showEmptyHint) {
                // Hiện "icon hướng dẫn" trông giống 1 ô app thật (tile màu + icon + nhãn)
                // để người dùng hiểu đây là vùng có thể có icon, không phải chỉ là thông báo
                val hintCell = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                }
                // Tile nền màu accent với icon dấu "+"
                val tileFrame = android.widget.FrameLayout(this).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(ThemePrefs.accent(this@DesktopActivity))
                    }
                }
                tileFrame.addView(android.widget.TextView(this).apply {
                    text = "+"
                    textSize = 32f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                }, android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                ))
                hintCell.addView(tileFrame, android.widget.LinearLayout.LayoutParams(dp(52), dp(52)))
                hintCell.addView(android.widget.TextView(this).apply {
                    text = "Thêm app"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), 0x99000000.toInt())
                    setPadding(0, dp(4), 0, 0)
                }, android.widget.LinearLayout.LayoutParams(dp(76), android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))
                // Tooltip khi chạm vào "icon hướng dẫn"
                hintCell.setOnClickListener {
                    android.widget.Toast.makeText(this,
                        "Giữ app trong \"DS Ứng Dụng\" → chọn \"Thêm vào Điện thoại\"",
                        android.widget.Toast.LENGTH_LONG).show()
                }
                desktopArea.addView(hintCell, android.widget.FrameLayout.LayoutParams(dp(76), dp(92)).also {
                    it.gravity = Gravity.CENTER
                    // Đặt ở vùng dưới đồng hồ
                    it.topMargin = statusBarHeight() + dp(150)
                    it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                })
            }
            return
        }

        val iconSizePx = dp(52)
        val cellW = dp(76)
        val cellH = dp(92)
        val areaW = desktopArea.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val cols = (areaW / cellW).coerceAtLeast(1)
        // Icon mặc định bắt đầu bên DƯỚI vùng đồng hồ để không che khuất đồng hồ
        // (statusBar + clockWidget ≈ 140dp). App đã kéo tự do vẫn giữ vị trí đã lưu.
        val clockReservedTop = statusBarHeight() + dp(140)

        pinned.forEachIndexed { index, pkgName ->
            val appInfo: ApplicationInfo = try {
                pm.getApplicationInfo(pkgName, 0)
            } catch (e: Exception) {
                return@forEachIndexed
            }
            val icon = try {
                pm.getApplicationIcon(appInfo)
            } catch (e: Exception) {
                null
            }
            val label = pm.getApplicationLabel(appInfo).toString()

            val iconView = ImageView(this).apply {
                if (icon != null) setImageDrawable(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val labelView = TextView(this).apply {
                text = label
                textSize = 11f
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), 0x99000000.toInt())
            }
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            cell.addView(iconView, LinearLayout.LayoutParams(iconSizePx, iconSizePx))
            cell.addView(labelView, LinearLayout.LayoutParams(cellW - dp(8), ViewGroup.LayoutParams.WRAP_CONTENT))

            desktopArea.addView(cell, FrameLayout.LayoutParams(cellW, cellH))

            // Vị trí đã lưu (phân số 0f..1f) hoặc vị trí lưới mặc định theo thứ tự nếu app này
            // CHƯA từng bị kéo tới đâu - giống cách Windows tự sắp icon mới theo lưới trước khi
            // người dùng tự kéo đi chỗ khác.
            val saved = DesktopIconStore.getPosition(this, pkgName)
            val defaultCol = index % cols
            val defaultRow = index / cols
            cell.post {
                val areaWNow = desktopArea.width.toFloat()
                val areaHNow = desktopArea.height.toFloat()
                val x = if (saved != null) saved.first * areaWNow else (defaultCol * cellW).toFloat()
                // Vị trí y mặc định bắt đầu bên dưới đồng hồ (clockReservedTop)
                // - App đã kéo (saved != null) giữ vị trí cũ đã lưu theo tỉ lệ màn hình
                val y = if (saved != null) saved.second * areaHNow
                        else (clockReservedTop + defaultRow * cellH).toFloat()
                cell.x = x.coerceIn(0f, (areaWNow - cellW).coerceAtLeast(0f))
                cell.y = y.coerceIn(0f, (areaHNow - cellH).coerceAtLeast(0f))
            }

            attachDrag(cell, pkgName,
                onTap = {
                    // Chạm (không kéo) -> mở app, giống hành vi chạm icon trên màn hình chính
                    // Android thật.
                    val launchIntent = pm.getLaunchIntentForPackage(pkgName)
                    if (launchIntent != null) startActivity(launchIntent)
                },
                onLongPress = { showRemoveBadge(cell, pkgName) },
                onDroppedInDock = {
                    DesktopDockStore.add(this, pkgName)
                    layoutPinnedIcons()
                    rebuildDock()
                }
            )
        }
    }

    /** Kéo-thả tự do 1 icon (app HOẶC ô đồng hồ [CLOCK_ID]) trong [desktopArea]: nhấn giữ + di
     *  chuyển để đổi vị trí (lưu lại qua [DesktopIconStore], khoá bằng [itemId]); chạm ngắn
     *  không di chuyển (dưới ngưỡng [tapSlop]) -> coi là TAP, gọi [onTap]; giữ nguyên tại chỗ đủ
     *  lâu (không di chuyển) -> coi là LONG PRESS, gọi [onLongPress]; kéo rồi THẢ TAY trong lúc
     *  ngón tay đang nằm trong dải DOCK cạnh phải (toạ độ MÀN HÌNH THẬT >= mép trái dock, xem
     *  [dockZoneStartPx] - PHẢI dùng toạ độ màn hình thật [MotionEvent.getRawX], không phải toạ
     *  độ cục bộ trong [desktopArea], vì [desktopArea] đã CHỪA HẲN dải dock ra khỏi bề ngang của
     *  nó nên không bao giờ tự nhận được toạ độ cục bộ nằm trong dock) -> gọi [onDroppedInDock]
     *  THAY VÌ lưu vị trí tự do như bình thường - đúng ý "kéo vào thanh bên phải". Cỡ icon đọc
     *  TRỰC TIẾP từ [View.getWidth]/[View.getHeight] của [view] tại thời điểm kéo (không nhận
     *  tham số cỡ cố định) để dùng chung được cho CẢ icon app (cỡ cố định) LẪN ô đồng hồ (cỡ tự
     *  co theo nội dung chữ, [ViewGroup.LayoutParams.WRAP_CONTENT]). [minYPx] = giới hạn TRÊN
     *  không cho kéo lên quá (mặc định chừa chỗ đồng hồ cho icon app; ô đồng hồ tự truyền giá trị
     *  thấp hơn hẳn vì chính nó không cần né chính nó). */
    private fun attachDrag(
        view: View, itemId: String, onTap: () -> Unit, onLongPress: () -> Unit,
        onDroppedInDock: () -> Unit,
        minYPx: Int = statusBarHeight() + dp(140)
    ) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var longPressFired = false
        val tapSlop = dp(10)
        val dockZoneStartPx = resources.displayMetrics.widthPixels - dp(72)
        val longPressRunnable = Runnable {
            longPressFired = true
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPress()
        }
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = v.x
                    startY = v.y
                    longPressFired = false
                    v.postDelayed(longPressRunnable, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) + abs(dy) > tapSlop) v.removeCallbacks(longPressRunnable)
                    if (!longPressFired) {
                        val cellW = v.width
                        val cellH = v.height
                        val maxX = (desktopArea.width - cellW).coerceAtLeast(0).toFloat()
                        val maxY = (desktopArea.height - cellH).coerceAtLeast(0).toFloat()
                        v.x = (startX + dx).coerceIn(0f, maxX)
                        v.y = (startY + dy).coerceIn(minYPx.toFloat().coerceAtMost(maxY), maxY)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPressRunnable)
                    if (longPressFired) return@setOnTouchListener true
                    val moved = abs(event.rawX - downRawX) + abs(event.rawY - downRawY)
                    when {
                        moved < tapSlop -> onTap()
                        event.rawX >= dockZoneStartPx -> onDroppedInDock()
                        else -> {
                            val areaW = desktopArea.width.toFloat()
                            val areaH = desktopArea.height.toFloat()
                            if (areaW > 0 && areaH > 0) {
                                DesktopIconStore.setPosition(this, itemId, v.x / areaW, v.y / areaH)
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longPressRunnable)
                    true
                }
                else -> false
            }
        }
    }

    /** Menu phẳng kiểu WP (giống [HomeScreenManager.showPinContextMenu]) hiện khi nhấn giữ 1
     *  icon NGAY TRÊN trang Điện thoại - chỉ 1 lựa chọn "Bỏ khỏi Điện thoại" (không có "Ghim
     *  vào start"/"Đánh dấu sao" ở đây, vì đó là hành động của trang Start, giữ 2 trang tách
     *  biệt rạch ròi đúng yêu cầu). */
    /** Dấu "✕" nhỏ hiện ở góc trên-trái 1 icon trên trang Điện thoại khi NHẤN GIỮ vào nó - chạm
     *  vào dấu này để loại bỏ app đó khỏi trang (thay cho menu chữ "Bỏ khỏi Điện thoại" cũ, đồng
     *  bộ với cách làm mới ở [HomeScreenManager.addRemoveBadge] cho trang Start).
     *
     *  [cell] nằm trong [desktopArea] (FrameLayout) với vị trí TỰ DO qua [View.x]/[View.y] (xem
     *  [attachDrag]) chứ KHÔNG dùng layout gravity cố định như tile lưới ở Start - nên dấu ✕
     *  được thêm làm SIBLING của [cell] (con trực tiếp của [desktopArea]), đặt x/y NGAY TẠI vị
     *  trí góc trên-trái hiện tại của [cell] (đọc trực tiếp cell.x/cell.y - lúc hàm này chạy,
     *  nhấn giữ nghĩa là icon đã hiện ra và có toạ độ thật, không cần chờ post{} như lúc dựng
     *  ban đầu), thay vì thêm làm con của [cell] (LinearLayout, không hỗ trợ định vị tự do). */
    private fun showRemoveBadge(cell: View, pkgName: String) {
        activeRemoveBadge?.let { desktopArea.removeView(it) }
        val badge = TextView(this).apply {
            text = "✕"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xFFE81123.toInt()) // đỏ - màu "xoá/cảnh báo" chuẩn của Windows
            }
            isClickable = true
            isFocusable = true
        }
        val size = dp(22)
        desktopArea.addView(badge, FrameLayout.LayoutParams(size, size))
        badge.x = cell.x + dp(2)
        badge.y = cell.y + dp(2)
        activeRemoveBadge = badge
        badge.setOnClickListener {
            desktopArea.removeView(badge)
            activeRemoveBadge = null
            DesktopAppsStore.remove(this@DesktopActivity, pkgName)
            desktopArea.post { layoutPinnedIcons() }
        }
    }
}
