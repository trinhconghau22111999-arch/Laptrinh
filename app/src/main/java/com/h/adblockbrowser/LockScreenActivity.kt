package com.h.adblockbrowser

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Locale

/** Màn khoá app - hiện MỖI LẦN mở app nếu đã đặt PIN hoặc Hình ở Cài đặt.
 *
 *  TRƯỚC ĐÂY: màn này chỉ là 1 form nhập PIN/hình căn giữa trên nền đen tuyệt đối - đúng như 1
 *  "dialog xin mật khẩu" bình thường, KHÔNG giống Lock Screen thật của Windows Phone/Windows 10
 *  Mobile chút nào (màn khoá thật của WP: ảnh nền toàn màn hình + đồng hồ/ngày tháng cỡ CỰC LỚN
 *  nằm giữa màn hình, KHÔNG có ô nhập gì cả - phải VUỐT LÊN thì lớp đồng hồ mới trượt biến mất,
 *  lúc đó mới lộ ra màn nhập PIN/hình bên dưới).
 *
 *  GIỜ: dựng đúng 2 LỚP xếp chồng như thật:
 *   1) [lockLayer] (LỚP TRÊN, hiện đầu tiên): ảnh nền + đồng hồ/ngày cỡ lớn kiểu Hub header +
 *      gợi ý "vuốt lên để mở khoá". Vuốt lên (hoặc chạm) layer này sẽ TRƯỢT nó lên và biến mất.
 *   2) [unlockLayer] (LỚP DƯỚI, luôn dựng sẵn nhưng bị che): form nhập PIN/hình như cũ, giữ
 *      NGUYÊN toàn bộ logic xác thực gốc (AppLockPrefs.verify) - chỉ đổi phần NHÌN THẤY, không
 *      đổi cách hoạt động, để không phá luồng khoá bảo mật đã có. */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var patternView: PatternLockView
    private lateinit var lockLayer: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val type = AppLockPrefs.lockType(this)
        val root = FrameLayout(this)

        // ── Lớp DƯỚI: form mở khoá thật (PIN hoặc hình), y hệt logic cũ ──
        val unlockLayer = buildUnlockLayer(type)
        root.addView(unlockLayer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // ── Lớp TRÊN: ảnh nền + đồng hồ lớn, đúng cảm giác Lock Screen thật, che kín lớp dưới
        //    cho tới khi người dùng vuốt lên/chạm vào ──
        lockLayer = buildLockLayer()
        root.addView(lockLayer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)
    }

    /** Lớp đồng hồ/ảnh nền - vuốt lên (kiểu Lock Screen thật) hoặc chạm nhẹ (cho ai không quen
     *  thao tác vuốt) đều trượt layer này lên và ẩn đi, lộ ra form mở khoá bên dưới. */
    private fun buildLockLayer(): View {
        val layer = FrameLayout(this)

        layer.addView(ImageView(this).apply {
            setImageResource(R.drawable.default_wallpaper)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Phủ tối nhẹ để chữ trắng luôn đọc được trên mọi ảnh nền, giống overlay thật của WP.
        layer.addView(View(this).apply { setBackgroundColor(0x66000000) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val clockColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sdfDate = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        val now = java.util.Date()
        clockColumn.addView(TextView(this).apply {
            text = sdfTime.format(now)
            textSize = 72f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        clockColumn.addView(TextView(this).apply {
            text = sdfDate.format(now)
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        layer.addView(clockColumn, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL })

        layer.addView(TextView(this).apply {
            text = "vuốt lên để mở khoá"
            textSize = 14f
            setTextColor(0xCCFFFFFF.toInt())
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM; it.bottomMargin = dp(48) })

        var downY = 0f
        layer.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downY = event.rawY; true }
                MotionEvent.ACTION_UP -> {
                    val dy = downY - event.rawY
                    if (dy > dp(60) || dy > -dp(20)) revealUnlockLayer() // vuốt lên rõ, HOẶC chỉ chạm nhẹ (không vuốt xuống)
                    true
                }
                else -> true
            }
        }
        return layer
    }

    /** Trượt lớp đồng hồ LÊN trên và ẩn hẳn - đúng hiệu ứng chuyển cảnh của Lock Screen thật khi
     *  vuốt lên, thay vì biến mất đột ngột. */
    private fun revealUnlockLayer() {
        if (lockLayer.translationY != 0f) return // đã trượt rồi, tránh gọi lại 2 lần
        lockLayer.animate()
            .translationY(-lockLayer.height.toFloat())
            .setDuration(220)
            .withEndAction { lockLayer.visibility = View.GONE }
            .start()
    }

    private fun buildUnlockLayer(type: String): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF000000.toInt())
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }

        val title = TextView(this).apply {
            text = "nhập khoá để mở app"
            textSize = 24f
            setTextColor(ThemePrefs.accent(this@LockScreenActivity))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, 0, 0, dp(24))
        }
        root.addView(title)

        if (type == "pattern") {
            root.addView(TextView(this).apply {
                text = "vẽ hình mở khoá"
                setTextColor(0xFF888888.toInt())
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                setPadding(0, 0, 0, dp(16))
            })
            patternView = PatternLockView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(280), dp(280))
                onPatternComplete = { pattern -> checkPattern(pattern) }
                onPatternTooShort = { Toast.makeText(this@LockScreenActivity, "Cần nối tối thiểu 4 chấm", Toast.LENGTH_SHORT).show() }
            }
            root.addView(patternView)
        } else {
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                // Ô nhập PIN kiểu Metro: chỉ có 1 khung mảnh màu accent, không bo góc.
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.TRANSPARENT)
                    setStroke(dp(2), ThemePrefs.accent(this@LockScreenActivity))
                }
                setPadding(dp(4), dp(4), dp(4), dp(10))
                hint = "• • • •"
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
            root.addView(input)
        }

        return root
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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
