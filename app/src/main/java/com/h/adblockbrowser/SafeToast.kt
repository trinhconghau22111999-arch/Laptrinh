package com.h.adblockbrowser

import android.content.Context
import android.widget.Toast

/** Toast dùng applicationContext. Trên Android 7.1.1 (Lenovo YB1-X90F), Toast.makeText(activity, ...) bị
 *  AppCompat/Material chen vào lúc nạp layout toast hệ thống -> InflateException + ArrayIndexOutOfBounds
 *  (StringBlock) và văng app. Context ứng dụng không có bộ nạp view của AppCompat nên tránh được lỗi đó. */
object SafeToast {
    fun make(context: Context, text: CharSequence, duration: Int): Toast =
        Toast.makeText(context.applicationContext, text, duration)

    fun make(context: Context, resId: Int, duration: Int): Toast =
        Toast.makeText(context.applicationContext, resId, duration)
}
