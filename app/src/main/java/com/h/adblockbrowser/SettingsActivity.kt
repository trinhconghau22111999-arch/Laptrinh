package com.h.adblockbrowser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** Cài đặt chung của app - mở từ icon bánh răng nhỏ ở góc trên-trái trang chủ. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout

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
        // Danh sách cài đặt kiểu Windows Phone: căn trái, không nền, không nút bo góc kiểu
        // pill như bản cũ - chỉ có tiêu đề lớn mảnh phía trên + các dòng phẳng cách nhau bằng
        // khoảng trắng, đúng phong cách màn "Settings" của WP/Windows 10 Mobile.
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setBackgroundColor(Color.BLACK)
            setPadding(dp(20), dp(40), dp(20), dp(32))
        }
        setContentView(root)

        root.addView(TextView(this).apply {
            text = "cài đặt"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, 0, 0, dp(20))
        })

        root.addView(menuRow("Đổi hình nền") { pickWallpaper.launch("image/*") })
        root.addView(menuRow("Quản lý khoá ứng dụng") {
            startActivity(Intent(this, AppLockSetupActivity::class.java))
        })
        root.addView(menuRow("Xoá dữ liệu duyệt web (cookie/cache)") {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            Toast.makeText(this, "Đã xoá dữ liệu duyệt web", Toast.LENGTH_SHORT).show()
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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
}
