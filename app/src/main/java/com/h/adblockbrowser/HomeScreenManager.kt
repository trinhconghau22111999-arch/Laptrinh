package com.h.adblockbrowser

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
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
import android.widget.Toast
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
        "settings" to ShortcutItem(
            "settings", "Cài đặt", ShortcutType.ACTIVITY, "SettingsActivity", R.drawable.ic_shortcut_settings
        ),
        "files" to ShortcutItem(
            "files", "Quản lý tệp", ShortcutType.ACTIVITY, "FilesActivity", R.drawable.ic_shortcut_files
        ),
        // Ô "Phone": mở màn giả lập MÀN HÌNH CHÍNH ANDROID THẬT (icon tự do kéo-thả + dock dọc
        // cạnh phải) - đóng vai trò tương tự icon "Desktop/This PC" trên máy tính Windows, xem
        // giải thích đầy đủ ở class doc của DesktopActivity.kt.
        "phone" to ShortcutItem(
            "phone", "Điện thoại", ShortcutType.ACTIVITY, "DesktopActivity", R.drawable.ic_wp_mobile_flat
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
 *      gồm các ô CỐ ĐỊNH (YouTube, Ẩn danh, Cài đặt...) VÀ các app người dùng tự "Ghim vào
 *      start". Nút "Cài đặt" (bánh răng) TRƯỚC ĐÂY là 1 nút riêng nổi cố định ở góc trên-phải
 *      màn hình, GIỜ ĐÃ GỘP vào thành 1 ô tile bình thường trong lưới (xem ShortcutsRepository
 *      key "settings") - không còn nút nổi tách biệt nữa, mọi thứ đều là tile.
 *   2) TRANG "ứng dụng" (bên phải, vuốt sang mới thấy): DANH SÁCH TOÀN BỘ ỨNG DỤNG đã cài, xếp
 *      A-Z. NHẤN GIỮ (long-press) 1 app trong danh sách này sẽ hiện menu "Ghim vào start" -
 *      chọn xong app đó xuất hiện thành tile ngay trên trang "start", đúng kiểu Windows Phone.
 *      NHẤN GIỮ tile vừa ghim đó trên trang "start" sẽ hiện menu "Bỏ ghim khỏi start". */
