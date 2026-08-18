package com.h.adblockbrowser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Button
import android.widget.LinearLayout
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
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF000000.toInt())
            setPadding(dp(32), dp(40), dp(32), dp(32))
        }
        setContentView(root)

        root.addView(menuButton("Đổi hình nền") { pickWallpaper.launch("image/*") })
        root.addView(menuButton("Quản lý khoá ứng dụng") {
            startActivity(Intent(this, AppLockSetupActivity::class.java))
        })
        root.addView(menuButton("Xoá dữ liệu duyệt web (cookie/cache)") {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            Toast.makeText(this, "Đã xoá dữ liệu duyệt web", Toast.LENGTH_SHORT).show()
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun menuButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0xFF1A1A1A.toInt())
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8)
        layoutParams = lp
        setOnClickListener { onClick() }
    }
}
