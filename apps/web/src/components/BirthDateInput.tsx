import clsx from 'clsx'
import { CalendarDays } from 'lucide-react'
import { useState } from 'react'

const MIN_YEAR = 1920

/** 숫자 8자리 → 'YYYY.MM.DD' (입력 중에는 채워진 만큼만 점을 찍는다) */
function format(digits: string) {
  if (digits.length <= 4) return digits
  if (digits.length <= 6) return `${digits.slice(0, 4)}.${digits.slice(4)}`
  return `${digits.slice(0, 4)}.${digits.slice(4, 6)}.${digits.slice(6)}`
}

function fromIso(iso: string) {
  return iso ? iso.replaceAll('-', '') : ''
}

/** 실제 달력에 있는 날짜인지(2월 30일 X), 1920년 이후 ~ 오늘 이전인지 확인 */
function toValidIso(digits: string): string | null {
  if (digits.length !== 8) return null
  const year = Number(digits.slice(0, 4))
  const month = Number(digits.slice(4, 6))
  const day = Number(digits.slice(6, 8))
  const date = new Date(year, month - 1, day)
  const real = date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
  if (!real || year < MIN_YEAR || date > new Date()) return null
  return `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6, 8)}`
}

function koreanAge(iso: string) {
  const birth = new Date(iso)
  const today = new Date()
  let age = today.getFullYear() - birth.getFullYear()
  const beforeBirthday =
    today.getMonth() < birth.getMonth() || (today.getMonth() === birth.getMonth() && today.getDate() < birth.getDate())
  if (beforeBirthday) age--
  return age
}

/**
 * 토스·카카오처럼 생년월일 8자리를 숫자로 입력하는 방식.
 * 모바일에서 숫자 키패드가 뜨고, 입력하는 동안 자동으로 YYYY.MM.DD 형태가 된다.
 * 올바른 날짜가 완성되면 'YYYY-MM-DD'를, 아니면 ''를 onChange로 넘긴다.
 */
export function BirthDateInput({
  value,
  onChange,
  onBlur,
  error,
}: {
  value: string
  onChange: (iso: string) => void
  onBlur?: () => void
  error?: string
}) {
  const [digits, setDigits] = useState(() => fromIso(value))
  const iso = toValidIso(digits)
  const invalid = digits.length === 8 && !iso
  const message = invalid ? '올바른 생년월일이 아니에요' : error

  return (
    <div>
      <div
        className={clsx(
          'flex h-12 items-center gap-2 rounded-xl bg-ink-50 px-4 ring-1 transition focus-within:bg-white focus-within:ring-2',
          message ? 'ring-red-300 focus-within:ring-red-400' : 'ring-ink-200 focus-within:ring-brand-400',
        )}
      >
        <CalendarDays className="size-4 shrink-0 text-ink-400" />
        <input
          value={format(digits)}
          onChange={(e) => {
            const next = e.target.value.replace(/\D/g, '').slice(0, 8)
            setDigits(next)
            onChange(toValidIso(next) ?? '')
          }}
          onBlur={onBlur}
          inputMode="numeric"
          autoComplete="bday"
          placeholder="예) 19980520"
          aria-invalid={!!message}
          className="h-full min-w-0 flex-1 bg-transparent text-[15px] tracking-wide tabular-nums outline-none placeholder:tracking-normal placeholder:text-ink-400"
        />
        {iso && (
          <span className="shrink-0 animate-pop rounded-lg bg-brand-50 px-2 py-0.5 text-xs font-bold text-brand-700">
            만 {koreanAge(iso)}세
          </span>
        )}
      </div>
      {message ? (
        <p className="mt-1.5 text-xs text-red-600">{message}</p>
      ) : (
        <p className="mt-1.5 text-xs text-ink-400">숫자 8자리로 입력해 주세요. 다른 사람에게는 나이대만 보여요</p>
      )}
    </div>
  )
}
