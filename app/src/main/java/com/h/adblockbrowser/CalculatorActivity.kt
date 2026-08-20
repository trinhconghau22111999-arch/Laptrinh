package com.h.adblockbrowser

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Máy tính hiện ĐẦY ĐỦ phép tính đang gõ trên 1 dòng (vd "6-3", rồi "6-3=3" sau khi bấm "="),
 *  không chỉ hiện mỗi số cuối như trước - đúng cách 1 máy tính thật hoạt động. */
class CalculatorActivity : AppCompatActivity() {

    private var navBarHandle: WpNavBar.Handle? = null

    /** Thoát màn này kèm hiệu ứng "trượt ra bên phải" kiểu Windows Phone (xem [finishWp] ở
     *  UiUtils.kt), dù finish() được gọi từ đâu (nút Back nổi, mũi tên ◀, phím Back cứng...). */
    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.wp_slide_in_left, R.anim.wp_slide_out_right)
    }


    private lateinit var display: TextView
    private var expression = ""
    private var justEvaluated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            // Chừa khoảng trống bằng đúng chiều cao WpNavBar ở đáy, tránh hàng phím cuối
            // (0, ., =) bị thanh điều hướng nổi đè lên - xem addFloatingBackButton bên dưới.
            setPadding(0, 0, 0, dp(WpNavBar.HEIGHT_DP))
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 40, 0, 0)
        }
        root.addView(titleRow)

        display = TextView(this).apply {
            text = "0"
            textSize = 40f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(32, 80, 32, 40)
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 260)
        }
        root.addView(display)

        // Thêm khoảng trống co giãn TRƯỚC bàn phím để phím "hạ xuống" thấp hơn, cân đối hơn.
        val spacer = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.18f)
        }
        root.addView(spacer)

        val grid = GridLayout(this).apply {
            columnCount = 4
            rowCount = 5
            setPadding(0, 0, 0, 40)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.82f)
        }
        root.addView(grid)

        // Bỏ nút "±" (vừa cộng vừa trừ) theo yêu cầu - thay bằng "⌫" (xoá lùi 1 ký tự), hữu ích
        // hơn nhiều để sửa lỗi gõ nhầm mà không cần bấm "C" xoá sạch từ đầu.
        val labels = listOf(
            "C", "⌫", "%", "÷",
            "7", "8", "9", "×",
            "4", "5", "6", "−",
            "1", "2", "3", "+",
            "0", ".", "=", ""
        )

        for (label in labels) {
            if (label.isEmpty()) continue
            val btn = Button(this).apply {
                text = label
                textSize = 26f
                setTextColor(if (label in listOf("÷", "×", "−", "+", "=")) ThemePrefs.accent(this@CalculatorActivity) else 0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF0D0D0D.toInt())
                val lp = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                )
                lp.width = 0
                lp.height = 0
                lp.setMargins(6, 6, 6, 6)
                layoutParams = lp
                setOnClickListener { onKeyPress(label) }
            }
            grid.addView(btn)
        }

        // ── Thanh điều hướng 2 nút kiểu Windows Phone thật: ◁ Back / ⊞ Start - đồng bộ với
        // MainActivity/AccountsActivity/... (WpNavBar.kt). Màn này là màn "gốc" (không có
        // trang con để lùi), nên cả Back lẫn Start đều thoát về nơi đã mở màn này. ──
        val outer = FrameLayout(this)
        outer.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(outer)
        navBarHandle = WpNavBar.attach(
            activity = this,
            root = outer,
            onBack = { onBackPressed() },
            onStart = { onBackPressed() }
        )
    }

    override fun onResume() {
        super.onResume()
        navBarHandle?.resync()
    }

    override fun onDestroy() {
        navBarHandle?.detach()
        super.onDestroy()
    }

    private val opChars = "÷×−+"

    private fun lastOperandStart(): Int {
        val idx = expression.indexOfLast { it in opChars }
        return if (idx == -1) 0 else idx + 1
    }

    private fun onKeyPress(key: String) {
        when (key) {
            "C" -> {
                expression = ""
                justEvaluated = false
            }
            "⌫" -> {
                if (justEvaluated) {
                    expression = ""
                    justEvaluated = false
                } else if (expression.isNotEmpty()) {
                    expression = expression.dropLast(1)
                }
            }
            "%" -> {
                val start = lastOperandStart()
                val numPart = expression.substring(start)
                val num = numPart.toDoubleOrNull()
                if (num != null) {
                    expression = expression.substring(0, start) + (num / 100).toCleanString()
                }
            }
            "÷", "×", "−", "+" -> {
                if (justEvaluated) {
                    expression = expression.substringAfter("=", expression)
                    justEvaluated = false
                }
                if (expression.isEmpty()) {
                    // Cho phép bắt đầu bằng số âm
                    if (key == "−") expression = "−"
                } else {
                    val last = expression.last()
                    expression = if (last in opChars) expression.dropLast(1) + key else expression + key
                }
            }
            "=" -> {
                val result = evaluate(expression)
                if (result != null) {
                    expression = "$expression=${result.toCleanString()}"
                    justEvaluated = true
                }
            }
            "." -> {
                val start = lastOperandStart()
                val numPart = expression.substring(start)
                if (!numPart.contains(".")) {
                    expression += if (numPart.isEmpty()) "0." else "."
                }
            }
            else -> { // các phím số 0-9 - KHÔNG tự xoá số 0 đầu tiên nữa, gõ gì hiện đúng nấy
                if (justEvaluated) {
                    expression = key
                    justEvaluated = false
                } else {
                    expression += key
                }
            }
        }
        display.text = if (expression.isEmpty()) "0" else expression
    }

    /** Tính giá trị biểu thức theo thứ tự TỪ TRÁI SANG PHẢI (không ưu tiên nhân/chia trước, đúng
     *  kiểu máy tính bỏ túi cơ bản - đơn giản, dễ đoán kết quả). */
    private fun evaluate(expr: String): Double? {
        if (expr.isEmpty()) return null
        val cleanExpr = if (expr.startsWith("−")) "-" + expr.substring(1) else expr
        val tokens = Regex("(-?\\d+\\.?\\d*|[÷×−+])").findAll(cleanExpr).map { it.value }.toList()
        if (tokens.isEmpty()) return null
        var result = tokens[0].toDoubleOrNull() ?: return null
        var i = 1
        while (i < tokens.size - 1) {
            val op = tokens[i]
            val next = tokens.getOrNull(i + 1)?.toDoubleOrNull() ?: return result
            result = when (op) {
                "÷" -> if (next != 0.0) result / next else return null
                "×" -> result * next
                "−" -> result - next
                "+" -> result + next
                else -> result
            }
            i += 2
        }
        return result
    }

    private fun Double.toCleanString(): String {
        return if (this == this.toLong().toDouble()) this.toLong().toString() else this.toString()
    }
}
