package com.h.adblockbrowser

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Loại 1 mục trên trang chủ: mở thẳng 1 trang web (WEB) hay mở 1 Activity trong app (ACTIVITY). */
enum class ShortcutType { WEB, ACTIVITY }

data class ShortcutItem(
    val key: String,
    val label: String,
    val type: ShortcutType,
    val target: String,
    val iconRes: Int
)

/** Danh sách các mục trên trang chủ: YouTube, Ẩn danh, Đa tab, và Cài đặt mã khoá app.
 *  Toàn bộ các "app con" khác (Máy tính, Lịch, Camera, Đồng hồ, Ghi chú, Quản lý tệp, Thư viện,
 *  Xem ảnh, Phát media, Ghi âm, Truy cập nhanh...) và thanh 3 nút điều hướng nổi đã bị xoá hoàn
 *  toàn khỏi app theo yêu cầu dọn dẹp trước đó. */
object ShortcutsRepository {
    val ALL: LinkedHashMap<String, ShortcutItem> = linkedMapOf(
        "youtube" to ShortcutItem(
            "youtube", "YouTube", ShortcutType.WEB, "https://www.youtube.com", R.drawable.ic_shortcut_youtube
        ),
        "incognito" to ShortcutItem(
            "incognito", "Ẩn danh", ShortcutType.ACTIVITY, "IncognitoActivity", R.drawable.ic_shortcut_incognito
        ),
        "accounts" to ShortcutItem(
            "accounts", "Nhiều T.khoản", ShortcutType.ACTIVITY, "AccountsActivity", R.drawable.ic_shortcut_accounts
        ),
        "app_lock" to ShortcutItem(
            "app_lock", "Khoá", ShortcutType.ACTIVITY, "AppLockSetupActivity", R.drawable.ic_shortcut_applock
        ),
        "files" to ShortcutItem(
            "files", "Quản lý tệp", ShortcutType.ACTIVITY, "FilesActivity", R.drawable.ic_shortcut_files
        )
    )
}

/** Trang chủ rút gọn: chỉ hiện đúng các icon cố định (không phân trang, không kéo-thả, không
 *  menu tuỳ chỉnh) - thay cho bản cũ nhiều trang/kéo-thả/Cài đặt đã bị xoá. */
class HomeScreenManager(
    private val context: Context,
    private val onOpenShortcut: (ShortcutItem) -> Unit,
    private val onOpenSettings: () -> Unit
) {
    fun build(): FrameLayout {
        val root = FrameLayout(context)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            )
        }

        listOf("youtube", "incognito", "accounts", "files").forEach { key ->
            ShortcutsRepository.ALL[key]?.let { item ->
                row.addView(buildIconCell(item), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.leftMargin = dp(8); it.rightMargin = dp(8) })
            }
        }

        root.addView(row)

        // Nút Cài đặt: icon nhỏ ở góc trên-trái màn hình chính
        val btnSettings = ImageView(context).apply {
            setImageResource(R.drawable.ic_shortcut_settings)
            layoutParams = FrameLayout.LayoutParams(dp(28), dp(28)).also {
                it.gravity = Gravity.TOP or Gravity.START
                it.topMargin = dp(40)
                it.leftMargin = dp(16)
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isClickable = true; isFocusable = true
            background = android.util.TypedValue().let {
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
                context.getDrawable(it.resourceId)
            }
            setOnClickListener { onOpenSettings() }
        }
        root.addView(btnSettings)

        return root
    }

    private fun buildIconCell(item: ShortcutItem): View {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(10), dp(6), dp(10))
            isClickable = true; isFocusable = true
            background = android.util.TypedValue().let {
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
                context.getDrawable(it.resourceId)
            }
        }

        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(51), dp(51))
            setImageResource(item.iconRes)
            // Icon "Nhiều tài khoản" (hình người) - thêm viền tròn bao quanh cho nổi bật, giống
            // kiểu vòng tròn avatar ở màn chọn hồ sơ (AccountsActivity).
            if (item.key == "accounts") {
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setStroke(dp(2), Color.WHITE)
                }
            }
        }
        val label = TextView(context).apply {
            text = item.label
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(2), dp(6), dp(2), 0)
            setShadowLayer(6f, 0f, 1f, Color.BLACK)
        }

        cell.addView(icon)
        cell.addView(label)
        cell.setOnClickListener { onOpenShortcut(item) }
        return cell
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
