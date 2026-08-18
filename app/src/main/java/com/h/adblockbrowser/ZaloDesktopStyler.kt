package com.h.adblockbrowser

/** Zalo Web bản máy tính (chat.zalo.me) vốn thiết kế cho màn hình rộng: cột danh sách trò chuyện
 *  + cột nội dung chat nằm CẠNH NHAU. Trên điện thoại, giao diện này quá chật. JS này chèn CSS để
 *  thu lại theo kiểu app di động: cho phép cuộn ngang mượt, tự phóng cỡ chữ vùng gõ tin nhắn/nút
 *  gửi cho dễ bấm bằng ngón tay - chỉ áp dụng cho domain zalo.me (gọi từ MainActivity khi phát
 *  hiện đúng domain), không ảnh hưởng trang khác. */
object ZaloDesktopStyler {
    const val JS = """
        (function() {
            if (window.__zaloStylerRunning) return;
            window.__zaloStylerRunning = true;
            function applyStyle() {
                try {
                    var id = 'zalo-mobile-style-override';
                    if (document.getElementById(id)) return;
                    var style = document.createElement('style');
                    style.id = id;
                    style.innerHTML =
                        'html, body { min-width: 0 !important; overflow-x: auto !important; }' +
                        '* { -webkit-tap-highlight-color: transparent; }' +
                        'input, textarea, button, [contenteditable="true"] { font-size: 16px !important; }' +
                        '.zl-editor, [contenteditable="true"] { min-height: 40px !important; }';
                    document.head.appendChild(style);

                    var meta = document.querySelector('meta[name=viewport]');
                    var content = 'width=1100, initial-scale=0.32, minimum-scale=0.2, maximum-scale=3.0, user-scalable=yes';
                    if (meta) { meta.setAttribute('content', content); }
                    else {
                        meta = document.createElement('meta');
                        meta.name = 'viewport'; meta.content = content;
                        document.head.appendChild(meta);
                    }
                } catch (e) {}
            }
            applyStyle();
            setInterval(applyStyle, 1500);
        })();
    """

    fun isZalo(host: String?): Boolean = host != null && host.contains("zalo.me")
}
