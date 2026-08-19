package com.h.adblockbrowser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

/** BroadcastReceiver cho nhắc nhở theo ngày ở trang Lịch (CalendarActivity): hiện thông báo
 *  (kèm âm thanh/rung) đúng giờ đã hẹn - không dựng riêng 1 màn hình chuông báo thức toàn màn
 *  hình để giữ phạm vi thay đổi gọn nhẹ. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Nhắc nhở"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, System.currentTimeMillis().toInt())
        val isAlarm = intent.getBooleanExtra(EXTRA_IS_ALARM, false)
        val repeatDaily = intent.getBooleanExtra(EXTRA_REPEAT_DAILY, false)
        val soundIndex = intent.getIntExtra(EXTRA_SOUND_INDEX, 0)

        val channelId = if (isAlarm) CHANNEL_ALARM else CHANNEL_REMINDER
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = if (isAlarm) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(
                channelId,
                if (isAlarm) "Báo thức" else "Nhắc nhở lịch",
                importance
            )
            nm.createNotificationChannel(channel)
        }

        // Ghi chú: bản rút gọn này chỉ phục vụ nhắc nhở lịch (isAlarm luôn = false ở CalendarActivity),
        // nên dùng âm thông báo mặc định của hệ thống thay vì AlarmSounds (đã không còn trong app).
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val openIntent = Intent(context, CalendarActivity::class.java)
        val contentPending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setSound(soundUri)
            .setPriority(if (isAlarm) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVibrate(if (isAlarm) longArrayOf(0, 600, 300, 600, 300, 600) else longArrayOf(0, 300))
            .setAutoCancel(true)
            .setContentIntent(contentPending)

        try {
            nm.notify(notifId, builder.build())
        } catch (e: SecurityException) {
            // Chưa cấp quyền POST_NOTIFICATIONS (Android 13+) - bỏ qua an toàn, không crash.
        }

        // Báo thức lặp lại hàng ngày: tự hẹn lại cho đúng giờ này vào NGÀY MAI ngay sau khi vừa
        // báo xong - để người dùng không phải tự bật lại mỗi ngày.
        if (isAlarm && repeatDaily) {
            val nextTrigger = System.currentTimeMillis() + 24L * 60 * 60 * 1000
            AlarmScheduler.schedule(context, nextTrigger, notifId, title, message, isAlarm = true, repeatDaily = true, soundIndex = soundIndex)
        }
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val EXTRA_IS_ALARM = "extra_is_alarm"
        const val EXTRA_REPEAT_DAILY = "extra_repeat_daily"
        const val EXTRA_SOUND_INDEX = "extra_sound_index"
        const val CHANNEL_REMINDER = "channel_reminder"
        const val CHANNEL_ALARM = "channel_alarm"
    }
}
