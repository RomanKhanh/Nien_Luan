import { useState } from 'react'
import type { ProductDetail } from '@/api/types'
import { dominantSkill, skillTheme } from '@/lib/skills'
import { youtubeEmbedUrl, youtubeId } from '@/lib/youtube'
import { ProductArt } from './ProductArt'

export function ProductGallery({ product }: { product: ProductDetail }) {
  const [index, setIndex] = useState(0)
  const videoId = youtubeId(product.videoUrl)
  const videoOffset = videoId ? 1 : 0
  const count = videoOffset + product.images.length
  const activeIndex = index < count ? index : 0
  const showingVideo = Boolean(videoId && activeIndex === 0)
  const image = product.images[activeIndex - videoOffset]
  const skill = dominantSkill(product.skillImpacts)
  const theme = skillTheme(skill ?? '')
  const changeSlide = (direction: number) => setIndex((activeIndex + direction + count) % count)
  const thumbnailClass = (selected: boolean) =>
    `relative w-20 shrink-0 overflow-hidden rounded-2xl border-[3px] bg-white transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${selected ? 'border-primary' : 'border-transparent opacity-70 hover:opacity-100'}`

  return (
    <section className="min-w-0" aria-label="Video và ảnh sản phẩm" aria-roledescription="bộ trình chiếu">
      <div
        className="relative aspect-square overflow-hidden rounded-[28px] border-[6px] bg-white shadow-pop"
        style={{ borderColor: theme.color }}
      >
        {showingVideo && videoId ? (
          <iframe
            key={videoId}
            src={youtubeEmbedUrl(videoId)}
            title={`Video hướng dẫn sử dụng ${product.name}`}
            className="absolute inset-0 h-full w-full border-0 bg-ink"
            allow="accelerometer; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            referrerPolicy="strict-origin-when-cross-origin"
            allowFullScreen
          />
        ) : image ? (
          <img src={image.url} alt={`${product.name} – ảnh ${activeIndex - videoOffset + 1}`} className="h-full w-full object-contain" />
        ) : (
          <ProductArt name={product.name} skillCode={skill} className="h-full" />
        )}
      </div>

      {count > 1 && (
        <div className="mt-4 flex items-center gap-3">
          <button
            type="button"
            onClick={() => changeSlide(-1)}
            aria-label="Xem video hoặc ảnh trước"
            className="grid h-11 w-11 shrink-0 place-items-center rounded-full border-2 border-line bg-white text-2xl text-ink shadow-card transition hover:border-primary hover:text-primary"
          >
            <span aria-hidden>←</span>
          </button>
          <div className="flex min-w-0 flex-1 gap-2 overflow-x-auto p-1" aria-label="Chọn video hoặc ảnh">
            {videoId && (
              <button type="button" onClick={() => setIndex(0)} className={thumbnailClass(showingVideo)} aria-label="Video hướng dẫn sử dụng" aria-pressed={showingVideo}>
                <span className="flex aspect-[4/3] flex-col items-center justify-center gap-1 bg-ink text-white">
                  <span aria-hidden>▶</span>
                  <span className="text-[11px] font-bold">Video</span>
                </span>
              </button>
            )}
            {product.images.map((img, i) => (
              <button
                type="button"
                key={img.id}
                onClick={() => setIndex(i + videoOffset)}
                className={thumbnailClass(activeIndex === i + videoOffset)}
                aria-label={`Ảnh ${i + 1}`}
                aria-pressed={activeIndex === i + videoOffset}
              >
                <img src={img.url} alt="" loading="lazy" className="aspect-[4/3] w-full object-cover" />
              </button>
            ))}
          </div>
          <button
            type="button"
            onClick={() => changeSlide(1)}
            aria-label="Xem video hoặc ảnh tiếp theo"
            className="grid h-11 w-11 shrink-0 place-items-center rounded-full border-2 border-line bg-white text-2xl text-ink shadow-card transition hover:border-primary hover:text-primary"
          >
            <span aria-hidden>→</span>
          </button>
        </div>
      )}
      {count > 0 && (
        <p className="mt-3 text-center text-[13px] text-ink-muted" aria-live="polite" aria-atomic="true">
          {showingVideo ? 'Video hướng dẫn sử dụng' : `Ảnh ${activeIndex - videoOffset + 1}`} · {activeIndex + 1}/{count}
        </p>
      )}
    </section>
  )
}
