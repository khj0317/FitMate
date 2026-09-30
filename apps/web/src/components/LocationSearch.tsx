import clsx from 'clsx'
import { CheckCircle2, LoaderCircle, MapPin, Search, X } from 'lucide-react'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { searchLocations } from '../lib/queries'
import type { LocationInput, LocationSuggestion } from '../lib/types'

const DEBOUNCE_MS = 200

/**
 * 네이버 검색창처럼 입력하는 대로 지역 후보를 보여주고, 고르면 좌표와 동 단위 지역명을 넘겨준다.
 * - 한글 조합 중에도 검색 (onChange는 조합 중에도 호출된다)
 * - 늦게 도착한 이전 검색 결과가 최신 결과를 덮어쓰지 않도록 지금 검색어의 결과만 반영
 * - ↑↓ 이동, Enter 선택, Esc 닫기
 */
export function LocationSearch({
  value,
  onChange,
  error,
}: {
  value: LocationInput | null
  onChange: (location: LocationInput) => void
  error?: string
}) {
  const listId = useId()
  const [query, setQuery] = useState(value?.areaName ?? '')
  // 어떤 검색어의 결과인지 함께 둬서, "검색 중"과 결과는 그릴 때 계산한다
  const [found, setFound] = useState<{ query: string; items: LocationSuggestion[] }>({ query: '', items: [] })
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const latestQuery = useRef('')
  const containerRef = useRef<HTMLDivElement>(null)
  const trimmed = query.trim()

  // 입력이 멈추면 검색
  useEffect(() => {
    latestQuery.current = trimmed
    if (!open || !trimmed) return
    const timer = setTimeout(() => {
      const save = (items: LocationSuggestion[]) => {
        if (latestQuery.current !== trimmed) return // 그사이 입력이 바뀌었으면 늦게 온 결과는 버린다
        setFound({ query: trimmed, items })
        setActive(0)
      }
      searchLocations(trimmed).then(save, () => save([]))
    }, DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [trimmed, open])

  // 바깥을 클릭하면 닫는다
  useEffect(() => {
    const onClick = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [])

  const selectedMatchesInput = value && query === value.areaName
  const showList = open && trimmed.length > 0
  const loading = showList && found.query !== trimmed
  const results = found.query === trimmed ? found.items : []

  const select = (suggestion: LocationSuggestion) => {
    onChange({ latitude: suggestion.latitude, longitude: suggestion.longitude, areaName: suggestion.areaName })
    setQuery(suggestion.areaName)
    setOpen(false)
  }

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.nativeEvent.isComposing) return
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setOpen(true)
      setActive((i) => Math.min(i + 1, results.length - 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActive((i) => Math.max(i - 1, 0))
    } else if (event.key === 'Enter' && open && results[active]) {
      event.preventDefault()
      select(results[active])
    } else if (event.key === 'Escape') {
      event.stopPropagation() // 모달 안에서 써도 목록만 닫히게
      setOpen(false)
    }
  }

  return (
    <div ref={containerRef} className="relative">
      <div
        className={clsx(
          'flex h-12 items-center gap-2 rounded-xl bg-ink-50 px-4 ring-1 transition focus-within:bg-white focus-within:ring-2',
          error ? 'ring-red-300 focus-within:ring-red-400' : 'ring-ink-200 focus-within:ring-brand-400',
          showList && 'rounded-b-none',
        )}
      >
        <Search className="size-4 shrink-0 text-ink-400" />
        <input
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-autocomplete="list"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value)
            setOpen(true)
          }}
          onFocus={() => query && setOpen(true)}
          onKeyDown={onKeyDown}
          placeholder="동 이름이나 역 이름으로 검색 (예: 성수, 강남역)"
          className="h-full min-w-0 flex-1 bg-transparent text-[15px] outline-none placeholder:text-ink-400"
        />
        {loading ? (
          <LoaderCircle className="size-4 shrink-0 animate-spin text-brand-500" />
        ) : (
          query && (
            <button
              type="button"
              onClick={() => {
                setQuery('')
                setOpen(true)
              }}
              className="cursor-pointer rounded-full p-0.5 text-ink-400 hover:bg-ink-200 hover:text-ink-700"
              aria-label="검색어 지우기"
            >
              <X className="size-4" />
            </button>
          )
        )}
      </div>

      {showList && (
        <ul
          id={listId}
          role="listbox"
          className="absolute inset-x-0 top-full z-30 max-h-72 overflow-y-auto rounded-b-xl bg-white py-1 shadow-lift ring-1 ring-ink-200 scrollbar-thin"
        >
          {results.length === 0 ? (
            <li className="px-4 py-3 text-sm text-ink-400">{loading ? '검색 중...' : '검색 결과가 없어요. 다른 이름으로 검색해 보세요.'}</li>
          ) : (
            results.map((suggestion, index) => (
              <li
                key={`${suggestion.name}-${suggestion.address}`}
                role="option"
                aria-selected={index === active}
                onMouseDown={(e) => e.preventDefault()} // 입력창 blur 전에 선택되도록
                onClick={() => select(suggestion)}
                onMouseEnter={() => setActive(index)}
                className={clsx('flex cursor-pointer items-start gap-3 px-4 py-2.5', index === active && 'bg-brand-50')}
              >
                <MapPin className={clsx('mt-0.5 size-4 shrink-0', index === active ? 'text-brand-500' : 'text-ink-300')} />
                <div className="min-w-0">
                  <p className="truncate text-[15px] text-ink-900">
                    <Highlight text={suggestion.name} query={query.trim()} />
                  </p>
                  {suggestion.address !== suggestion.name && (
                    <p className="truncate text-xs text-ink-400">{suggestion.address}</p>
                  )}
                </div>
              </li>
            ))
          )}
        </ul>
      )}

      {error ? (
        <p className="mt-1.5 text-xs text-red-600">{error}</p>
      ) : selectedMatchesInput ? (
        <p className="mt-1.5 flex items-center gap-1 text-xs font-medium text-emerald-600">
          <CheckCircle2 className="size-3.5" /> {value.areaName} (으)로 설정돼요
        </p>
      ) : (
        <p className="mt-1.5 text-xs text-ink-400">목록에서 지역을 선택해 주세요. 정확한 위치는 다른 사람에게 공개되지 않아요.</p>
      )}
    </div>
  )
}

/** 검색어와 일치하는 부분을 굵게 표시 (띄어쓰기 차이는 무시하지 않고 그대로 비교) */
function Highlight({ text, query }: { text: string; query: string }) {
  const index = query ? text.indexOf(query) : -1
  if (index < 0) return <>{text}</>
  return (
    <>
      {text.slice(0, index)}
      <mark className="bg-transparent font-bold text-brand-600">{text.slice(index, index + query.length)}</mark>
      {text.slice(index + query.length)}
    </>
  )
}
