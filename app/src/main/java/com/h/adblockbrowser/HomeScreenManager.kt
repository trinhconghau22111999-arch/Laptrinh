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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

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

/** Trang chủ kiểu PIVOT/HUB 2 TRANG của Windows Phone / Windows 10 Mobile - vuốt ngang để
 *  chuyển giữa 2 trang riêng biệt, đúng cảm giác Start Screen thật:
 *   1) TRANG "start" (bên trái, hiện ra đầu tiên): widget giờ/ngày + lưới "Live Tile" vuông -
 *      gồm các ô CỐ ĐỊNH (YouTube, Ẩn danh...) VÀ các app người dùng tự "Ghim vào start".
 *   2) TRANG "ứng dụng" (bên phải, vuốt sang mới thấy): DANH SÁCH TOÀN BỘ ỨNG DỤNG đã cài, xếp
 *      A-Z. NHẤN GIỮ (long-press) 1 app trong danh sách này sẽ hiện menu "Ghim vào start" -
 *      chọn xong app đó xuất hiện thành tile ngay trên trang "start", đúng kiểu Windows Phone.
 *      NHẤN GIỮ tile vừa ghim đó trên trang "start" sẽ hiện menu "Bỏ ghim khỏi start". */
class HomeScreenManager(
    private val context: Context,
    private val onOpenShortcut: (ShortcutItem) -> Unit,
    private val onOpenSettings: () -> Unit
) {
    /** Bảng màu Live Tile - dùng chung [ThemePrefs.PALETTE] (đúng 20 màu Accent/Live Tile gốc
     *  của Windows Phone) để đồng bộ với lưới chọn màu ở Cài đặt > Giao diện - xoay vòng cho
     *  từng ô ghim để mỗi tile 1 màu khác nhau, đúng cảm giác Start Screen thật. */
    private val tilePalette = ThemePrefs.PALETTE

    /** Giữ lại tham chiếu để [refreshPages] có thể dựng lại nội dung 2 trang ngay khi người
     *  dùng ghim/bỏ ghim 1 app, mà KHÔNG cần thoát vào lại trang chủ mới thấy cập nhật. */
    private var pageAdapterRef: PageAdapter? = null
    private var clockWidgetRef: View? = null

    /** [clockWidget]: widget giờ/ngày (nếu có) được chèn làm mục ĐẦU TIÊN trong nội dung cuộn
     *  dọc của TRANG "start", NẰM NGAY TRONG LUỒNG LAYOUT (không phải overlay nổi tự do) - nhờ
     *  vậy nó "dính" liền phía trên lưới tile, và khi người dùng chụm/dãn tay phóng to widget
     *  này ra, chiều cao của nó tăng lên sẽ tự động ĐẨY lưới tile bên dưới xuống theo. */
    fun build(clockWidget: View? = null): FrameLayout {
        clockWidgetRef = clockWidget
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // ── ViewPager2: 2 TRANG Pivot vuốt ngang - trang 0 "start", trang 1 "ứng dụng" ──
        val pages = mutableListOf(buildStartPage(clockWidget), buildAppListPage())
        val adapter = PageAdapter(pages)
        pageAdapterRef = adapter
        val pager = ViewPager2(context).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            this.adapter = adapter
            offscreenPageLimit = 1
        }
        root.addView(pager)

        // ── Nút Cài đặt (bánh răng) - CỐ ĐỊNH ở góc trên-phải, nổi trên CẢ 2 trang. ──
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

    /** Dựng lại nội dung CẢ 2 trang và báo cho ViewPager2 refresh - gọi ngay sau khi người dùng
     *  ghim hoặc bỏ ghim 1 app, để tile mới hiện/mất trên trang "start" NGAY LẬP TỨC. */
    private fun refreshPages() {
        val adapter = pageAdapterRef ?: return
        adapter.pages[0] = buildStartPage(clockWidgetRef)
        adapter.pages[1] = buildAppListPage()
        adapter.notifyDataSetChanged()
    }

    /** Dựng TRANG "start" (trang trái - hiện mặc định): widget giờ/ngày + tiêu đề "start" +
     *  lưới Live Tile 3 cột, gồm các ô cố định VÀ các app người dùng đã ghim thêm. */
    private fun buildStartPage(clockWidget: View?): View {
        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Đáy chừa thêm đúng chiều cao WpNavBar (xem WpNavBar.HEIGHT_DP) - thanh điều
            // hướng nổi là cửa sổ hệ thống riêng luôn đè lên trên cùng, nếu chỉ để dp(24) như
            // trước thì tile cuối cùng của lưới Live Tile bị nó che khuất mất 1 phần.
            setPadding(dp(20), dp(40), dp(20), dp(24) + dp(WpNavBar.HEIGHT_DP))
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ── Widget giờ/ngày (nếu có) - luôn ở trên cùng của trang "start" ──
        if (clockWidget != null) {
            (clockWidget.parent as? ViewGroup)?.removeView(clockWidget)
            content.addView(clockWidget, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER_HORIZONTAL; it.bottomMargin = dp(12) })
        }

        // ── Tiêu đề "start" kiểu Hub/Pivot header của WP: chữ thường, mảnh, rất to ──
        content.addView(sectionHeader("start"))

        // ── Lưới Live Tile - ĐÚNG kiểu Start Screen WP thật: TRỘN cỡ vuông (1 đơn vị) và
        // "wide" hình chữ nhật dài (2 đơn vị, gấp đôi bề ngang) trong lưới ngang 3 cột, thay vì
        // toàn bộ tile cùng 1 cỡ vuông như trước (khác biệt hình ảnh rõ nhất so với WP thật).
        //
        // TỰ XẾP HÀNG THỦ CÔNG bằng LinearLayout lồng nhau (cộng dồn "đơn vị" từng tile theo thứ
        // tự, đủ 3 đơn vị/hàng thì xuống hàng mới) THAY VÌ dùng GridLayout tự động dồn ô +
        // columnSpec: GridLayout tự đặt vị trí (row/col = UNDEFINED) rất dễ vỡ layout (hở ô,
        // lệch hàng) khi trộn nhiều columnSpec span khác nhau trong cùng lưới - rủi ro không
        // đáng, trong khi cách xếp hàng thủ công này luôn cho kết quả chắc chắn, dễ kiểm chứng.
        val pinnedKeys = listOf("youtube", "incognito", "accounts", "files", "calendar", "calculator", "clock", "app_lock")
        // Các ô này chiếm 2/3 lưới (tile "wide") - YouTube vì là chức năng dùng nhiều nhất
        // (giống Store/Photos hay được đặt wide trên Start Screen thật), "Nhiều T.khoản" vì tên
        // dài, đặt wide giúp chữ không bị ngắt dòng xấu trong ô vuông chật.
        val wideKeys = setOf("youtube", "accounts")
        val tileUnitsPerRow = 3
        var colorIndex = 0

        val tilesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        var currentRow: LinearLayout? = null
        var unitsInRow = 0
        fun addTileToGrid(tileView: View, units: Int) {
            if (currentRow == null || unitsInRow + units > tileUnitsPerRow) {
                currentRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                tilesContainer.addView(currentRow, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(96)
                ))
                unitsInRow = 0
            }
            currentRow!!.addView(tileView, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, units.toFloat()
            ).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) })
            unitsInRow += units
        }

        // Các ô cố định (YouTube, Ẩn danh, Nhiều T.khoản, ...)
        pinnedKeys.forEach { key ->
            ShortcutsRepository.ALL[key]?.let { item ->
                val tileColor = tilePalette[colorIndex % tilePalette.size]; colorIndex++
                val units = if (key in wideKeys) 2 else 1
                val tile = buildLiveTile(item.label, item.iconRes, tileColor) { onOpenShortcut(item) }
                addTileToGrid(tile, units)
            }
        }

        // ── Các app người dùng đã NHẤN GIỮ trong trang "ứng dụng" rồi chọn "Ghim vào start" ──
        // (luôn cỡ vuông 1 đơn vị - đúng mặc định của WP thật khi ghim mới 1 app, muốn đổi cỡ
        // phải tự vào menu "Đổi kích thước" riêng, ngoài phạm vi sửa lần này).
        val pm = context.packageManager
        PinnedAppsStore.getAll(context).forEach { pkgName ->
            val appIcon = try { pm.getApplicationIcon(pkgName) } catch (e: Exception) { null }
            val appLabel = try { pm.getApplicationInfo(pkgName, 0).loadLabel(pm).toString() } catch (e: Exception) { null }
            if (appIcon != null && appLabel != null) {
                val tileColor = tilePalette[colorIndex % tilePalette.size]; colorIndex++
                val tile = buildAppTile(appLabel, appIcon, tileColor,
                    onClick = {
                        val launch = pm.getLaunchIntentForPackage(pkgName)
                        if (launch != null) {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launch)
                        }
                    },
                    onLongPress = { anchor -> showPinContextMenu(anchor, pkgName) }
                )
                addTileToGrid(tile, 1)
            }
        }

        content.addView(tilesContainer)

        scrollView.addView(content)
        return scrollView
    }

    /** Dựng TRANG "ứng dụng" (trang phải - vuốt sang mới thấy): DANH SÁCH TOÀN BỘ app đã cài,
     *  xếp A-Z. Mỗi dòng hỗ trợ NHẤN GIỮ để hiện menu ghim/bỏ ghim vào "start". */
    private fun buildAppListPage(): View {
        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Cùng lý do như buildStartPage() ở trên: chừa thêm đúng chiều cao WpNavBar, nếu
            // không app CUỐI CÙNG trong danh sách A-Z sẽ bị thanh điều hướng nổi che mất.
            setPadding(dp(20), dp(40), dp(20), dp(24) + dp(WpNavBar.HEIGHT_DP))
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(sectionHeader("ứng dụng"))

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
            content.addView(buildAppListRow(
                label, icon,
                onClick = {
                    val launch = pm.getLaunchIntentForPackage(pkgName)
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launch)
                    }
                },
                onLongPress = { anchor -> showPinContextMenu(anchor, pkgName) }
            ))
        }

        scrollView.addView(content)
        return scrollView
    }

    /** Menu bật lên khi NHẤN GIỮ 1 app (trong danh sách "ứng dụng" hoặc chính tile đã ghim trên
     *  "start") - tự đổi nhãn "Ghim vào start" / "Bỏ ghim khỏi start" tuỳ trạng thái hiện tại,
     *  rồi dựng lại 2 trang ngay để tile mới hiện/mất tức thì.
     *
     *  DỰNG THỦ CÔNG bằng [PopupWindow] thay vì [android.widget.PopupMenu] mặc định của Android:
     *  PopupMenu hệ thống luôn tự vẽ nền TRẮNG BO GÓC + ĐỔ BÓNG (Material Card) bất kể theme app
     *  đặt gì - hiện lên giữa 1 màn hình đen phẳng tuyệt đối sẽ rất chỏi, sai hẳn cảm giác context
     *  menu phẳng, không bóng, nền đen của WP/Windows 10 Mobile thật. */
    private fun showPinContextMenu(anchor: View, pkgName: String) {
        val pinned = PinnedAppsStore.isPinned(context, pkgName)
        val title = if (pinned) "Bỏ ghim khỏi start" else "Ghim vào start"

        lateinit var popup: PopupWindow
        val item = TextView(context).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(dp(22), dp(16), dp(22), dp(16))
            minWidth = dp(200)
            isClickable = true
            isFocusable = true
            background = pressedOverlay()
            setOnClickListener {
                if (pinned) PinnedAppsStore.unpin(context, pkgName) else PinnedAppsStore.pin(context, pkgName)
                refreshPages()
                popup.dismiss()
            }
        }
        val menuBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // Nền đen tuyệt đối phẳng, viền mảnh màu xám đậm thay vì bóng đổ - đúng cảm giác
            // context menu WP thật nổi trên nền tối, phân biệt bằng đường viền chứ không bằng
            // elevation/shadow (Metro là thiết kế phẳng tuyệt đối).
            background = GradientDrawable().apply {
                setColor(0xFF1A1A1A.toInt())
                setStroke(dp(1), 0xFF3A3A3A.toInt())
            }
            addView(item)
        }

        popup = PopupWindow(
            menuBox, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true
        ).apply {
            elevation = 0f // bỏ hẳn bóng đổ mặc định của PopupWindow trên Android 5.0+
            animationStyle = 0 // bỏ hiệu ứng mờ dần mặc định, hiện tức thì như context menu WP thật
            isOutsideTouchable = true
        }
        popup.showAsDropDown(anchor, 0, dp(4))
    }

    /** Adapter tối giản cho ViewPager2: 2 trang (có thể được [refreshPages] dựng lại), bọc mỗi
     *  View có sẵn vào 1 FrameLayout chứa cho từng vị trí. */
    private class PageAdapter(val pages: MutableList<View>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val container = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            return object : RecyclerView.ViewHolder(container) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val container = holder.itemView as FrameLayout
            container.removeAllViews()
            val page = pages[position]
            (page.parent as? ViewGroup)?.removeView(page)
            container.addView(page)
        }

        override fun getItemCount(): Int = pages.size
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

    /** 1 ô "Live Tile" vuông kiểu WP cho các mục CỐ ĐỊNH (dùng icon vector có sẵn trong app):
     *  nền màu accent phẳng, icon trắng đơn sắc góc trên-trái, nhãn ở góc dưới-trái. */
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

        val labelView = buildTileLabel(label)

        tile.addView(icon)
        tile.addView(labelView)
        tile.setOnClickListener { onClick() }
        return tile
    }

    /** 1 ô "Live Tile" cho app NGƯỜI DÙNG TỰ GHIM (dùng icon thật của app, không phải icon
     *  vector đơn sắc) - bố cục giống [buildLiveTile], thêm NHẤN GIỮ để mở menu bỏ ghim. */
    private fun buildAppTile(
        label: String, icon: Drawable, tileColor: Int,
        onClick: () -> Unit, onLongPress: (View) -> Unit
    ): View {
        val tile = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(tileColor)
            }
            isClickable = true
            isFocusable = true
            isLongClickable = true
            foreground = pressedOverlay()
        }

        val iconView = ImageView(context).apply {
            setImageDrawable(icon)
            layoutParams = FrameLayout.LayoutParams(dp(28), dp(28)).also {
                it.gravity = Gravity.TOP or Gravity.START
                it.leftMargin = dp(10); it.topMargin = dp(10)
            }
        }

        val labelView = buildTileLabel(label)

        tile.addView(iconView)
        tile.addView(labelView)
        tile.setOnClickListener { onClick() }
        tile.setOnLongClickListener { anchor -> onLongPress(anchor); true }
        return tile
    }

    private fun buildTileLabel(label: String): TextView = TextView(context).apply {
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

    /** 1 dòng trong danh sách app kiểu WP App List: icon nhỏ bên trái + tên app, không nền,
     *  không viền, không bo góc. NHẤN GIỮ (long-press) để mở menu ghim/bỏ ghim vào "start". */
    private fun buildAppListRow(
        label: String, iconDrawable: Drawable,
        onClick: () -> Unit, onLongPress: (View) -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(9), dp(4), dp(9))
            isClickable = true
            isFocusable = true
            isLongClickable = true
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
        row.setOnLongClickListener { anchor -> onLongPress(anchor); true }
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
