package com.h.adblockbrowser

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.net.URLDecoder

/**
 * FIX: trước đây WebView KHÔNG có DownloadListener nên bấm tải tệp trên trang web là không có gì
 * xảy ra (không lưu, không báo, không có trang tải xuống). Giờ:
 *  - http/https  -> DownloadManager (có thông báo, lưu vào thư mục "Download" công khai của máy,
 *                   gửi kèm cookie + User-Agent + Referer để tải được tệp cần đăng nhập)
 *  - blob: / data: -> JS đọc dữ liệu rồi chuyển xuống Kotlin lưu vào "Download"
 *  - Tải xong hiện hộp thoại: Mở tệp / Xem thư mục Tải xuống (FilesActivity, mặc định mở vào Download).
 */
object DownloadHelper {
    private const val REQ_WRITE_STORAGE = 7711
    private val main = Handler(Looper.getMainLooper())

    fun attach(activity: Activity, webView: WebView) {
        webView.addJavascriptInterface(Bridge(activity), "AndroidBlobSaver")
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            start(activity, webView, url, userAgent, contentDisposition, mimeType)
        }
    }

    /** Mở màn "Quản lý tệp" ở thư mục Download. */
    fun openDownloadsPage(context: Context) {
        try {
            context.startActivity(Intent(context, FilesActivity::class.java))
        } catch (e: Exception) {
            toast(context, "Không mở được thư mục Tải xuống")
        }
    }

    private fun toast(context: Context, text: String) {
        main.post { SafeToast.make(context, text, Toast.LENGTH_LONG).show() }
    }

    private fun needsLegacyWritePermission(activity: Activity): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    private fun start(activity: Activity, webView: WebView, url: String, ua: String?, cd: String?, mime: String?) {
        if (needsLegacyWritePermission(activity)) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_WRITE_STORAGE)
            toast(activity, "Hãy cho phép quyền lưu tệp, rồi bấm tải lại")
            return
        }
        when {
            url.startsWith("blob:") -> fetchBlob(webView, url, cd, mime)
            url.startsWith("data:") -> saveDataUrl(activity, url, cd)
            url.startsWith("http://") || url.startsWith("https://") -> enqueue(activity, webView, url, ua, cd, mime)
            else -> toast(activity, "Không hỗ trợ tải liên kết này")
        }
    }

    private fun enqueue(activity: Activity, webView: WebView, url: String, ua: String?, cd: String?, mime: String?) {
        try {
            val name = URLUtil.guessFileName(url, cd, mime)
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(name)
                if (!mime.isNullOrBlank()) setMimeType(mime)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                if (!ua.isNullOrBlank()) addRequestHeader("User-Agent", ua)
                webView.url?.let { addRequestHeader("Referer", it) }
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            }
            val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(req)
            toast(activity, "Đang tải: $name (lưu vào thư mục Download)")
            watchCompletion(activity, dm, id, name)
        } catch (e: Exception) {
            toast(activity, "Tải xuống lỗi: ${e.message}")
        }
    }

    private fun watchCompletion(activity: Activity, dm: DownloadManager, id: Long, name: String) {
        val app = activity.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != id) return
                try { app.unregisterReceiver(this) } catch (_: Exception) {}
                var ok = false
                var uri: Uri? = null
                var mime: String? = null
                dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                    if (c.moveToFirst()) {
                        ok = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                        mime = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE))
                    }
                }
                if (ok) uri = dm.getUriForDownloadedFile(id)
                main.post {
                    if (ok) showDone(activity, name, uri, mime) else toast(app, "Tải thất bại: $name")
                }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            app.registerReceiver(receiver, filter)
        }
    }

    private fun showDone(activity: Activity, name: String, uri: Uri?, mime: String?) {
        if (activity.isFinishing || activity.isDestroyed) {
            toast(activity, "Đã tải xong: $name (thư mục Download)")
            return
        }
        val b = AlertDialog.Builder(activity, R.style.Theme_WP_Dialog)
            .setTitle("Đã tải xong")
            .setMessage("$name\n\nĐã lưu trong thư mục Download của máy.")
            .setNegativeButton("Đóng", null)
            .setNeutralButton("Thư mục Tải xuống") { _, _ -> openDownloadsPage(activity) }
        if (uri != null) {
            b.setPositiveButton("Mở") { _, _ ->
                try {
                    // Android 7+ cấm đưa file:// cho app khác -> đổi sang content:// qua FileProvider
                    val shareUri = if (uri.scheme == "file") {
                        FileProvider.getUriForFile(activity, "com.h.adblockbrowser2.fileprovider", File(uri.path!!))
                    } else uri
                    val i = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(shareUri, mime ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(i)
                } catch (e: Exception) {
                    toast(activity, "Không có app nào mở được tệp này")
                }
            }
        }
        try { b.show() } catch (_: Exception) { toast(activity, "Đã tải xong: $name") }
    }

    // ── blob: ──
    private fun fetchBlob(webView: WebView, url: String, cd: String?, mime: String?) {
        val fname = URLUtil.guessFileName(url, cd, mime)
        val js = "(function(){var x=new XMLHttpRequest();x.open('GET'," + quote(url) + ",true);" +
            "x.responseType='blob';x.onload=function(){var r=new FileReader();" +
            "r.onloadend=function(){AndroidBlobSaver.save(r.result," + quote(fname) + ");};" +
            "r.readAsDataURL(x.response);};" +
            "x.onerror=function(){AndroidBlobSaver.fail();};x.send();})();"
        webView.evaluateJavascript(js, null)
    }

    private fun quote(s: String): String =
        "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ") + "'"

    private class Bridge(private val activity: Activity) {
        @JavascriptInterface
        fun save(dataUrl: String, fileName: String) { saveDataUrl(activity, dataUrl, null, fileName) }

        @JavascriptInterface
        fun fail() { toast(activity, "Không đọc được tệp để tải") }
    }

    // ── data: (và dữ liệu blob đã chuyển thành data URL) ──
    private fun saveDataUrl(activity: Activity, dataUrl: String, cd: String?, forcedName: String? = null) {
        Thread {
            try {
                val comma = dataUrl.indexOf(',')
                if (comma < 0) throw IllegalArgumentException("data URL sai")
                val header = dataUrl.substring(5, comma)
                val isB64 = header.endsWith(";base64")
                val mime = header.substringBefore(';').ifBlank { "application/octet-stream" }
                val payload = dataUrl.substring(comma + 1)
                val bytes = if (isB64) Base64.decode(payload, Base64.DEFAULT)
                else URLDecoder.decode(payload, "UTF-8").toByteArray()
                var name = forcedName ?: URLUtil.guessFileName("file", cd, mime)
                if (!name.contains('.')) {
                    MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { name = "$name.$it" }
                }
                val result = writeToDownloads(activity, name, mime, bytes)
                main.post { showDone(activity, result.first, result.second, mime) }
            } catch (e: Exception) {
                toast(activity, "Lưu tệp lỗi: ${e.message}")
            }
        }.start()
    }

    /** Ghi byte vào thư mục Download công khai, trả về (tên cuối cùng, Uri). */
    private fun writeToDownloads(context: Context, name: String, mime: String, bytes: ByteArray): Pair<String, Uri?> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Không tạo được tệp trong Download")
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            return name to uri
        }
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        var f = File(dir, name)
        var n = 1
        while (f.exists()) {
            val base = name.substringBeforeLast('.', name)
            val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
            f = File(dir, "$base ($n)$ext"); n++
        }
        f.writeBytes(bytes)
        MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), arrayOf(mime), null)
        val uri = try {
            FileProvider.getUriForFile(context, "com.h.adblockbrowser2.fileprovider", f)
        } catch (_: Exception) { null }
        return f.name to uri
    }
}
