package com.h.adblockbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Ghi lại lỗi làm văng app (kể cả ở các tiến trình hồ sơ :acctN / :incognito) vào 1 tệp, rồi lần mở
 *  app sau hiện hộp thoại chứa nguyên văn lỗi (có nút Sao chép) để biết đúng nguyên nhân. */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val proc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application_processName() else "?"
                val text = "Thời gian: ${java.util.Date()}\n" +
                    "Tiến trình: $proc | Luồng: ${thread.name}\n" +
                    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | ${Build.MANUFACTURER} ${Build.MODEL}\n\n" +
                    sw.toString().take(6000)
                File(app.filesDir, FILE).writeText(text)
            } catch (e: Throwable) { }
            previous?.uncaughtException(thread, throwable)
        }
    }

    @Suppress("NewApi")
    private fun Application_processName(): String = try { android.app.Application.getProcessName() } catch (e: Throwable) { "?" }

    /** Gọi ở onResume của màn hình chính / danh sách hồ sơ. */
    fun showIfAny(activity: Activity) {
        val f = File(activity.filesDir, FILE)
        if (!f.exists()) return
        val text = try { f.readText() } catch (e: Exception) { return }
        f.delete()
        if (text.isBlank() || activity.isFinishing) return
        val tv = TextView(activity).apply {
            this.text = text
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(24, 16, 24, 16)
        }
        AlertDialog.Builder(activity)
            .setTitle("App vừa bị lỗi văng")
            .setView(ScrollView(activity).apply { addView(tv) })
            .setPositiveButton("Sao chép") { _, _ ->
                val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", text))
                Toast.makeText(activity, "Đã sao chép", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Đóng", null)
            .show()
    }
}
