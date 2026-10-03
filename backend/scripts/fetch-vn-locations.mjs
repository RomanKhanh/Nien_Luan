// Tải dữ liệu địa chính 63 tỉnh/thành TRƯỚC sáp nhập 07/2025 (tỉnh -> quận/huyện -> phường/xã)
// từ https://provinces.open-api.vn/api/v1/ và lưu thành src/main/resources/locations/vn-locations.json.
//
// Chạy MỘT LẦN bằng tay khi cần làm mới dữ liệu (Node 18+, cần mạng):
//   cd backend && node scripts/fetch-vn-locations.mjs
// App chỉ đọc file JSON đã commit, lúc chạy không gọi API ngoài.
//
// Gọi danh sách tỉnh trước, rồi depth=3 cho TỪNG tỉnh, nghỉ giữa các lần gọi để không dồn tải lên API.
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const API = 'https://provinces.open-api.vn/api/v1'
const DELAY_MS = 600
const OUTPUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'main', 'resources', 'locations',
  'vn-locations.json')

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function getJson(path, attempt = 1) {
  const res = await fetch(API + path)
  if (res.ok) return res.json()
  if (attempt < 3) {
    await sleep(DELAY_MS * 4 * attempt)
    return getJson(path, attempt + 1)
  }
  throw new Error(`GET ${path} -> HTTP ${res.status}`)
}

// chỉ giữ mã + tên, bỏ codename, division_type, phone_code...
const pick = ({ code, name }) => ({ code, name: name.trim() })

const provinces = await getJson('/p/')
if (provinces.length !== 63) throw new Error(`Expected 63 provinces, got ${provinces.length}`)

const result = []
for (const [i, p] of provinces.entries()) {
  await sleep(DELAY_MS)
  const detail = await getJson(`/p/${p.code}?depth=3`)
  const districts = detail.districts.map((d) => ({ ...pick(d), wards: d.wards.map(pick) }))
  result.push({ ...pick(detail), districts })
  const wards = districts.reduce((sum, d) => sum + d.wards.length, 0)
  console.log(`${i + 1}/63 ${detail.name}: ${districts.length} quận/huyện, ${wards} phường/xã`)
}

mkdirSync(dirname(OUTPUT), { recursive: true })
// mỗi tỉnh một dòng: file vẫn nhỏ mà diff khi làm mới dữ liệu dễ đọc
writeFileSync(OUTPUT, '[\n' + result.map((p) => JSON.stringify(p)).join(',\n') + '\n]\n')
const districtCount = result.reduce((sum, p) => sum + p.districts.length, 0)
const wardCount = result.reduce((sum, p) => sum + p.districts.reduce((s, d) => s + d.wards.length, 0), 0)
console.log(`Saved ${result.length} provinces, ${districtCount} districts, ${wardCount} wards -> ${OUTPUT}`)
