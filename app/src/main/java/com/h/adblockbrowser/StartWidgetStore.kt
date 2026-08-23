package com.h.adblockbrowser

import android.content.Context

/** Danh sách các WIDGET ANDROID THẬT (App Widget hệ thống - đồng hồ, thời tiết, lịch... của
 *  CÁC APP KHÁC đã cài trên máy) mà người dùng đã thêm vào trang Start qua menu nhấn giữ khoảng
 *  trống > "Tiện ích" (xem [HomeScreenManager.showStartLongPressMenu] +
 *  [MainActivity.pickWidget]). Lưu ĐÚNG THỨ TỰ đã thêm (id widget, do chính
 *  [android.appwidget.AppWidgetHost] của app cấp khi ghim - xem [MainActivity.appWidgetHost]) -
 *  widget thêm sau nối vào CUỐI danh sách, hiện Ở CUỐI lưới Start (sau mọi tile/app khác).
 *  Kích cỡ hiển thị của TỪNG widget lưu riêng qua [TileSizeStore] (dùng chung cơ chế với tile
 *  thường, khoá bằng chuỗi "widget_<id>" - xem [HomeScreenManager.widgetSizeKey]), KHÔNG lưu ở
 *  đây. */
object StartWidgetStore {
    private const val PREFS = "start_widgets"
    private const val KEY = "ids"

    fun getAll(context: Context): List<Int> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(",").mapNotNull { it.toIntOrNull() }
    }

    fun add(context: Context, widgetId: Int) {
        val list = getAll(context).toMutableList()
        if (!list.contains(widgetId)) {
            list.add(widgetId)
            save(context, list)
        }
    }

    fun remove(context: Context, widgetId: Int) {
        val list = getAll(context).toMutableList()
        if (list.remove(widgetId)) save(context, list)
    }

    private fun save(context: Context, list: List<Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, list.joinToString(",")).apply()
    }
}
