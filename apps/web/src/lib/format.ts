import type { DayOfWeek, Gender, Presence, SkillLevel } from './types'

export const SKILL_LABEL: Record<SkillLevel, string> = {
  BEGINNER: '초급',
  INTERMEDIATE: '중급',
  ADVANCED: '고급',
}

export const SKILL_LEVELS: SkillLevel[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED']

export const GENDER_LABEL: Record<Gender, string> = { MALE: '남성', FEMALE: '여성' }

export const DAYS: { value: DayOfWeek; short: string }[] = [
  { value: 'MONDAY', short: '월' },
  { value: 'TUESDAY', short: '화' },
  { value: 'WEDNESDAY', short: '수' },
  { value: 'THURSDAY', short: '목' },
  { value: 'FRIDAY', short: '금' },
  { value: 'SATURDAY', short: '토' },
  { value: 'SUNDAY', short: '일' },
]

const SPORT_EMOJI: Record<string, string> = {
  GYM: '🏋️',
  RUNNING: '🏃',
  CLIMBING: '🧗',
  SWIMMING: '🏊',
  TENNIS: '🎾',
  BADMINTON: '🏸',
  CYCLING: '🚴',
  YOGA: '🧘',
  FUTSAL: '⚽',
  HIKING: '🥾',
}

const SPORT_EMOJI_BY_NAME: Record<string, string> = {
  헬스: '🏋️',
  러닝: '🏃',
  클라이밍: '🧗',
  수영: '🏊',
  테니스: '🎾',
  배드민턴: '🏸',
  자전거: '🚴',
  요가: '🧘',
  풋살: '⚽',
  등산: '🥾',
}

export function sportEmoji(codeOrName: string) {
  return SPORT_EMOJI[codeOrName] ?? SPORT_EMOJI_BY_NAME[codeOrName] ?? '💪'
}

export function formatMinutes(minutes: number) {
  if (minutes <= 0) return '겹치는 시간 없음'
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return `주 ${hours > 0 ? `${hours}시간` : ''}${rest > 0 ? ` ${rest}분` : ''}`.trim()
}

export function timeAgo(iso: string) {
  const diff = (Date.now() - new Date(iso).getTime()) / 1000
  if (diff < 60) return '방금 전'
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전`
  if (diff < 86400) return `${Math.floor(diff / 3600)}시간 전`
  if (diff < 86400 * 7) return `${Math.floor(diff / 86400)}일 전`
  return new Date(iso).toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' })
}

export function clockTime(iso: string) {
  return new Date(iso).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })
}

export function dayLabel(iso: string) {
  return new Date(iso).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })
}

export function isSameDay(a: string, b: string) {
  return new Date(a).toDateString() === new Date(b).toDateString()
}

/** 현재 접속중 / 5분 전 접속 / 3시간 전 접속 / 2일 전 접속. 접속 기록이 없으면 null */
export function presenceLabel(presence: Presence | undefined) {
  if (!presence) return null
  if (presence.online) return '현재 접속중'
  if (!presence.lastSeenAt) return null
  const seconds = (Date.now() - new Date(presence.lastSeenAt).getTime()) / 1000
  if (seconds < 60) return '방금 전 접속'
  if (seconds < 3600) return `${Math.floor(seconds / 60)}분 전 접속`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}시간 전 접속`
  if (seconds < 86400 * 30) return `${Math.floor(seconds / 86400)}일 전 접속`
  return '한 달 이상 전 접속'
}
