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

/** Màn hình "Nhiều tài khoản" - danh sách các hồ sơ trình duyệt (giống màn chọn hồ sơ của
 *  Chrome/Android: mỗi hồ sơ = 1 vòng tròn avatar + tên). Bấm vào 1 hồ sơ -> mở trình duyệt
 *  RIÊNG cho hồ sơ đó (dữ liệu/cookie tách biệt hoàn toàn - xem AccountBrowserActivity.kt).
 *  Bấm "+" để thêm hồ sơ mới (tối đa AccountProfileStore.MAX_PROFILES). Giữ (long-press) vào
 *  1 hồ sơ để đổi tên hoặc xoá hồ sơ khỏi danh sách. */
class AccountsActivity : AppCompatActivity() {

    /** Thoát màn này kèm hiệu ứng "trượt ra bên phải" kiểu Windows Phone (xem [finishWp] ở
     *  UiUtils.kt), dù finish() được gọi từ đâu (nút Back nổi, mũi tên ◀, phím Back cứng...). */
    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.wp_slide_in_left, R.anim.wp_slide_out_right)
    }


    private lateinit var grid: GridLayout
    private var floatingBackButtonHandle: WpNavBar.Handle? = null

    // Dùng đúng bảng 20 màu Live Tile gốc của Windows Phone (ThemePrefs.PALETTE) thay vì bảng
    // màu Material tự bịa riêng cho màn này - trước đây màn "Nhiều tài khoản" là màn hình DUY
    // NHẤT trong app có bảng màu lệch tông (xanh dương/tím/xanh lá kiểu Google) so với mọi màn
    // hình khác đều dùng chung 1 bảng màu Metro.
    private val colors = ThemePrefs.PALETTE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        // Ẩn CẢ status bar LẪN thanh điều hướng hệ thống - xem giải thích đầy đủ ở
        // UiUtils.hideStatusBar()/MainActivity.enableImmersiveMode(). Màn này không có nhiều
        // tab nên không có nút "Đa nhiệm" riêng (chỉ Back/Start) - xem WpNavBar.attach() bên dưới.
        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A0A0A.toInt())
            setPadding(dp(20), dp(48), dp(20), dp(20))
        }

        root.addView(TextView(this).apply {
            text = "Nhiều tài khoản"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        })
        root.addView(TextView(this).apply {
            text = "Mỗi hồ sơ là 1 phiên đăng nhập riêng, hoàn toàn tách biệt (cookie & dữ liệu\nkhông chung nhau). Bạn có thể tạo nhiều hồ sơ để đăng nhập nhiều tài\nkhoản khác nhau cùng lúc - mỗi tài khoản tương ứng với 1 hồ sơ riêng."
            textSize = 12f
            setTextColor(0xFF999999.toInt())
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
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

    // ---------- Thanh điều hướng 2 nút kiểu Windows Phone thật: ◁ Back / ⊞ Start (dùng chung
    // WpNavBar.kt - đồng bộ với MainActivity / AccountBrowserActivity / IncognitoActivity, thay
    // vì tự dựng widget riêng như trước) ----------
    // Màn hình này không có ô địa chỉ nào để "tìm kiếm" nên KHÔNG truyền onSearch - WpNavBar tự
    // chỉ vẽ 2 nút Back/Start khi thiếu tham số đó (xem WpNavBar.attach). Đây là màn "gốc"
    // (không có trang con để lùi), nên cả Back lẫn Start đều thoát về lại nơi đã mở màn này.
    private fun addFloatingBackButton(root: FrameLayout) {
        floatingBackButtonHandle = WpNavBar.attach(
            activity = this,
            root = root,
            onBack = { onBackPressed() },
            onStart = { onBackPressed() }
        )
    }

    override fun onResume() {
        super.onResume()
        render()
        // Đọc lại vị trí nút Back nổi mới nhất - xem giải thích đồng bộ ở FloatingBackButton.kt.
        floatingBackButtonHandle?.resync()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Áp lại ẩn thanh trạng thái/điều hướng mỗi lần cửa sổ lấy lại focus (chuyển app đi rồi
        // quay lại, đóng dialog hệ thống...) - xem giải thích chi tiết ở
        // MainActivity.onWindowFocusChanged(). Màn này dùng AlertDialog hệ thống (cửa sổ riêng)
        // cho các thao tác Thêm/Đổi tên nên không cần kiểm tra IME như MainActivity.
        if (hasFocus) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
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

    // Mỗi hồ sơ giờ là 1 "Live Tile" VUÔNG PHẲNG (không viền, không bo góc) đúng ngôn ngữ hình
    // khối của toàn app - trước đây avatar hình TRÒN có viền trắng là chi tiết duy nhất trong
    // cả app phá vỡ hoàn toàn phong cách Metro (Metro không có hình tròn/bo góc/viền nổi).
    private fun buildProfileCell(profile: AccountProfileStore.Profile): android.view.View {
        val color = colors[(profile.slot - 1) % colors.size]
        val cell = FrameLayout(this).apply {
            val lp = GridLayout.LayoutParams()
            lp.width = dp(96)
            lp.height = dp(96)
            lp.setMargins(dp(2), dp(2), dp(2), dp(2))
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(color)
            }
            isClickable = true
            setOnClickListener { openProfile(profile) }
            setOnLongClickListener { showProfileOptions(profile); true }
        }
        cell.addView(TextView(this).apply {
            text = profile.name.trim().take(1).uppercase().ifBlank { "?" }
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.TOP or Gravity.START; it.leftMargin = dp(8); it.topMargin = dp(4) }
        })
        cell.addView(TextView(this).apply {
            text = profile.name
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            maxLines = 2
            android.text.TextUtils.TruncateAt.END.let { ellipsize = it }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.BOTTOM or Gravity.START; it.leftMargin = dp(8); it.bottomMargin = dp(6); it.rightMargin = dp(8) }
        })
        return cell
    }

    private fun buildAddCell(): android.view.View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val lp = GridLayout.LayoutParams()
            lp.width = dp(96)
            lp.height = dp(96)
            lp.setMargins(dp(2), dp(2), dp(2), dp(2))
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), 0xFF444444.toInt())
            }
            isClickable = true
            setOnClickListener { showAddDialog() }
        }
        cell.addView(TextView(this).apply {
            text = "+"
            textSize = 30f
            setTextColor(0xFFCCCCCC.toInt())
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        })
        return cell
    }

    private fun showAddDialog() {
        val input = EditText(this).apply {
            hint = "Ví dụ: Cá nhân, Công việc..."
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF888888.toInt())
            // Ép bàn phím hiện nút "Xong" (Done) ở góc dưới-phải THAY VÌ nút xuống dòng "↵" mặc
            // định - trước đây EditText không khai inputType/imeOptions nên Android coi là ô
            // NHIỀU DÒNG, bấm "↵" chỉ xuống dòng chứ không submit được gì cả.
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(20), dp(10), dp(20), 0)
            addView(input)
        }
        fun submit() {
            val name = input.text.toString().trim()
            val profile = AccountProfileStore.add(this, name)
            if (profile == null) {
                Toast.makeText(this, "Đã đạt tối đa ${AccountProfileStore.MAX_PROFILES} tài khoản", Toast.LENGTH_SHORT).show()
            } else {
                render()
                openProfile(profile)
            }
        }
        val dialog = AlertDialog.Builder(this, R.style.Theme_WP_Dialog)
            .setTitle("Thêm tài khoản mới")
            .setView(container)
            .setPositiveButton("Thêm") { _, _ -> submit() }
            .setNegativeButton("Huỷ", null)
            .create()
        // Bấm nút "Xong" trên bàn phím sau khi gõ tên -> submit NGAY, không cần với tay bấm
        // riêng nút "Thêm" - đúng kỳ vọng thông thường khi gõ xong 1 ô rồi bấm Enter.
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                submit(); dialog.dismiss(); true
            } else false
        }
        dialog.show()
    }

    private fun showProfileOptions(profile: AccountProfileStore.Profile) {
        AlertDialog.Builder(this, R.style.Theme_WP_Dialog)
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
            setTextColor(Color.WHITE)
            // Cùng lý do như [showAddDialog]: ép nút "Xong" thay vì nút xuống dòng "↵".
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(20), dp(10), dp(20), 0)
            addView(input)
        }
        fun submit() {
            val newName = input.text.toString().trim()
            if (newName.isNotBlank()) {
                AccountProfileStore.rename(this, profile.slot, newName)
                render()
            }
        }
        val dialog = AlertDialog.Builder(this, R.style.Theme_WP_Dialog)
            .setTitle("Đổi tên hồ sơ")
            .setView(container)
            .setPositiveButton("Lưu") { _, _ -> submit() }
            .setNegativeButton("Huỷ", null)
            .create()
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                submit(); dialog.dismiss(); true
            } else false
        }
        dialog.show()
    }

    private fun showDeleteConfirm(profile: AccountProfileStore.Profile) {
        AlertDialog.Builder(this, R.style.Theme_WP_Dialog)
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
        startActivityWp(Intent(this, activityClass))
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
