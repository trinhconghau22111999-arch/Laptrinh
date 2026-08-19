package com.h.adblockbrowser

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Bọc AlarmManager cho gọn - dùng cho nhắc nhở theo ngày ở CalendarActivity, bắn tới
 *  [ReminderReceiver]. */
object AlarmScheduler {

    fun schedule(
        context: Context, triggerAtMillis: Long, notifId: Int,
        title: String, message: String, isAlarm: Boolean, repeatDaily: Boolean = false, soundIndex: Int = 0
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_MESSAGE, message)
            putExtra(ReminderReceiver.EXTRA_NOTIF_ID, notifId)
            putExtra(ReminderReceiver.EXTRA_IS_ALARM, isAlarm)
            putExtra(ReminderReceiver.EXTRA_REPEAT_DAILY, repeatDaily)
            putExtra(ReminderReceiver.EXTRA_SOUND_INDEX, soundIndex)
        }
        val pending = PendingIntent.getBroadcast(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        } catch (e: SecurityException) {
            // Thiếu quyền hẹn giờ chính xác trên máy này - bỏ qua an toàn, không crash app.
        }
    }

    fun cancel(context: Context, notifId: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pending)
    }
}
