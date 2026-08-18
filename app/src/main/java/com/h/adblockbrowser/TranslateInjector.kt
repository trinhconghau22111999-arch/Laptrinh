package com.h.adblockbrowser

/** Tự động dịch MỌI trang (bất kể ngôn ngữ gốc là gì) sang tiếng Việt bằng widget "Google Dịch
 *  trang web" chính chủ của Google (translate.google.com/translate_a/element.js) - không cần API
 *  key riêng. Dùng pageLanguage "auto" để Google tự nhận diện đúng ngôn ngữ gốc của từng trang -
 *  nếu trang đã là tiếng Việt, Google tự nhận ra và không dịch (dịch việt->việt không đổi gì).
 *  Tự ẩn khung/thanh giao diện RIÊNG của widget này (khung chọn ngôn ngữ, banner "Đã dịch sang...")
 *  - nó chỉ cần chạy NGẦM để dịch nội dung, không cần hiện khung riêng đè lên trang.
 *
 *  QUAN TRỌNG: dùng MutationObserver để CHỦ ĐỘNG XOÁ (không chỉ ẩn bằng CSS) bất kỳ phần tử nào
 *  Google chèn vào - vì Google hay đổi cấu trúc DOM / banner có lúc render trước khi CSS kịp áp
 *  dụng, khiến banner trắng "Được dịch sang:..." lọt ra ngoài nếu chỉ dựa vào vài class CSS cố
 *  định như bản cũ. Ép cả <html> lẫn <body> về top/margin 0 vì tuỳ phiên bản Google đẩy 1 trong 2. */
object TranslateInjector {
    const val JS = """
        (function() {
            if (window.__translateInjected) return;
            window.__translateInjected = true;

            var style = document.createElement('style');
            style.innerHTML =
                '.goog-te-banner-frame, .goog-te-balloon-frame, #goog-gt-tt, .goog-tooltip, ' +
                '.goog-te-menu-value, iframe.goog-te-menu-frame, .skiptranslate iframe, ' +
                'iframe[id^="goog-gt-"], iframe[id^="goog-te-"] { ' +
                'display: none !important; visibility: hidden !important; height: 0 !important; }' +
                'html, body { top: 0px !important; margin-top: 0px !important; position: static !important; }' +
                '.goog-text-highlight { background: none !important; box-shadow: none !important; }' +
                '#google_translate_element { display: none !important; }';
            document.head.appendChild(style);

            function nukeBanner() {
                document.documentElement.style.top = '0px';
                document.documentElement.style.marginTop = '0px';
                document.body.style.top = '0px';
                document.body.style.marginTop = '0px';
                var sels = [
                    'iframe.goog-te-banner-frame', 'iframe[id^="goog-gt-"]', 'iframe[id^="goog-te-"]',
                    '.goog-te-banner-frame', '.goog-te-balloon-frame', '#goog-gt-tt'
                ];
                sels.forEach(function(sel) {
                    document.querySelectorAll(sel).forEach(function(el) {
                        if (el && el.parentNode) el.parentNode.removeChild(el);
                    });
                });
            }

            var observer = new MutationObserver(nukeBanner);
            observer.observe(document.documentElement, { childList: true, subtree: true });

            var div = document.createElement('div');
            div.id = 'google_translate_element';
            div.style.display = 'none';
            document.body.appendChild(div);

            window.googleTranslateElementInit = function() {
                try {
                    new google.translate.TranslateElement(
                        { pageLanguage: 'auto', includedLanguages: 'vi', autoDisplay: false },
                        'google_translate_element'
                    );
                    var tries = 0;
                    var timer = setInterval(function() {
                        tries++;
                        var combo = document.querySelector('.goog-te-combo');
                        if (combo) {
                            combo.value = 'vi';
                            combo.dispatchEvent(new Event('change'));
                            clearInterval(timer);
                        } else if (tries > 20) {
                            clearInterval(timer);
                        }
                        nukeBanner();
                    }, 300);
                } catch (e) {}
            };

            var script = document.createElement('script');
            script.src = 'https://translate.google.com/translate_a/element.js?cb=googleTranslateElementInit';
            document.body.appendChild(script);
        })();
    """
}
