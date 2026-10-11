package com.h.adblockbrowser

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** "Nhiều tài khoản" trên Android DƯỚI 9 (vd. Android 7.1.1).
 *
 *  Từ Android 9, mỗi tiến trình có thư mục dữ liệu WebView riêng nên mỗi hồ sơ (:acct1..:acct10)
 *  tách cookie thật sự. Android 7 KHÔNG có tính năng đó: mọi tiến trình dùng CHUNG 1 thư mục WebView
 *  (dễ treo/văng, nhất là lúc đăng nhập). Vì vậy trên máy cũ, mọi hồ sơ chạy trong CÙNG 1 tiến trình
 *  (AccountBrowserActivityLegacy) và tách đăng nhập bằng cách "đổi cookie":
 *   - Rời hồ sơ: lưu cookie của hồ sơ đó (theo từng tên miền đã truy cập) vào SharedPreferences.
 *   - Vào hồ sơ khác: xoá sạch cookie của WebView rồi nạp lại cookie đã lưu của hồ sơ đó.
 *  GIỚI HẠN: mỗi lúc chỉ có 1 hồ sơ "đang đăng nhập" (không chạy song song được); localStorage /
 *  IndexedDB vẫn dùng chung; chỉ lưu được tên=giá trị của cookie (mất cờ HttpOnly/Domain/hạn dùng,
 *  cookie nạp lại là cookie theo từng tên miền, hạn 1 năm). */
object LegacyProfileCookies {

    /** true trên Android < 9. */
    val enabled: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.P

    private const val PREFS = "legacy_profile_cookies"
    private const val KEY_ACTIVE = "active_slot"
    private const val MAX_HOSTS = 400
    private const val ONE_YEAR_SECONDS = 31536000

    // Các tên miền đã thấy của hồ sơ đang "active" (ghi từ nhiều luồng nên dùng tập đồng thời).
    private val hosts: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private var hostsSlot = -1

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Hồ sơ đang sở hữu cookie hiện có trong WebView (0 = chưa hồ sơ nào). */
    fun activeSlot(ctx: Context): Int = prefs(ctx).getInt(KEY_ACTIVE, 0)

    /** Ghi nhận 1 tên miền mà hồ sơ đang dùng đã kết nối tới (gọi từ mọi request, nhẹ). */
    fun noteHost(host: String?) {
        if (!enabled || host.isNullOrEmpty()) return
        if (hosts.size >= MAX_HOSTS) return
        hosts.add(host.lowercase())
    }

    private fun ensureHosts(ctx: Context, slot: Int) {
        if (hostsSlot == slot) return
        hosts.clear()
        val saved = prefs(ctx).getString("hosts_$slot", "") ?: ""
        saved.split("\n").filter { it.isNotBlank() }.forEach { hosts.add(it) }
        hostsSlot = slot
    }

    private fun capture(ctx: Context, slot: Int) {
        if (slot <= 0) return
        val cm = CookieManager.getInstance()
        val snapshot = hosts.toList()
        val obj = JSONObject()
        for (h in snapshot) {
            val c = try { cm.getCookie("https://$h") } catch (e: Exception) { null }
            if (!c.isNullOrBlank()) obj.put(h, c)
        }
        prefs(ctx).edit()
            .putString("cookies_$slot", obj.toString())
            .putString("hosts_$slot", snapshot.joinToString("\n"))
            .apply()
    }

    /** Lưu cookie hiện tại vào hồ sơ [slot] NẾU hồ sơ đó đang sở hữu cookie (tránh ghi nhầm cookie
     *  của hồ sơ khác khi 1 Activity cũ chạy onPause muộn). */
    fun captureIfActive(ctx: Context, slot: Int) {
        if (!enabled || slot <= 0) return
        if (activeSlot(ctx) != slot) return
        ensureHosts(ctx, slot)
        capture(ctx, slot)
    }

    /** Chuyển quyền sở hữu cookie sang hồ sơ [slot]: lưu hồ sơ trước, xoá sạch, nạp cookie của [slot].
     *  PHẢI gọi TRƯỚC khi tạo/tải bất kỳ WebView nào của hồ sơ. */
    fun activate(ctx: Context, slot: Int) {
        if (!enabled || slot <= 0) return
        val p = prefs(ctx)
        val current = p.getInt(KEY_ACTIVE, 0)
        if (current == slot) {
            ensureHosts(ctx, slot)
            return
        }
        if (current > 0) {
            ensureHosts(ctx, current)
            capture(ctx, current)
        }
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.removeAllCookies(null)

        hostsSlot = -1
        ensureHosts(ctx, slot)

        val saved = p.getString("cookies_$slot", null)
        if (!saved.isNullOrBlank()) {
            try {
                val obj = JSONObject(saved)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val h = keys.next()
                    val line = obj.optString(h, "")
                    for (part in line.split(";")) {
                        val kv = part.trim()
                        if (kv.isEmpty() || !kv.contains("=")) continue
                        cm.setCookie("https://$h", "$kv; Path=/; Secure; Max-Age=$ONE_YEAR_SECONDS")
                    }
                }
            } catch (e: Exception) {
                // Dữ liệu lưu hỏng -> bỏ qua, hồ sơ bắt đầu trống (đăng nhập lại).
            }
        }
        cm.flush()
        p.edit().putInt(KEY_ACTIVE, slot).apply()
    }

    /** Xoá dữ liệu cookie đã lưu của hồ sơ [slot] (khi người dùng xoá hồ sơ). */
    fun clearSlot(ctx: Context, slot: Int) {
        if (!enabled || slot <= 0) return
        val p = prefs(ctx)
        p.edit().remove("cookies_$slot").remove("hosts_$slot").apply()
        if (p.getInt(KEY_ACTIVE, 0) == slot) {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
            p.edit().putInt(KEY_ACTIVE, 0).apply()
            hosts.clear()
            hostsSlot = -1
        }
    }
}
