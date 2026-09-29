import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router'
import { sportEmoji } from '../lib/format'
import { dDay, gatheringTime } from '../lib/gathering'
import type { GatheringSummary } from '../lib/types'

function escapeHtml(text: string) {
  return text.replace(/[&<>"']/g, (char) => `&#${char.charCodeAt(0)};`)
}

/**
 * 근처 모임을 지도에 표시한다 (OpenStreetMap 타일, 별도 API 키 불필요).
 * 내 활동 지역을 가운데에 두고, 마커를 누르면 모임 요약이 뜨고 한 번 더 누르면 상세로 간다.
 */
export function GatheringMap({ gatherings, center }: {
  gatherings: GatheringSummary[]
  center: { latitude: number; longitude: number }
}) {
  const navigate = useNavigate()
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<L.Map | null>(null)
  const layerRef = useRef<L.LayerGroup | null>(null)

  // 지도는 한 번만 만든다
  useEffect(() => {
    if (!containerRef.current) return
    const map = L.map(containerRef.current, { zoomControl: true, attributionControl: true })
      .setView([center.latitude, center.longitude], 14)
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(map)
    L.circleMarker([center.latitude, center.longitude], {
      radius: 7, color: '#fff', weight: 3, fillColor: '#0ea5e9', fillOpacity: 1,
    }).bindTooltip('내 활동 지역').addTo(map)
    layerRef.current = L.layerGroup().addTo(map)
    mapRef.current = map
    return () => {
      map.remove()
      mapRef.current = null
    }
  }, [center.latitude, center.longitude])

  // 모임 목록이 바뀌면 마커를 다시 그린다
  useEffect(() => {
    const map = mapRef.current
    const layer = layerRef.current
    if (!map || !layer) return
    layer.clearLayers()
    const bounds = L.latLngBounds([[center.latitude, center.longitude]])
    for (const gathering of gatherings) {
      const full = gathering.currentCount >= gathering.capacity
      const icon = L.divIcon({
        className: '',
        html: `<div class="gathering-pin${full ? ' gathering-pin--full' : ''}"><span>${sportEmoji(gathering.sportCode)}</span></div>`,
        iconSize: [40, 40],
        iconAnchor: [20, 40],
        popupAnchor: [0, -38],
      })
      const popup = document.createElement('button')
      popup.type = 'button'
      popup.className = 'gathering-popup'
      popup.innerHTML = `
        <span class="gathering-popup__meta">${escapeHtml(dDay(gathering.startsAt))} · ${gathering.currentCount}/${gathering.capacity}명</span>
        <strong>${escapeHtml(gathering.title)}</strong>
        <span>${escapeHtml(gatheringTime(gathering.startsAt))}</span>
        <span>${escapeHtml(gathering.placeName)}</span>
        <span class="gathering-popup__link">자세히 보기 →</span>`
      popup.addEventListener('click', () => navigate(`/gatherings/${gathering.id}`))
      L.marker([gathering.latitude, gathering.longitude], { icon, title: gathering.title })
        .bindPopup(popup, { closeButton: false })
        .addTo(layer)
      bounds.extend([gathering.latitude, gathering.longitude])
    }
    if (gatherings.length > 0) map.fitBounds(bounds, { padding: [48, 48], maxZoom: 15 })
  }, [gatherings, center.latitude, center.longitude, navigate])

  return (
    <div
      ref={containerRef}
      className="relative z-0 h-[60dvh] min-h-80 overflow-hidden rounded-3xl shadow-card ring-1 ring-ink-100"
      role="region"
      aria-label="근처 모임 지도"
    />
  )
}
