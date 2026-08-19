package com.h.adblockbrowser

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

object AlarmSounds {
    /** Tối đa 5 âm báo thức lấy từ danh sách chuông báo thức có sẵn của máy (mỗi máy Android đều
     *  có sẵn ít nhất vài âm chuông hệ thống, không cần đóng gói file âm thanh riêng cho app). */
    fun options(context: Context): List<Pair<String, Uri>> {
        val result = ArrayList<Pair<String, Uri>>()
        try {
            val manager = RingtoneManager(context)
            manager.setType(RingtoneManager.TYPE_ALARM)
            val cursor = manager.cursor
            while (cursor.moveToNext() && result.size < 5) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: "Âm ${result.size + 1}"
                val uri = manager.getRingtoneUri(cursor.position)
                result.add(title to uri)
            }
        } catch (e: Exception) { }
        if (result.isEmpty()) {
            val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            result.add("Mặc định" to fallback)
        }
        return result
    }

    fun uriFor(context: Context, index: Int): Uri {
        val opts = options(context)
        return opts.getOrNull(index)?.second ?: opts[0].second
    }
}
