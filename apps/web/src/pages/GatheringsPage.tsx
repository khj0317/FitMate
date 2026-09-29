import clsx from 'clsx'
import { CalendarDays, List, Map as MapIcon, MapPin, Minus, Plus, SlidersHorizontal, Star, UsersRound } from 'lucide-react'
import { lazy, Suspense, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { Avatar } from '../components/Avatar'
import { LocationSearch } from '../components/LocationSearch'
import { MannerReviewModal } from '../components/MannerReviewModal'
import { Modal } from '../components/Modal'
import { Badge, Button, Card, Chip, EmptyState, Field, Input, PageHeader, PageLoader, Segmented, Textarea } from '../components/ui'
import { errorMessage } from '../lib/api'
import { sportEmoji } from '../lib/format'
import { dDay, gatheringTime, seatsLeft, STATUS_LABEL, STATUS_TONE } from '../lib/gathering'
import { useCreateGathering, useMe, useMyGatherings, useNearbyGatherings, usePendingReviews, useSports } from '../lib/queries'
import type { GatheringSummary, LocationInput, PendingReview } from '../lib/types'
import { useToast } from '../providers/ToastProvider'

// 지도(Leaflet)는 무거워서 지도 보기를 누를 때 불러온다
const GatheringMap = lazy(() => import('../components/GatheringMap').then((module) => ({ default: module.GatheringMap })))

const RADIUS_OPTIONS = [1, 3, 5, 10, 20]

export function GatheringsPage() {
  const navigate = useNavigate()
  const { data: me, isLoading: meLoading } = useMe()
  const [tab, setTab] = useState<'nearby' | 'mine'>('nearby')
  const [view, setView] = useState<'list' | 'map'>('list')
  const [sportId, setSportId] = useState<number | null>(null)
  const [radiusKm, setRadiusKm] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [reviewTarget, setReviewTarget] = useState<PendingReview | null>(null)

  const nearby = useNearbyGatherings(sportId, radiusKm, !!me?.location && tab === 'nearby')
  const mine = useMyGatherings()
  const { data: pending } = usePendingReviews()
  const { data: sports } = useSports()

  if (meLoading || !me) return <PageLoader />
  const effectiveRadius = radiusKm ?? me.searchRadiusKm
  const list = tab === 'nearby' ? nearby : mine

  return (
    <div className="mx-auto max-w-6xl px-4 py-6 md:px-8 md:py-10">
      <PageHeader
        title="운동 모임"
        description="여럿이 함께하면 더 재밌어요. 근처 모임에 참여하거나 직접 열어 보세요"
        action={
          <Button onClick={() => setCreating(true)} disabled={!me.location}>
            <Plus className="size-4" /> 모임 만들기
          </Button>
        }
      />

      {!!pending?.length && <PendingReviews items={pending} onReview={setReviewTarget} />}

      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <Segmented
          options={[
            { value: 'nearby', label: '근처 모임' },
            { value: 'mine', label: `내 모임${mine.data?.length ? ` ${mine.data.length}` : ''}` },
          ]}
          value={tab}
          onChange={setTab}
        />
        {tab === 'nearby' && me.location && (
          <div className="inline-flex rounded-xl bg-ink-100 p-1" role="group" aria-label="보기 방식">
            {([['list', List, '목록'], ['map', MapIcon, '지도']] as const).map(([value, Icon, label]) => (
              <button
                key={value}
                type="button"
                onClick={() => setView(value)}
                aria-pressed={view === value}
                className={clsx(
                  'flex cursor-pointer items-center gap-1 rounded-lg px-3 py-1.5 text-sm font-semibold transition-all',
                  view === value ? 'bg-white text-ink-900 shadow-sm' : 'text-ink-500 hover:text-ink-800',
                )}
              >
                <Icon className="size-4" /> {label}
              </button>
            ))}
          </div>
        )}
      </div>

      {tab === 'nearby' && !me.location ? (
        <Card>
          <EmptyState
            emoji="📍"
            title="활동 지역을 먼저 설정해 주세요"
            description="활동 지역을 기준으로 가까운 모임을 찾아드려요."
            action={<Button onClick={() => navigate('/profile')}>프로필 설정하러 가기</Button>}
          />
        </Card>
      ) : (
        <>
          {tab === 'nearby' && (
            <div className="mb-6 space-y-3">
              <div className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 scrollbar-thin md:mx-0 md:flex-wrap md:px-0">
                <Chip active={sportId === null} onClick={() => setSportId(null)}>
                  ✨ 전체
                </Chip>
                {sports?.map((sport) => (
                  <Chip key={sport.id} active={sportId === sport.id} onClick={() => setSportId(sport.id)}>
                    {sportEmoji(sport.code)} {sport.name}
                  </Chip>
                ))}
              </div>
              <div className="flex items-center gap-2 text-sm">
                <SlidersHorizontal className="size-4 text-ink-400" />
                <span className="font-medium text-ink-500">{me.location?.areaName} 반경</span>
                <div className="flex gap-1">
                  {RADIUS_OPTIONS.map((km) => (
                    <button
                      key={km}
                      onClick={() => setRadiusKm(km)}
                      className={clsx(
                        'cursor-pointer rounded-lg px-2.5 py-1 font-semibold transition-colors',
                        effectiveRadius === km ? 'bg-brand-500 text-white' : 'text-ink-500 hover:bg-ink-100',
                      )}
                    >
                      {km}km
                    </button>
                  ))}
                </div>
              </div>
            </div>
          )}

          {list.isLoading ? (
            <PageLoader />
          ) : list.error ? (
            <Card>
              <EmptyState emoji="😵" title="모임을 불러오지 못했어요" description={errorMessage(list.error)} />
            </Card>
          ) : tab === 'nearby' && view === 'map' && me.location ? (
            <Suspense fallback={<PageLoader />}>
              <GatheringMap gatherings={list.data ?? []} center={me.location} />
            </Suspense>
          ) : !list.data?.length ? (
            <Card>
              {tab === 'nearby' ? (
                <EmptyState
                  emoji="🏟️"
                  title="아직 근처에 열린 모임이 없어요"
                  description="첫 모임을 직접 열어 보세요. 가까운 메이트들이 참여할 수 있어요."
                  action={<Button onClick={() => setCreating(true)}>모임 만들기</Button>}
                />
              ) : (
                <EmptyState emoji="🗓️" title="참여 중인 모임이 없어요" description="근처 모임에 참여하면 여기에 모여요." />
              )}
            </Card>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {list.data.map((gathering) => (
                <GatheringCard key={gathering.id} gathering={gathering} />
              ))}
            </div>
          )}
        </>
      )}

      {/* 열 때마다 새로 마운트해서 현재 시각 기준으로 날짜·시간을 다시 잡는다 */}
      <CreateGatheringModal key={String(creating)} open={creating} onClose={() => setCreating(false)} defaultLocation={me.location} />
      <MannerReviewModal target={reviewTarget} onClose={() => setReviewTarget(null)} />
    </div>
  )
}

function PendingReviews({ items, onReview }: { items: PendingReview[]; onReview: (item: PendingReview) => void }) {
  return (
    <Card className="mb-6 overflow-hidden">
      <div className="flex items-center gap-2 bg-linear-to-r from-amber-50 to-brand-50 px-5 py-3">
        <Star className="size-4 fill-amber-400 text-amber-400" />
        <p className="text-sm font-bold text-ink-800">함께 운동한 메이트를 평가해 주세요</p>
        <span className="hidden text-xs text-ink-500 sm:inline">매너 온도에 반영돼요</span>
      </div>
      <div className="flex gap-3 overflow-x-auto p-4 scrollbar-thin">
        {items.map((item) => (
          <div
            key={`${item.targetId}-${item.gatheringId ?? 'g'}-${item.matchRequestId ?? 'm'}`}
            className="flex w-44 shrink-0 flex-col items-center rounded-2xl p-3 text-center ring-1 ring-ink-100"
          >
            <Avatar id={item.targetId} name={item.nickname} imageUrl={item.profileImageUrl} />
            <p className="mt-2 w-full truncate text-sm font-bold">{item.nickname}</p>
            <p className="w-full truncate text-xs text-ink-500">{item.context}</p>
            <Button size="sm" variant="secondary" className="mt-3 w-full" onClick={() => onReview(item)}>
              평가하기
            </Button>
          </div>
        ))}
      </div>
    </Card>
  )
}

export function GatheringCard({ gathering }: { gathering: GatheringSummary }) {
  const left = seatsLeft(gathering)
  const ended = gathering.status === 'COMPLETED' || gathering.status === 'CANCELED'
  return (
    <Link
      to={`/gatherings/${gathering.id}`}
      className={clsx(
        'group flex flex-col rounded-3xl bg-white p-5 shadow-card ring-1 ring-ink-100 transition-all hover:-translate-y-0.5 hover:shadow-lift',
        ended && 'opacity-70',
      )}
    >
      <div className="flex items-start gap-3">
        <div className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-brand-50 text-2xl">
          {sportEmoji(gathering.sportCode)}
        </div>
        <div className="min-w-0 flex-1">
          <div className="mb-1 flex flex-wrap items-center gap-1.5">
            <Badge tone={STATUS_TONE[gathering.status]}>{STATUS_LABEL[gathering.status]}</Badge>
            {gathering.joined && <Badge tone="green">참여 중</Badge>}
            {!ended && <span className="text-xs font-bold text-brand-600">{dDay(gathering.startsAt)}</span>}
          </div>
          <p className="line-clamp-2 font-bold leading-snug text-ink-900">{gathering.title}</p>
        </div>
      </div>

      <div className="mt-4 space-y-1.5 text-sm text-ink-600">
        <p className="flex items-center gap-2">
          <CalendarDays className="size-4 shrink-0 text-ink-400" />
          {gatheringTime(gathering.startsAt)}
        </p>
        <p className="flex items-center gap-2">
          <MapPin className="size-4 shrink-0 text-ink-400" />
          <span className="truncate">{gathering.placeName}</span>
          {gathering.distanceKm !== null && <span className="shrink-0 text-ink-400">· {gathering.distanceKm}km</span>}
        </p>
      </div>

      <div className="mt-auto pt-5">
        <div className="mb-1.5 flex items-center justify-between text-xs font-semibold">
          <span className="flex items-center gap-1 text-ink-600">
            <UsersRound className="size-3.5" /> {gathering.currentCount}/{gathering.capacity}명
          </span>
          {gathering.status === 'RECRUITING' && <span className="text-brand-600">{left}자리 남음</span>}
        </div>
        <div className="h-2 overflow-hidden rounded-full bg-ink-100">
          <div
            className={clsx('h-full rounded-full transition-all', left === 0 ? 'bg-ink-400' : 'bg-brand-500')}
            style={{ width: `${Math.min(100, (gathering.currentCount / gathering.capacity) * 100)}%` }}
          />
        </div>
        <div className="mt-4 flex items-center gap-2 border-t border-ink-100 pt-3 text-xs text-ink-500">
          <Avatar
            id={gathering.host.userId}
            name={gathering.host.nickname}
            imageUrl={gathering.host.profileImageUrl}
            size="sm"
            className="size-6! text-[10px]!"
          />
          <span className="truncate font-medium text-ink-700">{gathering.host.nickname}</span>
          <span>모임장 · {gathering.host.mannerScore}°</span>
        </div>
      </div>
    </Link>
  )
}

// ---------- 모임 만들기 ----------

/** 오늘부터 14일 */
function upcomingDays() {
  return Array.from({ length: 14 }, (_, offset) => {
    const date = new Date()
    date.setHours(0, 0, 0, 0)
    date.setDate(date.getDate() + offset)
    return date
  })
}

/** 06:00 ~ 23:30, 30분 단위 */
const TIME_SLOTS = Array.from({ length: 36 }, (_, index) => {
  const minutes = 6 * 60 + index * 30
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
})

function slotLabel(slot: string) {
  const [hour, minute] = slot.split(':').map(Number)
  const period = hour < 12 ? '오전' : '오후'
  const displayHour = hour % 12 === 0 ? 12 : hour % 12
  return `${period} ${displayHour}:${String(minute).padStart(2, '0')}`
}

function combine(day: Date, slot: string) {
  const [hour, minute] = slot.split(':').map(Number)
  const date = new Date(day)
  date.setHours(hour, minute, 0, 0)
  return date
}

const MIN_LEAD_MS = 10 * 60_000

function CreateGatheringModal({
  open,
  onClose,
  defaultLocation,
}: {
  open: boolean
  onClose: () => void
  defaultLocation: LocationInput | null
}) {
  const navigate = useNavigate()
  const toast = useToast()
  const create = useCreateGathering()
  const { data: sports } = useSports()
  const [now] = useState(Date.now)
  const days = upcomingDays()

  const [sportId, setSportId] = useState<number | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [location, setLocation] = useState<LocationInput | null>(defaultLocation)
  const [placeName, setPlaceName] = useState('')
  const [dayIndex, setDayIndex] = useState(1)
  const [slot, setSlot] = useState('19:00')
  const [capacity, setCapacity] = useState(4)
  const [submitted, setSubmitted] = useState(false)

  const startsAt = combine(days[dayIndex], slot)
  const tooSoon = startsAt.getTime() < now + MIN_LEAD_MS
  const errors = {
    sport: sportId === null ? '종목을 골라 주세요' : undefined,
    title: !title.trim() ? '모임 이름을 입력해 주세요' : undefined,
    place: !placeName.trim() ? '만날 장소를 입력해 주세요' : !location ? '지역을 검색해서 골라 주세요' : undefined,
    time: tooSoon ? '지금부터 10분 뒤 이후로 골라 주세요' : undefined,
  }
  const valid = !Object.values(errors).some(Boolean)

  const reset = () => {
    setSportId(null)
    setTitle('')
    setDescription('')
    setPlaceName('')
    setLocation(defaultLocation)
    setDayIndex(1)
    setSlot('19:00')
    setCapacity(4)
    setSubmitted(false)
  }

  const close = () => {
    reset()
    onClose()
  }

  const onSubmit = () => {
    setSubmitted(true)
    if (!valid || !location || sportId === null) return
    create.mutate(
      {
        sportId,
        title: title.trim(),
        description: description.trim(),
        placeName: placeName.trim(),
        location: { latitude: location.latitude, longitude: location.longitude },
        startsAt: startsAt.toISOString(),
        capacity,
      },
      {
        onSuccess: (detail) => {
          toast('모임을 열었어요! 단체 채팅방도 함께 만들어졌어요')
          close()
          navigate(`/gatherings/${detail.summary.id}`)
        },
        onError: (e) => toast(errorMessage(e), 'error'),
      },
    )
  }

  return (
    <Modal open={open} onClose={close} title="모임 만들기">
      <div className="space-y-5">
        <div>
          <p className="mb-1.5 text-sm font-semibold text-ink-700">운동 종목</p>
          <div className="flex flex-wrap gap-2">
            {sports?.map((sport) => (
              <Chip key={sport.id} active={sportId === sport.id} onClick={() => setSportId(sport.id)}>
                {sportEmoji(sport.code)} {sport.name}
              </Chip>
            ))}
          </div>
          {submitted && errors.sport && <p className="mt-1.5 text-xs text-red-600">{errors.sport}</p>}
        </div>

        <Field label="모임 이름" error={submitted ? errors.title : undefined}>
          <Input value={title} maxLength={100} onChange={(e) => setTitle(e.target.value)} placeholder="예) 토요일 아침 한강 5km 러닝" />
        </Field>

        <Field label="소개" hint="준비물, 난이도, 진행 방식 등을 적어 주세요 (선택)">
          <Textarea value={description} maxLength={2000} rows={3} onChange={(e) => setDescription(e.target.value)} />
        </Field>

        <div className="space-y-2">
          <Field label="만날 장소" error={submitted ? errors.place : undefined}>
            <Input value={placeName} maxLength={100} onChange={(e) => setPlaceName(e.target.value)} placeholder="예) 뚝섬유원지역 2번 출구" />
          </Field>
          <LocationSearch
            value={location}
            onChange={(picked) => {
              setLocation(picked)
              if (!placeName.trim()) setPlaceName(picked.areaName)
            }}
          />
          <p className="text-xs text-ink-400">지역은 근처 사람에게 모임을 보여 주는 데 쓰여요.</p>
        </div>

        <div>
          <p className="mb-1.5 text-sm font-semibold text-ink-700">날짜</p>
          <div className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1 scrollbar-thin">
            {days.map((day, index) => {
              const weekday = day.toLocaleDateString('ko-KR', { weekday: 'short' })
              const weekend = day.getDay() === 0 || day.getDay() === 6
              return (
                <button
                  key={day.toISOString()}
                  type="button"
                  onClick={() => setDayIndex(index)}
                  className={clsx(
                    'flex w-14 shrink-0 cursor-pointer flex-col items-center rounded-2xl py-2 transition-colors',
                    dayIndex === index ? 'bg-ink-900 text-white' : 'bg-white ring-1 ring-ink-200 hover:ring-ink-300',
                  )}
                >
                  <span
                    className={clsx(
                      'text-[11px] font-medium',
                      dayIndex === index ? 'text-white/70' : weekend ? 'text-brand-500' : 'text-ink-400',
                    )}
                  >
                    {index === 0 ? '오늘' : index === 1 ? '내일' : weekday}
                  </span>
                  <span className="text-lg font-bold">{day.getDate()}</span>
                </button>
              )
            })}
          </div>
        </div>

        <Field label="시간" error={submitted ? errors.time : undefined}>
          <select
            value={slot}
            onChange={(e) => setSlot(e.target.value)}
            className="h-12 w-full cursor-pointer rounded-xl bg-ink-50 px-4 text-[15px] ring-1 ring-ink-200 focus:bg-white focus:ring-2 focus:ring-brand-400 focus:outline-none"
          >
            {TIME_SLOTS.map((option) => (
              <option key={option} value={option} disabled={combine(days[dayIndex], option).getTime() < now + MIN_LEAD_MS}>
                {slotLabel(option)}
              </option>
            ))}
          </select>
        </Field>

        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-semibold text-ink-700">정원</p>
            <p className="text-xs text-ink-400">나를 포함해 2~50명</p>
          </div>
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setCapacity((value) => Math.max(2, value - 1))}
              disabled={capacity <= 2}
              className="flex size-9 cursor-pointer items-center justify-center rounded-full ring-1 ring-ink-200 hover:bg-ink-50 disabled:cursor-not-allowed disabled:opacity-40"
              aria-label="정원 줄이기"
            >
              <Minus className="size-4" />
            </button>
            <span className="w-12 text-center text-lg font-bold">{capacity}명</span>
            <button
              type="button"
              onClick={() => setCapacity((value) => Math.min(50, value + 1))}
              disabled={capacity >= 50}
              className="flex size-9 cursor-pointer items-center justify-center rounded-full ring-1 ring-ink-200 hover:bg-ink-50 disabled:cursor-not-allowed disabled:opacity-40"
              aria-label="정원 늘리기"
            >
              <Plus className="size-4" />
            </button>
          </div>
        </div>

        <Button className="w-full" size="lg" loading={create.isPending} onClick={onSubmit}>
          {gatheringTime(startsAt.toISOString())}에 모임 열기
        </Button>
      </div>
    </Modal>
  )
}
