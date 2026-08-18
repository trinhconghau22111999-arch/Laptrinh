package com.h.adblockbrowser

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import android.webkit.WebView

/** BẮT BUỘC phải có class này: trình duyệt chính (tiến trình mặc định), chế độ Ẩn danh (tiến
 *  trình ":incognito") VÀ 10 hồ sơ "Nhiều tài khoản" (tiến trình ":acct1".."acct10", xem
 *  AndroidManifest.xml) ĐỀU dùng WebView. Nếu 2 tiến trình của CÙNG 1 app dùng chung 1 thư mục
 *  dữ liệu WebView, Android sẽ ném lỗi ngay khi WebView thứ 2 khởi tạo (thường lộ ra đúng lúc
 *  WebView/CookieManager hoạt động nhiều nhất - ví dụ lúc đăng nhập Google):
 *  "WebView cannot be used from more than one process" - CRASH ngay lập tức. Gọi
 *  WebView.setDataDirectorySuffix() SỚM NHẤT có thể (ngay đầu Application.onCreate(), trước khi
 *  bất kỳ WebView/CookieManager nào được đụng tới trong tiến trình đó) để mỗi tiến trình có thư
 *  mục dữ liệu WebView riêng, không đụng nhau nữa.
 *  LƯU Ý: TRƯỚC ĐÂY chỉ xử lý riêng tiến trình ":incognito", QUÊN áp dụng cho ":acct1".."acct10"
 *  -> mỗi hồ sơ "Nhiều tài khoản" vẫn dùng CHUNG thư mục dữ liệu WebView với tiến trình chính
 *  -> crash ngay khi đăng nhập Google ở màn "Nhiều tài khoản". Nay xử lý CHUNG cho MỌI tiến
 *  trình phụ (bất kỳ tên nào có dấu ":"), dùng luôn phần sau dấu ":" làm suffix nên không cần
 *  liệt kê tay từng acct1..acct10. */
class BrowserApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = currentProcessName()
            // processName dạng "com.h.adblockbrowser:acct3" / "...:incognito" - tiến trình
            // CHÍNH không có dấu ":" nên bỏ qua (đã là mặc định, không cần suffix).
            val suffix = processName?.substringAfter(':', "")
            if (!suffix.isNullOrEmpty()) {
                try {
                    WebView.setDataDirectorySuffix(suffix)
                } catch (e: Exception) {
                    // Nếu vì lý do nào đó đã có WebView chạm vào trước (không nên xảy ra), bỏ
                    // qua an toàn thay vì crash cứng ngay tại đây.
                }
            }
        }
    }

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getProcessName()
        }
        val pid = Process.myPid()
        val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        return am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
    }
}
