package com.h.adblockbrowser

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

/** FrameLayout cho phép CHỤM/DÃN 2 ngón để phóng to - thu nhỏ 1 view con (dùng cho widget
 *  giờ/ngày nổi ở trang chủ), mà KHÔNG chặn mất sự kiện chạm 1 ngón (tap) trên các view con bên
 *  trong (ví dụ bấm vào giờ/phút hoặc ngày/tháng để mở màn hình tương ứng vẫn hoạt động bình
 *  thường).
 *
 *  CÁCH HOẠT ĐỘNG: chỉ can thiệp (intercept) chuỗi sự kiện chạm khi phát hiện NGÓN TAY THỨ 2 đặt
 *  xuống (pointerCount >= 2) - lúc đó Android tự động gửi ACTION_CANCEL xuống view con đang giữ
 *  sự kiện (nếu có, ví dụ TextView giờ đang chờ xác nhận tap) và mọi sự kiện tiếp theo của chuỗi
 *  chạm này được xử lý ngay tại đây bằng [ScaleGestureDetector] để tính tỉ lệ phóng to/thu nhỏ.
 *  Khi chỉ có 1 ngón (chạm/tap bình thường) thì KHÔNG can thiệp gì cả, để sự kiện đi thẳng xuống
 *  view con như mặc định. */
class PinchZoomLayout(context: Context) : FrameLayout(context) {

    /** Gọi mỗi khi tỉ lệ phóng to/thu nhỏ thay đổi - factor > 1 là đang phóng to, < 1 là thu nhỏ,
     *  nhân dồn (lũy tích) với tỉ lệ hiện tại ở nơi gọi để ra tỉ lệ mới. */
    var onScale: ((factor: Float) -> Unit)? = null

    private var intercepting = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                onScale?.invoke(detector.scaleFactor)
                return true
            }
        }
    )

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (ev.pointerCount >= 2) intercepting = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> intercepting = false
        }
        return intercepting
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            intercepting = false
        }
        return true
    }
}
