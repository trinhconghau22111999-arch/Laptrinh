package com.h.adblockbrowser

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
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
        ),
        "calendar" to ShortcutItem(
            "calendar", "Lịch", ShortcutType.ACTIVITY, "CalendarActivity", R.drawable.ic_shortcut_calendar
        ),
        "calculator" to ShortcutItem(
            "calculator", "Máy tính", ShortcutType.ACTIVITY, "CalculatorActivity", R.drawable.ic_shortcut_calculator
        ),
        "clock" to ShortcutItem(
            "clock", "Đồng hồ", ShortcutType.ACTIVITY, "ClockActivity", R.drawable.ic_shortcut_clock
        )
    )
}

/** Trang chủ launcher: hàng shortcut cố định phía trên + lưới toàn bộ app đã cài phía dưới
 *  (cuộn dọc). Bấm vào app -> mở ngay; Back từ app đó -> về lại màn hình này (vì đây là HOME). */
class HomeScreenManager(
    private val context: Context,
    private val onOpenShortcut: (ShortcutItem) -> Unit,
    private val onOpenSettings: () -> Unit
) {
    fun build(): FrameLayout {
        val root = FrameLayout(context)

        // ── Nút Cài đặt góc trên-trái ──
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

        // ── Toàn bộ nội dung cuộn dọc ──
        val scrollView = ScrollView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Đẩy xuống tránh đè nút Settings
            setPadding(0, dp(80), 0, dp(16))
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ── Hàng shortcut cố định ──
        val shortcutRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        }
        listOf("youtube", "incognito", "accounts", "files", "calendar", "calculator", "clock").forEach { key ->
            ShortcutsRepository.ALL[key]?.let { item ->
                shortcutRow.addView(buildIconCell(
                    label = item.label,
                    iconDrawable = null,
                    iconRes = item.iconRes,
                    isAccounts = item.key == "accounts",
                    onClick = { onOpenShortcut(item) }
                ), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.leftMargin = dp(8); it.rightMargin = dp(8) })
            }
        }
        content.addView(shortcutRow)

        // ── Đường kẻ phân cách ──
        content.addView(View(context).apply {
            setBackgroundColor(0x33FFFFFF)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            ).also { it.leftMargin = dp(16); it.rightMargin = dp(16); it.bottomMargin = dp(12) }
        })

        // ── Lưới app đã cài ──
        val apps = installedApps()
        val cols = 4
        val grid = GridLayout(context).apply {
            columnCount = cols
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        apps.forEach { info ->
            val pm = context.packageManager
            val label = info.loadLabel(pm).toString()
            val icon = info.loadIcon(pm)
            val pkgName = info.activityInfo.packageName
            val cell = buildIconCell(
                label = label,
                iconDrawable = icon,
                iconRes = null,
                isAccounts = false,
                onClick = {
                    val launch = pm.getLaunchIntentForPackage(pkgName)
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launch)
                    }
                }
            )
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(0, 0, 0, 0)
            }
            grid.addView(cell, lp)
        }
        content.addView(grid)

        scrollView.addView(content)
        root.addView(scrollView)

        return root
    }

    /** Lấy danh sách app có thể launch (trừ chính app này) - sắp xếp A-Z theo tên hiển thị. */
    private fun installedApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val pm = context.packageManager
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }

    private fun buildIconCell(
        label: String,
        iconDrawable: Drawable?,
        iconRes: Int?,
        isAccounts: Boolean,
        onClick: () -> Unit
    ): View {
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
            if (iconDrawable != null) setImageDrawable(iconDrawable)
            else if (iconRes != null) setImageResource(iconRes)
            if (isAccounts) {
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setStroke(dp(2), Color.WHITE)
                }
            }
        }
        val labelView = TextView(context).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(2), dp(4), dp(2), 0)
            setShadowLayer(6f, 0f, 1f, Color.BLACK)
        }

        cell.addView(icon)
        cell.addView(labelView)
        cell.setOnClickListener { onClick() }
        return cell
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
