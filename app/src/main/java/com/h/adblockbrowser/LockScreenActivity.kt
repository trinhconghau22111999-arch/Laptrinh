package com.h.adblockbrowser

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Màn khoá app - hiện MỖI LẦN mở app nếu đã đặt PIN hoặc Hình ở Cài đặt. Nhập đúng mới vào được
 *  màn chính; bấm back chỉ đưa app xuống nền (không cho vượt qua bằng cách back ra). */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var patternView: PatternLockView
    private lateinit var tvHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val type = AppLockPrefs.lockType(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF000000.toInt())
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }

        val title = TextView(this).apply {
            text = "🔒 Nhập khoá để mở app"
            textSize = 18f
            setTextColor(0xFFC724FF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        }
        root.addView(title)

        if (type == "pattern") {
            tvHint = TextView(this).apply {
                text = "Vẽ hình mở khoá"
                setTextColor(0xFF888888.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(16))
            }
            root.addView(tvHint)
            patternView = PatternLockView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(280), dp(280))
                onPatternComplete = { pattern -> checkPattern(pattern) }
                onPatternTooShort = { Toast.makeText(this@LockScreenActivity, "Cần nối tối thiểu 4 chấm", Toast.LENGTH_SHORT).show() }
            }
            root.addView(patternView)
        } else {
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                hint = "Nhập mã PIN"
                setHintTextColor(0xFF666666.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            // Nhập đủ số là tự mở luôn, không cần bấm nút xác nhận: cứ 4 số trở lên là thử ngầm
            // (không báo sai ngay, vì PIN có thể dài hơn 4 số) - đúng lúc khớp thì mở ngay; nếu
            // gõ tới 6 số (độ dài PIN tối đa hợp lý) mà vẫn sai mới báo lỗi và xoá để nhập lại.
            input.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val pin = s?.toString() ?: return
                    if (pin.length < 4) return
                    if (AppLockPrefs.verify(this@LockScreenActivity, pin)) {
                        setResult(RESULT_OK)
                        finish()
                    } else if (pin.length >= 6) {
                        Toast.makeText(this@LockScreenActivity, "Sai mã PIN", Toast.LENGTH_SHORT).show()
                        input.setText("")
                    }
                }
            })
            val btnOk = Button(this).apply {
                text = "Mở khoá"
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFFC724FF.toInt())
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(16)
                layoutParams = lp
                setOnClickListener { checkPin(input.text.toString()) }
            }
            root.addView(input)
            root.addView(btnOk)
        }

        setContentView(root)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun checkPin(pin: String) {
        if (AppLockPrefs.verify(this, pin)) {
            setResult(RESULT_OK)
            finish()
        } else {
            Toast.makeText(this, "Sai mã PIN", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPattern(pattern: String) {
        if (AppLockPrefs.verify(this, pattern)) {
            setResult(RESULT_OK)
            finish()
        } else {
            Toast.makeText(this, "Sai hình mở khoá", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        // Không cho back để né qua màn khoá - chỉ đưa app xuống nền như bấm nút Home.
        moveTaskToBack(true)
    }
}
