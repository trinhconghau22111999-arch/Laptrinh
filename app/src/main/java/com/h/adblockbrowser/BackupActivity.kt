package com.h.adblockbrowser

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale

/** Màn hình "Sao lưu & chia sẻ": gói dữ liệu app (các tài khoản đã đăng nhập, danh sách hồ sơ, tab,
 *  dấu trang, giao diện...) vào 1 tệp được MÃ HOÁ bằng mật khẩu người dùng đặt, rồi chia sẻ sang máy
 *  khác đã cài app này. Máy kia chọn "Khôi phục từ tệp sao lưu", nhập mật khẩu -> các tài khoản đã
 *  đăng nhập sẵn. Chi tiết dữ liệu nào được/không được sao lưu: xem [BackupManager]. */
class BackupActivity : AppCompatActivity() {

    private val dateFormat = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
    private val displayDateFormat = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())

    private var pendingSaveFile: File? = null
    private var progressDialog: AlertDialog? = null
    private var progressText: TextView? = null

    private val saveLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> onSaveTargetChosen(uri) }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) askRestorePassword(uri) }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.wp_slide_in_left, R.anim.wp_slide_out_right)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }

    override fun onDestroy() {
        hideProgress()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A0A0A.toInt())
            setPadding(dp(20), dp(48), dp(20), dp(20))
        }
        root.addView(TextView(this).apply {
            text = "Sao lưu & chia sẻ"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, 0, 0, dp(12))
        })
        root.addView(infoText(
            "Gói toàn bộ dữ liệu của app (các tài khoản đã đăng nhập, danh sách hồ sơ, tab, dấu trang, " +
                "giao diện) vào 1 tệp được MÃ HOÁ bằng mật khẩu bạn đặt, rồi gửi sang máy khác đã cài " +
                "sẵn app này. Máy kia khôi phục xong là các tài khoản đã đăng nhập sẵn."
        ))
        root.addView(bigButton("Sao lưu & chia sẻ", "Tạo tệp sao lưu rồi gửi qua Zalo, Drive, Bluetooth...") {
            askBackupPassword { pw -> runBackup(pw) { file -> shareFile(file) } }
        })
        root.addView(bigButton("Lưu tệp sao lưu vào máy", "Lưu ra bộ nhớ (ví dụ thư mục Download) để tự chép sang máy khác") {
            askBackupPassword { pw ->
                runBackup(pw) { file ->
                    pendingSaveFile = file
                    saveLauncher.launch(file.name)
                }
            }
        })
        root.addView(bigButton("Khôi phục từ tệp sao lưu", "Chọn tệp sao lưu đã nhận được trên máy này") {
            restoreLauncher.launch(arrayOf("*/*"))
        })
        root.addView(infoText(
            "Lưu ý: app không lưu chữ mật khẩu của bạn. Thứ được chuyển sang là PHIÊN ĐĂNG NHẬP của từng " +
                "tài khoản. Một số trang (như Google) có thể tự hỏi đăng nhập lại khi thấy máy mới - đó là " +
                "cơ chế bảo mật của trang đó.\n\n" +
                "Hãy giữ kín tệp sao lưu và mật khẩu: ai có cả hai sẽ vào được tài khoản của bạn. " +
                "Quên mật khẩu thì không mở lại được tệp."
        ).apply { setPadding(0, dp(20), 0, 0); textSize = 13f })

        val scroll = ScrollView(this).apply { addView(root) }
        val outer = FrameLayout(this)
        outer.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        outer.setBackgroundColor(0xFF0A0A0A.toInt())
        setContentView(outer)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(outer) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }
    }

    // ------------------------------------------------------------------ SAO LƯU

    private fun runBackup(password: CharArray, onDone: (File) -> Unit) {
        val outDir = File(cacheDir, "exports")
        outDir.mkdirs()
        // Xoá bản sao lưu cũ còn trong bộ nhớ đệm (dù đã mã hoá cũng không nên để lại).
        outDir.listFiles()?.forEach { if (it.name.endsWith("." + BackupManager.FILE_EXTENSION)) it.delete() }
        val outFile = File(outDir, "laptrinh-backup-${dateFormat.format(Date())}.${BackupManager.FILE_EXTENSION}")

        showProgress("Đang sao lưu...")
        Thread {
            try {
                val result = BackupManager.createBackup(this, password, outFile) { msg -> ui { updateProgress(msg) } }
                ui {
                    hideProgress()
                    if (result.webViewFiles == 0 && result.profiles > 0) {
                        Toast.makeText(this, "Chưa thấy dữ liệu đăng nhập nào để sao lưu - hãy mở hồ sơ và đăng nhập trước.", Toast.LENGTH_LONG).show()
                    }
                    onDone(result.file)
                }
            } catch (e: Exception) {
                outFile.delete()
                ui {
                    hideProgress()
                    showMessage("Sao lưu thất bại", e.message ?: e.toString())
                }
            } finally {
                Arrays.fill(password, '\u0000')
            }
        }.start()
    }

    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Sao lưu Lập trình")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Chia sẻ bản sao lưu"))
        } catch (e: Exception) {
            showMessage("Không chia sẻ được", e.message ?: e.toString())
        }
    }

    private fun onSaveTargetChosen(uri: android.net.Uri?) {
        val src = pendingSaveFile
        pendingSaveFile = null
        if (uri == null || src == null || !src.exists()) {
            Toast.makeText(this, "Đã huỷ lưu tệp", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            try {
                val out = contentResolver.openOutputStream(uri) ?: throw IOException("Không ghi được vào vị trí đã chọn")
                out.use { o -> src.inputStream().use { it.copyTo(o) } }
                ui { Toast.makeText(this, "Đã lưu tệp sao lưu", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                ui { showMessage("Lưu tệp thất bại", e.message ?: e.toString()) }
            }
        }.start()
    }

    // ------------------------------------------------------------------ KHÔI PHỤC

    private fun askRestorePassword(uri: android.net.Uri) {
        askPassword("Mật khẩu bản sao lưu", "Nhập mật khẩu đã đặt khi sao lưu.", confirm = false) { pw ->
            showProgress("Đang giải mã và kiểm tra tệp...")
            Thread {
                try {
                    val input = contentResolver.openInputStream(uri) ?: throw IOException("Không đọc được tệp đã chọn")
                    val plan = input.use {
                        BackupManager.prepareRestore(this, it, pw) { msg -> ui { updateProgress(msg) } }
                    }
                    ui {
                        hideProgress()
                        confirmRestore(plan)
                    }
                } catch (e: BackupCrypto.WrongPasswordException) {
                    ui {
                        hideProgress()
                        showMessage("Không mở được tệp", "Mật khẩu không đúng, hoặc tệp đã chọn không phải bản sao lưu của app.")
                    }
                } catch (e: Exception) {
                    ui {
                        hideProgress()
                        showMessage("Không đọc được bản sao lưu", e.message ?: e.toString())
                    }
                } finally {
                    Arrays.fill(pw, '\u0000')
                }
            }.start()
        }
    }

    private fun confirmRestore(plan: BackupManager.RestorePlan) {
        val whenText = if (plan.createdAt > 0) displayDateFormat.format(Date(plan.createdAt)) else "không rõ"
        val deviceText = if (plan.device.isNotBlank()) " trên máy ${plan.device}" else ""
        AlertDialog.Builder(this, R.style.Theme_WP_Dialog)
            .setTitle("Khôi phục bản sao lưu?")
            .setMessage(
                "Bản sao lưu tạo lúc $whenText$deviceText, gồm ${plan.profileCount} hồ sơ tài khoản.\n\n" +
                    "Toàn bộ dữ liệu hiện tại của app trên máy này (tài khoản đã đăng nhập, tab, dấu trang...) " +
                    "sẽ bị THAY THẾ bằng dữ liệu trong bản sao lưu. Các hồ sơ đang mở sẽ bị đóng."
            )
            .setPositiveButton("Khôi phục") { _, _ -> runRestore(plan) }
            .setNegativeButton("Huỷ") { _, _ -> BackupManager.discardRestore(plan) }
            .setOnCancelListener { BackupManager.discardRestore(plan) }
            .show()
    }

    private fun runRestore(plan: BackupManager.RestorePlan) {
        showProgress("Đang khôi phục...")
        Thread {
            try {
                BackupManager.applyRestore(this, plan) { msg -> ui { updateProgress(msg) } }
                ui {
                    hideProgress()
                    AlertDialog.Builder(this, R.style.Theme_WP_Dialog)
                        .setTitle("Khôi phục xong")
                        .setMessage("Các tài khoản đã được khôi phục. Mở lại ứng dụng để dùng.")
                        .setCancelable(false)
                        .setPositiveButton("Mở lại ứng dụng") { _, _ ->
                            val i = Intent(this, MainActivity::class.java)
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            startActivity(i)
                            finish()
                        }
                        .show()
                }
            } catch (e: Exception) {
                BackupManager.discardRestore(plan)
                ui {
                    hideProgress()
                    showMessage("Khôi phục thất bại", e.message ?: e.toString())
                }
            }
        }.start()
    }

    // ------------------------------------------------------------------ HỘP THOẠI + TIỆN ÍCH GIAO DIỆN

    private fun askBackupPassword(onOk: (CharArray) -> Unit) {
        askPassword(
            "Đặt mật khẩu cho bản sao lưu",
            "Mật khẩu này dùng để mở tệp ở máy kia (tối thiểu 6 ký tự). Quên mật khẩu thì không mở lại được tệp.",
            confirm = true,
            onOk = onOk
        )
    }

    private fun passwordField(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        setTextColor(Color.BLACK)
        setHintTextColor(0xFF888888.toInt())
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setSingleLine(true)
    }

    private fun askPassword(title: String, message: String, confirm: Boolean, onOk: (CharArray) -> Unit) {
        val first = passwordField("Mật khẩu")
        val second = if (confirm) passwordField("Nhập lại mật khẩu") else null
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), 0)
            addView(first)
            if (second != null) addView(second)
        }
        val dialog = AlertDialog.Builder(this, R.style.Theme_WP_Dialog)
            .setTitle(title)
            .setMessage(message)
            .setView(container)
            .setPositiveButton("OK", null)
            .setNegativeButton("Huỷ", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val len = first.text.length
                if (len == 0) {
                    first.error = "Hãy nhập mật khẩu"
                    return@setOnClickListener
                }
                if (confirm) {
                    if (len < 6) {
                        first.error = "Tối thiểu 6 ký tự"
                        return@setOnClickListener
                    }
                    if (first.text.toString() != second!!.text.toString()) {
                        second.error = "Hai mật khẩu không khớp"
                        return@setOnClickListener
                    }
                }
                val chars = CharArray(len)
                first.text.getChars(0, len, chars, 0)
                dialog.dismiss()
                onOk(chars)
            }
        }
        dialog.show()
    }

    private fun showProgress(message: String) {
        hideProgress()
        val text = TextView(this).apply {
            this.text = message
            setTextColor(Color.BLACK)
            textSize = 16f
            setPadding(dp(16), 0, 0, 0)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            addView(ProgressBar(this@BackupActivity))
            addView(text)
        }
        progressText = text
        progressDialog = AlertDialog.Builder(this, R.style.Theme_WP_Dialog)
            .setView(row)
            .setCancelable(false)
            .create()
            .also { it.show() }
    }

    private fun updateProgress(message: String) {
        progressText?.text = message
    }

    private fun hideProgress() {
        try {
            progressDialog?.dismiss()
        } catch (ignored: Exception) {
        }
        progressDialog = null
        progressText = null
    }

    private fun showMessage(title: String, message: String) {
        AlertDialog.Builder(this, R.style.Theme_WP_Dialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    /** Chạy [block] trên luồng giao diện, nhưng bỏ qua nếu màn hình đã đóng (tránh crash khi hiện hộp thoại). */
    private fun ui(block: () -> Unit) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) block()
        }
    }

    private fun infoText(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(0xFFB0B0B0.toInt())
        setPadding(0, 0, 0, dp(8))
    }

    private fun bigButton(title: String, subtitle: String, onClick: () -> Unit): View {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(12)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(ThemePrefs.accent(this@BackupActivity))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = lp
        }
        box.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        box.addView(TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(0xFFEAEAEA.toInt())
            setPadding(0, dp(4), 0, 0)
        })
        return box
    }
}
