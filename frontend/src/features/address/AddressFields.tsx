import { useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { locationApi } from '@/api/endpoints'
import type { ProvinceOption } from '@/api/types'
import { Field, Input, Select } from '@/components/ui/Field'
import { normalizeSearch } from '@/lib/search'
import type { AddressValue } from './address'

// dữ liệu địa chính cố định: tải một lần mỗi phiên
const STATIC = { staleTime: Infinity, gcTime: Infinity } as const

type Errors = Partial<Record<keyof AddressValue, string>>

/**
 * Bộ chọn địa chỉ 3 cấp (63 tỉnh/thành trước sáp nhập): tỉnh có ô gõ tìm không dấu, chọn tỉnh mới mở huyện,
 * chọn huyện mới mở xã; đổi cấp trên thì xoá lựa chọn cấp dưới. Kèm ô địa chỉ chi tiết.
 */
export function AddressFields({
  value,
  onChange,
  errors = {},
  idPrefix,
}: {
  value: AddressValue
  onChange: (patch: Partial<AddressValue>) => void
  errors?: Errors
  idPrefix: string
}) {
  const provinces = useQuery({ queryKey: ['locations', 'provinces'], queryFn: locationApi.provinces, ...STATIC })
  const provinceCode = Number(value.provinceCode) || 0
  const districtCode = Number(value.districtCode) || 0
  const districts = useQuery({
    queryKey: ['locations', 'districts', provinceCode],
    queryFn: () => locationApi.districts(provinceCode),
    enabled: provinceCode > 0,
    ...STATIC,
  })
  const wards = useQuery({
    queryKey: ['locations', 'wards', districtCode],
    queryFn: () => locationApi.wards(districtCode),
    enabled: districtCode > 0 && value.wardRequired,
    ...STATIC,
  })

  return (
    <div className="grid gap-4 sm:grid-cols-3">
      <Field label="Tỉnh / thành phố" htmlFor={`${idPrefix}-province`} required error={errors.provinceCode}>
        <ProvinceCombobox
          id={`${idPrefix}-province`}
          provinces={provinces.data ?? []}
          loading={provinces.isPending}
          value={provinceCode}
          invalid={Boolean(errors.provinceCode)}
          onSelect={(code) => {
            if (code === provinceCode) return
            onChange({ provinceCode: String(code), districtCode: '', wardCode: '', wardRequired: true })
          }}
        />
      </Field>
      <Field label="Quận / huyện" htmlFor={`${idPrefix}-district`} required error={errors.districtCode}>
        <Select
          id={`${idPrefix}-district`}
          value={value.districtCode}
          disabled={!provinceCode || districts.isPending}
          invalid={Boolean(errors.districtCode)}
          onChange={(e) => {
            const district = districts.data?.find((d) => d.code === Number(e.target.value))
            onChange({ districtCode: e.target.value, wardCode: '', wardRequired: district?.hasWards ?? true })
          }}
        >
          <option value="">
            {!provinceCode ? 'Chọn tỉnh/thành trước' : districts.isPending ? 'Đang tải…' : 'Chọn quận/huyện'}
          </option>
          {districts.data?.map((d) => (
            <option key={d.code} value={d.code}>
              {d.name}
            </option>
          ))}
        </Select>
      </Field>
      <Field label="Phường / xã" htmlFor={`${idPrefix}-ward`} required={value.wardRequired} error={errors.wardCode}>
        <Select
          id={`${idPrefix}-ward`}
          value={value.wardCode}
          disabled={!districtCode || !value.wardRequired || wards.isPending}
          invalid={Boolean(errors.wardCode)}
          onChange={(e) => onChange({ wardCode: e.target.value })}
        >
          <option value="">
            {!districtCode
              ? 'Chọn quận/huyện trước'
              : !value.wardRequired
                ? 'Huyện không có cấp xã'
                : wards.isPending
                  ? 'Đang tải…'
                  : 'Chọn phường/xã'}
          </option>
          {wards.data?.map((w) => (
            <option key={w.code} value={w.code}>
              {w.name}
            </option>
          ))}
        </Select>
      </Field>
      <Field
        label="Địa chỉ chi tiết (số nhà, tên đường)"
        htmlFor={`${idPrefix}-detail`}
        required
        error={errors.addressDetail}
        className="sm:col-span-3"
      >
        <Input
          id={`${idPrefix}-detail`}
          autoComplete="address-line1"
          placeholder="VD: 12 Nguyễn Văn Cừ"
          maxLength={255}
          value={value.addressDetail}
          invalid={Boolean(errors.addressDetail)}
          onChange={(e) => onChange({ addressDetail: e.target.value })}
        />
      </Field>
    </div>
  )
}

// "Thành phố Cần Thơ" -> "Cần Thơ": sắp xếp theo tên riêng, không theo "Tỉnh" / "Thành phố"
function shortName(name: string): string {
  return name.replace(/^(Tỉnh|Thành phố)\s+/, '')
}

// ô chọn tỉnh có gõ tìm: gõ "can tho", "hcm"... không dấu vẫn ra; lên / xuống để chọn, Enter để lấy, Esc để đóng
function ProvinceCombobox({
  id,
  provinces,
  loading,
  value,
  invalid,
  onSelect,
}: {
  id: string
  provinces: ProvinceOption[]
  loading: boolean
  value: number
  invalid: boolean
  onSelect: (code: number) => void
}) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const listRef = useRef<HTMLUListElement>(null)
  const listId = useId()

  const sorted = useMemo(
    () =>
      provinces
        .map((p) => ({ ...p, key: normalizeSearch(p.name) }))
        .sort((a, b) => shortName(a.name).localeCompare(shortName(b.name), 'vi')),
    [provinces],
  )
  const q = normalizeSearch(query)
  // "hcm" / "tp hcm" / "sai gon" cho TP. Hồ Chí Minh
  const aliasHcm = /^(tp ?)?hcm$|^sai ?gon$|^sg$/.test(q)
  const filtered = q ? sorted.filter((p) => p.key.includes(q) || (aliasHcm && p.code === 79)) : sorted
  const selected = provinces.find((p) => p.code === value)

  const choose = (code: number) => {
    onSelect(code)
    setOpen(false)
    setQuery('')
  }

  const moveActive = (next: number) => {
    setActive(next)
    listRef.current?.children[next]?.scrollIntoView({ block: 'nearest' })
  }

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      setOpen(false)
      setQuery('')
    } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      if (!open) {
        setOpen(true)
        return
      }
      const step = e.key === 'ArrowDown' ? 1 : -1
      if (filtered.length) moveActive((active + step + filtered.length) % filtered.length)
    } else if (e.key === 'Enter' && open) {
      // không gửi form khi đang chọn tỉnh
      e.preventDefault()
      if (filtered[active]) choose(filtered[active].code)
    }
  }

  return (
    <div className="relative">
      <Input
        id={id}
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={open && filtered[active] ? `${listId}-${filtered[active].code}` : undefined}
        autoComplete="off"
        disabled={loading}
        invalid={invalid}
        placeholder={loading ? 'Đang tải…' : 'Gõ để tìm tỉnh/thành'}
        value={open ? query : (selected?.name ?? '')}
        onFocus={() => {
          setOpen(true)
          setActive(0)
        }}
        onBlur={() => {
          setOpen(false)
          setQuery('')
        }}
        onChange={(e) => {
          // danh sách đang đóng (vừa chọn xong) thì ô đang hiện tên tỉnh: gõ tiếp là tìm lại từ đầu
          const native = e.nativeEvent as InputEvent
          setQuery(open ? e.target.value : (native.data ?? ''))
          setOpen(true)
          setActive(0)
        }}
        onKeyDown={onKeyDown}
      />
      {open && (
        <ul
          ref={listRef}
          id={listId}
          role="listbox"
          // giữ focus ở ô nhập để onBlur không đóng danh sách trước khi bấm xong
          onMouseDown={(e) => e.preventDefault()}
          className="absolute inset-x-0 top-full z-40 mt-1.5 max-h-64 overflow-y-auto rounded-[14px] border border-line bg-surface py-1 shadow-[0_12px_32px_rgba(15,23,42,0.14)]"
        >
          {filtered.length === 0 ? (
            <li className="px-3.5 py-2 text-[13.5px] text-ink-muted">Không tìm thấy tỉnh/thành</li>
          ) : (
            filtered.map((p, i) => (
              <li
                key={p.code}
                id={`${listId}-${p.code}`}
                role="option"
                aria-selected={p.code === value}
                onMouseEnter={() => setActive(i)}
                onClick={() => choose(p.code)}
                className={`flex cursor-pointer items-center justify-between gap-2 px-3.5 py-2 text-[14px] ${
                  i === active ? 'bg-sky-soft' : ''
                } ${p.code === value ? 'font-semibold text-primary' : ''}`}
              >
                <span>{p.name}</span>
                <span className="shrink-0 text-[11.5px] text-ink-faint">{p.regionLabel}</span>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  )
}
