package com.h.adblockbrowser

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Sao lưu / khôi phục DỮ LIỆU của app để chuyển sang máy khác đã cài sẵn app này.
 *
 * App KHÔNG tự lưu chữ mật khẩu. "Tài khoản đã đăng nhập" của từng hồ sơ "Nhiều tài khoản" nằm ở
 * dữ liệu WebView riêng của tiến trình ":acctN" (thư mục app_webview_acctN: cookie, phiên đăng
 * nhập, localStorage...). Nên bản sao lưu mang theo:
 *   1. Dữ liệu WebView của từng hồ sơ  -> máy kia mở hồ sơ là ĐÃ ĐĂNG NHẬP SẴN.
 *   2. Cài đặt/danh sách của app (SharedPreferences): danh sách hồ sơ, tab đang mở, dấu trang,
 *      màu giao diện... (xem [isBackupPrefName] - chỉ những kho liệt kê rõ mới được sao lưu).
 * KHÔNG sao lưu (cố ý): khoá ứng dụng (PIN/hình - gắn với từng máy), ảnh nền, widget, dữ liệu +
 * tab của chế độ Ẩn danh (Ẩn danh vốn để không lưu lại), cache.
 *
 * Toàn bộ nén thành 1 tệp rồi mã hoá bằng mật khẩu người dùng - xem [BackupCrypto].
 */
object BackupManager {

    const val FILE_EXTENSION = "lpbak"
    private const val FORMAT_VERSION = 1
    private const val ENTRY_META = "meta.json"
    private const val DIR_PREFS = "prefs"
    private const val DIR_WEBVIEW = "webview"

    /** Kho SharedPreferences cố định được sao lưu. THÊM kho mới của app vào đây để nó được sao lưu. */
    private val FIXED_PREF_NAMES = setOf(
        "account_profiles", "account_session", "starred_pages_incognito",
        "theme_prefs", "tile_sizes", "pinned_order", "pinned_apps_start", "starred_apps_list"
    )
    private val ACCOUNT_STARRED_REGEX = Regex("^starred_pages_account_\\d+$")
    private val WEBVIEW_DIR_REGEX = Regex("^app_webview_(acct\\d+)$")
    private val SLOT_NAME_REGEX = Regex("^acct\\d+$")

    /** Tên thư mục/tệp KHÔNG sao lưu (cache dựng lại được, tệp khoá, tệp riêng của từng tiến trình). */
    private val WEBVIEW_EXCLUDED_NAMES = setOf(
        "Cache", "Code Cache", "GPUCache", "ShaderCache", "DawnCache", "GrShaderCache",
        "component_crx_cache", "Crashpad", "blob_storage", "CacheStorage", "ScriptCache",
        "webview_data.lock", "SingletonLock", "SingletonCookie", "SingletonSocket"
    )

    private fun isBackupPrefName(name: String): Boolean =
        name in FIXED_PREF_NAMES || ACCOUNT_STARRED_REGEX.matches(name)

    class BackupResult(val file: File, val profiles: Int, val prefsFiles: Int, val webViewFiles: Int, val skippedFiles: Int)

    /** Kết quả bước 1 của khôi phục: tệp đã được GIẢI MÃ + XÁC THỰC xong vào thư mục tạm, chưa đụng
     *  tới dữ liệu hiện tại. Người dùng xác nhận rồi mới gọi [applyRestore]. */
    class RestorePlan(val stagingDir: File, val createdAt: Long, val device: String, val profileCount: Int)

    // ------------------------------------------------------------------ SAO LƯU

