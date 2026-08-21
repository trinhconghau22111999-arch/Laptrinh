package com.h.adblockbrowser

import android.view.View
import android.widget.PopupWindow

/** Hiện [PopupWindow] "thông minh" ngay DƯỚI [anchor] giống hệt showAsDropDown() bình thường -
 *  NHƯNG nếu [anchor] nằm gần đáy màn hình (menu không đủ chỗ bên dưới, bị cắt/che khuất bởi mép
 *  màn hình hoặc thanh điều hướng) thì TỰ ĐỘNG hiện lên PHÍA TRÊN [anchor] thay vì bên dưới, để
 *  toàn bộ menu luôn hiển thị đầy đủ, không bao giờ bị khuất mất. Dùng chung cho MỌI context menu
 *  kiểu popup phẳng trong app (xem [HomeScreenManager] và [StarredView]) thay vì gọi thẳng
 *  showAsDropDown() với offset cố định như trước - trước đây LUÔN hiện xuống dưới bất kể có đủ
 *  chỗ hay không, khiến menu bật lên ở gần cuối danh sách bị cắt mất, không bấm được các mục cuối.
 *
 *  CÁCH HOẠT ĐỘNG: đo trước kích thước THẬT của nội dung popup ([PopupWindow.getContentView])
 *  bằng [View.measure] với chế độ UNSPECIFIED (để View tự báo đúng kích thước nó cần, không bị ép
 *  theo kích thước màn hình cha) - so sánh chiều cao đó với khoảng trống thực tế còn lại phía dưới
 *  [anchor] (tính từ toạ độ MÀN HÌNH THẬT qua [View.getLocationOnScreen], không phải toạ độ trong
 *  layout cha). Đủ chỗ -> hiện dưới như bình thường. Không đủ -> hiện trên bằng offset Y ÂM
 *  (khoảng cách = chiều cao anchor + chiều cao popup + khoảng hở [gapDp], theo đúng công thức
 *  offset của showAsDropDown: offset tính từ MÉP DƯỚI anchor, offset âm đủ lớn sẽ đẩy popup lên
 *  trên hẳn mép trên của anchor). */
fun PopupWindow.showSmartDropDown(anchor: View, xOffDp: Int = 0, gapDp: Int = 4) {
    val density = anchor.resources.displayMetrics.density
    val xOff = (xOffDp * density).toInt()
    val gap = (gapDp * density).toInt()

    contentView.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    val popupHeight = contentView.measuredHeight

    val anchorLoc = IntArray(2)
    anchor.getLocationOnScreen(anchorLoc)
    val anchorBottomOnScreen = anchorLoc[1] + anchor.height
    val screenHeight = anchor.resources.displayMetrics.heightPixels
    val spaceBelow = screenHeight - anchorBottomOnScreen

    if (popupHeight <= 0 || spaceBelow >= popupHeight + gap) {
        // Đủ chỗ bên dưới (hoặc chưa đo được kích thước popup - an toàn hơn là cứ hiện dưới như
        // hành vi mặc định cũ) -> hiện xuống dưới bình thường.
        showAsDropDown(anchor, xOff, gap)
    } else {
        // Không đủ chỗ bên dưới -> hiện lên TRÊN anchor.
        showAsDropDown(anchor, xOff, -(anchor.height + popupHeight + gap))
    }
}
