package com.h.adblockbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Lưu ghi chú/nhắc nhở theo NGÀY (key "yyyy-MM-dd") cho CalendarActivity. Mỗi ngày có 1 danh
 *  sách ghi chú, mỗi ghi chú có tiêu đề và giờ nhắc tuỳ chọn (-1 nếu chỉ là ghi chú, không hẹn giờ). */
data class NoteItem(val id: Long, val title: String, val timeMinutes: Int)

object NotesStore {
    private const val PREFS = "calendar_notes"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun notesForDate(context: Context, dateKey: String): List<NoteItem> {
        val raw = prefs(context).getString(dateKey, null) ?: return emptyList()
        val result = ArrayList<NoteItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result.add(NoteItem(o.getLong("id"), o.getString("title"), o.optInt("time", -1)))
            }
        } catch (e: Exception) { }
        return result.sortedBy { if (it.timeMinutes < 0) Int.MAX_VALUE else it.timeMinutes }
    }

    fun addNote(context: Context, dateKey: String, title: String, timeMinutes: Int): NoteItem {
        val list = notesForDate(context, dateKey).toMutableList()
        val note = NoteItem(System.currentTimeMillis(), title, timeMinutes)
        list.add(note)
        save(context, dateKey, list)
        return note
    }

    fun deleteNote(context: Context, dateKey: String, id: Long) {
        val list = notesForDate(context, dateKey).filter { it.id != id }
        save(context, dateKey, list)
    }

    private fun save(context: Context, dateKey: String, list: List<NoteItem>) {
        val arr = JSONArray()
        for (n in list) {
            val o = JSONObject()
            o.put("id", n.id)
            o.put("title", n.title)
            o.put("time", n.timeMinutes)
            arr.put(o)
        }
        prefs(context).edit().putString(dateKey, arr.toString()).apply()
    }

    /** Tất cả các ngày (key "yyyy-MM-dd") hiện có ít nhất 1 ghi chú - dùng để chấm 1 chấm nhỏ lên
     *  ô ngày đó trong lưới lịch. */
    fun datesWithNotes(context: Context): Set<String> =
        prefs(context).all.keys.filter { notesForDate(context, it).isNotEmpty() }.toSet()
}
