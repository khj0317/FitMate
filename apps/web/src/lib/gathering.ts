import type { GatheringStatus, GatheringSummary, MannerTag } from './types'

export const STATUS_LABEL: Record<GatheringStatus, string> = {
  RECRUITING: '모집 중',
  CLOSED: '모집 마감',
  COMPLETED: '종료',
  CANCELED: '취소됨',
}

export const STATUS_TONE: Record<GatheringStatus, 'brand' | 'neutral' | 'red' | 'green'> = {
  RECRUITING: 'brand',
  CLOSED: 'neutral',
  COMPLETED: 'neutral',
  CANCELED: 'red',
}

export const MANNER_TAGS: { tag: MannerTag; label: string; emoji: string; positive: boolean }[] = [
  { tag: 'PUNCTUAL', label: '시간 약속을 잘 지켜요', emoji: '⏰', positive: true },
  { tag: 'KIND', label: '친절하고 매너가 좋아요', emoji: '😊', positive: true },
  { tag: 'SKILLED', label: '운동 실력이 좋아요', emoji: '💪', positive: true },
  { tag: 'TEACHES', label: '잘 알려줘요', emoji: '🧑‍🏫', positive: true },
  { tag: 'FUN', label: '함께해서 즐거웠어요', emoji: '🎉', positive: true },
  { tag: 'LATE', label: '약속 시간에 늦었어요', emoji: '🐢', positive: false },
  { tag: 'NO_SHOW', label: '말없이 나오지 않았어요', emoji: '🚫', positive: false },
  { tag: 'RUDE', label: '불친절했어요', emoji: '😠', positive: false },
]

export const MANNER_TAG_INFO = Object.fromEntries(MANNER_TAGS.map((info) => [info.tag, info])) as Record<
  MannerTag,
  (typeof MANNER_TAGS)[number]
>

/** 10월 3일 (금) 오전 7:00 */
export function gatheringTime(iso: string) {
  const date = new Date(iso)
  const day = date.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' })
  const time = date.toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })
  return `${day} ${time}`
}

/** 오늘 / 내일 / D-3 / 끝남 */
export function dDay(iso: string) {
  const start = new Date(iso)
  if (start.getTime() <= Date.now()) return '끝남'
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const target = new Date(start)
  target.setHours(0, 0, 0, 0)
  const days = Math.round((target.getTime() - today.getTime()) / 86_400_000)
  if (days === 0) return '오늘'
  if (days === 1) return '내일'
  return `D-${days}`
}

export function seatsLeft(gathering: GatheringSummary) {
  return Math.max(0, gathering.capacity - gathering.currentCount)
}
