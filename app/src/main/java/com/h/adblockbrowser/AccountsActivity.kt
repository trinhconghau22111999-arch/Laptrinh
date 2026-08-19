package com.h.adblockbrowser

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Màn hình "Nhiều tài khoản" - danh sách các hồ sơ trình duyệt (giống màn chọn hồ sơ của
 *  Chrome/Android: mỗi hồ sơ = 1 vòng tròn avatar + tên). Bấm vào 1 hồ sơ -> mở trình duyệt
 *  RIÊNG cho hồ sơ đó (dữ liệu/cookie tách biệt hoàn toàn - xem AccountBrowserActivity.kt).
 *  Bấm "+" để thêm hồ sơ mới (tối đa AccountProfileStore.MAX_PROFILES). Giữ (long-press) vào
 *  1 hồ sơ để đổi tên hoặc xoá hồ sơ khỏi danh sách. */
class AccountsActivity : AppCompatActivity() {

    private lateinit var grid: GridLayout
    private var floatingBackButtonHandle: FloatingBackButton.Handle? = null

    private val colors = intArrayOf(
        0xFF29B6F6.toInt(), 0xFFAB47BC.toInt(), 0xFF66BB6A.toInt(),
        0xFFFFA726.toInt(), 0xFFEF5350.toInt(), 0xFF26A69A.toInt(),
        0xFF7E57C2.toInt(), 0xFFEC407A.toInt(), 0xFF8D6E63.toInt(),
        0xFF5C6BC0.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themeColor(R.color.bg_primary))
            setPadding(dp(20), dp(48), dp(20), dp(20))
        }

        root.addView(TextView(this).apply {
            text = "Nhiều tài khoản Google"
            textSize = 20f
            setTextColor(themeColor(R.color.text_primary))
        })
        root.addView(TextView(this).apply {
            text = "Mỗi hồ sơ có cookie & phiên đăng nhập riêng, hoàn toàn tách biệt - có thể\nđăng nhập nhiều tài khoản Google khác nhau cùng lúc."
            textSize = 12f
            setTextColor(themeColor(R.color.text_secondary))
            setPadding(0, dp(6), 0, dp(20))
        })

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        grid = GridLayout(this).apply {
            columnCount = 3
        }
        scroll.addView(grid)
        root.addView(scroll)

        val outer = FrameLayout(this)
        outer.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(outer)
        // FIX khoảng đen dư ở trên/dưới màn hình - xem giải thích chi tiết trong MainActivity.kt.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(outer) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }
        addFloatingBackButton(outer)
        render()
    }

    // ---------- Nút Back nổi (dùng chung FloatingBackButton.kt - đồng bộ với MainActivity /
    // AccountBrowserActivity / IncognitoActivity, thay vì tự dựng widget riêng như trước) ----------
    // Màn hình này cũng ẩn thanh điều hướng hệ thống (immersive) nên cần nút back riêng trong
    // app. Đây là màn "gốc" (không có trang con để lùi), nên bấm nhanh = thoát màn này về lại
    // nơi đã mở nó (MainActivity). Không cần hành động long-press riêng ở màn này.
    private fun addFloatingBackButton(root: FrameLayout) {
        // fixed = true: nút Back CỐ ĐỊNH ở góc DƯỚI-PHẢI, không kéo-thả được nữa và không đổi
        // vị trí dù xoay ngang/dọc màn hình (xem chi tiết ở FloatingBackButton.attach).
        floatingBackButtonHandle = FloatingBackButton.attach(
            activity = this,
            root = root,
            onTap = { onBackPressed() },
            defaultIsRight = true,
            fixed = true
        )
    }

    override fun onResume() {
        super.onResume()
        render()
        // Đọc lại vị trí nút Back nổi mới nhất - xem giải thích đồng bộ ở FloatingBackButton.kt.
        floatingBackButtonHandle?.resync()
    }

    override fun onDestroy() {
        floatingBackButtonHandle?.detach()
        super.onDestroy()
    }

    private fun render() {
        grid.removeAllViews()
        val profiles = AccountProfileStore.load(this)
        for (profile in profiles) {
            grid.addView(buildProfileCell(profile))
        }
        if (profiles.size < AccountProfileStore.MAX_PROFILES) {
            grid.addView(buildAddCell())
        }
    }

    private fun buildProfileCell(profile: AccountProfileStore.Profile): android.view.View {
        val color = colors[(profile.slot - 1) % colors.size]
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(14), dp(10), dp(14))
            val lp = GridLayout.LayoutParams()
            lp.width = dp(96)
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT
            layoutParams = lp
            isClickable = true
            setOnClickListener { openProfile(profile) }
            setOnLongClickListener { showProfileOptions(profile); true }
        }
        cell.addView(TextView(this).apply {
            text = profile.name.trim().take(1).uppercase().ifBlank { "?" }
            textSize = 24f
            setTextColor(themeColor(R.color.text_primary))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(dp(2), themeColor(R.color.text_primary))
            }
        })
        cell.addView(TextView(this).apply {
            text = profile.name
            textSize = 12f
            setTextColor(themeColor(R.color.text_primary))
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, dp(8), 0, 0)
        })
        return cell
    }

    private fun buildAddCell(): android.view.View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(14), dp(10), dp(14))
            val lp = GridLayout.LayoutParams()
            lp.width = dp(96)
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT
            layoutParams = lp
            isClickable = true
            setOnClickListener { showAddDialog() }
        }
        cell.addView(TextView(this).apply {
            text = "+"
            textSize = 28f
            setTextColor(themeColor(R.color.text_primary))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF1E1E1E.toInt())
                setStroke(dp(1), 0xFF444444.toInt())
            }
        })
        cell.addView(TextView(this).apply {
            text = "Thêm tài khoản"
            textSize = 12f
            setTextColor(themeColor(R.color.text_primary))
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, dp(8), 0, 0)
        })
        return cell
    }

    private fun showAddDialog() {
        val input = EditText(this).apply {
            hint = "Ví dụ: Cá nhân, Công việc..."
            setTextColor(themeColor(R.color.text_primary))
            setHintTextColor(themeColor(R.color.text_secondary))
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(20), dp(10), dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Thêm tài khoản mới")
            .setView(container)
            .setPositiveButton("Thêm") { _, _ ->
                val name = input.text.toString().trim()
                val profile = AccountProfileStore.add(this, name)
                if (profile == null) {
                    Toast.makeText(this, "Đã đạt tối đa ${AccountProfileStore.MAX_PROFILES} tài khoản", Toast.LENGTH_SHORT).show()
                } else {
                    render()
                    openProfile(profile)
                }
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun showProfileOptions(profile: AccountProfileStore.Profile) {
        AlertDialog.Builder(this)
            .setTitle(profile.name)
            .setItems(arrayOf("Đổi tên", "Xoá hồ sơ", "Huỷ")) { _, which ->
                when (which) {
                    0 -> showRenameDialog(profile)
                    1 -> showDeleteConfirm(profile)
                }
            }
            .show()
    }

    private fun showRenameDialog(profile: AccountProfileStore.Profile) {
        val input = EditText(this).apply {
            setText(profile.name)
            setTextColor(themeColor(R.color.text_primary))
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(20), dp(10), dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Đổi tên hồ sơ")
            .setView(container)
            .setPositiveButton("Lưu") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    AccountProfileStore.rename(this, profile.slot, newName)
                    render()
                }
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun showDeleteConfirm(profile: AccountProfileStore.Profile) {
        AlertDialog.Builder(this)
            .setTitle("Xoá hồ sơ \"${profile.name}\"?")
            .setMessage("Danh sách tab đã lưu của hồ sơ này sẽ bị xoá khỏi app.")
            .setPositiveButton("Xoá") { _, _ ->
                AccountProfileStore.remove(this, profile.slot)
                render()
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun openProfile(profile: AccountProfileStore.Profile) {
        val activityClass = classForSlot(profile.slot) ?: return
        startActivity(Intent(this, activityClass))
    }

    private fun classForSlot(slot: Int): Class<*>? = when (slot) {
        1 -> AccountBrowserActivity1::class.java
        2 -> AccountBrowserActivity2::class.java
        3 -> AccountBrowserActivity3::class.java
        4 -> AccountBrowserActivity4::class.java
        5 -> AccountBrowserActivity5::class.java
        6 -> AccountBrowserActivity6::class.java
        7 -> AccountBrowserActivity7::class.java
        8 -> AccountBrowserActivity8::class.java
        9 -> AccountBrowserActivity9::class.java
        10 -> AccountBrowserActivity10::class.java
        else -> null
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

}
