import clsx from 'clsx'
import { LoaderCircle, MapPin, Search } from 'lucide-react'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { loadKakaoMap } from '../lib/kakaoMap'
import { LocationSearch } from './LocationSearch'

export interface PickedPlace {
  latitude: number
  longitude: number
}

const DEBOUNCE_MS = 250

/**
 * 모임 장소 고르기: 이름으로 검색(카카오 장소 검색, 지도 가운데 가까운 순)하거나 지도를 눌러 핀을 옮긴다.
 * 장소 이름(placeName)은 자유롭게 고쳐 쓸 수 있고("3번 출구 앞"), 위치는 마지막으로 고른 곳이다.
 */
export function PlacePicker({
  placeName,
  onPlaceNameChange,
  location,
  onLocationChange,
  error,
}: {
  placeName: string
  onPlaceNameChange: (name: string) => void
  location: PickedPlace | null
  onLocationChange: (place: PickedPlace) => void
  error?: string
}) {
  const listId = useId()
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<kakao.maps.Map | null>(null)
  const pinRef = useRef<kakao.maps.CustomOverlay | null>(null)
  const [ready, setReady] = useState(false)
  const [failed, setFailed] = useState(false)
  // 어떤 검색어의 결과인지 함께 둬서, 입력이 바뀌면 이전 결과를 보여 주지 않고 "검색 중"으로 계산한다
  const [found, setFound] = useState<{ keyword: string; places: kakao.maps.services.PlaceResult[] }>({ keyword: '', places: [] })
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const latestKeyword = useRef('')
  // 처음 위치(내 활동 지역)로 지도를 한 번만 만든다. 지도 클릭 핸들러는 그때 한 번 등록되므로 콜백은 ref로 최신 값을 쓴다
  const initial = useRef(location)
  const onLocationChangeRef = useRef(onLocationChange)
  useEffect(() => {
    onLocationChangeRef.current = onLocationChange
  })

  useEffect(() => {
    let cancelled = false
    let observer: ResizeObserver | undefined
    loadKakaoMap().then(({ maps }) => {
      const container = containerRef.current
      if (cancelled || !container) return
      const start = initial.current ?? { latitude: 37.5665, longitude: 126.978 } // 없으면 서울시청
      const map = new maps.Map(container, { center: new maps.LatLng(start.latitude, start.longitude), level: 4 })
      const pin = document.createElement('div')
      pin.className = 'gathering-pin'
      pin.innerHTML = '<span>📍</span>'
      pinRef.current = new maps.CustomOverlay({ position: map.getCenter(), content: pin, yAnchor: 1 })
      if (initial.current) pinRef.current.setMap(map)
      maps.event.addListener(map, 'click', ({ latLng }) => {
        onLocationChangeRef.current({ latitude: latLng.getLat(), longitude: latLng.getLng() })
      })
      // 모달이 열리는 동안 크기가 바뀌면 지도가 잘려 보이므로 다시 맞춘다
      observer = new ResizeObserver(() => map.relayout())
      observer.observe(container)
      mapRef.current = map
      setReady(true)
    }).catch(() => {
      if (!cancelled) setFailed(true) // 지도를 못 불러와도 모임은 만들 수 있게 지역 검색으로 대신한다
    })
    return () => {
      cancelled = true
      observer?.disconnect()
      mapRef.current = null
    }
  }, [])

  // 고른 위치로 핀과 지도를 옮긴다
  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map || !location || !pinRef.current) return
    const position = new window.kakao.maps.LatLng(location.latitude, location.longitude)
    pinRef.current.setPosition(position)
    pinRef.current.setMap(map)
    map.panTo(position)
  }, [ready, location])

  // 입력이 멈추면 지도 가운데 가까운 순으로 장소를 찾는다
  useEffect(() => {
    const keyword = placeName.trim()
    latestKeyword.current = keyword
    if (!ready || !open || keyword.length < 2) return
    const timer = setTimeout(() => {
      const { maps } = window.kakao
      new maps.services.Places().keywordSearch(
        keyword,
        (data, status) => {
          if (latestKeyword.current !== keyword) return // 그사이 입력이 바뀌었으면 늦게 온 결과는 버린다
          setFound({ keyword, places: status === maps.services.Status.OK ? data : [] })
          setActive(0)
        },
        { location: mapRef.current?.getCenter(), sort: 'accuracy', size: 6 },
      )
    }, DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [placeName, open, ready])

  const keyword = placeName.trim()
  const showList = open && keyword.length >= 2
  const loading = showList && found.keyword !== keyword
  const results = found.keyword === keyword ? found.places : [] // 늦게 온 이전 검색 결과는 쓰지 않는다

  const pick = (place: kakao.maps.services.PlaceResult) => {
    onPlaceNameChange(place.place_name)
    onLocationChange({ latitude: Number(place.y), longitude: Number(place.x) })
    mapRef.current?.setLevel(3)
    setOpen(false)
  }

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.nativeEvent.isComposing || !open || results.length === 0) return
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActive((i) => (i + 1) % results.length)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActive((i) => (i - 1 + results.length) % results.length)
    } else if (event.key === 'Enter') {
      event.preventDefault()
      pick(results[active])
    } else if (event.key === 'Escape') {
      event.stopPropagation() // 목록만 닫고, 모달까지 닫히지 않게
      setOpen(false)
    }
  }

  return (
    <div className="space-y-2">
      <div className="relative">
        <Search className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-ink-400" />
        <input
          value={placeName}
          maxLength={100}
          onChange={(event) => {
            onPlaceNameChange(event.target.value)
            setOpen(true)
          }}
          onFocus={(event) => {
            setOpen(true)
            // 휴대폰에서는 키보드가 올라오면 아래로 펼쳐지는 검색 결과가 가려지므로 입력칸을 위로 올린다
            const input = event.currentTarget
            if (window.matchMedia('(max-width: 767px)').matches) {
              setTimeout(() => input.scrollIntoView({ block: 'start', behavior: 'smooth' }), 300)
            }
          }}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          onKeyDown={onKeyDown}
          placeholder="장소 검색 (예: 뚝섬유원지역, 서울숲)"
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-invalid={Boolean(error)}
          className={clsx(
            'h-12 w-full rounded-xl border bg-white pr-10 pl-10 text-[15px] outline-none transition placeholder:text-ink-300 focus:ring-4',
            error ? 'border-red-300 focus:ring-red-100' : 'border-ink-200 focus:border-brand-400 focus:ring-brand-100',
          )}
        />
        {loading && <LoaderCircle className="absolute top-1/2 right-3.5 size-4 -translate-y-1/2 animate-spin text-ink-400" />}
        {showList && !loading && (
          <ul
            id={listId}
            role="listbox"
            className="absolute inset-x-0 top-full z-20 mt-1.5 max-h-72 overflow-y-auto rounded-2xl bg-white p-1.5 shadow-lift ring-1 ring-ink-100"
          >
            {results.length === 0 ? (
              <li className="px-3 py-3 text-sm text-ink-400">검색 결과가 없어요. 지도를 눌러 위치를 골라도 돼요</li>
            ) : (
              results.map((place, index) => (
                <li key={place.id} role="option" aria-selected={index === active}>
                  <button
                    type="button"
                    onMouseDown={(event) => event.preventDefault()} // 입력창 blur보다 먼저 고르게
                    onClick={() => pick(place)}
                    onMouseEnter={() => setActive(index)}
                    className={clsx(
                      'flex w-full items-start gap-2.5 rounded-xl px-3 py-2.5 text-left',
                      index === active && 'bg-brand-50',
                    )}
                  >
                    <MapPin className="mt-0.5 size-4 shrink-0 text-brand-500" />
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-semibold text-ink-900">{place.place_name}</span>
                      <span className="block truncate text-xs text-ink-500">
                        {place.road_address_name || place.address_name}
                        {place.category_group_name && ` · ${place.category_group_name}`}
                      </span>
                    </span>
                  </button>
                </li>
              ))
            )}
          </ul>
        )}
      </div>
      {failed ? (
        <>
          <LocationSearch value={null} onChange={(picked) => onLocationChange(picked)} />
          <p className="text-xs text-ink-400">지도를 불러오지 못했어요. 지역을 검색해서 모임 위치를 골라 주세요</p>
        </>
      ) : (
        <>
          <div ref={containerRef} className="h-52 overflow-hidden rounded-2xl bg-ink-50 ring-1 ring-ink-100" aria-label="모임 장소 지도" />
          <p className="text-xs text-ink-400">
            {location ? '지도를 눌러 핀 위치를 옮길 수 있어요. 장소 이름은 "3번 출구 앞"처럼 고쳐 써도 돼요' : '장소를 검색하거나 지도를 눌러 위치를 골라 주세요'}
          </p>
        </>
      )}
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  )
}
