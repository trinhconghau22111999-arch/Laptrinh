package com.h.adblockbrowser

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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

        // ── Tiêu đề "start" kiểu Hub/Pivot header của WP: chữ thường, mảnh, rất to - ĐẶT LÊN
        // TRÊN CÙNG (trước đây widget giờ/ngày nằm trên tiêu đề, giờ đảo lại theo yêu cầu). ──
        content.addView(sectionHeader("start"))

        // ── Widget giờ/ngày (nếu có) - NẰM DƯỚI tiêu đề "start" ──
        // MATCH_PARENT (thay vì WRAP_CONTENT + căn giữa trước đây) để khung nền của widget trải
        // hết bề ngang, đúng như các ô Live Tile bên dưới - xem [MainActivity.buildClockWidget].
        if (clockWidget != null) {
            (clockWidget.parent as? ViewGroup)?.removeView(clockWidget)
            content.addView(clockWidget, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(12) })
        }

        // ── Lưới Live Tile - ĐÚNG kiểu Start Screen WP thật: TRỘN cỡ vuông (1 đơn vị) và
        // "wide" hình chữ nhật dài (2 đơn vị, gấp đôi bề ngang) trong lưới ngang 3 cột, thay vì
        // toàn bộ tile cùng 1 cỡ vuông như trước (khác biệt hình ảnh rõ nhất so với WP thật).
        //
        // TỰ XẾP HÀNG THỦ CÔNG bằng LinearLayout lồng nhau (cộng dồn "đơn vị" từng tile theo thứ
        // tự, đủ 3 đơn vị/hàng thì xuống hàng mới) THAY VÌ dùng GridLayout tự động dồn ô +
        // columnSpec: GridLayout tự đặt vị trí (row/col = UNDEFINED) rất dễ vỡ layout (hở ô,
        // lệch hàng) khi trộn nhiều columnSpec span khác nhau trong cùng lưới - rủi ro không
        // đáng, trong khi cách xếp hàng thủ công này luôn cho kết quả chắc chắn, dễ kiểm chứng.
        val pinnedKeys = listOf("youtube", "incognito", "accounts", "files", "phone", "calendar", "calculator", "clock", "settings")
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
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(110) // 96→110dp: tile to hơn, đúng WP thật
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

    /** Thứ tự các danh mục app hiển thị trên trang "ứng dụng" - danh mục nào không có app nào
     *  thuộc về thì tự động bỏ qua, không hiện tiêu đề rỗng. "Khác" luôn đứng cuối cùng, gom
     *  mọi app không khớp bất kỳ quy tắc nhận diện nào bên dưới. */
    private val categoryOrder = listOf(
        "Điện thoại", "Mạng xã hội", "Trình duyệt", "Ứng dụng Google", "Ngân hàng", "Mua sắm", "Office", "Khác"
    )

    /** Tên gói (package) của các app THƯỜNG GẶP theo từng danh mục - dùng để nhận diện CHẮC
     *  CHẮN NHẤT (so khớp đúng package name), ưu tiên hơn so khớp theo tên hiển thị (label) vì
     *  label có thể trùng chữ ở nhiều app không liên quan. Gồm cả tên gói của Android gốc (AOSP)
     *  LẪN các bản OEM phổ biến ở Việt Nam (Samsung, Xiaomi/MIUI...) vì mỗi hãng máy thường tự
     *  đóng gói lại app hệ thống (Điện thoại, Danh bạ, Máy ảnh...) với package riêng. */
    private val phonePackages = setOf(
        // Cuộc gọi
        "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer",
        "com.android.incallui", "com.android.phone",
        // Tin nhắn
        "com.android.mms", "com.google.android.apps.messaging", "com.samsung.android.messaging",
        // Danh bạ
        "com.android.contacts", "com.google.android.contacts", "com.samsung.android.app.contacts",
        // Camera
        "com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera",
        "com.sec.android.app.camera",
        // Thư viện (thư viện ảnh CỦA MÁY - khác "Thư viện Google"/Google Photos, xem googlePackages)
        "com.android.gallery3d", "com.sec.android.gallery3d", "com.miui.gallery",
        // Ghi âm
        "com.android.soundrecorder", "com.samsung.android.app.soundrecorder", "com.miui.SoundRecorder",
        // Máy phát nhạc
        "com.android.music", "com.sec.android.app.music", "com.google.android.music",
        "com.miui.player",
        // Máy tính
        "com.android.calculator2", "com.google.android.calculator", "com.sec.android.app.popupcalculator",
        "com.miui.calculator",
        // Đồng hồ
        "com.android.deskclock", "com.google.android.deskclock", "com.sec.android.app.clockpackage",
        "com.android.alarmclock",
        // Lịch
        "com.android.calendar", "com.google.android.calendar", "com.samsung.android.calendar",
        // Ghi chú - Google Keep, Samsung Notes, MIUI Notes, NotebookLM
        "com.google.android.keep", "com.samsung.android.app.notes", "com.miui.notes",
        "com.samsung.android.memo", "com.google.android.apps.notebooklm",
        // Microsoft To Do, OneNote (ghi chú/task - xếp cùng nhóm điện thoại)
        "com.microsoft.todos", "com.microsoft.office.onenote"
    )
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

        if (pkgName in phonePackages) return "Điện thoại"
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

        categoryOrder.forEach { category ->
            val appsInCategory = grouped[category] ?: return@forEach
            content.addView(groupHeader(category))
            appsInCategory.forEach { addAppRow(it) }
        }

        scrollView.addView(content)
        return scrollView
    }

    /** Menu bật lên khi NHẤN GIỮ 1 app (trong danh sách "ứng dụng" hoặc chính tile đã ghim trên
     *  "start") - 2 dòng: "Ghim/Bỏ ghim vào start" và "Đánh dấu/Bỏ đánh dấu sao", tự đổi nhãn
     *  tuỳ trạng thái hiện tại, rồi dựng lại 2 trang ngay để tile/nhóm "★" mới hiện/mất tức thì.
     *
     *  DỰNG THỦ CÔNG bằng [PopupWindow] thay vì [android.widget.PopupMenu] mặc định của Android:
     *  PopupMenu hệ thống luôn tự vẽ nền TRẮNG BO GÓC + ĐỔ BÓNG (Material Card) bất kể theme app
     *  đặt gì - hiện lên giữa 1 màn hình đen phẳng tuyệt đối sẽ rất chỏi, sai hẳn cảm giác context
     *  menu phẳng, không bóng, nền đen của WP/Windows 10 Mobile thật. */
    private fun showPinContextMenu(anchor: View, pkgName: String) {
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
        val itemPin = menuItem(if (pinned) "Bỏ ghim khỏi start" else "Ghim vào start") {
            if (pinned) PinnedAppsStore.unpin(context, pkgName) else PinnedAppsStore.pin(context, pkgName)
        }
        val itemStar = menuItem(if (starred) "Bỏ đánh dấu sao" else "Đánh dấu sao") {
            if (starred) StarredAppsStore.unstar(context, pkgName) else StarredAppsStore.star(context, pkgName)
        }
        val divider = View(context).apply {
            setBackgroundColor(0xFF3A3A3A.toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
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
            addView(itemPin)
            addView(divider)
            addView(itemStar)
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

    /** Tiêu đề lớn kiểu Pivot/Hub header của WP.
     *  [smallHeader]=true: dùng cho trang "DS Ứng Dụng" (28sp) để không chiếm quá nhiều không gian.
     *  [smallHeader]=false (mặc định): cỡ 46sp đặc trưng Hub header cho trang "start". */
    private fun sectionHeader(text: String, smallHeader: Boolean = false): View = TextView(context).apply {
        this.text = text
        textSize = if (smallHeader) 28f else 46f
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        setPadding(dp(2), dp(8), dp(2), dp(10))
    }

    /** Tiêu đề phân nhóm trong danh sách app (danh mục: "Trình duyệt", "Game"...), kiểu WP App
     *  List: chữ to, màu accent, đứng riêng 1 dòng làm mốc phân cách trực quan giữa các nhóm. */
    private fun groupHeader(text: String): View = TextView(context).apply {
        this.text = text
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
            // 36dp (tăng từ 28dp) + margin 12dp - đúng tỉ lệ tile WP thật với tile 110dp
            layoutParams = FrameLayout.LayoutParams(dp(36), dp(36)).also {
                it.gravity = Gravity.TOP or Gravity.START
                it.leftMargin = dp(12); it.topMargin = dp(12)
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
            layoutParams = FrameLayout.LayoutParams(dp(36), dp(36)).also {
                it.gravity = Gravity.TOP or Gravity.START
                it.leftMargin = dp(12); it.topMargin = dp(12)
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
        textSize = 15f  // 13f → 15f: chữ tile lớn hơn, dễ đọc hơn, đúng WP thật
        setTextColor(Color.WHITE)
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
