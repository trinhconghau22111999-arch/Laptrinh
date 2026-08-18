package com.h.adblockbrowser

import android.content.Context

/** Danh sách các "hồ sơ" (profile) trình duyệt nhiều tài khoản.
 *  Mỗi hồ sơ chiếm 1 "slot" cố định (1..MAX_PROFILES), và mỗi slot chạy trong 1 TIẾN TRÌNH
 *  ANDROID RIÊNG (xem AccountBrowserActivity.kt + AndroidManifest.xml: mỗi slot có 1 class
 *  Activity + android:process riêng). Nhờ vậy cookie/phiên đăng nhập/localStorage của từng
 *  slot HOÀN TOÀN TÁCH BIỆT nhau ở cấp hệ điều hành - đăng nhập tài khoản Google A ở slot 1
 *  sẽ không ảnh hưởng / không nhìn thấy được từ slot 2 (tài khoản Google B), v.v. Đây là cách
 *  làm chuẩn của Android để cô lập dữ liệu WebView, giống hệt cách "Ẩn danh" đã dùng
 *  (android:process=":incognito") trong app này, chỉ khác là ở đây KHÔNG tự xoá dữ liệu -
 *  mục đích là GIỮ ĐĂNG NHẬP lâu dài cho từng tài khoản riêng biệt. */
object AccountProfileStore {
    const val MAX_PROFILES = 10

    private const val PREFS = "account_profiles"
    private const val KEY_LIST = "profiles" // "slot|name;slot|name;..."

    data class Profile(val slot: Int, val name: String)

    fun load(context: Context): List<Profile> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split("|", limit = 2)
            val slot = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val name = parts.getOrNull(1) ?: return@mapNotNull null
            Profile(slot, name)
        }.sortedBy { it.slot }
    }

    private fun saveAll(context: Context, profiles: List<Profile>) {
        val raw = profiles.joinToString(";") { "${it.slot}|${it.name}" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LIST, raw).apply()
    }

    /** Tạo hồ sơ mới ở slot trống đầu tiên. Trả về null nếu đã đủ MAX_PROFILES. */
    fun add(context: Context, name: String): Profile? {
        val current = load(context)
        val usedSlots = current.map { it.slot }.toSet()
        val freeSlot = (1..MAX_PROFILES).firstOrNull { it !in usedSlots } ?: return null
        val profile = Profile(freeSlot, name.ifBlank { "Tài khoản $freeSlot" })
        saveAll(context, current + profile)
        return profile
    }

    fun rename(context: Context, slot: Int, newName: String) {
        val current = load(context).map { if (it.slot == slot) it.copy(name = newName) else it }
        saveAll(context, current)
    }

    /** Xoá hồ sơ khỏi danh sách + xoá danh sách tab đã lưu của slot đó. LƯU Ý: cookie/phiên đăng
     *  nhập thực tế nằm trong thư mục dữ liệu riêng của tiến trình app_webview_acctN, không thể
     *  xoá sạch từ tiến trình chính - người dùng cần vào Cài đặt hệ thống > Ứng dụng > Xoá dữ
     *  liệu nếu muốn xoá đăng nhập triệt để trước khi dùng slot đó cho tài khoản khác. */
    fun remove(context: Context, slot: Int) {
        saveAll(context, load(context).filter { it.slot != slot })
        AccountSessionStore.clear(context, slot)
    }
}
