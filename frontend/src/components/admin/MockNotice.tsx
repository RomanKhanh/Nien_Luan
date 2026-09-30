// báo cho admin biết dữ liệu chatbot / tri thức đang là bản giả lập (xem api/chat.ts)
export function MockNotice() {
  return (
    <p className="mb-5 rounded-2xl border-2 border-dashed border-sun bg-[#fff8e1] px-4 py-3 text-[13.5px] text-ink-2">
      <b>Bản demo:</b> backend chatbot chưa có nên dữ liệu ở đây được giả lập và lưu trong trình duyệt này. Khi có API
      thật, đặt <code className="rounded bg-white px-1">VITE_CHAT_MOCK=false</code> để chuyển sang dữ liệu thật.
    </p>
  )
}
