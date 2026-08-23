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
        "files" to ShortcutItem(
            "files", "Quản lý tệp", ShortcutType.ACTIVITY, "FilesActivity", R.drawable.ic_shortcut_files
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
    private val onOpenShortcut: (ShortcutItem) -> Unit,
    // Mở trình chọn ảnh TRONG APP để đổi hình nền RIÊNG của trang Start (KHÁC hẳn hình nền màn
    // hình chính THẬT của điện thoại - trước đây "Hình nền" ở menu nhấn giữ lỡ mở nhầm trình
    // chọn hình nền HỆ THỐNG (Intent.ACTION_SET_WALLPAPER), đổi xong không thấy gì khác trên
    // trang Start vì app không hề đọc hình nền hệ thống, chỉ đọc [WallpaperPrefs] riêng của
    // mình - khiến người dùng tưởng nhầm "nền trang Start giờ là nền màn hình chính [thật]".
    // Xem [MainActivity.pickCustomWallpaper]).
    private val onPickWallpaper: () -> Unit
) {
    /** Bảng màu Live Tile - dùng chung [ThemePrefs.PALETTE] (đúng 20 màu Accent/Live Tile gốc
     *  của Windows Phone) để đồng bộ với lưới chọn màu ở Cài đặt > Giao diện - xoay vòng cho
     *  từng ô ghim để mỗi tile 1 màu khác nhau, đúng cảm giác Start Screen thật. */
    private val tilePalette = ThemePrefs.PALETTE

    /** Giữ lại tham chiếu để [refreshPages] có thể dựng lại nội dung 2 trang ngay khi người
     *  dùng ghim/bỏ ghim 1 app, mà KHÔNG cần thoát vào lại trang chủ mới thấy cập nhật. */
    private var pageAdapterRef: PageAdapter? = null
    private var pagerRef: ViewPager2? = null

    /** Cuộn về trang "start" (trang 0) ngay lập tức - gọi khi bấm nút Home (Windows) hoặc Back
     *  về màn chính, để trang Start luôn là trang hiển thị mặc định. */
    fun goToStart() {
        pagerRef?.setCurrentItem(0, true)
    }

    /** Trả về true nếu đang đứng ở trang 1 "DS Ứng Dụng" - dùng để Back từ trang này
     *  về trang "start" thay vì đưa app xuống nền ngay. */
    fun isOnAppListPage(): Boolean = (pagerRef?.currentItem ?: 0) == 1

    /** Dựng nội dung 2 trang Pivot ("start" + "ứng dụng") trong 1 ViewPager2. */
    fun build(): FrameLayout {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // ── ViewPager2: 2 TRANG Pivot vuốt ngang - trang 0 "start", trang 1 "ứng dụng" ──
        val pages = mutableListOf(buildStartPage(), buildAppListPage())
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
        adapter.pages[0] = buildStartPage()
        adapter.pages[1] = buildAppListPage()
        adapter.notifyDataSetChanged()
        (adapter.pages[0] as? ScrollView)?.let { sv -> sv.post { sv.scrollTo(0, prevStartScrollY) } }
        (adapter.pages[1] as? ScrollView)?.let { sv -> sv.post { sv.scrollTo(0, prevAppListScrollY) } }
    }

    /** Dựng TRANG "start" (trang trái - hiện mặc định): lưới Live Tile 3 cột, gồm các ô cố
     *  định VÀ các app người dùng đã ghim thêm. */
    private fun buildStartPage(): View {
        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Đáy chừa thêm đúng chiều cao WpNavBar (xem WpNavBar.HEIGHT_DP) - thanh điều
            // hướng nổi là cửa sổ hệ thống riêng luôn đè lên trên cùng, nếu chỉ để dp(24) như
            // trước thì tile cuối cùng của lưới Live Tile bị nó che khuất mất 1 phần.
            setPadding(dp(20), dp(8), dp(20), dp(4) + dp(WpNavBar.HEIGHT_DP))
            overScrollMode = View.OVER_SCROLL_NEVER
            // BẮT BUỘC để content (con trực tiếp) DÃN RA ÍT NHẤT bằng đúng vùng nhìn thấy của
            // ScrollView khi nội dung ngắn hơn màn hình (mặc định KHÔNG bật, content chỉ cao
            // vừa khít số tile hiện có) - nếu không bật, khoảng trống bên dưới tile cuối cùng
            // nằm NGOÀI phạm vi của content, chạm vào đó sẽ không nhấn giữ được gì cả.
            isFillViewport = true
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // NHẤN GIỮ vào KHOẢNG TRỐNG của trang Start (không trúng tile nào - các tile đã tự
            // "ăn" sự kiện chạm của riêng nó nên không lọt tới đây) -> hiện menu kiểu màn hình
            // chính Android thật: Hình nền / Tiện ích / Cài đặt (xem [showStartLongPressMenu]).
            //
            // FIX: gắn listener này LÊN "content" (LinearLayout) - KHÔNG PHẢI lên "scrollView"
            // như bản trước (đó là lý do tính năng "chưa hoạt động" dù code trông đúng và biên
            // dịch không lỗi). ScrollView.onTouchEvent() tự viết lại HOÀN TOÀN cơ chế xử lý
            // chạm (để tự làm cuộn/fling/quán tính) THAY VÌ gọi View.onTouchEvent() mặc định -
            // mà chính View.onTouchEvent() mặc định mới là nơi Android phát hiện nhấn-giữ rồi
            // gọi tới OnLongClickListener. Gắn listener LÊN ScrollView chỉ bật cờ isLongClickable
            // (biên dịch được, KHÔNG báo lỗi gì) NHƯNG callback không bao giờ được gọi vì
            // ScrollView không bao giờ chạy tới đoạn code kiểm tra long-press đó. LinearLayout
            // (như "content" đây) KHÔNG override onTouchEvent() nên vẫn dùng đúng cơ chế phát
            // hiện nhấn-giữ chuẩn của View, hoạt động bình thường.
            setOnLongClickListener { anchor ->
                showStartLongPressMenu(anchor)
                true
            }
        }

        // (Tiêu đề "start" đã được gỡ theo yêu cầu.)

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
        val defaultUserKeys = PinnedAppsStore.getAll(context)
        val userKeys = PinnedOrderStore.getUserOrder(context, defaultUserKeys).toMutableList()
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

        // (Widget giờ/ngày lớn đã được gỡ khỏi trang Start theo yêu cầu.)

        // ── 4 nút CỐ ĐỊNH (YouTube/Ẩn danh/Nhiều T.khoản/Quản lý tệp) - GIAO DIỆN ĐƠN GIẢN
        // KIỂU ANDROID CHUẨN (xem [buildSimpleAppIcon]), TÁCH HẲN khỏi hệ thống lưới Live Tile
        // bên dưới: KHÔNG đổi kích cỡ, KHÔNG kéo-thả đổi vị trí, KHÔNG menu nhấn giữ, KHÔNG
        // chiếm nguyên 1 ô lưới rộng (vùng chạm chỉ đúng bằng icon+tên) - thứ tự và vị trí LUÔN
        // CỐ ĐỊNH y hệt 4 icon tắt (shortcut) trên màn hình chính Android thật, không còn dính
        // dáng "Live Tile Windows Phone" nữa. Xếp 2 CỘT x 2 HÀNG bằng LinearLayout lồng nhau đơn
        // giản (không cần GridPlacer/GridLayout phức tạp - chỉ 4 ô cố định, không cần thuật toán
        // chiếm-dụng-ô-linh-hoạt của lưới Live Tile bên dưới).
        val fixedKeys = listOf("youtube", "incognito", "accounts", "files")
        val fixedSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        fixedKeys.chunked(2).forEach { rowKeys ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            rowKeys.forEach { key ->
                ShortcutsRepository.ALL[key]?.let { item ->
                    val tileColor = if (key == "youtube") ThemePrefs.PALETTE[11]
                                    else tilePalette[colorIndex % tilePalette.size].also { colorIndex++ }
                    row.addView(buildSimpleAppIcon(item.label, item.iconRes, tileColor) { onOpenShortcut(item) })
                }
            }
            fixedSection.addView(row)
        }
        content.addView(fixedSection)

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
                        // fixedKeys rỗng: 4 nút cố định không còn tham gia lưới Live Tile nữa
                        // (xem [buildSimpleAppIcon]), nên không còn tile nào có tag khớp trong
                        // fixedKeys để hoán đổi vị trí cùng - tham số này chỉ còn ý nghĩa hình
                        // thức, giữ lại để khớp chữ ký [startTileDrag].
                        startTileDrag(anchor, pkgName, false, mutableListOf(), userKeys, gridContainer, cellPitchPx)
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

    /** 1 mục trong danh sách "ứng dụng" - ứng với 1 app THẬT đã cài trên máy (installedApps()).
     *  [pkgName] LUÔN khác null (chỉ còn app thật, không còn mục cố định giả nào trộn vào nữa -
     *  xem class doc [buildAppListPage]). */
    private data class AppEntry(
        val label: String, val icon: Drawable, val pkgName: String?,
        val onClick: () -> Unit, val onLongPress: ((View) -> Unit)?,
        val dedupPkg: String? = null
    )

    /** Dò xem thiết bị có app nào cài đè icon/tên khác cho CÙNG package hay không - KHÔNG dùng
     *  nữa (xem lịch sử) - giữ lại field [AppEntry.dedupPkg] chỉ để không phải sửa lại chỗ khai
     *  báo, LUÔN null vì danh sách giờ CHỈ gồm app thật, không còn mục ảo cần loại trùng. */

    /** Dựng TRANG "ứng dụng" (trang phải - vuốt sang mới thấy): TOÀN BỘ APP THẬT đã cài trên
     *  máy (installedApps()), xếp thành 1 DANH SÁCH DUY NHẤT, LIÊN TỤC, sắp A-Z theo tên hiển
     *  thị - KHÔNG còn 12 mục "cố định"/"ảo" (Gọi điện, Cuộc gọi, Nhắn tin, Danh bạ, Camera, Thư
     *  viện, Ghi âm, Ghi chú, Lịch, Đồng hồ, Máy tính, Quản lý tập tin) trộn vào nữa - trước đây
     *  có 2 lần đổi qua lại (khi thì CHỈ giữ app thật, khi thì CHỈ giữ 12 mục cố định) gây nhầm
     *  lẫn, giờ CHỐT hẳn: chỉ app thật, đơn giản và đúng với tên "DS Ứng Dụng" nhất - máy có gì
     *  cài sẵn thì hiện đúng cái đó, không có app "nửa vời" không tương ứng app cụ thể nào cả.
     *  KHÔNG còn tiêu đề phân nhóm nào (không danh mục, không chữ cái A/B/C...) - chỉ có ĐÚNG 1
     *  ngoại lệ: nhóm app đã "ghim lên đầu trang" hiện Ở ĐẦU trang, CHỈ hiện khi có ít nhất 1 app
     *  đã ghim (xem [StarredAppsStore]) - nhấn giữ 1 app rồi chọn "Ghim lên đầu trang" ở menu bật
     *  lên (showPinContextMenu) để thêm vào đó - KHÔNG còn tiêu đề chữ báo hiệu phía trên nhóm
     *  này nữa (trước đây là "★ ĐÃ ĐÁNH DẤU SAO"). App đã ghim vẫn hiện NGUYÊN VẸN ở đúng vị trí
     *  A-Z của nó trong danh sách chính bên dưới - nhóm đầu trang chỉ là 1 BẢN SAO/lối tắt nổi
     *  lên trên, không di chuyển hay xoá app khỏi danh sách chính. */
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
        val allEntries = installedApps().map { info ->
            val pkgName = info.activityInfo.packageName
            AppEntry(
                label = info.loadLabel(pm).toString(),
                icon = info.loadIcon(pm),
                pkgName = pkgName,
                onClick = {
                    val launch = pm.getLaunchIntentForPackage(pkgName)
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launch)
                    }
                },
                onLongPress = { anchor -> showPinContextMenu(anchor, pkgName) }
            )
        }.sortedBy { it.label.lowercase() }

        // Dùng chung 1 closure dựng dòng app (tránh lặp lại) cho cả nhóm "ghim lên đầu trang" lẫn
        // danh sách chính bên dưới.
        fun addRow(entry: AppEntry) {
            content.addView(buildAppListRow(
                entry.label, entry.icon,
                onClick = entry.onClick,
                onLongPress = entry.onLongPress ?: {}
            ))
        }

        // ── Nhóm "ghim lên đầu trang" - CHỈ hiện khi có ít nhất 1 app đã ghim (xem
        // [StarredAppsStore]) - nhấn giữ 1 app rồi chọn "Ghim lên đầu trang" ở menu bật lên
        // ([showPinContextMenu]) để thêm vào đây. ──
        val starredPkgs = StarredAppsStore.getAll(context)
        val starredEntries = allEntries.filter { it.pkgName in starredPkgs }
        if (starredEntries.isNotEmpty()) {
            starredEntries.forEach { addRow(it) }
            content.addView(View(context).apply { setBackgroundColor(0x22FFFFFF) }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            ).also { it.topMargin = dp(8); it.bottomMargin = dp(8) })
        }

        // ── Danh sách chính: 1 LIST DUY NHẤT, LIÊN TỤC, A-Z, KHÔNG tiêu đề phân nhóm nào. ──
        allEntries.forEach { addRow(it) }

        scrollView.addView(content)
        return scrollView
    }

    /** Trả về TOÀN BỘ app thật đã cài trên máy có icon hiện trên launcher (CATEGORY_LAUNCHER) -
     *  loại trừ chính app này. Dùng cho [buildAppListPage]. */
    private fun installedApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val pm = context.packageManager
        // App "Phone" (com.phone.launcher) là BẢN SAO "Choi" của CHÍNH app này (fork riêng chỉ
        // giữ trang Start, cùng icon 4-ô-vuông xanh) - cài chung máy để test nên hiện lẫn vào
        // đây như 1 app ngoài bình thường, gây rối vì trông y hệt icon Start của app hiện tại -
        // loại luôn ra, coi như "chính mình" giống [context.packageName].
        val selfClonePkg = "com.phone.launcher"
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName && it.activityInfo.packageName != selfClonePkg }
            // LOẠI TRÙNG theo packageName - 1 số app khai báo NHIỀU launcher activity trong CÙNG
            // 1 package (vd icon phụ/alias), khiến queryIntentActivities() trả về NHIỀU
            // ResolveInfo cho CÙNG 1 app thật, hiện lặp lại y hệt tên+icon trong danh sách "ứng
            // dụng" (vd "Camera" xuất hiện 2 lần giống hệt nhau) - chỉ giữ lại activity ĐẦU TIÊN
            // gặp cho mỗi package.
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }

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
        builtPopup.showSmartDropDown(tile)
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

        /** Menu bật lên khi NHẤN GIỮ vào KHOẢNG TRỐNG (không trúng tile nào) trên trang Start - 3
     *  dòng đúng kiểu màn hình chính Android thật: "Hình nền" / "Tiện ích" / "Cài đặt" - CẢ 3
     *  đều là LỐI TẮT MỞ THẲNG màn hình HỆ THỐNG tương ứng (không phải màn hình riêng của app
     *  này), dùng chung phong cách popup phẳng nền đen với [showPinContextMenu]. Android không
     *  có 1 Intent công khai, đảm bảo hoạt động trên MỌI máy/hãng để mở thẳng "trang chọn tiện
     *  ích" (khác "Hình nền"/"Cài đặt" đều có Intent chuẩn, ổn định) - nên "Tiện ích" thử
     *  [Settings.ACTION_HOME_SETTINGS] trước (màn "Ứng dụng màn hình chính", nhiều hãng gộp luôn
     *  lối vào tiện ích ở đây), lỗi thì mới rơi về [Settings.ACTION_SETTINGS] (trang Cài đặt hệ
     *  thống gốc) kèm Toast hướng dẫn tìm mục "Màn hình chính"/"Tiện ích" trong đó. */
    private fun showStartLongPressMenu(anchor: View) {
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
                popup.dismiss()
                onTap()
            }
        }
        fun openSystem(vararg intents: Intent, notFoundMsg: String) {
            for (intent in intents) {
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                } catch (e: Exception) { /* thử intent kế tiếp */ }
            }
            Toast.makeText(context, notFoundMsg, Toast.LENGTH_SHORT).show()
        }
        val itemWallpaper = menuItem("Hình nền") { onPickWallpaper() }
        val itemWidgets = menuItem("Tiện ích") {
            openSystem(
                Intent(android.provider.Settings.ACTION_HOME_SETTINGS),
                Intent(android.provider.Settings.ACTION_SETTINGS),
                notFoundMsg = "Không mở được cài đặt Tiện ích - vào Cài đặt hệ thống > Màn hình chính để tìm"
            )
        }
        val itemSettings = menuItem("Cài đặt") {
            openSystem(
                Intent(android.provider.Settings.ACTION_SETTINGS),
                notFoundMsg = "Không mở được Cài đặt hệ thống"
            )
        }
        fun divider() = View(context).apply {
            setBackgroundColor(0xFF3A3A3A.toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        }
        val menuBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1A1A1A.toInt())
                setStroke(dp(1), 0xFF3A3A3A.toInt())
            }
            val rows = listOf(itemWallpaper, itemWidgets, itemSettings)
            rows.forEachIndexed { i, row ->
                if (i > 0) addView(divider())
                addView(row)
            }
        }
        popup = PopupWindow(
            menuBox, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true
        ).apply {
            elevation = 0f
            animationStyle = 0
            isOutsideTouchable = true
        }
        popup.showSmartDropDown(anchor)
    }

    /** Menu bật lên khi NHẤN GIỮ 1 app (trong danh sách "ứng dụng" hoặc chính tile đã ghim trên
     *  "start") - 2 dòng: "Ghim/Bỏ ghim vào start" và "Đánh dấu/Bỏ đánh dấu sao", tự đổi nhãn
     *  tuỳ trạng thái hiện tại, rồi dựng lại 2 trang ngay để tile/nhóm "★" mới hiện/mất tức thì.
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
        val itemStar = menuItem(if (starred) "Bỏ ghim đầu trang" else "Ghim lên đầu trang") {
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
            // itemPin có thể là null (trang "DS Ứng Dụng", app đã ghim rồi
            // - xem 2 nhánh if ở trên) - dùng listOfNotNull() + chèn divider() MỚI (view riêng
            // mỗi lần, không tái dùng) giữa các dòng THỰC SỰ có mặt, tránh 2 divider dính liền
            // nhau hoặc divider ở đầu khi dòng đầu tiên bị ẩn.
            val rows = listOfNotNull(itemPin, itemStar, itemResize, itemUninstall)
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
        popup.showSmartDropDown(anchor)
    }

    /** Trạng thái đang ở "chế độ đổi cỡ" của 1 tile - lưu CẢ 2 tay cầm (cạnh dưới + cạnh phải)
     *  + nền foreground GỐC (để khôi phục khi thoát chế độ) - gắn vào [View.setTag] của chính
     *  tile đó. */
    private data class ResizeState(
        val handleBottom: View, val handleRight: View, val handleTop: View, val handleLeft: View,
        val originalForeground: Drawable?
    )

    /** NHẤN GIỮ 1 tile để BẬT "chế độ đổi cỡ": hiện viền trắng bao quanh tile + 4 TAY CẦM RIÊNG
     *  BIỆT, MỖI CẠNH 1 TAY CẦM (trên/dưới/trái/phải) - kéo cạnh DƯỚI hoặc TRÊN để đổi CHIỀU
     *  CAO, kéo cạnh TRÁI hoặc PHẢI để đổi CHIỀU RỘNG - kéo cạnh TRÊN/TRÁI vẫn giữ nguyên cạnh
     *  ĐỐI DIỆN (dưới/phải) đứng yên, tile "phình" về phía tay cầm đang kéo, đúng cảm giác kéo
     *  từ 4 phía như 1 cửa sổ thật, không chỉ riêng góc dưới-phải như trước. Thả tay cầm nào ->
     *  tự CHỐT chiều đó về cỡ HỢP LỆ gần nhất trong 4 cỡ [TileSize] (lưới hiện tại chỉ hỗ trợ
     *  đúng 4 cỡ rời rạc Nhỏ/Rộng/Cao/To, CHƯA phải tự do hoàn toàn theo từng pixel - kéo giữa
     *  chừng sẽ tự nhảy về cỡ gần nhất khi thả tay), rồi lưu qua [onCommit] (tự lưu +
     *  refreshPages() ở nơi gọi) - [onCommit] LUÔN kéo theo 1 lần DỰNG LẠI TOÀN BỘ lưới từ đầu
     *  qua [GridPlacer] (không cập nhật tại chỗ), nên tile nào đang "bị chiếm chỗ" bởi tile vừa
     *  phình to ra sẽ TỰ ĐỘNG được xếp qua ô trống kế tiếp (bị "đôn đi") - không bao giờ có 2
     *  tile chồng lấn vĩnh viễn lên nhau sau khi thả tay, kể cả những tile khác đứng sau nó
     *  trong danh sách cũng tự dồn theo dây chuyền nếu cần.
     *  NHẤN GIỮ LẦN NỮA trong lúc đang ở chế độ đổi cỡ -> HUỶ, không lưu gì, khôi phục nguyên
     *  trạng. */
    private fun enterResizeMode(
        tileView: View, gridContainer: FrameLayout, cellPitchPx: Int, onCommit: (TileSize) -> Unit
    ) {
        val existing = tileView.tag as? ResizeState
        if (existing != null) {
            // Đang ở chế độ đổi cỡ rồi -> nhấn giữ lần nữa để HUỶ, gỡ viền + 4 tay cầm, không lưu.
            gridContainer.removeView(existing.handleBottom)
            gridContainer.removeView(existing.handleRight)
            gridContainer.removeView(existing.handleTop)
            gridContainer.removeView(existing.handleLeft)
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
        val handleBottom = makeHandle() // giữa cạnh dưới - chỉ đổi CHIỀU CAO, cạnh trên đứng yên
        val handleRight = makeHandle()  // giữa cạnh phải - chỉ đổi CHIỀU RỘNG, cạnh trái đứng yên
        val handleTop = makeHandle()    // giữa cạnh trên - chỉ đổi CHIỀU CAO, cạnh dưới đứng yên
        val handleLeft = makeHandle()   // giữa cạnh trái - chỉ đổi CHIỀU RỘNG, cạnh phải đứng yên

        fun tileLp() = tileView.layoutParams as FrameLayout.LayoutParams
        // 4 tay cầm luôn "dính" đúng giữa mỗi cạnh của tile - gọi lại mỗi khi tile đổi kích
        // thước HOẶC đổi vị trí (kéo cạnh trên/trái làm dịch cả leftMargin/topMargin, xem 2 tay
        // cầm bên dưới) trong lúc kéo, để tay cầm di chuyển theo cùng ngón tay chứ không đứng
        // yên 1 chỗ.
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
            handleTop.layoutParams = FrameLayout.LayoutParams(handleSizePx, handleSizePx).also {
                it.leftMargin = lp.leftMargin + lp.width / 2 - handleSizePx / 2
                it.topMargin = lp.topMargin - handleSizePx / 2
            }
            handleLeft.layoutParams = FrameLayout.LayoutParams(handleSizePx, handleSizePx).also {
                it.leftMargin = lp.leftMargin - handleSizePx / 2
                it.topMargin = lp.topMargin + lp.height / 2 - handleSizePx / 2
            }
        }
        gridContainer.addView(handleBottom)
        gridContainer.addView(handleRight)
        gridContainer.addView(handleTop)
        gridContainer.addView(handleLeft)
        positionHandles()
        tileView.tag = ResizeState(handleBottom, handleRight, handleTop, handleLeft, originalForeground)

        val minPx = cellPitchPx - dp(4)
        val maxPx = cellPitchPx * 2 - dp(4)
        val threshold = (cellPitchPx * 1.5f).toInt()

        // Chốt 1 chiều (rộng HOẶC cao) về cỡ hợp lệ gần nhất rồi báo [onCommit] - dùng chung cho
        // cả 4 tay cầm, mỗi tay cầm chỉ đổi ĐÚNG 1 chiều nên chiều còn lại giữ nguyên giá trị cỡ
        // đã chốt trước đó (lấy từ [TileSizeStore] gián tiếp qua kích thước layoutParams hiện tại).
        fun commit() {
            val lp = tileLp()
            val w = if (lp.width >= threshold) 2 else 1
            val h = if (lp.height >= threshold) 2 else 1
            val picked = TileSize.values().first { it.w == w && it.h == h }
            gridContainer.removeView(handleBottom)
            gridContainer.removeView(handleRight)
            gridContainer.removeView(handleTop)
            gridContainer.removeView(handleLeft)
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

        // Kéo cạnh TRÊN: kéo LÊN (dy âm) -> tile "phình" LÊN TRÊN, cạnh DƯỚI đứng yên - nghĩa là
        // topMargin phải GIẢM đúng bằng đúng phần chiều cao TĂNG THÊM (bù trừ để mép dưới không
        // xê dịch), khác hẳn tay cầm dưới (chỉ đổi height, topMargin đứng yên).
        var startTouchYTop = 0f
        var startHeightPxTop = 0
        var startTopMarginTop = 0
        handleTop.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startTouchYTop = event.rawY
                    val lp = tileLp()
                    startHeightPxTop = lp.height
                    startTopMarginTop = lp.topMargin
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dy = (event.rawY - startTouchYTop).toInt()
                    val newHeight = (startHeightPxTop - dy).coerceIn(minPx, maxPx)
                    val lp = tileLp()
                    lp.height = newHeight
                    lp.topMargin = startTopMarginTop + (startHeightPxTop - newHeight)
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

        // Kéo cạnh TRÁI: kéo SANG TRÁI (dx âm) -> tile "phình" SANG TRÁI, cạnh PHẢI đứng yên -
        // leftMargin GIẢM đúng bằng đúng phần chiều rộng TĂNG THÊM, khác tay cầm phải (chỉ đổi
        // width, leftMargin đứng yên).
        var startTouchXLeft = 0f
        var startWidthPxLeft = 0
        var startLeftMarginLeft = 0
        handleLeft.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startTouchXLeft = event.rawX
                    val lp = tileLp()
                    startWidthPxLeft = lp.width
                    startLeftMarginLeft = lp.leftMargin
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startTouchXLeft).toInt()
                    val newWidth = (startWidthPxLeft - dx).coerceIn(minPx, maxPx)
                    val lp = tileLp()
                    lp.width = newWidth
                    lp.leftMargin = startLeftMarginLeft + (startWidthPxLeft - newWidth)
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

    /** 1 nút icon ĐƠN GIẢN kiểu Android chuẩn (icon tròn + tên bên dưới, canh giữa) - dùng cho 4
     *  mục CỐ ĐỊNH (YouTube/Ẩn danh/Nhiều T.khoản/Quản lý tệp). KHÔNG tham gia hệ thống lưới
     *  Live Tile ([GridPlacer]/[TileSizeStore]/[PinnedOrderStore]) như [buildLiveTile] trước đây
     *  nữa - không đổi kích cỡ, không kéo-thả đổi vị trí, không menu nhấn giữ (setOnLongClickListener
     *  KHÔNG được gắn) - CHỈ CHẠM để mở, thứ tự CỐ ĐỊNH luôn luôn, đúng y hệt cách 4 icon tắt
     *  (shortcut) cố định trên màn hình chính Android thật hoạt động. Vùng chạm cũng THU GỌN
     *  đúng bằng khung icon+tên (wrap_content) - không còn chiếm nguyên 1 ô lưới rộng như tile
     *  cũ (từng khiến chạm trượt ra ngoài icon vẫn mở nhầm app, hoặc giữ tay ở vùng trống quanh
     *  icon vẫn vô tình hiện menu đổi kích cỡ/vị trí không còn tồn tại nữa). */
    private fun buildSimpleAppIcon(label: String, iconRes: Int, tileColor: Int, onClick: () -> Unit): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            foreground = pressedOverlay()
            setPadding(dp(6), dp(8), dp(6), dp(8))
            setOnClickListener { onClick() }
        }
        applyWpTilePressAnim(column) // giữ hiệu ứng "bóp nhẹ" khi chạm, đồng bộ cảm giác với các icon khác trong app

        val iconBg = FrameLayout(context).apply {
            // Vuông bo góc nhẹ (thay vì tròn) - theo yêu cầu.
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(tileColor)
            }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        val icon = ImageView(context).apply {
            setImageResource(iconRes)
            val pad = dp(7)
            setPadding(pad, pad, pad, pad)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        iconBg.addView(icon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val labelView = TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(Color.WHITE)
            setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), 0xCC000000.toInt())
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dp(4)
            }
        }

        column.addView(iconBg)
        column.addView(labelView)
        return column
    }

    /** buildLiveTile() (tile Live Tile kiểu WP cho 4 mục cố định) đã bị XOÁ HẲN - 4 mục cố định
     *  giờ dùng [buildSimpleAppIcon] đơn giản, không tham gia lưới nữa (xem [buildStartPage]). */

    /** 1 ô "Live Tile" cho app NGƯỜI DÙNG TỰ GHIM (dùng icon thật của app, không phải icon
     *  vector đơn sắc) - bố cục tương tự (trước là buildLiveTile, nay đã xoá), thêm NHẤN GIỮ để mở menu bỏ ghim. */
    private fun buildAppTile(
        label: String, icon: Drawable, tileColor: Int,
        onClick: () -> Unit, onLongPress: (View) -> Unit
    ): View {
        val tile = FrameLayout(context).apply {
            // KHÔNG còn viền/ô vuông ngoài - chỉ còn icon (ô màu nhỏ) + tên bên dưới, giống 1 icon
            // app Android bình thường. Nhấn giữ để chọn/kéo-thả VẪN ăn ở BẤT KỲ đâu trong ô lưới
            // (isLongClickable + onLongClickListener bên dưới không đổi gì) - nhưng bấm CHẠM một
            // phát để mở app thì CHỈ ăn khi chạm đúng icon/tên (xem iconBg/labelView bên dưới).
            isFocusable = true
            isLongClickable = true
        }
        applyWpTilePressAnim(tile)

        // Ô nền màu BÊN TRONG chứa icon app thật - VUÔNG BO GÓC NHẸ, đồng bộ với
        // [buildSimpleAppIcon] (xem giải thích ở đó).
        val iconBg = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(tileColor)
            }
            layoutParams = FrameLayout.LayoutParams(dp(48), dp(48)).also {
                it.gravity = Gravity.TOP or Gravity.START
                it.leftMargin = dp(10); it.topMargin = dp(10)
            }
            isClickable = true
            isFocusable = true
            foreground = pressedOverlay()
            setOnClickListener { onClick() }
        }
        val iconView = ImageView(context).apply {
            setImageDrawable(icon)
            val pad = dp(7)
            setPadding(pad, pad, pad, pad)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        iconBg.addView(iconView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val labelView = buildTileLabel(label).apply {
            isClickable = true
            setOnClickListener { onClick() }
        }
        tile.addView(iconBg)
        tile.addView(labelView)
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
        // ── Nền THẺ VUÔNG VỨC trắng phía sau icon - 1 số app hệ thống đã tự trả về icon TRÒN
        // hoặc bo góc khác nhau tuỳ launcher/hãng máy (loadIcon() trả về ĐÚNG hình dạng mà OS đã
        // "nướng sẵn" vào ảnh, không can thiệp được), khiến danh sách lẫn lộn hình vuông/tròn rất
        // rối mắt - giờ LUÔN bọc icon trong 1 khung nền HÌNH CHỮ NHẬT VUÔNG GÓC (không bo tròn)
        // màu trắng phía sau, để MỌI icon - dù bản thân nó tròn, vuông, hay bo góc - đều hiện ra
        // với ĐƯỜNG VIỀN NGOÀI vuông vức, đồng nhất tuyệt đối trên toàn danh sách. Với icon vốn
        // đã có sẵn khung vuông/bo góc trắng riêng (đa số app) thì gần như không thấy khác biệt
        // (2 lớp trắng chồng khít lên nhau); với icon tròn (như "Camera" tím) thì phần góc vuông
        // còn trống quanh hình tròn sẽ hiện màu trắng thay vì lộ hình nền phía sau app, TỰ ĐỘNG
        // "vuông hoá" icon đó mà không cần can thiệp/crop từng ảnh cụ thể. ──
        val iconCard = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
            }
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).also { it.rightMargin = dp(20) }
        }
        val icon = ImageView(context).apply {
            setImageDrawable(iconDrawable)
            val inset = dp(4)
            layoutParams = FrameLayout.LayoutParams(dp(64) - inset * 2, dp(64) - inset * 2).also {
                it.gravity = Gravity.CENTER
            }
        }
        iconCard.addView(icon)
        val text = TextView(context).apply {
            text = label
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        row.addView(iconCard)
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

    /** Giống [pressedOverlay] nhưng bo TRÒN - hiện không có nơi nào dùng (icon nền đã đổi lại
     *  thành vuông bo góc), giữ lại phòng cần dùng lại. */
    private fun pressedOverlayRound(): Drawable {
        val pressedState = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x33FFFFFF) }
        val normalState = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT) }
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedState)
            addState(intArrayOf(), normalState)
        }
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
