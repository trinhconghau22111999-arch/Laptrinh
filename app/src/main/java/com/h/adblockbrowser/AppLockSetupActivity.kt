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

class AppLockSetupActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private var firstPin: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF000000.toInt())
            setPadding(dp(32), dp(40), dp(32), dp(32))
        }
        setContentView(root)
        showMenu()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun clearRoot() { root.removeAllViews() }

    private fun addTitle(text: String) {
        root.addView(TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(0xFF0078D7.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        })
    }

    private fun showMenu() {
        clearRoot()
        val current = AppLockPrefs.lockType(this)
        val pinLabel = if (current == "pin") "Đổi mã PIN" else "Đặt mã PIN"
        root.addView(menuButton(pinLabel) { firstPin = null; showPinStep(1) })
    }

    private fun menuButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0xFF1A1A1A.toInt())
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8)
        layoutParams = lp
        setOnClickListener { onClick() }
    }

    private fun showPinStep(step: Int) {
        clearRoot()
        addTitle(if (step == 1) "Nhập mã PIN mới (tối thiểu 4 số)" else "Nhập lại mã PIN để xác nhận")
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        root.addView(input)
        root.addView(menuButton("Tiếp tục") {
            val pin = input.text.toString()
            if (pin.length < 4) {
                Toast.makeText(this, "PIN cần tối thiểu 4 số", Toast.LENGTH_SHORT).show()
                return@menuButton
            }
            if (step == 1) {
                firstPin = pin
                showPinStep(2)
            } else {
                if (pin == firstPin) {
                    AppLockPrefs.setPin(this, pin)
                    Toast.makeText(this, "Đã đặt mã PIN", Toast.LENGTH_SHORT).show()
                    showMenu()
                } else {
                    Toast.makeText(this, "Hai lần nhập không khớp, thử lại", Toast.LENGTH_SHORT).show()
                    firstPin = null
                    showPinStep(1)
                }
            }
        })
        root.addView(menuButton("Huỷ") { showMenu() })
    }
}
