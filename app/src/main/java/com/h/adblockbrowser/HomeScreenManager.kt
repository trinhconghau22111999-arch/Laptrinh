package com.h.adblockbrowser

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
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

/** Trang chủ kiểu MÀN HÌNH START của Windows Phone / Windows 10 Mobile:
 *   1) Phía trên: lưới "Live Tile" vuông, phẳng, mỗi ô 1 màu accent riêng (xoay vòng bảng màu
 *      gốc của WP) chứa icon trắng đơn sắc + nhãn ở góc dưới trái - giống hệt các ô ghim
 *      (pinned tile) trên Start Screen thật, xếp 3 cột kiểu tile cỡ vừa (medium tile).
 *   2) Phía dưới: DANH SÁCH TOÀN BỘ ỨNG DỤNG đã cài, xếp thành danh sách dọc theo thứ tự
 *      A-Z với tiêu đề chữ cái lớn màu accent đứng riêng từng nhóm - đúng kiểu "App list" khi
 *      vuốt Start Screen của WP sang phải, KHÔNG phải lưới icon tròn kiểu iOS như bản cũ. */
class HomeScreenManager(
    private val context: Context,
    private val onOpenShortcut: (ShortcutItem) -> Unit,
    private val onOpenSettings: () -> Unit
) {
    /** Bảng màu Live Tile - dùng chung [ThemePrefs.PALETTE] (đúng 20 màu Accent/Live Tile gốc
     *  của Windows Phone) để đồng bộ với lưới chọn màu ở Cài đặt > Giao diện, thay vì trang chủ
     *  tự có 1 bảng 8 màu rút gọn riêng như trước - xoay vòng cho từng ô ghim để mỗi tile 1 màu
     *  khác nhau, đúng cảm giác Start Screen thật (không phải app nào cũng cùng 1 màu). */
    private val tilePalette = ThemePrefs.PALETTE

    /** [clockWidget]: widget giờ/ngày (nếu có) được chèn làm mục ĐẦU TIÊN trong nội dung cuộn
     *  dọc, NẰM NGAY TRONG LUỒNG LAYOUT (không phải overlay nổi tự do như trước) - nhờ vậy nó
     *  "dính" liền phía trên lưới tile, và khi người dùng chụm/dãn tay phóng to widget này ra,
     *  chiều cao của nó tăng lên sẽ tự động ĐẨY lưới tile + danh sách app bên dưới xuống theo. */
    fun build(clockWidget: View? = null): FrameLayout {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // ── Toàn bộ nội dung cuộn dọc ──
        val scrollView = ScrollView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(20), dp(40), dp(20), dp(24))
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ── Widget giờ/ngày (nếu có) - luôn ở trên cùng, dính liền phía trên lưới tile ──
        if (clockWidget != null) {
            content.addView(clockWidget, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER_HORIZONTAL; it.bottomMargin = dp(12) })
        }

        // ── Tiêu đề "start" kiểu Hub/Pivot header của WP: chữ thường, mảnh, rất to ──
        content.addView(sectionHeader("start"))

        // ── Lưới Live Tile - 3 cột, ô vuông sát nhau (khe hở 2dp) ──
        val pinnedKeys = listOf("youtube", "incognito", "accounts", "files", "calendar", "calculator", "clock", "app_lock")
        val tileCols = 3
        val tileGrid = GridLayout(context).apply {
            columnCount = tileCols
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(28) }
        }
        pinnedKeys.forEachIndexed { index, key ->
            ShortcutsRepository.ALL[key]?.let { item ->
                val tileColor = tilePalette[index % tilePalette.size]
                val lp = GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(96)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                    setMargins(dp(1), dp(1), dp(1), dp(1))
                }
                tileGrid.addView(buildLiveTile(item.label, item.iconRes, tileColor) { onOpenShortcut(item) }, lp)
            }
        }
        content.addView(tileGrid)

        // ── Tiêu đề "danh sách ứng dụng" kiểu Hub/Pivot header ──
        content.addView(sectionHeader("ứng dụng"))

        // ── Danh sách toàn bộ app đã cài, xếp A-Z, có tiêu đề chữ cái từng nhóm ──
        val apps = installedApps()
        var lastLetter: String? = null
        apps.forEach { info ->
            val pm = context.packageManager
            val label = info.loadLabel(pm).toString()
            val icon = info.loadIcon(pm)
            val pkgName = info.activityInfo.packageName
            val firstLetter = label.trim().take(1).uppercase().ifEmpty { "#" }
            if (firstLetter != lastLetter) {
                content.addView(alphabetHeader(firstLetter))
                lastLetter = firstLetter
            }
            content.addView(buildAppListRow(label, icon) {
                val launch = pm.getLaunchIntentForPackage(pkgName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                }
            })
        }

        scrollView.addView(content)
        root.addView(scrollView)

        // ── Nút Cài đặt (bánh răng) - CỐ ĐỊNH ở góc trên-phải, nổi trên nội dung cuộn, LUÔN
        //    hiện sẵn kể cả khi cuộn xuống. TRƯỚC ĐÂY [onOpenSettings] được truyền vào nhưng
        //    KHÔNG có nút nào gọi tới nó trong toàn bộ HomeScreenManager - nghĩa là màn Cài đặt
        //    không có cách nào mở được từ trang chủ. Thêm nút này để sửa lỗi đó. */
        val btnSettings = ImageView(context).apply {
            setImageResource(R.drawable.ic_shortcut_settings)
            setColorFilter(Color.WHITE)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = pressedOverlay()
            isClickable = true
            isFocusable = true
            contentDescription = "Cài đặt"
            setOnClickListener { onOpenSettings() }
        }
        root.addView(btnSettings, FrameLayout.LayoutParams(dp(40), dp(40)).also {
            it.gravity = Gravity.TOP or Gravity.END
            it.topMargin = dp(12); it.rightMargin = dp(12)
        })

        return root
    }

    /** Tiêu đề lớn kiểu Pivot/Hub header của WP: chữ thường, cực mảnh, cỡ lớn, màu trắng. */
    private fun sectionHeader(text: String): View = TextView(context).apply {
        this.text = text
        textSize = 30f
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        setPadding(dp(2), dp(4), dp(2), dp(10))
    }

    /** Tiêu đề chữ cái phân nhóm A-Z trong danh sách app, kiểu WP App List: chữ to, màu accent,
     *  đứng riêng 1 dòng làm mốc phân cách trực quan giữa các nhóm chữ cái. */
    private fun alphabetHeader(letter: String): View = TextView(context).apply {
        text = letter.lowercase()
        textSize = 22f
        setTextColor(ThemePrefs.accent(context))
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        setPadding(dp(4), dp(14), dp(4), dp(4))
    }

    /** 1 ô "Live Tile" vuông kiểu WP: nền màu accent phẳng (KHÔNG bo góc, KHÔNG đổ bóng),
     *  icon trắng đơn sắc ở góc trên-trái, nhãn chữ ở góc dưới-trái - đúng bố cục tile chuẩn. */
    private fun buildLiveTile(label: String, iconRes: Int, tileColor: Int, onClick: () -> Unit): View {
        val tile = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(tileColor)
            }
            isClickable = true
            isFocusable = true
            foreground = pressedOverlay()
        }

        val icon = ImageView(context).apply {
            setImageResource(iconRes)
            layoutParams = FrameLayout.LayoutParams(dp(28), dp(28)).also {
                it.gravity = Gravity.TOP or Gravity.START
                it.leftMargin = dp(10); it.topMargin = dp(10)
            }
        }

        val labelView = TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also {
                it.gravity = Gravity.BOTTOM or Gravity.START
                it.leftMargin = dp(8); it.bottomMargin = dp(6); it.rightMargin = dp(8)
            }
        }

        tile.addView(icon)
        tile.addView(labelView)
        tile.setOnClickListener { onClick() }
        return tile
    }

    /** 1 dòng trong danh sách app kiểu WP App List: icon nhỏ bên trái + tên app, không nền,
     *  không viền, không bo góc - chỉ cách nhau bằng khoảng trắng, tối giản tuyệt đối. */
    private fun buildAppListRow(label: String, iconDrawable: Drawable, onClick: () -> Unit): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(9), dp(4), dp(9))
            isClickable = true
            isFocusable = true
            foreground = pressedOverlay()
        }
        val icon = ImageView(context).apply {
            setImageDrawable(iconDrawable)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).also { it.rightMargin = dp(16) }
        }
        val text = TextView(context).apply {
            text = label
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        row.addView(icon)
        row.addView(text)
        row.setOnClickListener { onClick() }
        return row
    }

    /** Hiệu ứng khi bấm: KHÔNG dùng ripple tròn Material mặc định (kiểu Android/iOS bo góc),
     *  chỉ làm tối nhẹ toàn bộ ô hình chữ nhật - đúng cảm giác "bấm phẳng" của Metro UI. */
    private fun pressedOverlay(): Drawable {
        val pressedState = GradientDrawable().apply { setColor(0x33FFFFFF) }
        val normalState = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedState)
            addState(intArrayOf(), normalState)
        }
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

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
