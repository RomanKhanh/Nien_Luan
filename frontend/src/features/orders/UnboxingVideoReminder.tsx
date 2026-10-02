// Nhắc khách quay video khi mở hàng: video mở hàng là bằng chứng bắt buộc khi gửi yêu cầu
// đổi / trả hàng (hàng hỏng do vận chuyển, giao nhầm, thiếu hàng...)
export function UnboxingVideoReminder({ compact = false }: { compact?: boolean }) {
  return (
    <div
      role="note"
      className="flex gap-3 rounded-2xl border-2 border-[#f5c451] bg-[#fff7e0] p-4 text-left text-[13.5px] text-ink-2"
    >
      <span className="text-[24px] leading-none" aria-hidden>
        🎥
      </span>
      <div>
        <p className="font-bold text-ink">Nhớ quay video khi mở hàng!</p>
        <p className="mt-0.5">
          Quay liên tục từ lúc hộp còn nguyên tem đến khi lấy sản phẩm ra. Video mở hàng là <b>bắt buộc</b> nếu bạn cần
          đổi hoặc trả hàng.
        </p>
        {!compact && (
          <p className="mt-1 text-[12.5px] text-ink-muted">
            Video giúp xác định hàng hỏng do vận chuyển hay giao nhầm, để bảo vệ quyền lợi của chính bạn.
          </p>
        )}
      </div>
    </div>
  )
}