    fun createBackup(context: Context, password: CharArray, outFile: File, progress: (String) -> Unit): BackupResult {
        progress("Đóng các hồ sơ đang mở...")
        killProfileProcesses(context)

        outFile.parentFile?.mkdirs()
        val profileCount = AccountProfileStore.load(context).size
        var prefsCount = 0
        val stats = IntArray(2) // [0] = số tệp đã nén, [1] = số tệp bỏ qua (không đọc được)
        try {
            FileOutputStream(outFile).use { fos ->
                val encrypted = BackupCrypto.encryptingStream(BufferedOutputStream(fos), password)
                ZipOutputStream(encrypted).use { zip ->
                    putText(
                        zip, ENTRY_META,
                        JSONObject()
                            .put("format", FORMAT_VERSION)
                            .put("createdAt", System.currentTimeMillis())
                            .put("device", (Build.MANUFACTURER + " " + Build.MODEL).trim())
                            .put("profiles", profileCount)
                            .toString()
                    )

                    progress("Đang gói cài đặt và danh sách tài khoản...")
                    val prefsDir = File(context.dataDir, "shared_prefs")
                    val prefFiles = prefsDir.listFiles()
                    if (prefFiles != null) {
                        for (f in prefFiles) {
                            if (!f.isFile || !f.name.endsWith(".xml")) continue
                            val name = f.name.removeSuffix(".xml")
                            if (!isBackupPrefName(name)) continue
                            val entries: JSONArray? = try { parsePrefsXml(f) } catch (e: Exception) { null }
                            if (entries == null) continue
                            putText(zip, "$DIR_PREFS/$name.json", entries.toString())
                            prefsCount++
                        }
                    }

                    val dataChildren = context.dataDir.listFiles()
                    if (dataChildren != null) {
                        for (dir in dataChildren) {
                            val m = WEBVIEW_DIR_REGEX.matchEntire(dir.name)
                            if (!dir.isDirectory || m == null) continue
                            val slotName = m.groupValues[1]
                            progress("Đang gói phiên đăng nhập ($slotName)...")
                            zipDirectory(zip, dir, "$DIR_WEBVIEW/$slotName/", stats)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            outFile.delete()
            throw e
        }
        return BackupResult(outFile, profileCount, prefsCount, stats[0], stats[1])
    }

    private fun putText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun zipDirectory(zip: ZipOutputStream, dir: File, prefix: String, stats: IntArray) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.name in WEBVIEW_EXCLUDED_NAMES) continue
            if (child.isDirectory) {
                zipDirectory(zip, child, prefix + child.name + "/", stats)
                continue
            }
            // Mở tệp TRƯỚC khi tạo mục zip: tệp không mở được (đang bị khoá...) thì bỏ qua, không
            // để lại 1 mục zip nửa vời.
            val input = try { FileInputStream(child) } catch (e: IOException) { null }
            if (input == null) {
                stats[1]++
                continue
            }
            input.use {
                zip.putNextEntry(ZipEntry(prefix + child.name))
                it.copyTo(zip, 64 * 1024)
                zip.closeEntry()
            }
            stats[0]++
        }
    }

    /** Đọc 1 tệp SharedPreferences XML TRỰC TIẾP từ đĩa (không qua getSharedPreferences vì các tiến
     *  trình :acctN ghi vào cùng tệp, bản trong bộ nhớ của tiến trình chính có thể đã cũ). */
    private fun parsePrefsXml(file: File): JSONArray {
        val result = JSONArray()
        FileInputStream(file).use { fis ->
            val p = Xml.newPullParser()
            p.setInput(fis, "UTF-8")
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && p.depth == 2) {
                    val tag = p.name
                    val name = p.getAttributeValue(null, "name")
                    if (name != null) {
                        when (tag) {
                            "string" -> result.put(prefEntry(name, "s", p.nextText()))
                            "int" -> result.put(prefEntry(name, "i", p.getAttributeValue(null, "value") ?: "0"))
                            "long" -> result.put(prefEntry(name, "l", p.getAttributeValue(null, "value") ?: "0"))
                            "float" -> result.put(prefEntry(name, "f", p.getAttributeValue(null, "value") ?: "0"))
                            "boolean" -> result.put(prefEntry(name, "b", p.getAttributeValue(null, "value") ?: "false"))
                            "set" -> {
                                val arr = JSONArray()
                                while (true) {
                                    val e2 = p.next()
                                    if (e2 == XmlPullParser.END_DOCUMENT) break
                                    if (e2 == XmlPullParser.END_TAG && p.name == "set") break
                                    if (e2 == XmlPullParser.START_TAG && p.name == "string") arr.put(p.nextText())
                                }
                                result.put(JSONObject().put("n", name).put("t", "set").put("v", arr))
                            }
                        }
                    }
                }
                ev = p.next()
            }
        }
        return result
    }

    private fun prefEntry(name: String, type: String, value: String): JSONObject =
        JSONObject().put("n", name).put("t", type).put("v", value)

    // ------------------------------------------------------------------ KHÔI PHỤC

    /** Bước 1: giải mã + giải nén vào thư mục tạm và XÁC THỰC toàn bộ tệp. Sai mật khẩu ->
     *  [BackupCrypto.WrongPasswordException]; tệp hỏng/không phải bản sao lưu ->
     *  [BackupCrypto.CorruptBackupException]. Chưa động tới dữ liệu hiện tại của app. */
    fun prepareRestore(context: Context, input: InputStream, password: CharArray, progress: (String) -> Unit): RestorePlan {
        val staging = File(context.cacheDir, "restore_staging")
        deleteRecursively(staging)
        staging.mkdirs()
        try {
            progress("Đang giải mã và kiểm tra tệp...")
            val decrypted = BackupCrypto.decryptingStream(BufferedInputStream(input), password)
            val stagingPrefix = staging.canonicalPath + File.separator
            ZipInputStream(decrypted).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val target = File(staging, entry.name)
                    // Chống "Zip Slip": tên mục không được trỏ ra ngoài thư mục tạm.
                    if (!target.canonicalPath.startsWith(stagingPrefix)) {
                        throw BackupCrypto.CorruptBackupException("Tệp sao lưu chứa đường dẫn không hợp lệ")
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out -> zip.copyTo(out, 64 * 1024) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                // Đọc nốt phần còn lại của luồng mã hoá để XÁC THỰC MỌI KHỐI (kể cả khối cuối, chống
                // tệp bị cắt/sửa phần đuôi).
                val sink = ByteArray(8192)
                while (decrypted.read(sink) >= 0) { /* chỉ để xác thực */ }
            }

            val metaFile = File(staging, ENTRY_META)
            if (!metaFile.isFile) throw BackupCrypto.CorruptBackupException("Không phải bản sao lưu hợp lệ (thiếu thông tin)")
            val meta = JSONObject(metaFile.readText(Charsets.UTF_8))
            if (meta.optInt("format", 0) > FORMAT_VERSION) {
                throw BackupCrypto.CorruptBackupException("Bản sao lưu này tạo bởi phiên bản app mới hơn - hãy cập nhật app rồi thử lại")
            }
            return RestorePlan(
                staging,
                meta.optLong("createdAt", 0L),
                meta.optString("device", ""),
                meta.optInt("profiles", 0)
            )
        } catch (e: Exception) {
            deleteRecursively(staging)
            throw e
        }
    }

    /** Huỷ khôi phục (người dùng bấm Huỷ): xoá thư mục tạm. */
    fun discardRestore(plan: RestorePlan) {
        deleteRecursively(plan.stagingDir)
    }

    /** Bước 2: THAY THẾ dữ liệu hiện tại bằng dữ liệu trong bản sao lưu. */
    fun applyRestore(context: Context, plan: RestorePlan, progress: (String) -> Unit) {
        progress("Đóng các hồ sơ đang mở...")
        killProfileProcesses(context)

        progress("Xoá dữ liệu cũ...")
        // Phải xoá dữ liệu WebView + làm rỗng các kho cũ TRƯỚC khi chép, để máy này không còn sót
        // hồ sơ/phiên đăng nhập nào không có trong bản sao lưu.
        val dataChildren = context.dataDir.listFiles()
        if (dataChildren != null) {
            for (d in dataChildren) {
                if (d.isDirectory && WEBVIEW_DIR_REGEX.matches(d.name)) deleteRecursively(d)
            }
        }
        val prefFiles = File(context.dataDir, "shared_prefs").listFiles()
        if (prefFiles != null) {
            for (f in prefFiles) {
                if (!f.name.endsWith(".xml")) continue
                val name = f.name.removeSuffix(".xml")
                if (isBackupPrefName(name)) {
                    context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                }
            }
        }

        progress("Đang khôi phục phiên đăng nhập...")
        val webRoot = File(plan.stagingDir, DIR_WEBVIEW)
        val slotDirs = webRoot.listFiles()
        if (slotDirs != null) {
            for (slotDir in slotDirs) {
                if (slotDir.isDirectory && SLOT_NAME_REGEX.matches(slotDir.name)) {
                    copyDirectory(slotDir, File(context.dataDir, "app_webview_" + slotDir.name))
                }
            }
        }

        progress("Đang khôi phục cài đặt và danh sách tài khoản...")
        val prefsRoot = File(plan.stagingDir, DIR_PREFS)
        val jsonFiles = prefsRoot.listFiles()
        if (jsonFiles != null) {
            for (f in jsonFiles) {
                if (!f.name.endsWith(".json")) continue
                val name = f.name.removeSuffix(".json")
                if (!isBackupPrefName(name)) continue // chỉ nhận đúng các kho đã liệt kê
                applyPrefsJson(context, name, JSONArray(f.readText(Charsets.UTF_8)))
            }
        }

        deleteRecursively(plan.stagingDir)
    }

    private fun applyPrefsJson(context: Context, name: String, entries: JSONArray) {
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            val key = e.getString("n")
            when (e.getString("t")) {
                "s" -> editor.putString(key, e.getString("v"))
                "i" -> editor.putInt(key, e.getString("v").toInt())
                "l" -> editor.putLong(key, e.getString("v").toLong())
                "f" -> editor.putFloat(key, e.getString("v").toFloat())
                "b" -> editor.putBoolean(key, e.getString("v").toBoolean())
                "set" -> {
                    val arr = e.getJSONArray("v")
                    val set = HashSet<String>()
                    for (j in 0 until arr.length()) set.add(arr.getString(j))
                    editor.putStringSet(key, set)
                }
            }
        }
        editor.commit()
    }

    // ------------------------------------------------------------------ TIỆN ÍCH

    /** Tắt các tiến trình phụ của app (:acct1..:acct10, :incognito) để dữ liệu WebView của chúng
     *  không bị ghi trong lúc đọc/ghi đè (và để chúng nạp lại dữ liệu mới khi mở lại). Cùng UID nên
     *  được phép kill, không cần quyền gì thêm. */
    private fun killProfileProcesses(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val prefix = context.packageName + ":"
        val me = Process.myPid()
        var killed = 0
        val procs = am.runningAppProcesses
        if (procs != null) {
            for (p in procs) {
                if (p.pid != me && p.processName != null && p.processName.startsWith(prefix)) {
                    Process.killProcess(p.pid)
                    killed++
                }
            }
        }
        if (killed > 0) {
            try { Thread.sleep(700) } catch (ignored: InterruptedException) { }
        }
    }

    private fun copyDirectory(from: File, to: File) {
        to.mkdirs()
        val children = from.listFiles() ?: return
        for (child in children) {
            val dest = File(to, child.name)
            if (child.isDirectory) {
                copyDirectory(child, dest)
            } else {
                FileInputStream(child).use { input ->
                    FileOutputStream(dest).use { out -> input.copyTo(out, 64 * 1024) }
                }
            }
        }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }
}
