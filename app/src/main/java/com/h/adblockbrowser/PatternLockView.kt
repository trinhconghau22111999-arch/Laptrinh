package com.h.adblockbrowser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** Khoá hình 3x3 kiểu Android gốc - kéo ngón tay nối các chấm theo thứ tự để tạo/nhập mật khẩu
 *  hình. Tối thiểu 4 chấm mới hợp lệ (giống chuẩn khoá hình Android). */
class PatternLockView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val dotX = FloatArray(9)
    private val dotY = FloatArray(9)
    private val selected = ArrayList<Int>()
    private var curX = 0f
    private var curY = 0f
    private var tracking = false

    var onPatternComplete: ((String) -> Unit)? = null
    var onPatternTooShort: (() -> Unit)? = null

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A3A3A.toInt() }
    private val selectedDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0078D7.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0078D7.toInt(); style = Paint.Style.STROKE; strokeWidth = 10f; strokeCap = Paint.Cap.ROUND
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cell = minOf(w, h) / 3f
        val offsetX = (w - cell * 3) / 2f
        val offsetY = (h - cell * 3) / 2f
        for (row in 0..2) {
            for (col in 0..2) {
                val idx = row * 3 + col
                dotX[idx] = offsetX + cell * col + cell / 2f
                dotY[idx] = offsetY + cell * row + cell / 2f
            }
        }
    }

    private fun dotRadius() = minOf(width, height) / 3f * 0.10f
    private fun hitRadius() = minOf(width, height) / 3f * 0.38f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                selected.clear()
                tracking = true
                curX = event.x; curY = event.y
                checkHit(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return true
                curX = event.x; curY = event.y
                checkHit(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
                if (selected.size >= 4) {
                    onPatternComplete?.invoke(selected.joinToString("-"))
                } else if (selected.isNotEmpty()) {
                    onPatternTooShort?.invoke()
                }
                selected.clear()
                invalidate()
            }
        }
        return true
    }

    private fun checkHit(x: Float, y: Float) {
        for (i in 0 until 9) {
            if (selected.contains(i)) continue
            val dx = x - dotX[i]; val dy = y - dotY[i]
            if (dx * dx + dy * dy <= hitRadius() * hitRadius()) {
                selected.add(i)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (i in 0 until selected.size - 1) {
            val a = selected[i]; val b = selected[i + 1]
            canvas.drawLine(dotX[a], dotY[a], dotX[b], dotY[b], linePaint)
        }
        if (tracking && selected.isNotEmpty()) {
            val last = selected.last()
            canvas.drawLine(dotX[last], dotY[last], curX, curY, linePaint)
        }
        for (i in 0 until 9) {
            canvas.drawCircle(dotX[i], dotY[i], dotRadius(), if (selected.contains(i)) selectedDotPaint else dotPaint)
        }
    }
}
