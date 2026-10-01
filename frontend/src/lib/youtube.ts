// Lấy id video từ các dạng link YouTube phổ biến: watch?v=, youtu.be/, shorts/, embed/, live/
const YOUTUBE_ID =
  /^(?:https?:\/\/)?(?:www\.|m\.)?(?:youtube\.com\/(?:watch\?(?:.*&)?v=|shorts\/|embed\/|live\/)|youtu\.be\/)([A-Za-z0-9_-]{11})(?:[?&#/].*)?$/

export function youtubeId(url: string | null | undefined): string | null {
  return url ? (YOUTUBE_ID.exec(url.trim())?.[1] ?? null) : null
}

// youtube-nocookie: không đặt cookie theo dõi cho tới khi người xem bấm phát
export function youtubeEmbedUrl(id: string): string {
  return `https://www.youtube-nocookie.com/embed/${id}?rel=0`
}
