package com.h.adblockbrowser

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class CalendarActivity : AppCompatActivity() {

    /** Thoát màn này kèm hiệu ứng "trượt ra bên phải" kiểu Windows Phone (xem [finishWp] ở
     *  UiUtils.kt), dù finish() được gọi từ đâu (nút Back nổi, mũi tên ◀, phím Back cứng...). */
    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.wp_slide_in_left, R.anim.wp_slide_out_right)
    }


    private val today = Calendar.getInstance()
    private var viewYear = today.get(Calendar.YEAR)
    private var viewMonth = today.get(Calendar.MONTH) // 0-11
    private var selDay = today.get(Calendar.DAY_OF_MONTH)
    private var selMonth = viewMonth
    private var selYear = viewYear

    private lateinit var monthLabel: TextView
    private lateinit var dayGrid: GridLayout
    private lateinit var selectedLabel: TextView
    private lateinit var notesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }

        val monthRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), statusBarHeight() + dp(8), dp(16), dp(8))
        }
        val btnPrev = TextView(this).apply {
            text = "‹"; textSize = 24f; setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(16), dp(6), dp(16), dp(6))
            isClickable = true
            setOnClickListener { changeMonth(-1) }
        }
        monthLabel = TextView(this).apply {
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnNext = TextView(this).apply {
            text = "›"; textSize = 24f; setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(16), dp(6), dp(16), dp(6))
            isClickable = true
            setOnClickListener { changeMonth(1) }
        }
        monthRow.addView(btnPrev)
        monthRow.addView(monthLabel)
        monthRow.addView(btnNext)
        root.addView(monthRow)

        // ── Hàng tên thứ ──
        val weekRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (name in listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")) {
            weekRow.addView(TextView(this).apply {
                text = name
                textSize = 12f
                setTextColor(0xFF888888.toInt())
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        root.addView(weekRow)

        // ── Lưới ngày (tự vẽ, không dùng CalendarView có sẵn - để nhét được số âm lịch nhỏ dưới
        //    mỗi ô, điều mà CalendarView gốc của Android không cho tuỳ biến từng ô) ──
        dayGrid = GridLayout(this).apply {
            columnCount = 7
            rowCount = 6
            setPadding(dp(6), dp(4), dp(6), dp(8))
        }
        root.addView(dayGrid)

        selectedLabel = TextView(this).apply {
            textSize = 15f
            setTextColor(ThemePrefs.accent(this@CalendarActivity))
            setPadding(dp(20), dp(16), dp(20), dp(4))
        }

        notesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(8))
        }

        val btnAdd = Button(this).apply {
            text = "+  Thêm ghi chú / nhắc nhở"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(ThemePrefs.accent(this@CalendarActivity))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(20), dp(8), dp(20), dp(24))
            layoutParams = lp
            setOnClickListener { showAddNoteDialog() }
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val scrollContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollContent.addView(selectedLabel)
        scrollContent.addView(notesContainer)
        scrollContent.addView(btnAdd)
        scroll.addView(scrollContent)
        root.addView(scroll)

        setContentView(root)
        renderMonth()
        selectDay(selDay, selMonth, selYear)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun changeMonth(delta: Int) {
        viewMonth += delta
        if (viewMonth > 11) { viewMonth = 0; viewYear++ }
        if (viewMonth < 0) { viewMonth = 11; viewYear-- }
        renderMonth()
    }

    private fun renderMonth() {
        val monthNames = listOf(
            "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4", "Tháng 5", "Tháng 6",
            "Tháng 7", "Tháng 8", "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12"
        )
        monthLabel.text = "${monthNames[viewMonth]}, $viewYear"

        dayGrid.removeAllViews()

        val cal = Calendar.getInstance()
        cal.set(viewYear, viewMonth, 1)
        // Calendar.DAY_OF_WEEK: CN=1,T2=2...T7=7 -> quy đổi sang cột bắt đầu từ T2=0..CN=6
        val firstDow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val notesDates = NotesStore.datesWithNotes(this)

        var cellCount = 0
        repeat(firstDow) {
            dayGrid.addView(buildEmptyCell())
            cellCount++
        }
        for (d in 1..daysInMonth) {
            val isToday = (d == today.get(Calendar.DAY_OF_MONTH) && viewMonth == today.get(Calendar.MONTH) && viewYear == today.get(Calendar.YEAR))
            val isSelected = (d == selDay && viewMonth == selMonth && viewYear == selYear)
            val dateKey = dateKey(d, viewMonth, viewYear)
            val hasNote = notesDates.contains(dateKey)
            dayGrid.addView(buildDayCell(d, isToday, isSelected, hasNote))
            cellCount++
        }
        while (cellCount < 42) {
            dayGrid.addView(buildEmptyCell())
            cellCount++
        }
    }

    private fun buildEmptyCell(): View {
        val cell = LinearLayout(this)
        val lp = GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f)
        )
        lp.width = 0; lp.height = dp(52)
        cell.layoutParams = lp
        return cell
    }

    private fun buildDayCell(day: Int, isToday: Boolean, isSelected: Boolean, hasNote: Boolean): View {
        val lunar = try {
            LunarCalendar.solarToLunar(day, viewMonth + 1, viewYear)
        } catch (e: Exception) { intArrayOf(0, 0, 0, 0) }

        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            if (isSelected) {
                setBackgroundColor(ThemePrefs.accentWithAlpha(this@CalendarActivity, 0x33))
            } else if (isToday) {
                setBackgroundColor(ThemePrefs.accentWithAlpha(this@CalendarActivity, 0x1A))
            }
            setOnClickListener { selectDay(day, viewMonth, viewYear) }
        }
        val lp = GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f)
        )
        lp.width = 0; lp.height = dp(52)
        lp.setMargins(2, 2, 2, 2)
        cell.layoutParams = lp

        cell.addView(TextView(this).apply {
            text = day.toString()
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(if (isToday) ThemePrefs.accent(this@CalendarActivity) else 0xFFFFFFFF.toInt())
        })
        // Ngày âm lịch - chữ tím neon nhỏ phía dưới số ngày dương. Mùng 1 âm hiện kèm tháng âm
        // cho dễ nhận biết đầu tháng âm, giống các app lịch âm phổ biến.
        cell.addView(TextView(this).apply {
            text = if (lunar[0] == 1) "${lunar[0]}/${lunar[1]}" else lunar[0].toString()
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(0xFFE38CFF.toInt())
            setShadowLayer(6f, 0f, 0f, ThemePrefs.accent(this@CalendarActivity))
        })
        if (hasNote) {
            cell.addView(View(this).apply {
                val dotLp = LinearLayout.LayoutParams(dp(4), dp(4))
                dotLp.topMargin = dp(1)
                layoutParams = dotLp
                setBackgroundColor(ThemePrefs.accent(this@CalendarActivity))
            })
        }
        return cell
    }

    private fun selectDay(d: Int, m: Int, y: Int) {
        selDay = d; selMonth = m; selYear = y
        selectedLabel.text = "Ghi chú — %02d/%02d/%d".format(d, m + 1, y)
        renderMonth()
        renderNotes()
    }

    private fun dateKey(d: Int, m: Int, y: Int) = "%04d-%02d-%02d".format(y, m + 1, d)

    private fun renderNotes() {
        notesContainer.removeAllViews()
        val key = dateKey(selDay, selMonth, selYear)
        val notes = NotesStore.notesForDate(this, key)
        if (notes.isEmpty()) {
            notesContainer.addView(TextView(this).apply {
                text = "Chưa có ghi chú nào cho ngày này."
                setTextColor(0xFF888888.toInt())
                textSize = 13f
                setPadding(0, dp(4), 0, dp(4))
            })
            return
        }
        for (note in notes) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(0xFF0D0D0D.toInt())
                setPadding(dp(12), dp(10), dp(12), dp(10))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(6)
                layoutParams = lp
            }
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = note.title
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
            })
            if (note.timeMinutes >= 0) {
                textCol.addView(TextView(this).apply {
                    text = "⏰ %02d:%02d".format(note.timeMinutes / 60, note.timeMinutes % 60)
                    setTextColor(ThemePrefs.accent(this@CalendarActivity))
                    textSize = 12f
                })
            }
            val btnDelete = TextView(this).apply {
                text = "✕"
                textSize = 16f
                setTextColor(0xFF888888.toInt())
                setPadding(dp(10), dp(4), dp(4), dp(4))
                isClickable = true
                setOnClickListener {
                    NotesStore.deleteNote(this@CalendarActivity, key, note.id)
                    AlarmScheduler.cancel(this@CalendarActivity, note.id.toInt())
                    renderNotes()
                    renderMonth()
                }
            }
            row.addView(textCol)
            row.addView(btnDelete)
            notesContainer.addView(row)
        }
    }

    private fun showAddNoteDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        val input = EditText(this).apply {
            hint = "Nội dung ghi chú"
            setTextColor(0xFFFFFFFF.toInt())
        }
        var hour = 8; var minute = 0
        // Dùng toggle kiểu WP dùng chung (xem UiUtils.buildWpToggle) thay cho CheckBox mặc định
        // của Android (vuông bo góc + ripple Material, luôn chỏi trên nền phẳng WP) - để đồng bộ
        // với đúng 1 kiểu công tắc bật/tắt duy nhất trong toàn app (giống công tắc báo thức ở
        // ClockActivity), thay vì mỗi màn hình 1 kiểu control khác nhau.
        val tvRemindLabel = TextView(this).apply {
            text = "Hẹn giờ nhắc"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
        }
        val tvTime = TextView(this).apply {
            text = "Giờ nhắc: %02d:%02d".format(hour, minute)
            setTextColor(ThemePrefs.accent(this@CalendarActivity))
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
            isClickable = true
            setOnClickListener {
                TimePickerDialog(this@CalendarActivity, R.style.Theme_WP_Dialog, { _, h, m ->
                    hour = h; minute = m
                    text = "Giờ nhắc: %02d:%02d".format(hour, minute)
                }, hour, minute, true).show()
            }
        }
        var remindChecked = false
        val toggleRemind = buildWpToggle(false) { checked ->
            remindChecked = checked
            tvTime.visibility = if (checked) View.VISIBLE else View.GONE
        }
        val remindRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
            addView(tvRemindLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(toggleRemind)
        }
        container.addView(input)
        container.addView(remindRow)
        container.addView(tvTime)

        AlertDialog.Builder(this, R.style.Theme_WP_Dialog)
            .setTitle("Thêm ghi chú")
            .setView(container)
            .setPositiveButton("Lưu") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, "Nội dung không được để trống", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val key = dateKey(selDay, selMonth, selYear)
                val timeMinutes = if (remindChecked) hour * 60 + minute else -1
                val note = NotesStore.addNote(this, key, text, timeMinutes)
                if (timeMinutes >= 0) {
                    val triggerCal = Calendar.getInstance()
                    triggerCal.set(selYear, selMonth, selDay, hour, minute, 0)
                    if (triggerCal.timeInMillis > System.currentTimeMillis()) {
                        AlarmScheduler.schedule(
                            this, triggerCal.timeInMillis, note.id.toInt(),
                            "Nhắc nhở", text, isAlarm = false
                        )
                    }
                }
                renderNotes()
                renderMonth()
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }
}
