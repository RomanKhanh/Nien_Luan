import { skillTheme } from '@/lib/skills'

// Ảnh sản phẩm: dùng ảnh thật nếu có, không thì khối màu pastel theo nhóm kỹ năng trội nhất
// (quy ước card trong mockup: "Ảnh 4:3 nền pastel theo nhóm kỹ năng trội nhất")
export function ProductArt({
  url,
  name,
  skillCode,
  className = '',
}: {
  url?: string | null
  name: string
  skillCode?: string | null
  className?: string
}) {
  const theme = skillTheme(skillCode ?? '', '')
  if (url) {
    return <img src={url} alt={name} loading="lazy" className={`aspect-[4/3] w-full object-cover ${className}`} />
  }
  // hình khối đổi theo tên để các card cạnh nhau không giống hệt nhau
  const seed = [...name].reduce((sum, ch) => sum + ch.charCodeAt(0), 0)
  const rotate = (seed % 30) - 15
  const shape = seed % 3
  return (
    <div
      role="img"
      aria-label={name}
      className={`relative flex aspect-[4/3] w-full items-center justify-center overflow-hidden ${className}`}
      style={{ background: theme.soft }}
    >
      <div
        className="absolute h-[46%] w-[34%]"
        style={{
          background: theme.color,
          opacity: 0.9,
          borderRadius: shape === 0 ? '14px' : shape === 1 ? '999px' : '6px',
          transform: `rotate(${rotate}deg) translateX(-18%)`,
        }}
      />
      <div
        className="absolute h-[26%] w-[20%] rounded-md bg-accent"
        style={{ transform: `rotate(${-rotate}deg) translate(80%, 45%)` }}
      />
    </div>
  )
}
