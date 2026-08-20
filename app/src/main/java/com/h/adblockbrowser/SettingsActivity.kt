package com.h.adblockbrowser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * THAY ĐỔI SO VỚI BẢN CŨ (để giống Win10 Mobile Settings thật hơn):
 *
 * 1. TIÊU ĐỀ TRANG ĐÚNG WP: "cài đặt" dùng font "sans-serif-light" cỡ 42sp (không phải
 *    30sp như bản cũ). Settings trên WP thật dùng chữ rất to, nhẹ, nổi bật phía trên.
 *
 * 2. MỖI MỤC CÀI ĐẶT có SUBTITLE màu xám nhạt bên dưới (như Settings WP thật hiện thông
 *    tin trạng thái bên dưới tên mục - ví dụ "Đã bật" / "Đã tắt", tên theme hiện tại...).
 *    Bản cũ chỉ có 1 dòng chữ, thiếu hẳn subtitle đặc trưng này.
 *
 * 3. PADDING TRANG: padding trái/phải tăng từ 20dp lên 24dp; padding trên từ 40dp lên 52dp
 *    cho đúng khoảng thở của màn hình Settings WP thật.
 *
 * 4. DIVIDER DƯỚI MỤC: đường kẻ ngăn cách các mục có độ mờ 0x22FFFFFF (mờ hơn so với
 *    0x33FFFFFF của bản cũ) - nhạt hơn 1 chút, đúng WP thật (đường kẻ rất nhẹ, gần như
 *    vô hình).
 *
 * 5. GROUP HEADER: tiêu đề nhóm tăng topMargin từ 20dp lên 32dp để khoảng cách giữa các
 *    nhóm rộng hơn, đúng WP Settings thật (nhóm mục cách nhau rõ ràng hơn).
 *
 * 6. ACCENT PICKER: ô màu tăng từ 48dp lên 52dp; bỏ margin 4dp đổi thành 3dp để ô sát
 *    nhau hơn, đúng lưới màu Metro compact thật.
 *
 * 7. PADDING MỤC CÀI ĐẶT: padding dọc tăng từ 14dp lên 16dp mỗi chiều.
 *
 * 8. BACK BUTTON NAV BAR: thêm WpNavBar vào màn Settings để điều hướng đúng kiểu WP
 *    (thay vì chỉ dựa vào finish() gọi qua onBackPressed).
 */