class HomeScreenManager(
    private val context: Context,
    private val onOpenShortcut: (ShortcutItem) -> Unit
) {
    /** Bảng màu Live Tile - dùng chung [ThemePrefs.PALETTE] (đúng 20 màu Accent/Live Tile gốc
     *  của Windows Phone) để đồng bộ với lưới chọn màu ở Cài đặt > Giao diện - xoay vòng cho
     *  từng ô ghim để mỗi tile 1 màu khác nhau, đúng cảm giác Start Screen thật. */
    private val tilePalette = ThemePrefs.PALETTE

    /** Giữ lại tham chiếu để [refreshPages] có thể dựng lại nội dung 2 trang ngay khi người
     *  dùng ghim/bỏ ghim 1 app, mà KHÔNG cần thoát vào lại trang chủ mới thấy cập nhật. */
    private var pageAdapterRef: PageAdapter? = null
    private var clockWidgetRef: View? = null
    private var pagerRef: ViewPager2? = null

    /** Cuộn về trang "start" (trang 0) ngay lập tức - gọi khi bấm nút Home (Windows) hoặc Back
     *  về màn chính, để trang Start luôn là trang hiển thị mặc định. */
    fun goToStart() {
        pagerRef?.setCurrentItem(0, true)
    }

    /** Trả về true nếu đang đứng ở trang 1 "DS Ứng Dụng" - dùng để Back từ trang này
     *  về trang "start" thay vì đưa app xuống nền ngay. */
    fun isOnAppListPage(): Boolean = (pagerRef?.currentItem ?: 0) == 1

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
        pagerRef = pager
        root.addView(pager)

        return root
    }

    /** Dựng lại nội dung CẢ 2 trang và báo cho ViewPager2 refresh - gọi ngay sau khi người dùng
     *  ghim/bỏ ghim/đánh dấu sao/gỡ cài đặt 1 app, để tile hoặc nhóm mới hiện/mất trên trang
     *  NGAY LẬP TỨC. PUBLIC (không còn private) để MainActivity.onResume() gọi được - cần thiết
     *  riêng cho trường hợp "Gỡ cài đặt" (xem showPinContextMenu): hộp thoại gỡ cài đặt thật của
     *  Android là 1 màn hình HỆ THỐNG riêng, app không biết chính xác lúc nào người dùng bấm
     *  "Gỡ" xong (khác các hành động Ghim/Đánh dấu sao xử lý XONG NGAY trong app, refresh được
     *  ngay lập tức) - nên phải refresh lại 1 lần nữa khi quay về app (onResume) để app vừa gỡ
     *  biến mất khỏi danh sách, phòng trường hợp người dùng gỡ thành công.
     *
     *  GIỮ NGUYÊN vị trí đang cuộn của cả 2 trang: dựng lại nghĩa là tạo hẳn 1 [ScrollView] MỚI
     *  (xem [buildStartPage]/[buildAppListPage]), luôn bắt đầu ở vị trí cuộn = 0 - nếu không lưu
     *  lại và cuộn về đúng chỗ cũ, người dùng đang xem giữa/cuối danh sách (vd đang ở nhóm "Mạng
     *  xã hội" phía dưới) mà chọn 1 mục trong menu nhấn giữ sẽ bị "giật" ngược lên đầu trang -
     *  đúng lỗi người dùng gặp phải. post{} vì scrollTo() cần chạy SAU khi View mới đã layout
     *  xong (biết chiều cao thật), gọi ngay lúc addView xong sẽ không có tác dụng. */
    fun refreshPages() {
        val adapter = pageAdapterRef ?: return
        val prevStartScrollY = (adapter.pages.getOrNull(0) as? ScrollView)?.scrollY ?: 0
        val prevAppListScrollY = (adapter.pages.getOrNull(1) as? ScrollView)?.scrollY ?: 0
        adapter.pages[0] = buildStartPage(clockWidgetRef)
        adapter.pages[1] = buildAppListPage()
        adapter.notifyDataSetChanged()
        (adapter.pages[0] as? ScrollView)?.let { sv -> sv.post { sv.scrollTo(0, prevStartScrollY) } }
        (adapter.pages[1] as? ScrollView)?.let { sv -> sv.post { sv.scrollTo(0, prevAppListScrollY) } }
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
            setPadding(dp(20), dp(8), dp(20), dp(4) + dp(WpNavBar.HEIGHT_DP))
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ── Tiêu đề "start" kiểu Hub/Pivot header của WP: chữ thường, mảnh, rất to - ĐẶT LÊN
        // TRÊN CÙNG. ──
        content.addView(sectionHeader("start"))

        // ── Lưới Live Tile - ĐÚNG kiểu Start Screen WP thật: 4 CỠ tile khác nhau (Nhỏ 1x1,
        // Rộng 2x1, Cao 1x2, To 2x2) trong lưới 3 cột - NHẤN GIỮ bất kỳ tile nào (kể cả ô cố
        // định VÀ widget giờ/ngày) rồi kéo tay cầm để đổi cỡ, lưu vĩnh viễn qua [TileSizeStore].
        //
        // DÙNG LƯỚI CHIẾM-DỤNG-Ô THỰC SỰ (occupancy grid, xem [GridPlacer]) THAY VÌ xếp hàng
        // ngang bằng LinearLayout lồng nhau như trước: cách xếp hàng cũ CHỈ xử lý được tile
        // rộng theo CHIỀU NGANG (mỗi hàng cao cố định dp(110)) - không thể cho 1 tile "Cao"/"To"
        // chiếm 2 HÀNG cùng lúc. GridPlacer duyệt từng ô lưới theo thứ tự trái->phải, trên-
        // >dưới, tìm vị trí trống đầu tiên vừa đủ cho tile (w x h đơn vị) rồi đánh dấu chiếm
        // dụng - tile sau đó được ĐẶT VỊ TRÍ TUYỆT ĐỐI (FrameLayout, toạ độ tính bằng "đơn vị
        // ô" x kích thước 1 ô) thay vì thả trôi theo layout tự động, để chiều cao lồng nhiều
        // hàng của tile "Cao"/"To" không làm vỡ layout các tile lân cận.
        // Thứ tự mặc định - có thể thay đổi khi người dùng kéo-thả
        val defaultFixedKeys = listOf("youtube", "settings", "incognito", "accounts", "files", "phone", "calendar", "calculator", "clock")
        val pinnedKeys = PinnedOrderStore.getFixedOrder(context, defaultFixedKeys).toMutableList()
        val defaultUserKeys = PinnedAppsStore.getAll(context)
        val userKeys = PinnedOrderStore.getUserOrder(context, defaultUserKeys).toMutableList()
        // Cỡ MẶC ĐỊNH khi người dùng CHƯA từng tự đổi kích cỡ tile đó - YouTube/Nhiều T.khoản
        // mặc định "Rộng" ngay từ đầu (giữ nguyên hành vi cũ), các ô còn lại mặc định "Nhỏ".
        val wideKeys = setOf("youtube", "accounts")
        val gridColumns = 3
        // Cỡ 1 "đơn vị" ô lưới = (bề ngang khả dụng của trang)/3 - lấy theo bề ngang MÀN HÌNH
        // THẬT (không cần đợi layout đo xong) trừ đi padding 2 bên dp(20) của scrollView, để ô
        // 1x1 luôn VUÔNG THỰC SỰ (bề ngang = bề cao), khớp đúng tỉ lệ Live Tile WP thật.
        val screenWidthPx = context.resources.displayMetrics.widthPixels
        val cellPitchPx = (screenWidthPx - dp(20) * 2) / gridColumns
        var colorIndex = 0

        val placer = GridPlacer(gridColumns)
        val gridContainer = FrameLayout(context)
        fun addTile(tileView: View, size: TileSize) {
            val (row, col) = placer.place(size.w, size.h)
            val lp = FrameLayout.LayoutParams(
                cellPitchPx * size.w - dp(4), cellPitchPx * size.h - dp(4)
            )
            lp.leftMargin = col * cellPitchPx + dp(2)
            lp.topMargin = row * cellPitchPx + dp(2)
            gridContainer.addView(tileView, lp)
        }

        // ── Widget giờ/ngày (nếu có) - GIỜ LÀ TILE ĐẦU TIÊN trong chính lưới này (không còn
        // nằm riêng phía trên lưới như trước) - mặc định cỡ "To" (giữ đúng cảm giác nổi bật như
        // thiết kế gốc), NHẤN GIỮ rồi kéo tay cầm để đổi cỡ y hệt mọi tile khác. Nội dung bên
        // trong ([ClockWidgetView]) tự đổi cỡ chữ + ẩn/hiện dòng ngày tương ứng - xem [applySize].
        if (clockWidget != null) {
            (clockWidget.parent as? ViewGroup)?.removeView(clockWidget)
            val size = TileSizeStore.get(context, "clock_widget", TileSize.TO)
            (clockWidget as? ClockWidgetView)?.applySize(size)
            addTile(clockWidget, size)
            clockWidget.setOnLongClickListener {
                enterResizeMode(clockWidget, gridContainer, cellPitchPx) { picked ->
                    TileSizeStore.set(context, "clock_widget", picked)
                    refreshPages()
                }
                true
            }
        }

        // ── Gom tất cả tile vào 1 danh sách có thể swap thứ tự khi kéo-thả ──
        data class TileEntry(
            val id: String,           // key hoặc pkgName
            val isFixed: Boolean,     // true = tile cố định, false = tile user ghim
            val buildFn: () -> View   // factory tạo view (gọi lại sau khi swap)
        )
        val allEntries = mutableListOf<TileEntry>()

        // Tile clock widget
        if (clockWidget != null) {
            (clockWidget.parent as? ViewGroup)?.removeView(clockWidget)
            val size = TileSizeStore.get(context, "clock_widget", TileSize.TO)
            (clockWidget as? ClockWidgetView)?.applySize(size)
            val (row, col) = placer.place(size.w, size.h)
            val lp = FrameLayout.LayoutParams(cellPitchPx * size.w - dp(4), cellPitchPx * size.h - dp(4))
            lp.leftMargin = col * cellPitchPx + dp(2); lp.topMargin = row * cellPitchPx + dp(2)
            gridContainer.addView(clockWidget, lp)
            clockWidget.setOnLongClickListener {
                enterResizeMode(clockWidget, gridContainer, cellPitchPx) { picked ->
                    TileSizeStore.set(context, "clock_widget", picked)
                    refreshPages()
                }
                true
            }
        }

        // Tile cố định
        pinnedKeys.forEach { key ->
            ShortcutsRepository.ALL[key]?.let { item ->
                val tileColor = if (key == "youtube") ThemePrefs.PALETTE[11]
                                else tilePalette[colorIndex % tilePalette.size].also { colorIndex++ }
                val defaultSize = if (key in wideKeys) TileSize.RONG else TileSize.NHO
                val size = TileSizeStore.get(context, key, defaultSize)
                val tile = buildLiveTile(item.label, item.iconRes, tileColor) { onOpenShortcut(item) }
                tile.tag = key  // tag để enterDragMode nhận diện khi swap
                addTile(tile, size)
                // Nhấn giữ: nếu đang resize mode → cancel; còn không → hiện menu resize/drag
                tile.setOnLongClickListener {
                    val existing = tile.tag
                    if (existing is ResizeState) {
                        gridContainer.removeView(existing.handleBottom)
                        gridContainer.removeView(existing.handleRight)
                        tile.foreground = existing.originalForeground
                        tile.tag = null
                    } else {
                        startTileDrag(tile, key, true, pinnedKeys, userKeys, gridContainer, cellPitchPx)
                    }
                    true
                }
            }
        }

        // Tile user ghim
        val pm = context.packageManager
        userKeys.forEach { pkgName ->
            val appIcon = try { pm.getApplicationIcon(pkgName) } catch (e: Exception) { null }
            val appLabel = try { pm.getApplicationInfo(pkgName, 0).loadLabel(pm).toString() } catch (e: Exception) { null }
            if (appIcon != null && appLabel != null) {
                val tileColor = tilePalette[colorIndex % tilePalette.size]; colorIndex++
                val size = TileSizeStore.getForPackage(context, pkgName, TileSize.NHO)
                val tile = buildAppTile(appLabel, appIcon, tileColor,
                    onClick = {
                        val launch = pm.getLaunchIntentForPackage(pkgName)
                        if (launch != null) { launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(launch) }
                    },
                    onLongPress = { anchor ->
                        startTileDrag(anchor, pkgName, false, pinnedKeys, userKeys, gridContainer, cellPitchPx)
                    }
                )
                tile.tag = pkgName  // tag để enterDragMode nhận diện
                addTile(tile, size)
            }
        }

        // Chiều cao lưới = đúng số hàng đã dùng x cỡ 1 ô - PHẢI set SAU KHI đặt hết tile (lúc
        // này [placer] mới biết chính xác tổng số hàng cần dùng).
        gridContainer.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, placer.totalRows() * cellPitchPx
        )
        content.addView(gridContainer)

        scrollView.addView(content)
        return scrollView
    }

    /** Thuật toán xếp lưới "chiếm dụng ô" (occupancy grid) tối giản cho lưới N cột: duyệt từng
     *  ô theo thứ tự trái->phải rồi trên->dưới, tìm vị trí (hàng, cột) TRỐNG ĐẦU TIÊN đủ chỗ
     *  cho khối kích thước [w] x [h] đơn vị (không chồng lên tile đã đặt trước đó), tự thêm
     *  hàng mới khi cần. Dùng cho lưới Live Tile trên trang "start" để hỗ trợ tile "Cao"/"To"
     *  chiếm nhiều hàng cùng lúc - điều mà cách xếp hàng ngang đơn giản (LinearLayout lồng
     *  nhau, mỗi hàng cao cố định) KHÔNG làm được. */
    private class GridPlacer(private val columns: Int) {
        private val occupied = mutableListOf<BooleanArray>()
        private fun ensureRow(r: Int) { while (occupied.size <= r) occupied.add(BooleanArray(columns)) }
        fun place(w: Int, h: Int): Pair<Int, Int> {
            var row = 0
            while (true) {
                ensureRow(row + h - 1)
                for (col in 0..columns - w) {
                    var fits = true
                    loop@ for (rr in row until row + h) {
                        for (cc in col until col + w) {
                            if (occupied[rr][cc]) { fits = false; break@loop }
                        }
                    }
                    if (fits) {
                        for (rr in row until row + h) for (cc in col until col + w) occupied[rr][cc] = true
                        return row to col
                    }
                }
                row++
            }
        }
        fun totalRows(): Int = occupied.size
    }

    /** Thứ tự các danh mục app hiển thị trên trang "ứng dụng" - danh mục nào không có app nào
     *  thuộc về thì tự động bỏ qua, không hiện tiêu đề rỗng. "Khác" luôn đứng cuối cùng, gom
     *  mọi app không khớp bất kỳ quy tắc nhận diện nào bên dưới. "Điện thoại" KHÔNG còn nằm ở
     *  đây - nó là 1 danh mục CỐ ĐỊNH riêng (xem [buildPhoneCategoryRows]), luôn vẽ đầu tiên. */
    private val categoryOrder = listOf(
        "Mạng xã hội", "Trình duyệt", "Ứng dụng Google", "Ngân hàng", "Mua sắm", "Office", "Khác"
    )

    /** "Điện thoại" KHÔNG còn dò theo app thật đã cài nữa (nhiều máy không có app Gọi
     *  điện/Camera/Ghi âm... riêng biệt trong danh sách app - là app hệ thống ẩn, hoặc tên gói
     *  khác OEM mỗi máy - khiến mục này gần như luôn trống). Thay vào đó danh mục này giờ là 11
     *  MỤC CỐ ĐỊNH luôn hiện đủ (xem [buildPhoneCategoryRows]), 4 mục trỏ THẲNG vào tính năng
     *  CÓ SẴN trong app (Lịch/Máy tính/Đồng hồ/Quản lý tệp), còn lại mở app HỆ THỐNG THẬT tương
     *  ứng của máy qua Intent chuẩn (Gọi điện/Nhắn tin/Danh bạ/Camera/Thư viện/Ghi âm/Ghi chú). */
    /** Mua sắm: package name của các app TMĐT phổ biến ở Việt Nam */
    private val shoppingPackages = setOf(
        "com.shopee.vn", "com.lazada.android", "vn.sendo.app",
        "vn.tiki.app.tikiapp", "com.tiki.store",
        "com.vindemia.sendo", "air.com.sendbuyapp",
        "com.coupang.mobile", "vn.fptshop.fptshop",
        "com.thegioididong.mshop", "com.dienmayxanh.app",
        "vn.bachhoaxanh.app", "com.hasaki.app",
        "com.fahasa.ebook", "vn.momo.partner"
    )
    private val shoppingKeywords = listOf(
        "shopee", "lazada", "tiki", "sendo", "sen đỏ", "mua sắm", "shop",
        "cửa hàng", "thegioididong", "dienmayxanh", "bachhoaxanh", "hasaki",
        "fahasa", "fptshop", "coupang"
    )
    private val socialPackages = setOf(
        "com.google.android.youtube", "com.zing.zalo",
        "com.facebook.katana", "com.facebook.lite",
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill"
    )
    private val browserPackagesExplicit = setOf(
        "com.google.android.googlequicksearchbox", "com.android.chrome"
    )
    private val googlePackages = setOf(
        "com.google.android.gm", "com.android.vending",
        "com.google.android.apps.docs", "com.google.android.apps.maps",
        "com.google.android.apps.photos"
    )
    /** "Ngân hàng"/"Office" KHÔNG có danh sách app cố định trước (người dùng không liệt kê tên
     *  cụ thể) - nhận diện bằng CHỮ KHOÁ trong tên hiển thị (label), không phân biệt hoa/thường. */
    private val bankKeywords = listOf(
        "bank", "ngân hàng", "momo", "zalopay", "viettelpay", "shopeepay"
    )
    private val officeKeywords = listOf(
        "word", "excel", "powerpoint", "office", "onedrive"
    )

    /** Xác định danh mục hiển thị của 1 app - so khớp package name trước (chắc chắn nhất), rồi
     *  mới tới chữ khoá trong tên hiển thị, cuối cùng còn lại rơi vào "Khác":
     *   1) "Điện thoại": các app hệ thống cốt lõi (Cuộc gọi, Tin nhắn, Danh bạ, Camera, Thư
     *      viện, Ghi âm, Máy phát nhạc, Máy tính, Đồng hồ, Lịch, Ghi chú...).
     *   2) "Mạng xã hội": YouTube, Zalo, Facebook, TikTok.
     *   3) "Trình duyệt": Google (app tìm kiếm), Chrome - CỘNG THÊM bất kỳ app nào khác có đăng
     *      ký xử lý link web (http://), để các trình duyệt khác (Cốc Cốc, Firefox, Samsung
     *      Internet...) không có tên cụ thể vẫn được xếp đúng nhóm.
     *   4) "Ứng dụng Google": Mail (Gmail), CH Play (Play Store), Drive, Maps, Thư viện Google
     *      (Google Photos) - CỘNG THÊM mọi app khác thuộc hãng Google (package "com.google.")
     *      chưa khớp nhóm nào ở trên.
     *   5) "Ngân hàng"/6) "Office": nhận diện qua chữ khoá trong tên hiển thị (xem
     *      [bankKeywords]/[officeKeywords]) vì không có danh sách app cụ thể.
     *   7) "Khác": mọi app còn lại không khớp quy tắc nào ở trên. */
    private fun appCategoryLabel(info: ResolveInfo, browserPackages: Set<String>): String {
        val pkgName = info.activityInfo.packageName
        val label = info.loadLabel(context.packageManager).toString().lowercase()

        if (pkgName in socialPackages) return "Mạng xã hội"
        if (pkgName in browserPackagesExplicit || pkgName in browserPackages) return "Trình duyệt"
        if (pkgName in googlePackages || pkgName.startsWith("com.google.")) return "Ứng dụng Google"
        if (bankKeywords.any { label.contains(it) }) return "Ngân hàng"
        if (pkgName in shoppingPackages || shoppingKeywords.any { label.contains(it) }) return "Mua sắm"
        if (officeKeywords.any { label.contains(it) }) return "Office"
        return "Khác"
    }

    /** Dựng TRANG "ứng dụng" (trang phải - vuốt sang mới thấy): DANH SÁCH TOÀN BỘ app đã cài,
     *  PHÂN NHÓM THEO DANH MỤC (Trình duyệt, Mạng xã hội, Game...) thay vì A-Z như trước - dễ
     *  tìm app theo loại hơn. Trong mỗi nhóm, app vẫn xếp A-Z (kế thừa thứ tự đã sắp sẵn từ
     *  [installedApps]). Mỗi dòng hỗ trợ NHẤN GIỮ để hiện menu ghim/bỏ ghim vào "start". */
    private fun buildAppListPage(): View {
        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Chừa lề trái nhỉnh hơn lề phải 1 chút - trên màn hình rộng (máy tính bảng/ngang)
            // toàn bộ nội dung bị dồn sát mép trái trông rất lệch, nhích nhẹ sang phải cho cân
            // mắt hơn mà không phá bố cục danh sách 1 cột hiện có.
            setPadding(dp(32), dp(40), dp(16), dp(24) + dp(WpNavBar.HEIGHT_DP))
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(sectionHeader("DS Ứng Dụng", smallHeader = true))

        val pm = context.packageManager
        // Nhận diện trình duyệt: app nào ĐĂNG KÝ xử lý được link web (http://) coi như trình
        // duyệt - resolveActivity/queryIntentActivities là cách chuẩn của Android để hỏi "ai
        // xử lý được Intent này", không có API "isBrowser()" trực tiếp.
        val browserPackages: Set<String> = try {
            pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("http://")), 0)
                .map { it.activityInfo.packageName }
                .toSet()
        } catch (e: Exception) { emptySet() }

        val apps = installedApps()
        // Gom app theo danh mục, giữ nguyên thứ tự A-Z đã sắp sẵn bên trong từng nhóm
        // (LinkedHashMap giữ thứ tự thêm vào đầu tiên của từng key).
        val grouped = LinkedHashMap<String, MutableList<ResolveInfo>>()
        apps.forEach { info ->
            val label = appCategoryLabel(info, browserPackages)
            grouped.getOrPut(label) { mutableListOf() }.add(info)
        }

        // Dùng chung 1 closure dựng dòng app (tránh lặp lại onClick/onLongPress) cho cả nhóm
        // "★ ĐÃ ĐÁNH DẤU SAO" lẫn các nhóm danh mục thường bên dưới.
        fun addAppRow(info: ResolveInfo) {
            val label = info.loadLabel(pm).toString()
            val icon = info.loadIcon(pm)
            val pkgName = info.activityInfo.packageName
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

        // ── Nhóm "★ ĐÃ ĐÁNH DẤU SAO" - LUÔN Ở ĐẦU trang (trước mọi danh mục khác), chỉ hiện khi
        // có ít nhất 1 app đã đánh dấu (xem StarredAppsStore.kt) - nhấn giữ 1 app rồi chọn "Đánh
        // dấu sao" ở menu bật lên (showPinContextMenu) để thêm vào đây. App đánh dấu sao VẪN
        // hiện nguyên ở danh mục gốc của nó bên dưới - nhóm này chỉ là lối tắt nổi lên trên,
        // không di chuyển/xoá app khỏi danh mục thật của nó.
        val starredPkgs = StarredAppsStore.getAll(context).toSet()
        if (starredPkgs.isNotEmpty()) {
            val starredApps = apps.filter { it.activityInfo.packageName in starredPkgs }
            if (starredApps.isNotEmpty()) {
                content.addView(groupHeaderStarred("ĐÃ ĐÁNH DẤU SAO"))
                starredApps.forEach { addAppRow(it) }
            }
        }

        content.addView(groupHeader("Điện thoại"))
        buildPhoneCategoryRows().forEach { content.addView(it) }

        categoryOrder.forEach { category ->
            val appsInCategory = grouped[category] ?: return@forEach
            content.addView(groupHeader(category))
            appsInCategory.forEach { addAppRow(it) }
        }

        scrollView.addView(content)
        return scrollView
    }

    /** 11 MỤC CỐ ĐỊNH của danh mục "Điện thoại" - LUÔN hiện đủ, không phụ thuộc máy đã cài app
     *  gì (khác hẳn các danh mục khác vốn dò theo app thật đã cài, xem [appCategoryLabel]).
     *   - 4 mục ĐÃ CÓ SẴN trong app (Lịch/Máy tính/Đồng hồ/Quản lý tệp): mở thẳng qua
     *     [onOpenShortcut] - TÁI SỬ DỤNG đúng [ShortcutItem] đã định nghĩa cho tile Start
     *     ("calendar"/"calculator"/"clock"/"files" trong [ShortcutsRepository]), không viết
     *     riêng logic mở khác đi.
     *   - 7 mục còn lại (Gọi điện/Nhắn tin/Danh bạ/Camera/Thư viện/Ghi âm/Ghi chú): app chưa tự
     *     làm màn hình riêng cho các chức năng này - mở THẲNG app HỆ THỐNG THẬT của máy qua
     *     Intent chuẩn Android (dial/contacts/camera/gallery/ghi âm...), để người dùng vẫn dùng
     *     được chức năng thật ngay cả khi trong app chưa có phiên bản "giả lập" riêng. Nếu máy
     *     không có app nào xử lý (rất hiếm) thì báo lỗi bằng Toast thay vì crash. */
    private fun buildPhoneCategoryRows(): List<View> {
        fun launchSystem(intent: Intent, notFoundMsg: String) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, notFoundMsg, Toast.LENGTH_SHORT).show()
            }
        }
        fun row(label: String, iconRes: Int, onClick: () -> Unit): View =
            buildAppListRow(label, context.getDrawable(iconRes)!!, onClick = onClick, onLongPress = {})

        return listOf(
            row("Gọi điện", R.drawable.ic_shortcut_phonecall) {
                launchSystem(Intent(Intent.ACTION_DIAL), "Không tìm thấy ứng dụng Gọi điện")
            },
            row("Nhắn tin", R.drawable.ic_shortcut_messaging) {
                launchSystem(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
                    "Không tìm thấy ứng dụng Nhắn tin"
                )
            },
            row("Danh bạ", R.drawable.ic_shortcut_contacts) {
                launchSystem(
                    Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI),
                    "Không tìm thấy ứng dụng Danh bạ"
                )
            },
            row("Camera", R.drawable.ic_shortcut_camera) {
                launchSystem(
                    Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                    "Không tìm thấy ứng dụng Camera"
                )
            },
            row("Thư viện", R.drawable.ic_shortcut_gallery) {
                launchSystem(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
                    "Không tìm thấy ứng dụng Thư viện ảnh"
                )
            },
            row("Ghi âm", R.drawable.ic_shortcut_recorder) {
                launchSystem(Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION), "Không tìm thấy ứng dụng Ghi âm")
            },
            row("Ghi chú", R.drawable.ic_shortcut_notes) {
                // Không có Intent chuẩn nào của Android cho "mở app ghi chú" (khác Lịch/Máy
                // tính... vốn có CATEGORY_APP_*) - thử LẦN LƯỢT các app ghi chú phổ biến nhất
                // đã cài (Keep/Samsung Notes/MIUI Notes), dùng app ĐẦU TIÊN tìm thấy.
                val candidates = listOf(
                    "com.google.android.keep", "com.samsung.android.app.notes",
                    "com.miui.notes", "com.samsung.android.memo"
                )
                val launch = candidates.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                } else {
                    Toast.makeText(context, "Chưa cài ứng dụng Ghi chú nào", Toast.LENGTH_SHORT).show()
                }
            },
            row("Lịch", R.drawable.ic_shortcut_calendar) { onOpenShortcut(ShortcutsRepository.ALL.getValue("calendar")) },
            row("Đồng hồ", R.drawable.ic_shortcut_clock) { onOpenShortcut(ShortcutsRepository.ALL.getValue("clock")) },
            row("Máy tính", R.drawable.ic_shortcut_calculator) { onOpenShortcut(ShortcutsRepository.ALL.getValue("calculator")) },
            row("Quản lý tập tin", R.drawable.ic_shortcut_files) { onOpenShortcut(ShortcutsRepository.ALL.getValue("files")) }
        )
    }

    /** Nhấn giữ tile → hiện menu "Đổi vị trí" / "Đổi kích cỡ" / "Bỏ ghim". */
    private fun startTileDrag(
        tile: View, id: String, isFixed: Boolean,
        fixedKeys: MutableList<String>, userKeys: MutableList<String>,
        gridContainer: FrameLayout, cellPitchPx: Int
    ) {
        // "var...= null" (KHÔNG dùng lateinit) - vì removeBadge (dựng NGAY dưới đây) cần đọc
        // popup TỪ BÊN TRONG 1 lambda cục bộ trước khi popup được gán giá trị thật (gán ở cuối
        // hàm) - lateinit + "::popup.isInitialized" KHÔNG dùng được cho BIẾN CỤC BỘ (chỉ dùng
        // được cho property member của class/object; dùng cho biến cục bộ là lỗi cú pháp
        // "References to variables and parameters are unsupported", từng làm build thất bại).
        // Biến nullable + "?." là cách chuẩn, an toàn cho trường hợp này.
        var popup: PopupWindow? = null
        // Dấu ✕ góc trên-trái CHỈ hiện với tile KHÔNG cố định (app người dùng tự ghim) - tile cố
        // định (YouTube, Cài đặt...) không có khái niệm "loại bỏ khỏi start", giống hành vi cũ
        // của mục "Bỏ ghim khỏi start" trong menu (cũng chỉ hiện khi !isFixed).
        val removeBadge: View? = if (!isFixed && tile is FrameLayout) {
            addRemoveBadge(tile) {
                PinnedAppsStore.unpin(context, id)
                popup?.dismiss()
                refreshPages()
            }
        } else null
        fun item(label: String, onTap: () -> Unit): TextView = TextView(context).apply {
            text = label; textSize = 16f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(dp(22), dp(16), dp(22), dp(16)); minWidth = dp(200)
            isClickable = true; isFocusable = true; background = pressedOverlay()
            setOnClickListener { popup?.dismiss(); onTap() }
        }
        fun div() = View(context).apply {
            setBackgroundColor(0xFF3A3A3A.toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        }
        val menuBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(0xFF1A1A1A.toInt()); setStroke(dp(1), 0xFF3A3A3A.toInt()) }
            addView(item("Đổi vị trí") { enterDragMode(tile, id, isFixed, fixedKeys, userKeys, gridContainer) })
            addView(div())
            addView(item("Đổi kích cỡ") {
                enterResizeMode(tile, gridContainer, cellPitchPx) { picked ->
                    if (isFixed) TileSizeStore.set(context, id, picked)
                    else TileSizeStore.setForPackage(context, id, picked)
                    refreshPages()
                }
            })
            if (!isFixed) { addView(div()); addView(item("Bỏ ghim khỏi start") { PinnedAppsStore.unpin(context, id); refreshPages() }) }
        }
        popup = PopupWindow(menuBox, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = 0f; animationStyle = 0; isOutsideTouchable = true
        }
        // Dấu ✕ chỉ tồn tại trong lúc menu này còn mở - đóng menu bằng BẤT KỲ đường nào (chọn 1
        // mục, chạm ra ngoài, hay tự bấm ✕) đều phải dọn nó đi, nếu không sẽ dính lại vĩnh viễn
        // trên tile (vd chọn "Đổi vị trí" rồi huỷ giữa chừng, không mục nào gọi refreshPages()
        // để tự dựng lại tile mới không có ✕).
        val builtPopup = popup!!
        builtPopup.setOnDismissListener { removeBadge?.let { (tile as? FrameLayout)?.removeView(it) } }
        builtPopup.showAsDropDown(tile, 0, dp(4))
    }

    /** Chế độ đổi vị trí: tile đang giữ mờ đi, chạm vào tile khác → SWAP, chạm ngoài → huỷ. */
    private fun enterDragMode(
        dragTile: View, dragId: String, isFixed: Boolean,
        fixedKeys: MutableList<String>, userKeys: MutableList<String>,
        gridContainer: FrameLayout
    ) {
        dragTile.alpha = 0.35f
        val overlay = FrameLayout(context).apply { setBackgroundColor(0x33000000); isClickable = true }
        val hint = TextView(context).apply {
            text = "Chạm vào tile muốn đổi chỗ"
            textSize = 14f; setTextColor(Color.WHITE); gravity = android.view.Gravity.CENTER
            setBackgroundColor(0xBB000000.toInt()); setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        overlay.addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.gravity = android.view.Gravity.CENTER })

        fun cleanup() { dragTile.alpha = 1f; gridContainer.removeView(overlay) }

        val count = gridContainer.childCount
        for (i in 0 until count) {
            val child = gridContainer.getChildAt(i)
            if (child === dragTile || child === overlay) continue
            val targetId = (child.tag as? String) ?: continue
            child.setOnClickListener {
                // SWAP trong danh sách
                val fa = fixedKeys.indexOf(dragId); val fb = fixedKeys.indexOf(targetId)
                val ua = userKeys.indexOf(dragId);  val ub = userKeys.indexOf(targetId)
                when {
                    fa >= 0 && fb >= 0 -> { fixedKeys[fa] = targetId; fixedKeys[fb] = dragId }
                    ua >= 0 && ub >= 0 -> { userKeys[ua] = targetId; userKeys[ub] = dragId }
                    fa >= 0 && ub >= 0 -> { fixedKeys[fa] = targetId; userKeys[ub] = dragId }
                    ua >= 0 && fb >= 0 -> { userKeys[ua] = targetId; fixedKeys[fb] = dragId }
                }
                PinnedOrderStore.saveFixedOrder(context, fixedKeys)
                PinnedOrderStore.saveUserOrder(context, userKeys)
                cleanup(); refreshPages()
            }
        }
        overlay.setOnClickListener { cleanup() }
        gridContainer.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

        /** Menu bật lên khi NHẤN GIỮ 1 app (trong danh sách "ứng dụng" hoặc chính tile đã ghim trên
     *  "start") - 3 dòng: "Ghim/Bỏ ghim vào start", "Thêm/Bỏ khỏi Điện thoại" và "Đánh dấu/Bỏ
     *  đánh dấu sao", tự đổi nhãn tuỳ trạng thái hiện tại, rồi dựng lại 2 trang ngay để tile/nhóm
     *  "★" mới hiện/mất tức thì. "Ghim vào start" và "Thêm vào Điện thoại" là 2 hành động ĐỘC
     *  LẬP HOÀN TOÀN (xem [PinnedAppsStore] và [DesktopAppsStore]) - chọn 1 trong 2 không tự
     *  động thêm vào nơi còn lại, đúng ý phân biệt rạch ròi 2 trang.
     *
     *  DỰNG THỦ CÔNG bằng [PopupWindow] thay vì [android.widget.PopupMenu] mặc định của Android:
     *  PopupMenu hệ thống luôn tự vẽ nền TRẮNG BO GÓC + ĐỔ BÓNG (Material Card) bất kể theme app
     *  đặt gì - hiện lên giữa 1 màn hình đen phẳng tuyệt đối sẽ rất chỏi, sai hẳn cảm giác context
     *  menu phẳng, không bóng, nền đen của WP/Windows 10 Mobile thật. */
    private fun showPinContextMenu(
        anchor: View,
        pkgName: String,
        gridContainer: FrameLayout? = null,
        cellPitchPx: Int = 0
    ) {
        val pinned = PinnedAppsStore.isPinned(context, pkgName)
        val onDesktop = DesktopAppsStore.isAdded(context, pkgName)
        val starred = StarredAppsStore.isStarred(context, pkgName)

        lateinit var popup: PopupWindow
        fun menuItem(label: String, onTap: () -> Unit): TextView = TextView(context).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(dp(22), dp(16), dp(22), dp(16))
            minWidth = dp(200)
            isClickable = true
            isFocusable = true
            background = pressedOverlay()
            setOnClickListener {
                onTap()
                refreshPages()
                popup.dismiss()
            }
        }
        val itemPin = if (gridContainer == null) {
            // Trang "DS Ứng Dụng": CHỈ CHO PHÉP THÊM vào Start, KHÔNG cho bỏ ghim từ đây (theo
            // yêu cầu - xoá khỏi Start chỉ làm được TỪ CHÍNH trang Start: nhấn giữ tile rồi bấm
            // "Bỏ ghim khỏi start" hoặc dấu ✕, xem nhánh gridContainer != null bên dưới). App
            // ĐÃ ghim rồi thì ẩn hẳn dòng này (không còn gì để "thêm" nữa, và không cho bỏ).
            if (pinned) null else menuItem("Ghim vào start") { PinnedAppsStore.pin(context, pkgName) }
        } else {
            menuItem(if (pinned) "Bỏ ghim khỏi start" else "Ghim vào start") {
                if (pinned) PinnedAppsStore.unpin(context, pkgName) else PinnedAppsStore.pin(context, pkgName)
            }
        }
        val itemDesktop = if (gridContainer == null) {
            // Cùng lý do như itemPin ở trên - trang "DS Ứng Dụng" chỉ cho THÊM vào Điện thoại.
            if (onDesktop) null else menuItem("Thêm vào Điện thoại") { DesktopAppsStore.add(context, pkgName) }
        } else {
            menuItem(if (onDesktop) "Bỏ khỏi Điện thoại" else "Thêm vào Điện thoại") {
                if (onDesktop) DesktopAppsStore.remove(context, pkgName) else DesktopAppsStore.add(context, pkgName)
            }
        }
        val itemStar = menuItem(if (starred) "Bỏ đánh dấu sao" else "Đánh dấu sao") {
            if (starred) StarredAppsStore.unstar(context, pkgName) else StarredAppsStore.star(context, pkgName)
        }
        // "Đổi kích cỡ" KHÔNG dùng menuItem() (dismiss+refresh ngay khi tap) như 3 dòng trên -
        // tap vào phải ĐÓNG popup này rồi vào NGAY chế độ "đổi cỡ bằng cách kéo" (hiện viền +
        // tay cầm trên chính tile [anchor]) - xem [enterResizeMode]. Lưu + dựng lại lưới chỉ
        // xảy ra SAU KHI người dùng thả tay cầm.
        // "Đổi kích cỡ" CHỈ áp dụng khi menu này mở từ 1 tile TRONG LƯỚI Start (có gridContainer
        // thật) - trang "DS Ứng Dụng" (danh sách phẳng, không phải lưới tile) gọi hàm này KHÔNG
        // truyền gridContainer nên mục này sẽ tự ẩn, tránh crash vì không có lưới để đổi cỡ.
        val itemResize = if (gridContainer != null) TextView(context).apply {
            text = "Đổi kích cỡ"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(dp(22), dp(16), dp(22), dp(16))
            minWidth = dp(200)
            isClickable = true
            isFocusable = true
            background = pressedOverlay()
            setOnClickListener {
                popup.dismiss()
                enterResizeMode(anchor, gridContainer, cellPitchPx) { picked ->
                    TileSizeStore.setForPackage(context, pkgName, picked)
                    refreshPages()
                }
            }
        } else null
        fun divider() = View(context).apply {
            setBackgroundColor(0xFF3A3A3A.toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        }
        // "Gỡ cài đặt" - CHỈ hiện khi menu này mở từ trang "DS Ứng Dụng" (gridContainer == null,
        // xem giải thích ở "Đổi kích cỡ" ngay trên) - đúng yêu cầu "riêng trang ứng dụng phải có
        // ô gỡ cài đặt". GỠ THẬT SỰ khỏi máy (không phải chỉ bỏ ghim/xoá khỏi 1 trang trong app
        // này) - dùng Intent.ACTION_DELETE hệ thống để Android tự hiện đúng hộp thoại xác nhận
        // gỡ cài đặt CHUẨN của hệ điều hành (tên app, icon, cảnh báo...) - KHÔNG tự gỡ ngầm được
        // (và không nên - Android bắt buộc người dùng phải tự xác nhận gỡ app để đảm bảo an
        // toàn, app khác không được phép tự ý gỡ app khác kể cả khi người dùng đồng ý trong app
        // này). Không tự refreshPages() ngay - hộp thoại gỡ là 1 màn hình HỆ THỐNG riêng, không
        // biết ngay lúc này người dùng đã bấm "Gỡ" xong hay chưa; sẽ tự refresh lại khi quay về
        // app (xem MainActivity.onResume() gọi refreshPages()).
        val itemUninstall = if (gridContainer == null && pkgName != context.packageName) TextView(context).apply {
            text = "Gỡ cài đặt"
            textSize = 16f
            setTextColor(0xFFE81123.toInt()) // đỏ - hành động PHÁ HUỶ, khác các mục còn lại (trắng)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(dp(22), dp(16), dp(22), dp(16))
            minWidth = dp(200)
            isClickable = true
            isFocusable = true
            background = pressedOverlay()
            setOnClickListener {
                popup.dismiss()
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkgName"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) { }
            }
        } else null
        val menuBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // Nền đen tuyệt đối phẳng, viền mảnh màu xám đậm thay vì bóng đổ - đúng cảm giác
            // context menu WP thật nổi trên nền tối, phân biệt bằng đường viền chứ không bằng
            // elevation/shadow (Metro là thiết kế phẳng tuyệt đối).
            background = GradientDrawable().apply {
                setColor(0xFF1A1A1A.toInt())
                setStroke(dp(1), 0xFF3A3A3A.toInt())
            }
            // itemPin/itemDesktop có thể là null (trang "DS Ứng Dụng", app đã ghim/đã thêm rồi
            // - xem 2 nhánh if ở trên) - dùng listOfNotNull() + chèn divider() MỚI (view riêng
            // mỗi lần, không tái dùng) giữa các dòng THỰC SỰ có mặt, tránh 2 divider dính liền
            // nhau hoặc divider ở đầu khi dòng đầu tiên bị ẩn.
            val rows = listOfNotNull(itemPin, itemDesktop, itemStar, itemResize, itemUninstall)
            rows.forEachIndexed { i, row ->
                if (i > 0) addView(divider())
                addView(row)
            }
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

    /** Trạng thái đang ở "chế độ đổi cỡ" của 1 tile - lưu CẢ 2 tay cầm (cạnh dưới + cạnh phải)
     *  + nền foreground GỐC (để khôi phục khi thoát chế độ) - gắn vào [View.setTag] của chính
     *  tile đó. */
    private data class ResizeState(val handleBottom: View, val handleRight: View, val originalForeground: Drawable?)

    /** NHẤN GIỮ 1 tile để BẬT "chế độ đổi cỡ": hiện viền trắng bao quanh tile + 2 TAY CẦM RIÊNG
     *  BIỆT - 1 ở GIỮA CẠNH DƯỚI (kéo lên/xuống để đổi CHIỀU CAO) và 1 ở GIỮA CẠNH PHẢI (kéo
     *  trái/phải để đổi CHIỀU RỘNG) - THAY VÌ 1 tay cầm duy nhất ở góc dưới-phải điều khiển CẢ
     *  HAI chiều cùng lúc như trước, để người dùng chỉnh riêng từng chiều chính xác hơn, không
     *  bị đổi nhầm chiều kia khi chỉ muốn sửa 1 chiều. Thả tay cầm nào -> tự CHỐT chiều đó về cỡ
     *  HỢP LỆ gần nhất trong 4 cỡ [TileSize] (lưới hiện tại chỉ hỗ trợ đúng 4 cỡ rời rạc
     *  Nhỏ/Rộng/Cao/To, CHƯA phải tự do hoàn toàn theo từng pixel - kéo giữa chừng sẽ tự nhảy về
     *  cỡ gần nhất khi thả tay), rồi lưu qua [onCommit] (tự lưu + refreshPages() ở nơi gọi).
     *  NHẤN GIỮ LẦN NỮA trong lúc đang ở chế độ đổi cỡ -> HUỶ, không lưu gì, khôi phục nguyên
     *  trạng. */
    private fun enterResizeMode(
        tileView: View, gridContainer: FrameLayout, cellPitchPx: Int, onCommit: (TileSize) -> Unit
    ) {
        val existing = tileView.tag as? ResizeState
        if (existing != null) {
            // Đang ở chế độ đổi cỡ rồi -> nhấn giữ lần nữa để HUỶ, gỡ viền + 2 tay cầm, không lưu.
            gridContainer.removeView(existing.handleBottom)
            gridContainer.removeView(existing.handleRight)
            tileView.foreground = existing.originalForeground
            tileView.tag = null
            return
        }

        val originalForeground = tileView.foreground
        tileView.foreground = GradientDrawable().apply {
            setStroke(dp(3), Color.WHITE)
            setColor(Color.TRANSPARENT)
        }

        val handleSizePx = dp(28)
        fun makeHandle(): View = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
        }
        val handleBottom = makeHandle() // giữa cạnh dưới - chỉ đổi CHIỀU CAO
        val handleRight = makeHandle()  // giữa cạnh phải - chỉ đổi CHIỀU RỘNG

        fun tileLp() = tileView.layoutParams as FrameLayout.LayoutParams
        // 2 tay cầm luôn "dính" đúng giữa cạnh dưới / giữa cạnh phải của tile - gọi lại mỗi khi
        // tile đổi kích thước trong lúc kéo, để tay cầm di chuyển theo cùng ngón tay chứ không
        // đứng yên 1 chỗ.
        fun positionHandles() {
            val lp = tileLp()
            handleBottom.layoutParams = FrameLayout.LayoutParams(handleSizePx, handleSizePx).also {
                it.leftMargin = lp.leftMargin + lp.width / 2 - handleSizePx / 2
                it.topMargin = lp.topMargin + lp.height - handleSizePx / 2
            }
            handleRight.layoutParams = FrameLayout.LayoutParams(handleSizePx, handleSizePx).also {
                it.leftMargin = lp.leftMargin + lp.width - handleSizePx / 2
                it.topMargin = lp.topMargin + lp.height / 2 - handleSizePx / 2
            }
        }
        gridContainer.addView(handleBottom)
        gridContainer.addView(handleRight)
        positionHandles()
        tileView.tag = ResizeState(handleBottom, handleRight, originalForeground)

        val minPx = cellPitchPx - dp(4)
        val maxPx = cellPitchPx * 2 - dp(4)
        val threshold = (cellPitchPx * 1.5f).toInt()

        // Chốt 1 chiều (rộng HOẶC cao) về cỡ hợp lệ gần nhất rồi báo [onCommit] - dùng chung cho
        // cả 2 tay cầm, mỗi tay cầm chỉ đổi ĐÚNG 1 chiều nên chiều còn lại giữ nguyên giá trị cỡ
        // đã chốt trước đó (lấy từ [TileSizeStore] gián tiếp qua kích thước layoutParams hiện tại).
        fun commit() {
            val lp = tileLp()
            val w = if (lp.width >= threshold) 2 else 1
            val h = if (lp.height >= threshold) 2 else 1
            val picked = TileSize.values().first { it.w == w && it.h == h }
            gridContainer.removeView(handleBottom)
            gridContainer.removeView(handleRight)
            tileView.foreground = originalForeground
            tileView.tag = null
            onCommit(picked)
        }

        var startTouchY = 0f
        var startHeightPx = 0
        handleBottom.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startTouchY = event.rawY
                    startHeightPx = tileLp().height
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dy = (event.rawY - startTouchY).toInt()
                    val lp = tileLp()
                    lp.height = (startHeightPx + dy).coerceIn(minPx, maxPx)
                    tileView.layoutParams = lp
                    positionHandles()
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    commit(); true
                }
                else -> false
            }
        }

        var startTouchX = 0f
        var startWidthPx = 0
        handleRight.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startTouchX = event.rawX
                    startWidthPx = tileLp().width
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startTouchX).toInt()
                    val lp = tileLp()
                    lp.width = (startWidthPx + dx).coerceIn(minPx, maxPx)
                    tileView.layoutParams = lp
                    positionHandles()
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    commit(); true
                }
                else -> false
            }
        }
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

    /** Tiêu đề lớn kiểu Pivot/Hub header của WP.
     *  [smallHeader]=true: dùng cho trang "DS Ứng Dụng" (28sp) để không chiếm quá nhiều không gian.
     *  [smallHeader]=false (mặc định): cỡ 46sp đặc trưng Hub header cho trang "start".
     *  CHỮ CÁI ĐẦU luôn IN HOA (vd "start" -> "Start") - phần còn lại vẫn chữ thường. */
    private fun sectionHeader(text: String, smallHeader: Boolean = false): View = TextView(context).apply {
        this.text = text.replaceFirstChar { it.titlecase() }
        textSize = if (smallHeader) 28f else 46f
        setTextColor(Color.WHITE)
        // "sans-serif-medium" (đậm hơn 1 nấc so với "sans-serif-light" trước đây) - chỉ áp
        // riêng cho 2 tiêu đề "start"/"DS Ứng Dụng" theo yêu cầu, các chữ khác trong app (menu,
        // nhãn tile...) vẫn giữ nguyên "sans-serif-light" mảnh như cũ.
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(2), dp(8), dp(2), dp(10))
    }

    /** Tiêu đề phân nhóm trong danh sách app (danh mục: "Trình duyệt", "Game"...), kiểu WP App
     *  List: chữ to, màu accent, đứng riêng 1 dòng làm mốc phân cách trực quan giữa các nhóm.
     *  CHỮ CÁI ĐẦU luôn IN HOA (đa số danh mục đã viết hoa sẵn nên phần lớn không đổi gì). */
    private fun groupHeader(text: String): View = TextView(context).apply {
        this.text = text.replaceFirstChar { it.titlecase() }
        textSize = 22f
        setTextColor(ThemePrefs.accent(context))
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        setPadding(dp(4), dp(14), dp(4), dp(4))
    }

    /** Giống [groupHeader] nhưng có thêm ICON DẤU SAO (★) đứng trước chữ - dùng riêng cho nhóm
     *  "ĐÃ ĐÁNH DẤU SAO" ở đầu trang "ứng dụng", để phân biệt trực quan ngay lập tức với các
     *  nhóm danh mục thường (Trình duyệt, Game...) vốn chỉ có chữ. */
    private fun groupHeaderStarred(text: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(14), dp(4), dp(4))
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_wp_star_filled)
            setColorFilter(ThemePrefs.accent(context))
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).also { it.marginEnd = dp(8) }
        })
        addView(TextView(context).apply {
            this.text = text
            textSize = 22f
            setTextColor(ThemePrefs.accent(context))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
    }

    /** 1 ô "Live Tile" vuông kiểu WP cho các mục CỐ ĐỊNH (dùng icon vector có sẵn trong app):
     *  nền màu accent phẳng, icon trắng đơn sắc góc trên-trái, nhãn ở góc dưới-trái. */
    /** Hiệu ứng "bóp nhẹ" (squish) khi CHẠM vào 1 Live Tile - co lại còn ~92% rồi bật về 100%
     *  ngay khi thả tay, kèm hiệu ứng sáng nhẹ [pressedOverlay] - đây là phản hồi chạm ĐẶC
     *  TRƯNG, dễ nhận ra nhất của Windows Phone/Windows 10 Mobile thật khi mở 1 app từ Start
     *  (khác hẳn ripple lan toả tròn của Material Design hay hiệu ứng mờ dần của iOS). Dùng
     *  animate() (scaleX/scaleY) thay vì StateListAnimator - hoạt động đồng nhất trên mọi
     *  phiên bản Android app hỗ trợ (kể cả bản cũ trước Lollipop không có StateListAnimator).
     *  onTouchListener CHỈ vẽ hiệu ứng, KHÔNG return true (không "nuốt" sự kiện chạm) để
     *  OnClickListener/OnLongClickListener của view vẫn hoạt động bình thường như trước. */
    private fun applyWpTilePressAnim(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
            }
            false
        }
    }

    /** Dấu "✕" nhỏ ở góc trên-trái 1 tile/icon (kiểu "chế độ chỉnh sửa" quen thuộc) - chạm vào
     *  để LOẠI BỎ app đó khỏi trang hiện tại (bỏ ghim khỏi start / bỏ khỏi Điện thoại), NHANH
     *  hơn hẳn so với phải mở menu rồi tìm đúng dòng chữ. Trả về chính view dấu ✕ để nơi gọi có
     *  thể removeView() khi cần huỷ (nhấn ra ngoài, hoặc đã xong việc khác trong menu).
     *  Đặt margin DƯƠNG (không âm) để dấu ✕ nằm gọn TRONG viền tile, tránh bị viền/ScrollView
     *  cha cắt mất 1 phần nếu dùng margin âm (tràn ra ngoài bounds của tile). */
    private fun addRemoveBadge(tile: FrameLayout, onRemove: () -> Unit): View {
        val badge = TextView(context).apply {
            text = "✕"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFE81123.toInt()) // đỏ - màu "xoá/cảnh báo" chuẩn trong bảng màu Windows
            }
            layoutParams = FrameLayout.LayoutParams(dp(22), dp(22)).also {
                it.gravity = Gravity.TOP or Gravity.START
                it.leftMargin = dp(4); it.topMargin = dp(4)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onRemove() }
        }
        tile.addView(badge)
        return badge
    }

    private fun buildLiveTile(label: String, iconRes: Int, tileColor: Int, onClick: () -> Unit): View {
        val tile = FrameLayout(context).apply {
            // Nền TRONG SUỐT - thấy xuyên qua wallpaper
            // Viền trắng 1.5dp bao quanh tile
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0x00000000)
                setStroke(dp(1), 0xFFFFFFFF.toInt())
            }
            isClickable = true
            isFocusable = true
            foreground = pressedOverlay()
        }
        applyWpTilePressAnim(tile)

        // Ô nền màu BÊN TRONG chứa icon - hình vuông nhỏ căn góc trên-trái
        val iconBg = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(tileColor)
                cornerRadius = dp(4).toFloat()
            }
            layoutParams = FrameLayout.LayoutParams(dp(67), dp(67)).also {
                it.gravity = Gravity.TOP or Gravity.START
                it.leftMargin = dp(10); it.topMargin = dp(10)
            }
        }
        val icon = ImageView(context).apply {
            setImageResource(iconRes)
            val pad = dp(10)
            setPadding(pad, pad, pad, pad)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        iconBg.addView(icon, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val labelView = buildTileLabel(label)
        tile.addView(iconBg)
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
            // Nền TRONG SUỐT + viền trắng - giống buildLiveTile
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0x00000000)
                setStroke(dp(1), 0xFFFFFFFF.toInt())
            }
            isClickable = true
            isFocusable = true
            isLongClickable = true
            foreground = pressedOverlay()
        }
        applyWpTilePressAnim(tile)

        // Ô nền màu BÊN TRONG chứa icon app thật
        val iconBg = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(tileColor)
                cornerRadius = dp(4).toFloat()
            }
            layoutParams = FrameLayout.LayoutParams(dp(67), dp(67)).also {
                it.gravity = Gravity.TOP or Gravity.START
                it.leftMargin = dp(10); it.topMargin = dp(10)
            }
        }
        val iconView = ImageView(context).apply {
            setImageDrawable(icon)
            val pad = dp(8)
            setPadding(pad, pad, pad, pad)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        iconBg.addView(iconView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val labelView = buildTileLabel(label)
        tile.addView(iconBg)
        tile.addView(labelView)
        tile.setOnClickListener { onClick() }
        tile.setOnLongClickListener { anchor -> onLongPress(anchor); true }
        return tile
    }

    private fun buildTileLabel(label: String): TextView = TextView(context).apply {
        text = label
        textSize = 15f
        setTextColor(Color.WHITE)
        // Đổ bóng đậm để chữ đọc được trên nền trong suốt (thấy wallpaper)
        setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), 0xCC000000.toInt())
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also {
            it.gravity = Gravity.BOTTOM or Gravity.START
            it.leftMargin = dp(10); it.bottomMargin = dp(8); it.rightMargin = dp(10)
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
            setPadding(dp(4), dp(12), dp(4), dp(12))
            isClickable = true
            isFocusable = true
            isLongClickable = true
            foreground = pressedOverlay()
        }
        val icon = ImageView(context).apply {
            setImageDrawable(iconDrawable)
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).also { it.rightMargin = dp(20) }
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
