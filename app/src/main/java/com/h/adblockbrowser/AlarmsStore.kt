package com.h.adblockbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AlarmItem(val id: Int, val hour: Int, val minute: Int, val label: String, val enabled: Boolean, val soundIndex: Int = 0)

/** Lưu danh sách báo thức của trang Đồng hồ (ClockActivity). */
object AlarmsStore {
    private const val PREFS = "clock_alarms"
    private const val KEY = "alarms"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<AlarmItem> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        val result = ArrayList<AlarmItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result.add(
                    AlarmItem(
                        o.getInt("id"), o.getInt("hour"), o.getInt("minute"),
                        o.optString("label", ""), o.optBoolean("enabled", true),
                        o.optInt("soundIndex", 0)
                    )
                )
            }
        } catch (e: Exception) { }
        return result.sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    fun add(context: Context, hour: Int, minute: Int, label: String, soundIndex: Int): AlarmItem {
        val list = all(context).toMutableList()
        val item = AlarmItem((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), hour, minute, label, true, soundIndex)
        list.add(item)
        save(context, list)
        return item
    }

    fun setEnabled(context: Context, id: Int, enabled: Boolean) {
        val list = all(context).map { if (it.id == id) it.copy(enabled = enabled) else it }
        save(context, list)
    }

    fun delete(context: Context, id: Int) {
        save(context, all(context).filter { it.id != id })
    }

    private fun save(context: Context, list: List<AlarmItem>) {
        val arr = JSONArray()
        for (a in list) {
            val o = JSONObject()
            o.put("id", a.id); o.put("hour", a.hour); o.put("minute", a.minute)
            o.put("label", a.label); o.put("enabled", a.enabled); o.put("soundIndex", a.soundIndex)
            arr.put(o)
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }
}