class SettingsActivity : AppCompatActivity() {

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.wp_slide_in_left, R.anim.wp_slide_out_right)
    }

    private lateinit var root: LinearLayout
    private lateinit var accentSwatches: MutableList<View>
    private var navBarHandle: WpNavBar.Handle? = null

    private val pickWallpaper = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { }
            WallpaperPrefs.set(this, uri.toString())
            Toast.makeText(this, "Đã đổi hình nền", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root frame để chứa cả scroll content lẫn NavBar nổi
        val rootFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // padding dưới thêm để nội dung không bị NavBar (54dp) + AppBar (64dp) che mất
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setBackgroundColor(Color.BLACK)
            // padding trái/phải 24dp, trên 52dp, dưới đủ chỗ cho NavBar + AppBar
            setPadding(dp(24), dp(52), dp(24), dp(54 + 64 + 16))
        }
        scroll.addView(root)
        rootFrame.addView(scroll)
        setContentView(rootFrame)

        // ── Tiêu đề trang: "cài đặt" - chữ to, mảnh, đúng WP Settings ──
        root.addView(TextView(this).apply {
            text = "cài đặt"
            textSize = 42f  // to hơn (30f → 42f), đúng WP thật
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, 0, 0, dp(28)) // tăng bottom padding
        })

        // ═══ NHÓM: GIAO DIỆN ═══
        root.addView(groupHeader("giao diện"))
        root.addView(menuRow("Màu nhấn", "Chọn màu accent cho toàn ứng dụng") { /* scroll to accent picker */ })
        root.addView(accentPicker())
        root.addView(menuRow("Hình nền", "Đổi ảnh nền trang chủ") { pickWallpaper.launch("image/*") })

        // ═══ NHÓM: TRÌNH DUYỆT ═══
        root.addView(groupHeader("trình duyệt"))
        root.addView(menuRow("Xoá dữ liệu duyệt web", "Cookie, cache và lịch sử trang web") {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            Toast.makeText(this, "Đã xoá dữ liệu duyệt web", Toast.LENGTH_SHORT).show()
        })

        // ═══ NHÓM: BẢO MẬT ═══
        root.addView(groupHeader("bảo mật"))
        root.addView(menuRow("Khoá ứng dụng", "PIN hoặc hình mở khoá khi khởi động") {
            startActivityWp(Intent(this, AppLockSetupActivity::class.java))
        })

        // ── WpNavBar ở cuối để nổi trên màn Settings ──
        navBarHandle = WpNavBar.attach(
            activity = this,
            root = rootFrame,
            onBack = { onBackPressed() },
            onStart = { finish() }
        )
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        navBarHandle?.resync()
    }

    override fun onDestroy() {
        navBarHandle?.detach()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /**
     * Tiêu đề nhóm kiểu WP Settings: chữ thường, màu xám, cỡ nhỏ, khoảng cách rộng phía trên.
     * topMargin tăng từ 20dp lên 32dp để các nhóm tách rõ hơn.
     */
    private fun groupHeader(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(0xFF9A9A9A.toInt())
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(32); lp.bottomMargin = dp(6) // topMargin 32dp (tăng từ 20dp)
        layoutParams = lp
    }

    /**
     * 1 dòng cài đặt kiểu WP Settings thật:
     * - Dòng 1: tên mục (chữ trắng, 18sp, light)
     * - Dòng 2: subtitle mô tả ngắn (chữ xám nhạt, 13sp) - MỚI, bản cũ không có
     * - Đường kẻ mờ phía dưới (mờ hơn bản cũ: 0x22 thay vì 0x33)
     */
    private fun menuRow(label: String, subtitle: String? = null, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            isFocusable = true
        }
        val textMain = TextView(this).apply {
            text = label
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, dp(16), 0, if (subtitle != null) dp(2) else dp(16)) // padding dọc 16dp
        }
        row.addView(textMain)

        // Subtitle (MỚI) - mô tả trạng thái/chức năng ngắn gọn, đúng kiểu WP Settings thật
        if (subtitle != null) {
            row.addView(TextView(this).apply {
                text = subtitle
                textSize = 13f
                setTextColor(0xFF9A9A9A.toInt())
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                setPadding(0, 0, 0, dp(14))
            })
        }

        // Đường kẻ mờ hơn (0x22 thay vì 0x33) - đúng WP thật (rất nhẹ, gần như vô hình)
        val divider = View(this).apply {
            setBackgroundColor(0x22FFFFFF)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        }
        row.addView(divider)
        row.setOnClickListener { onClick() }
        return row
    }

    /**
     * Lưới chọn màu NHẤN kiểu WP: ô vuông phẳng, không bo góc.
     * Kích thước ô 52dp (tăng từ 48dp), margin 3dp (giảm từ 4dp) để sát nhau hơn.
     */
    private fun accentPicker(): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(20)
            layoutParams = lp
        }

        val currentAccent = ThemePrefs.accent(this)
        val cols = 5
        val grid = GridLayout(this).apply {
            columnCount = cols
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        accentSwatches = mutableListOf()

        ThemePrefs.PALETTE.forEachIndexed { _, color ->
            val swatch = buildSwatch(color, isSelected = color == currentAccent) { view ->
                ThemePrefs.setAccent(this, color)
                accentSwatches.forEach { s -> (s as FrameLayout).isSelected = false; refreshSwatchBorder(s) }
                (view as FrameLayout).isSelected = true
                refreshSwatchBorder(view)
                Toast.makeText(this, "Đã đổi màu nhấn", Toast.LENGTH_SHORT).show()
            }
            accentSwatches.add(swatch)
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            )
            lp.width = dp(52)  // tăng từ 48dp
            lp.height = dp(52) // tăng từ 48dp
            lp.setMargins(dp(3), dp(3), dp(3), dp(3)) // giảm từ 4dp → 3dp, ô sát nhau hơn
            grid.addView(swatch, lp)
        }
        wrap.addView(grid)
        return wrap
    }

    private fun buildSwatch(color: Int, isSelected: Boolean, onPick: (View) -> Unit): View {
        val swatch = FrameLayout(this).apply {
            this.isSelected = isSelected
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply { setColor(color) }
        }
        val innerBorder = View(this).apply {
            visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
            background = GradientDrawable().apply {
                setStroke(dp(2), Color.WHITE)
                setColor(Color.TRANSPARENT)
            }
        }
        swatch.addView(innerBorder, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) })
        swatch.setOnClickListener { onPick(swatch) }
        return swatch
    }

    private fun refreshSwatchBorder(swatch: FrameLayout) {
        val border = swatch.getChildAt(0)
        border.visibility = if (swatch.isSelected) View.VISIBLE else View.INVISIBLE
    }
}
