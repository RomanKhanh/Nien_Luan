package com.brainblocks.backend.enums;

// loại file khách đính kèm khi gửi yêu cầu đổi / trả / khiếu nại chất lượng
public enum EvidenceKind {
    // video quay liên tục lúc mở hàng: bắt buộc với đổi / trả hàng
    UNBOXING_VIDEO,
    // ảnh / video minh họa (tình trạng sản phẩm, lỗi chất lượng...), không bắt buộc
    CONDITION
}
