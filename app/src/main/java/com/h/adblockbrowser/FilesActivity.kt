package com.h.adblockbrowser

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class FilesActivity : AppCompatActivity() {

    /** Thoát màn này kèm hiệu ứng "trượt ra bên phải" kiểu Windows Phone (xem [finishWp] ở
     *  UiUtils.kt), dù finish() được gọi từ đâu (nút Back nổi, mũi tên ◀, phím Back cứng...). */
    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.wp_slide_in_left, R.anim.wp_slide_out_right)
    }


    private lateinit var tvPath: TextView
    private lateinit var listView: ListView
    private lateinit var actionBar: LinearLayout
    private lateinit var tvSelCount: TextView
    private var currentDir: File = Environment.getExternalStorageDirectory()
    private var entries: List<File> = emptyList()

    private var selectionMode = false
    private val selected = LinkedHashSet<Int>() // chỉ số trong `entries`

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ẩn thanh trạng thái (giờ/mạng/pin) - phần khoanh đỏ người dùng muốn ẩn ở trên cùng.
        hideStatusBar()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(24, dp(8), 24, 24)
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
            // Ẩn hẳn cả khối tiêu đề (mũi tên Back + chữ "Trang Tệp") theo yêu cầu - không còn
            // hiện phía trên nữa. Điều hướng "lên thư mục cha" vẫn hoạt động bình thường qua nút
            // Back hệ thống (xem onBackPressed() ở dưới, không phụ thuộc mũi tên này).
            visibility = android.view.View.GONE
        }
        val title = TextView(this).apply {
            text = "Trang Tệp"
            textSize = 20f
            setTextColor(ThemePrefs.accent(this@FilesActivity))
            setPadding(12, 0, 0, 0)
        }
        // Nút back: nếu đang chọn nhiều thì thoát chế độ chọn trước; nếu không thì đi lên 1 cấp
        // thư mục (giống hệt onBackPressed()), không thoát thẳng nếu đang ở thư mục con.
        titleRow.addView(buildBackArrow(onBack = {
            if (selectionMode) exitSelectionMode() else onBackPressed()
        }))
        titleRow.addView(title)

        tvPath = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF888888.toInt())
            setPadding(0, 0, 0, 12)
            // Ẩn luôn dòng đường dẫn (/storage/emulated/0...) - chỉ giữ lại TextView này ở dạng
            // ẩn để logic hasParentRow() (so sánh path hiện tại với thư mục gốc) vẫn hoạt động
            // bình thường, không liên quan tới việc hiển thị.
            visibility = android.view.View.GONE
        }

        // ── Thanh hành động khi đang chọn nhiều - chỉ hiện khi selectionMode = true ──
        actionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF1A1A1A.toInt())
            setPadding(dp(12), dp(8), dp(12), dp(8))
            visibility = android.view.View.GONE
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(8)
            layoutParams = lp
        }
        tvSelCount = TextView(this).apply {
            text = "Đã chọn 0"
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnShare = TextView(this).apply {
            // "☑" (ballot-box-with-check) trước đây hiển thị dạng emoji màu tuỳ máy - đổi thành
            // chữ thường không icon, đúng kiểu WP App Bar mở rộng (chỉ chữ, không cần icon cho
            // mọi hành động - xem WpAppBar.expandedPanel).
            text = "chọn tất cả"
            setTextColor(ThemePrefs.accent(this@FilesActivity))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            isClickable = true
            setOnClickListener { selectAll() }
        }
        val btnDelete = TextView(this).apply {
            text = "Xoá"
            setTextColor(0xFFFF6B6B.toInt())
            setPadding(dp(12), dp(6), dp(12), dp(6))
            isClickable = true
            setOnClickListener { confirmDeleteSelected() }
        }
        val btnCancel = TextView(this).apply {
            text = "✕"
            setTextColor(0xFF888888.toInt())
            setPadding(dp(12), dp(6), dp(4), dp(6))
            isClickable = true
            setOnClickListener { exitSelectionMode() }
        }
        actionBar.addView(tvSelCount)
        actionBar.addView(btnShare)
        actionBar.addView(btnDelete)
        actionBar.addView(btnCancel)

        listView = ListView(this).apply {
            setBackgroundColor(0xFF0D0D0D.toInt())
        }

        root.addView(titleRow)
        root.addView(tvPath)
        root.addView(actionBar)
        root.addView(listView)
        setContentView(root)

        listView.setOnItemClickListener { _, _, position, _ ->
            if (selectionMode) toggleSelect(position) else onItemClick(position)
        }
        listView.setOnItemLongClickListener { _, _, position, _ ->
            if (!selectionMode) {
                selectionMode = true
                actionBar.visibility = android.view.View.VISIBLE
            }
            toggleSelect(position)
            true
        }

        checkAllFilesAccess()
        openDir(currentDir)
    }

    /** Gọi lại [hideStatusBar] mỗi khi cửa sổ THẬT SỰ được lấy focus - chỉ gọi 1 lần trong
     *  onCreate() KHÔNG đủ tin cậy vì lúc đó cửa sổ có thể chưa được hệ thống vẽ/lấy focus xong,
     *  nên yêu cầu ẩn status bar dễ bị bỏ qua hoặc bị hệ thống tự hiện lại ngay sau đó (đây chính
     *  là lý do "ẩn không được" dù đã gọi hideStatusBar() ở onCreate). Áp dụng lại ở đây đảm bảo
     *  luôn ẩn đúng, kể cả sau khi quay lại màn này từ 1 màn khác - giống cách MainActivity xử lý
     *  ở enableImmersiveMode()/onWindowFocusChanged(). */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    /** Android 11+ (API 30+) chặn app đọc/ghi/xoá file NGOÀI thư mục riêng của app trừ khi được
     *  cấp quyền "Truy cập mọi tệp" (MANAGE_EXTERNAL_STORAGE) - quyền này KHÔNG xin được qua hộp
     *  thoại quyền thông thường, phải mở đúng màn Cài đặt hệ thống để người dùng tự bật. Thiếu
     *  quyền này chính là lý do "không chia sẻ/xoá được tệp" dù code chia sẻ/xoá không có lỗi gì -
     *  Android âm thầm chặn ở tầng hệ điều hành, không báo lỗi rõ ràng cho app biết. */
    private fun checkAllFilesAccess() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this, R.style.Theme_WP_Dialog)
                    .setTitle("Cần quyền truy cập tệp")
                    .setMessage("Để xem/xoá/chia sẻ được mọi tệp trên máy, hãy cấp quyền \"Truy cập mọi tệp\" cho ứng dụng này ở Cài đặt hệ thống.")
                    .setPositiveButton("Mở Cài đặt") { _, _ ->
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            } catch (e2: Exception) { }
                        }
                    }
                    .setNegativeButton("Để sau", null)
                    .show()
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun hasParentRow(): Boolean =
        tvPath.text != Environment.getExternalStorageDirectory().absolutePath && currentDir.parentFile != null

    private fun openDir(dir: File) {
        currentDir = dir
        tvPath.text = dir.absolutePath
        exitSelectionMode()

        val list = (dir.listFiles()?.toMutableList() ?: mutableListOf())
        // Ẩn tệp/thư mục ẩn (tên bắt đầu bằng dấu chấm, vd. .pmtemp, .trashBin...) khỏi danh
        // sách hiển thị - đây là các thư mục dữ liệu nội bộ của hệ thống/app khác, không phải
        // thứ người dùng cần thấy hay thao tác tới trong màn Quản lý tệp này.
        list.removeAll { it.name.startsWith(".") }
        list.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        entries = list
        refreshAdapter()
    }

    /** 1 dòng dữ liệu cho danh sách tệp: [entryIndex] = -1 cho dòng ".." (lên thư mục cha),
     *  ngược lại là chỉ số thật trong [entries]. */
    private data class Row(val name: String, val isDir: Boolean, val entryIndex: Int, val selected: Boolean)

    /** Dựng lại danh sách tệp bằng ADAPTER TUỲ CHỈNH kiểu WP (icon thư mục/tệp phẳng đơn sắc +
     *  tên, nền đen, không viền/ripple tròn) THAY CHO android.R.layout.simple_list_item_1 - hàng
     *  danh sách MẶC ĐỊNH của hệ thống Android (chữ đen/kiểu chữ hệ thống, không icon, ripple
     *  tròn Material) hoàn toàn LẠC TÔNG so với phần còn lại của app (đã tự vẽ UI phẳng kiểu WP ở
     *  mọi màn khác) - đây là màn duy nhất còn "trông như 1 app Android bình thường" thay vì
     *  giống File Explorer thật của Windows 10 Mobile. Icon 📁/📄 dạng emoji trước đây cũng hiển
     *  thị màu sặc sỡ tuỳ font từng máy - giờ thay bằng vector đơn sắc trắng (ic_wp_folder /
     *  ic_wp_file), dấu chọn "✓" đổi thành icon ic_wp_check hiện ở lề phải thay vì chèn vào đầu
     *  tên tệp (đúng kiểu WP: dấu tick nằm RIÊNG Ở GÓC, không đổi chính tên hiển thị). */
    private fun refreshAdapter() {
        val rows = ArrayList<Row>()
        if (hasParentRow()) rows.add(Row("..", true, -1, false))
        entries.forEachIndexed { idx, f ->
            rows.add(Row(f.name, f.isDirectory, idx, selected.contains(idx)))
        }

        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = rows.size
            override fun getItem(position: Int): Any = rows[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = rows[position]
                val container = LinearLayout(this@FilesActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(11), dp(12), dp(11))
                    isClickable = true
                    isFocusable = true
                    // Chạm tối nhẹ hình chữ nhật - đúng cảm giác "bấm phẳng" Metro, không ripple
                    // tròn Material mặc định của ListView hệ thống (xem lý giải tương tự ở
                    // HomeScreenManager.pressedOverlay).
                    val pressed = GradientDrawable().apply { setColor(0x22FFFFFF) }
                    val normal = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
                    background = android.graphics.drawable.StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_pressed), pressed)
                        addState(intArrayOf(), normal)
                    }
                }
                container.addView(ImageView(this@FilesActivity).apply {
                    setImageResource(if (row.isDir) R.drawable.ic_wp_folder else R.drawable.ic_wp_file)
                    setColorFilter(if (row.entryIndex == -1) 0xFF888888.toInt() else Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).also { it.rightMargin = dp(16) }
                })
                container.addView(TextView(this@FilesActivity).apply {
                    text = if (row.entryIndex == -1) "Lên thư mục cha" else row.name
                    textSize = 16f
                    setTextColor(if (row.entryIndex == -1) 0xFFAAAAAA.toInt() else Color.WHITE)
                    typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                if (row.selected) {
                    container.addView(ImageView(this@FilesActivity).apply {
                        setImageResource(R.drawable.ic_wp_check)
                        setColorFilter(ThemePrefs.accent(this@FilesActivity))
                        layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).also { it.leftMargin = dp(8) }
                    })
                }
                return container
            }
        }
        listView.adapter = adapter
    }

    private fun toggleSelect(position: Int) {
        val hasParent = hasParentRow()
        if (hasParent && position == 0) return // không cho chọn dòng ".."
        val index = if (hasParent) position - 1 else position
        if (index < 0 || index >= entries.size) return
        if (selected.contains(index)) selected.remove(index) else selected.add(index)
        tvSelCount.text = "Đã chọn ${selected.size}"
        if (selected.isEmpty()) exitSelectionMode()
        refreshAdapter()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selected.clear()
        actionBar.visibility = android.view.View.GONE
        refreshAdapter()
    }

    private fun onItemClick(position: Int) {
        val hasParent = hasParentRow()
        if (hasParent && position == 0) {
            currentDir.parentFile?.let { openDir(it) }
            return
        }
        val index = if (hasParent) position - 1 else position
        if (index < 0 || index >= entries.size) return
        val file = entries[index]
        if (file.isDirectory) openDir(file) else openFile(file)
    }

    private fun openFile(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(this, "com.h.adblockbrowser.fileprovider", file)
            val mime = contentResolver.getType(uri) ?: guessMime(file.name)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, mime)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Không mở được tệp này", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guessMime(name: String): String {
        return when {
            name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") -> "image/*"
            name.endsWith(".mp4") || name.endsWith(".mkv") -> "video/*"
            name.endsWith(".mp3") || name.endsWith(".m4a") -> "audio/*"
            name.endsWith(".pdf") -> "application/pdf"
            name.endsWith(".txt") -> "text/plain"
            else -> "*/*"
        }
    }

    private fun selectAll() {
        entries.indices.forEach { selected.add(it) }
        tvSelCount.text = "Đã chọn ${selected.size}"
        refreshAdapter()
    }

    private fun confirmDeleteSelected() {
        val count = selected.size
        AlertDialog.Builder(this, R.style.Theme_WP_Dialog)
            .setTitle("Xoá $count mục?")
            .setMessage("Các tệp/thư mục đã chọn sẽ bị xoá vĩnh viễn.")
            .setPositiveButton("Xoá") { _, _ ->
                val toDelete = selected.mapNotNull { entries.getOrNull(it) }
                var okCount = 0
                val deletedPaths = ArrayList<String>()
                for (f in toDelete) {
                    val path = f.absolutePath
                    val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
                    if (ok) {
                        okCount++
                        deletedPaths.add(path)
                    }
                }
                // Xoá xong trên ổ đĩa vẫn chưa đủ - phải báo cho MediaStore biết, nếu không các
                // app khác (Thư viện ảnh, quản lý tệp khác, kết nối USB với máy tính...) vẫn
                // hiển thị tệp cũ do đọc từ cache MediaStore, khiến người dùng tưởng "xoá rồi
                // mà vẫn còn". scanFile() ép hệ thống cập nhật lại MediaStore ngay lập tức.
                if (deletedPaths.isNotEmpty()) {
                    MediaScannerConnection.scanFile(this, deletedPaths.toTypedArray(), null, null)
                }
                Toast.makeText(this, "Đã xoá $okCount/${toDelete.size} mục", Toast.LENGTH_SHORT).show()
                openDir(currentDir)
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    override fun onBackPressed() {
        if (selectionMode) {
            exitSelectionMode()
            return
        }
        val parent = currentDir.parentFile
        if (parent != null && currentDir != Environment.getExternalStorageDirectory()) {
            openDir(parent)
        } else {
            super.onBackPressed()
        }
    }
}
