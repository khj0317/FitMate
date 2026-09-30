import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { sportEmoji } from '../lib/format'
import { dDay, gatheringTime } from '../lib/gathering'
import { kakaoMapAvailable, loadKakaoMap } from '../lib/kakaoMap'
import type { GatheringSummary } from '../lib/types'

function escapeHtml(text: string) {
  return text.replace(/[&<>"']/g, (char) => `&#${char.charCodeAt(0)};`)
}

/**
 * 근처 모임을 카카오맵에 표시한다.
 * 내 활동 지역을 가운데에 두고, 마커를 누르면 모임 요약이 뜨고 요약을 누르면 상세로 간다.
 */
export function GatheringMap({ gatherings, center }: {
  gatherings: GatheringSummary[]
  center: { latitude: number; longitude: number }
}) {
  const navigate = useNavigate()
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<kakao.maps.Map | null>(null)
  const [ready, setReady] = useState(false)
  const [failed, setFailed] = useState(!kakaoMapAvailable)

  // 지도는 한 번만 만든다
  useEffect(() => {
    let cancelled = false
    loadKakaoMap()
      .then(({ maps }) => {
        if (cancelled || !containerRef.current) return
        const position = new maps.LatLng(center.latitude, center.longitude)
        const map = new maps.Map(containerRef.current, { center: position, level: 5 })
        map.addControl(new maps.ZoomControl(), maps.ControlPosition.RIGHT)
        const me = document.createElement('div')
        me.className = 'map-me'
        me.title = '내 활동 지역'
        new maps.CustomOverlay({ position, content: me, yAnchor: 0.5 }).setMap(map)
        mapRef.current = map
        setReady(true)
      })
      .catch(() => {
        if (!cancelled) setFailed(true)
      })
    return () => {
      cancelled = true
      mapRef.current = null
    }
  }, [center.latitude, center.longitude])

  // 모임 목록이 바뀌면 마커를 다시 그린다
  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map) return
    const { maps } = window.kakao
    const overlays: kakao.maps.CustomOverlay[] = []
    let openPopup: kakao.maps.CustomOverlay | null = null
    const closePopup = () => {
      openPopup?.setMap(null)
      openPopup = null
    }

    const bounds = new maps.LatLngBounds()
    bounds.extend(new maps.LatLng(center.latitude, center.longitude))
    for (const gathering of gatherings) {
      const position = new maps.LatLng(gathering.latitude, gathering.longitude)
      const full = gathering.currentCount >= gathering.capacity

      const popupContent = document.createElement('button')
      popupContent.type = 'button'
      popupContent.className = 'gathering-popup'
      popupContent.innerHTML = `
        <span class="gathering-popup__meta">${escapeHtml(dDay(gathering.startsAt))} · ${gathering.currentCount}/${gathering.capacity}명</span>
        <strong>${escapeHtml(gathering.title)}</strong>
        <span>${escapeHtml(gatheringTime(gathering.startsAt))}</span>
        <span>${escapeHtml(gathering.placeName)}</span>
        <span class="gathering-popup__link">자세히 보기 →</span>`
      popupContent.addEventListener('click', () => navigate(`/gatherings/${gathering.id}`))
      const popup = new maps.CustomOverlay({ position, content: popupContent, yAnchor: 1, zIndex: 10, clickable: true })

      const pin = document.createElement('button')
      pin.type = 'button'
      pin.className = `gathering-pin${full ? ' gathering-pin--full' : ''}`
      pin.title = gathering.title
      pin.setAttribute('aria-label', gathering.title)
      pin.innerHTML = `<span>${sportEmoji(gathering.sportCode)}</span>`
      pin.addEventListener('click', () => {
        const wasOpen = openPopup === popup
        closePopup()
        if (!wasOpen) {
          popup.setMap(map)
          openPopup = popup
        }
      })
      const marker = new maps.CustomOverlay({ position, content: pin, yAnchor: 1, clickable: true })
      marker.setMap(map)
      overlays.push(marker, popup)
      bounds.extend(position)
    }
    maps.event.addListener(map, 'click', closePopup)
    if (gatherings.length > 0) map.setBounds(bounds, 64, 48, 32, 48)
    return () => overlays.forEach((overlay) => overlay.setMap(null))
  }, [ready, gatherings, center.latitude, center.longitude, navigate])

  return (
    <div className="relative h-[60dvh] min-h-80 overflow-hidden rounded-3xl bg-ink-50 shadow-card ring-1 ring-ink-100">
      <div ref={containerRef} className="absolute inset-0" role="region" aria-label="근처 모임 지도" />
      {failed && (
        <p className="absolute inset-0 flex items-center justify-center p-6 text-center text-sm text-ink-500">
          지도를 불러오지 못했어요. 목록 보기로 모임을 확인해 주세요.
        </p>
      )}
    </div>
  )
}
