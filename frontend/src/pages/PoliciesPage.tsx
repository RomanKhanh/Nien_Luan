import { useEffect, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'

// Điều khoản, chính sách bảo mật và hỗ trợ (link ở chân các màn đăng nhập / đăng ký).
// Nội dung chỉ mô tả đúng những gì hệ thống đang làm; sửa trang này khi nghiệp vụ thay đổi.
const SECTIONS = [
  { id: 'terms', title: 'Điều khoản sử dụng' },
  { id: 'privacy', title: 'Chính sách bảo mật' },
  { id: 'support', title: 'Hỗ trợ' },
]

export default function PoliciesPage() {
  const { hash } = useLocation()

  // React Router không tự cuộn tới #id. Đợi 1 frame vì ShopLayout (effect của cha chạy sau con)
  // cuộn lên đầu trang mỗi lần đổi đường dẫn.
  useEffect(() => {
    if (!hash) return
    const frame = requestAnimationFrame(() => document.getElementById(hash.slice(1))?.scrollIntoView())
    return () => cancelAnimationFrame(frame)
  }, [hash])

  return (
    <div className="container-page max-w-[820px] py-10 sm:py-14">
      <h1 className="h1">Điều khoản & chính sách</h1>
      <p className="mt-2 text-ink-muted">Áp dụng cho tài khoản phụ huynh / người giám hộ trên BrainBlocks.</p>
      <nav aria-label="Mục lục" className="mt-5 flex flex-wrap gap-2">
        {SECTIONS.map((s) => (
          <a
            key={s.id}
            href={`#${s.id}`}
            className="rounded-full border-[1.5px] border-line-strong bg-surface px-3.5 py-1.5 text-[13px] font-semibold text-ink-2 hover:border-ink hover:text-ink"
          >
            {s.title}
          </a>
        ))}
      </nav>

      <Section id="terms" title="Điều khoản sử dụng">
        <li>Tài khoản dành cho phụ huynh hoặc người giám hộ mua đồ chơi cho trẻ. Trẻ em không tự tạo tài khoản.</li>
        <li>Bạn chịu trách nhiệm giữ bí mật mật khẩu. Đặt lại mật khẩu sẽ đăng xuất tài khoản khỏi mọi thiết bị.</li>
        <li>
          Đơn hàng đi qua các trạng thái: Chờ xác nhận → Đã xác nhận → Đang giao → Đã giao, hoặc Đã hủy. Đơn thanh
          toán MoMo không được thanh toán trong 24 giờ sẽ tự hủy.
        </li>
        <li>
          Yêu cầu đổi, trả, hủy đơn hoặc khiếu nại chất lượng được gửi qua mục Phản hồi & khiếu nại và do quản trị viên
          xử lý.
        </li>
        <li>
          Hồ sơ kỹ năng, chỉ số tác động và gợi ý của Chatbot chỉ mang tính tham khảo khi chọn đồ chơi, không dùng để
          đánh giá hay chẩn đoán năng lực, tâm lý hoặc sự phát triển của trẻ.
        </li>
        <li>Quản trị viên có thể khóa tài khoản vi phạm hoặc ẩn đánh giá, nhận xét có nội dung không phù hợp.</li>
      </Section>

      <Section id="privacy" title="Chính sách bảo mật">
        <li>
          Thông tin được lưu: họ tên, email của tài khoản; tên, số điện thoại và địa chỉ người nhận trong từng đơn
          hàng; hồ sơ của bé (tên, ngày sinh, giới tính, ghi chú, nhóm kỹ năng quan tâm, đồ chơi đã chọn hoặc mua) và
          lịch sử trò chuyện với Chatbot.
        </li>
        <li>Mật khẩu được mã hóa một chiều trước khi lưu, không ai, kể cả quản trị viên, xem được mật khẩu gốc.</li>
        <li>
          Lịch sử trò chuyện với Chatbot có thể được quản trị viên xem lại để kiểm tra chất lượng tư vấn. Không nên gửi
          thông tin nhạy cảm của bé vào khung chat.
        </li>
        <li>
          Khóa truy cập dịch vụ AI và cổng thanh toán chỉ nằm ở máy chủ, không gửi xuống trình duyệt. Thanh toán online
          được xử lý trên trang của MoMo, BrainBlocks không lưu thông tin thẻ hay ví.
        </li>
        <li>Email dùng để đăng nhập và chỉ nhận thư khi cần: mã xác nhận lúc đăng ký và link đặt lại mật khẩu.</li>
      </Section>

      <Section id="support" title="Hỗ trợ">
        <li>
          Theo dõi tình trạng đơn tại <Link to="/orders">Đơn hàng của tôi</Link>.
        </li>
        <li>
          Gửi yêu cầu đổi, trả, hủy đơn hoặc khiếu nại tại <Link to="/feedback">Phản hồi & khiếu nại</Link> (cần đăng
          nhập).
        </li>
        <li>
          Quên mật khẩu? Dùng <Link to="/forgot-password">Lấy lại mật khẩu</Link> để nhận link đặt mật khẩu mới qua
          email.
        </li>
        <li>
          Cần tư vấn chọn đồ chơi theo độ tuổi và kỹ năng? Hỏi <Link to="/chat">Chatbot BrainBlocks</Link>.
        </li>
      </Section>
    </div>
  )
}

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section id={id} className="mt-10 scroll-mt-32">
      <h2 className="h2">{title}</h2>
      <ul className="mt-3 list-disc space-y-2 pl-5 text-[14.5px] text-ink-2 marker:text-ink-faint">{children}</ul>
    </section>
  )
}
