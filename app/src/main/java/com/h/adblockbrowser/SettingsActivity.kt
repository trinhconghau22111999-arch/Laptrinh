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

/** Cài đặt chung của app - mở từ nút bánh răng cố định ở góc trên-phải trang chủ (xem
 *  [HomeScreenManager]). Xếp thành TỪNG NHÓM có tiêu đề nhỏ màu xám kiểu WP thật (Settings app
 *  trên Windows Phone/Windows 10 Mobile luôn nhóm các mục theo chủ đề: "system", "apps"... chứ
 *  không liệt kê phẳng 1 danh sách dài không phân nhóm như bản trước). */
class SettingsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var accentSwatches: MutableList<View>

    private val pickWallpaper = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { /* 1 số nguồn ảnh không hỗ trợ quyền lâu dài, vẫn dùng tạm được */ }
            WallpaperPrefs.set(this, uri.toString())
            Toast.makeText(this, "Đã đổi hình nền", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        // Danh sách cài đặt kiểu Windows Phone: căn trái, không nền, không nút bo góc kiểu
        // pill như bản cũ - chỉ có tiêu đề lớn mảnh phía trên + các dòng phẳng cách nhau bằng
        // khoảng trắng, đúng phong cách màn "Settings" của WP/Windows 10 Mobile.
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setBackgroundColor(Color.BLACK)
            setPadding(dp(20), dp(40), dp(20), dp(48))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "cài đặt"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, 0, 0, dp(24))
        })

        // ═══════════════════ NHÓM: GIAO DIỆN (cá nhân hoá) ═══════════════════
        root.addView(groupHeader("giao diện"))
        root.addView(accentPicker())
        root.addView(menuRow("Đổi hình nền") { pickWallpaper.launch("image/*") })

        // ═══════════════════ NHÓM: TRÌNH DUYỆT ═══════════════════
        root.addView(groupHeader("trình duyệt"))
        root.addView(menuRow("Xoá dữ liệu duyệt web (cookie/cache)") {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            Toast.makeText(this, "Đã xoá dữ liệu duyệt web", Toast.LENGTH_SHORT).show()
        })

        // ═══════════════════ NHÓM: BẢO MẬT ═══════════════════
        root.addView(groupHeader("bảo mật"))
        root.addView(menuRow("Quản lý khoá ứng dụng") {
            startActivity(Intent(this, AppLockSetupActivity::class.java))
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Tiêu đề nhỏ phân nhóm kiểu WP Settings thật: chữ THƯỜNG (không viết hoa), màu xám nhạt,
     *  cỡ nhỏ, có khoảng cách rộng phía trên để tách hẳn khỏi nhóm mục kế trước. */
    private fun groupHeader(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(0xFF9A9A9A.toInt())
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(20); lp.bottomMargin = dp(4)
        layoutParams = lp
    }

    /** 1 dòng cài đặt phẳng kiểu WP: chỉ chữ trắng căn trái + gạch chân mảnh màu accent phía
     *  dưới khi chạm - KHÔNG có khối nền xám bo góc như nút Material mặc định trước đây. */
    private fun menuRow(label: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
            isClickable = true
            isFocusable = true
        }
        val text = TextView(this).apply {
            text = label
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, dp(14), 0, dp(14))
        }
        val divider = View(this).apply {
            setBackgroundColor(0x33FFFFFF)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        }
        row.addView(text)
        row.addView(divider)
        row.setOnClickListener { onClick() }
        return row
    }

    /** Lưới chọn MÀU NHẤN kiểu đúng màn "background+accent color" thật của Windows Phone: các ô
     *  vuông phẳng (không bo góc), mỗi ô 1 màu trong [ThemePrefs.PALETTE] 20 màu gốc - ô đang
     *  được chọn có 1 khung viền trắng lồng bên trong để phân biệt, chạm ô khác là đổi ngay lập
     *  tức + lưu lại (không cần nút "Lưu" riêng, đúng kiểu Settings tức thời của WP). */
    private fun accentPicker(): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(20)
            layoutParams = lp
        }
        wrap.addView(TextView(this).apply {
            text = "Màu nhấn"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, dp(10), 0, dp(10))
        })

        val currentAccent = ThemePrefs.accent(this)
        val cols = 5
        val grid = GridLayout(this).apply {
            columnCount = cols
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        accentSwatches = mutableListOf()

        ThemePrefs.PALETTE.forEachIndexed { index, color ->
            val swatch = buildSwatch(color, isSelected = color == currentAccent) { view ->
                ThemePrefs.setAccent(this, color)
                // Cập nhật NGAY khung viền chọn trên toàn lưới (không cần vẽ lại cả màn hình) -
                // đúng cảm giác "áp dụng ngay" của WP; các màn hình khác sẽ lên màu mới ngay từ
                // lần MỞ TIẾP THEO (đọc lại ThemePrefs.accent() lúc onCreate của màn đó).
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
            lp.width = dp(48); lp.height = dp(48)
            lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            grid.addView(swatch, lp)
        }
        wrap.addView(grid)
        return wrap
    }

    /** 1 ô màu vuông phẳng - có viền trắng mỏng lồng bên trong khi [isSelected], không viền khi
     *  chưa chọn (giữ đúng bố cục "vuông sát nhau" của Metro, không bo góc/không đổ bóng). */
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
        swatch.addView(innerBorder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).also {
            it.setMargins(dp(4), dp(4), dp(4), dp(4))
        })
        swatch.setOnClickListener { onPick(swatch) }
        return swatch
    }

    private fun refreshSwatchBorder(swatch: FrameLayout) {
        val border = swatch.getChildAt(0)
        border.visibility = if (swatch.isSelected) View.VISIBLE else View.INVISIBLE
    }
}
