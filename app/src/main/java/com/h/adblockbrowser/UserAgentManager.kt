package com.h.adblockbrowser

/** Quản lý User-Agent gửi lên các trang web:
 *  - Mặc định: UA di động "sạch" (không có hậu tố ";  wv" mà WebView tự thêm) - hậu tố này khiến
 *    một số dịch vụ Google (Gmail...) nhận diện nhầm là trình duyệt "không đầy đủ tính năng" và tự
 *    chuyển về giao diện HTML rút gọn thay vì giao diện đầy đủ (bản cá nhân) thường thấy.
 *  - Zalo (chat.zalo.me): LUÔN ép sang UA máy tính, vì bản web di động của Zalo bị giới hạn tính
 *    năng hơn hẳn bản máy tính (xem ZaloDesktopStyler.kt để biết cách "thu gọn" lại bản máy tính
 *    cho vừa mắt trên điện thoại).
 *  - Nút "Bản máy tính" nổi trên thanh công cụ: ép TOÀN BỘ trang hiện tại sang UA máy tính, đúng
 *    tính năng "Request Desktop Site" quen thuộc trên Chrome. */
object UserAgentManager {

    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    fun uaFor(host: String?, forceDesktop: Boolean, path: String? = null): String {
        if (forceDesktop) return DESKTOP_UA
        if (host != null && host.contains("zalo.me")) return DESKTOP_UA
        // Google Maps (maps.google.com hoặc google.com/maps): ép UA máy tính để Google KHÔNG
        // tự hiện hộp thoại "Tiếp tục dùng web hay mở app Google Maps?" - hộp thoại đó chỉ hiện
        // khi Google nhận diện UA là trình duyệt di động; UA máy tính thì đi thẳng vào bản web,
        // không hỏi lại nữa.
        if (host != null) {
            val isMapsHost = host.contains("maps.google")
            val isMapsPath = host.contains("google.") && (path?.startsWith("/maps") == true)
            if (isMapsHost || isMapsPath) return DESKTOP_UA
        }
        return MOBILE_UA
    }

    fun isDesktopByDefault(host: String?): Boolean = host != null && host.contains("zalo.me")
}
