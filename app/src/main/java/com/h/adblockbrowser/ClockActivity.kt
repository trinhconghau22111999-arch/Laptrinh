package com.h.adblockbrowser

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ClockActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvSleepTime: TextView
    private lateinit var tvSleepDate: TextView

    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)
            val dateStr = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("vi")).format(now)
            tvTime.text = timeStr
            tvDate.text = dateStr
            tvSleepTime.text = timeStr
            tvSleepDate.text = dateStr
            handler.postDelayed(this, 1000)
        }
    }

    // ── Chế độ ngủ (screensaver): không thao tác gì quá 30s -> chỉ hiện đồng hồ to, ẩn Bấm giờ/
    // Báo thức. Chạm 2 lần (double-tap) để quay lại màn hình đầy đủ như lúc trước. ──
    private lateinit var normalContent: View
    private lateinit var sleepOverlay: View
    private var isSleeping = false
    private lateinit var gestureDetector: GestureDetector
    private val IDLE_TIMEOUT_MS = 30_000L
    private val sleepRunnable = Runnable { enterSleepMode() }

    // ── Bấm giờ (stopwatch) ──
    private lateinit var tvStopwatch: TextView
    private lateinit var btnStopwatchToggle: Button
    private var swRunning = false
    private var swStartBase = 0L      // mốc thời gian hệ thống khi bắt đầu/tiếp tục chạy
    private var swAccumulated = 0L    // tổng thời gian đã chạy trước đó (khi tạm dừng)

    private val swTick = object : Runnable {
        override fun run() {
            if (swRunning) {
                val elapsed = swAccumulated + (System.currentTimeMillis() - swStartBase)
                tvStopwatch.text = formatStopwatch(elapsed)
                handler.postDelayed(this, 50)
            }
        }
    }

    // ── Báo thức ──
    private lateinit var alarmsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val outer = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }
        normalContent = root

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 40, 20, 0)
        }
        root.addView(titleRow)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(32))
        }

        // ── Đồng hồ chính ──
        val clockCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(24))
        }
        tvTime = TextView(this).apply {
            textSize = 48f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setShadowLayer(20f, 0f, 0f, ThemePrefs.accent(this@ClockActivity))
        }
        tvDate = TextView(this).apply {
            textSize = 16f
            setTextColor(ThemePrefs.accent(this@ClockActivity))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }
        clockCol.addView(tvTime)
        clockCol.addView(tvDate)
        content.addView(clockCol)

        content.addView(divider())

        // ── Bấm giờ ──
        content.addView(sectionTitle("⏱ Bấm giờ"))
        tvStopwatch = TextView(this).apply {
            text = "00:00.0"
            textSize = 40f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(16))
        }
        content.addView(tvStopwatch)

        val swBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        btnStopwatchToggle = Button(this).apply {
            text = "Bắt đầu"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(ThemePrefs.accent(this@ClockActivity))
            setOnClickListener { toggleStopwatch() }
        }
        val btnReset = Button(this).apply {
            text = "Đặt lại"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF333333.toInt())
            setOnClickListener { resetStopwatch() }
        }
        val btnLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(8), 0, dp(8), 0)
        }
        swBtnRow.addView(btnStopwatchToggle, btnLp)
        swBtnRow.addView(btnReset, btnLp)
        content.addView(swBtnRow)

        content.addView(divider())

        // ── Báo thức ──
        val alarmTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val alarmTitle = TextView(this).apply {
            text = "⏰ Báo thức"
            textSize = 16f
            setTextColor(ThemePrefs.accent(this@ClockActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnAddAlarm = TextView(this).apply {
            text = "+ Thêm"
            textSize = 14f
            setTextColor(ThemePrefs.accent(this@ClockActivity))
            isClickable = true
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { showAddAlarmDialog() }
        }
        alarmTitleRow.addView(alarmTitle)
        alarmTitleRow.addView(btnAddAlarm)
        content.addView(alarmTitleRow)

        alarmsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        content.addView(alarmsContainer)

        scroll.addView(content)
        root.addView(scroll)
        outer.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // ── Màn hình ngủ: chỉ đồng hồ to, giữa màn hình, không có gì khác ──
        sleepOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF000000.toInt())
            visibility = View.GONE
        }
        tvSleepTime = TextView(this).apply {
            textSize = 64f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setShadowLayer(28f, 0f, 0f, ThemePrefs.accent(this@ClockActivity))
        }
        tvSleepDate = TextView(this).apply {
            textSize = 18f
            setTextColor(ThemePrefs.accent(this@ClockActivity))
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }
        val tvSleepHint = TextView(this).apply {
            text = "Chạm 2 lần để mở"
            textSize = 12f
            setTextColor(0xFF555555.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(40), 0, 0)
        }
        (sleepOverlay as LinearLayout).addView(tvSleepTime)
        (sleepOverlay as LinearLayout).addView(tvSleepDate)
        (sleepOverlay as LinearLayout).addView(tvSleepHint)
        outer.addView(sleepOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        setContentView(outer)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isSleeping) exitSleepMode()
                return true
            }
        })

        handler.post(tick)
        renderAlarms()
        resetIdleTimer()
    }

    // Theo dõi MỌI lượt chạm trên toàn màn hình (kể cả trên nút bấm) mà không "cướp" chạm khỏi
    // các nút đó - dùng dispatchTouchEvent thay vì gắn OnTouchListener lên root.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        if (!isSleeping && ev.action == MotionEvent.ACTION_DOWN) {
            resetIdleTimer()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun resetIdleTimer() {
        handler.removeCallbacks(sleepRunnable)
        handler.postDelayed(sleepRunnable, IDLE_TIMEOUT_MS)
    }

    private fun enterSleepMode() {
        isSleeping = true
        normalContent.visibility = View.GONE
        sleepOverlay.visibility = View.VISIBLE
    }

    private fun exitSleepMode() {
        isSleeping = false
        sleepOverlay.visibility = View.GONE
        normalContent.visibility = View.VISIBLE
        resetIdleTimer()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(ThemePrefs.accent(this@ClockActivity))
        setPadding(0, dp(4), 0, 0)
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(0xFF222222.toInt())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply {
            topMargin = dp(20); bottomMargin = dp(12)
        }
    }

    // ── Bấm giờ ──
    private fun toggleStopwatch() {
        if (swRunning) {
            swAccumulated += System.currentTimeMillis() - swStartBase
            swRunning = false
            btnStopwatchToggle.text = "Tiếp tục"
            handler.removeCallbacks(swTick)
        } else {
            swStartBase = System.currentTimeMillis()
            swRunning = true
            btnStopwatchToggle.text = "Tạm dừng"
            handler.post(swTick)
        }
    }

    private fun resetStopwatch() {
        swRunning = false
        swAccumulated = 0L
        handler.removeCallbacks(swTick)
        tvStopwatch.text = "00:00.0"
        btnStopwatchToggle.text = "Bắt đầu"
    }

    private fun formatStopwatch(ms: Long): String {
        val totalTenths = ms / 100
        val minutes = totalTenths / 600
        val seconds = (totalTenths / 10) % 60
        val tenths = totalTenths % 10
        return "%02d:%02d.%d".format(minutes, seconds, tenths)
    }

    // ── Báo thức ──
    private fun renderAlarms() {
        alarmsContainer.removeAllViews()
        val alarms = AlarmsStore.all(this)
        if (alarms.isEmpty()) {
            alarmsContainer.addView(TextView(this).apply {
                text = "Chưa có báo thức nào."
                setTextColor(0xFF888888.toInt())
                textSize = 13f
                setPadding(0, dp(4), 0, dp(4))
            })
            return
        }
        for (alarm in alarms) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(0xFF0D0D0D.toInt())
                setPadding(dp(14), dp(10), dp(10), dp(10))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(6)
                layoutParams = lp
            }
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = "%02d:%02d".format(alarm.hour, alarm.minute)
                textSize = 20f
                setTextColor(if (alarm.enabled) 0xFFFFFFFF.toInt() else 0xFF666666.toInt())
            })
            if (alarm.label.isNotBlank()) {
                textCol.addView(TextView(this).apply {
                    text = alarm.label
                    textSize = 12f
                    setTextColor(0xFF888888.toInt())
                })
            }
            val toggle = Switch(this).apply {
                isChecked = alarm.enabled
                thumbTintList = ColorStateList.valueOf(ThemePrefs.accent(this@ClockActivity))
                setOnCheckedChangeListener { _, checked ->
                    AlarmsStore.setEnabled(this@ClockActivity, alarm.id, checked)
                    if (checked) scheduleAlarm(alarm.copy(enabled = true))
                    else AlarmScheduler.cancel(this@ClockActivity, alarm.id)
                }
            }
            val btnDelete = TextView(this).apply {
                text = "✕"
                textSize = 16f
                setTextColor(0xFF888888.toInt())
                setPadding(dp(14), dp(4), dp(4), dp(4))
                isClickable = true
                setOnClickListener {
                    AlarmsStore.delete(this@ClockActivity, alarm.id)
                    AlarmScheduler.cancel(this@ClockActivity, alarm.id)
                    renderAlarms()
                }
            }
            row.addView(textCol)
            row.addView(toggle)
            row.addView(btnDelete)
            alarmsContainer.addView(row)
        }
    }

    private fun scheduleAlarm(alarm: AlarmItem) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, alarm.hour)
        cal.set(Calendar.MINUTE, alarm.minute)
        cal.set(Calendar.SECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        AlarmScheduler.schedule(
            this, cal.timeInMillis, alarm.id,
            "Báo thức", alarm.label.ifBlank { "Đã đến giờ hẹn" },
            isAlarm = true, repeatDaily = true, soundIndex = alarm.soundIndex
        )
    }

    private fun showAddAlarmDialog() {
        val now = Calendar.getInstance()
        var hour = now.get(Calendar.HOUR_OF_DAY)
        var minute = now.get(Calendar.MINUTE)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        val tvPick = TextView(this).apply {
            text = "Giờ: %02d:%02d".format(hour, minute)
            textSize = 16f
            setTextColor(ThemePrefs.accent(this@ClockActivity))
            isClickable = true
            setPadding(0, dp(8), 0, dp(16))
        }
        val input = EditText(this).apply {
            hint = "Ghi chú (không bắt buộc)"
            setTextColor(0xFFFFFFFF.toInt())
        }
        tvPick.setOnClickListener {
            TimePickerDialog(this, android.R.style.Theme_Material_Dialog, { _, h, m ->
                hour = h; minute = m
                tvPick.text = "Giờ: %02d:%02d".format(hour, minute)
            }, hour, minute, true).show()
        }
        val soundNames = AlarmSounds.options(this).map { it.first }
        var soundIndex = 0
        val tvSound = TextView(this).apply {
            text = "Âm báo: ${soundNames.getOrElse(0) { "Mặc định" }}"
            textSize = 14f
            setTextColor(ThemePrefs.accent(this@ClockActivity))
            isClickable = true
            setPadding(0, dp(12), 0, dp(4))
            setOnClickListener {
                AlertDialog.Builder(this@ClockActivity)
                    .setTitle("Chọn âm báo thức")
                    .setSingleChoiceItems(soundNames.toTypedArray(), soundIndex) { dialog, which ->
                        soundIndex = which
                        text = "Âm báo: ${soundNames[which]}"
                        dialog.dismiss()
                    }
                    .show()
            }
        }
        container.addView(tvPick)
        container.addView(input)
        container.addView(tvSound)

        AlertDialog.Builder(this)
            .setTitle("Thêm báo thức")
            .setView(container)
            .setPositiveButton("Lưu") { _, _ ->
                val alarm = AlarmsStore.add(this, hour, minute, input.text.toString().trim(), soundIndex)
                scheduleAlarm(alarm)
                renderAlarms()
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(swTick)
        handler.removeCallbacks(sleepRunnable)
        super.onDestroy()
    }
}
