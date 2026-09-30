import clsx from 'clsx'
import { CalendarClock, Check, MapPin, Send, SlidersHorizontal, Thermometer } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { Avatar } from '../components/Avatar'
import { Modal } from '../components/Modal'
import { SafetyMenu } from '../components/SafetyMenu'
import { ScoreRing } from '../components/ScoreRing'
import { Badge, Button, Card, Chip, EmptyState, Field, PageHeader, PageLoader, Textarea } from '../components/ui'
import { ApiError, errorMessage } from '../lib/api'
import { formatMinutes, GENDER_LABEL, presenceLabel, SKILL_LABEL, sportEmoji } from '../lib/format'
import { useMe, usePresence, useRecommendations, useSendMatchRequest } from '../lib/queries'
import type { MatchCandidate, Presence } from '../lib/types'
import { useToast } from '../providers/toastContext'

const RADIUS_OPTIONS = [1, 3, 5, 10, 20]

const SCORE_PARTS: { key: keyof MatchCandidate['scoreDetail']; label: string; max: number }[] = [
  { key: 'distance', label: '거리', max: 35 },
  { key: 'skill', label: '실력', max: 30 },
  { key: 'time', label: '시간', max: 25 },
  { key: 'manner', label: '매너', max: 10 },
]

export function DiscoverPage() {
  const navigate = useNavigate()
  const { data: me, isLoading: meLoading } = useMe()
  const [sportId, setSportId] = useState<number | null>(null)
  const [radiusKm, setRadiusKm] = useState<number | null>(null)
  const [target, setTarget] = useState<MatchCandidate | null>(null)
  const [requested, setRequested] = useState<Set<number>>(new Set())

  const profileReady = !!me?.location && (me.sports.length > 0 || sportId !== null)
  const { data: candidates, isLoading, error } = useRecommendations(sportId, radiusKm, profileReady)
  const { data: presence } = usePresence(candidates?.map((candidate) => candidate.userId) ?? [])

  if (meLoading || !me) return <PageLoader />
  const effectiveRadius = radiusKm ?? me.searchRadiusKm

  return (
    <div className="mx-auto max-w-6xl px-4 py-6 md:px-8 md:py-10">
      <PageHeader
        title="내 주변 운동 메이트"
        description={
          me.location ? (
            <span className="inline-flex items-center gap-1">
              <MapPin className="size-4 text-brand-500" />
              {me.location.areaName} 반경 {effectiveRadius}km
            </span>
          ) : (
            '활동 지역을 설정하면 가까운 운동 메이트를 찾아드려요'
          )
        }
      />

      {!profileReady ? (
        <Card>
          <EmptyState
            emoji="📍"
            title="프로필을 먼저 완성해 주세요"
            description="활동 지역과 운동 종목을 등록하면 거리·실력·운동 시간이 잘 맞는 메이트를 추천해 드려요."
            action={<Button onClick={() => navigate('/profile')}>프로필 설정하러 가기</Button>}
          />
        </Card>
      ) : (
        <>
          {/* 필터 */}
          <div className="mb-6 space-y-3">
            <div className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 scrollbar-thin md:mx-0 md:flex-wrap md:px-0">
              <Chip active={sportId === null} onClick={() => setSportId(null)}>
                ✨ 내 종목 전체
              </Chip>
              {me.sports.map((sport) => (
                <Chip key={sport.sportId} active={sportId === sport.sportId} onClick={() => setSportId(sport.sportId)}>
                  {sportEmoji(sport.code)} {sport.name}
                </Chip>
              ))}
            </div>
            <div className="flex items-center gap-2 text-sm">
              <SlidersHorizontal className="size-4 text-ink-400" />
              <span className="font-medium text-ink-500">반경</span>
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

          {isLoading ? (
            <PageLoader />
          ) : error ? (
            <Card>
              <EmptyState emoji="😵" title="추천을 불러오지 못했어요" description={errorMessage(error)} />
            </Card>
          ) : !candidates?.length ? (
            <Card>
              <EmptyState
                emoji="🔭"
                title="아직 주변에 메이트가 없어요"
                description="검색 반경을 넓히거나 다른 종목을 선택해 보세요."
                action={effectiveRadius < 20 && <Button variant="secondary" onClick={() => setRadiusKm(20)}>반경 20km로 넓히기</Button>}
              />
            </Card>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {candidates.map((candidate, index) => (
                <CandidateCard
                  key={candidate.userId}
                  candidate={candidate}
                  rank={index + 1}
                  requested={requested.has(candidate.userId)}
                  presence={presence?.get(candidate.userId)}
                  onRequest={() => setTarget(candidate)}
                />
              ))}
            </div>
          )}
        </>
      )}

      <RequestModal
        candidate={target}
        onClose={() => setTarget(null)}
        onSent={(userId) => setRequested((current) => new Set(current).add(userId))}
      />
    </div>
  )
}

function CandidateCard({
  candidate,
  rank,
  requested,
  presence,
  onRequest,
}: {
  candidate: MatchCandidate
  rank: number
  requested: boolean
  presence: Presence | undefined
  onRequest: () => void
}) {
  const profileLine = [candidate.ageGroup, candidate.gender && GENDER_LABEL[candidate.gender]].filter(Boolean).join(' · ')

  return (
    <Card
      className="flex animate-fade-up flex-col p-5 transition-all hover:-translate-y-0.5 hover:shadow-lift"
    >
      <div className="flex items-start gap-3">
        <Link to={`/users/${candidate.userId}`} className="relative" aria-label={`${candidate.nickname} 프로필 보기`}>
          <Avatar id={candidate.userId} name={candidate.nickname} imageUrl={candidate.profileImageUrl} online={presence?.online} />
          {rank <= 3 && (
            <span className="absolute -top-1 -left-1 flex size-5 items-center justify-center rounded-full bg-ink-900 text-[10px] font-bold text-white ring-2 ring-white">
              {rank}
            </span>
          )}
        </Link>
        <div className="min-w-0 flex-1">
          <Link to={`/users/${candidate.userId}`} className="block truncate text-[17px] font-bold hover:underline">
            {candidate.nickname}
          </Link>
          <p className="mt-0.5 truncate text-sm text-ink-500">
            {profileLine || '정보 비공개'}
            {presenceLabel(presence) && (
              <span className={clsx('ml-1.5 text-xs', presence?.online ? 'font-semibold text-emerald-600' : 'text-ink-400')}>
                · {presenceLabel(presence)}
              </span>
            )}
          </p>
          <div className="mt-2 flex flex-wrap gap-1.5">
            <Badge>
              <MapPin className="size-3" />
              {candidate.approximateDistanceKm}km 이내
            </Badge>
            <Badge tone="brand">
              <Thermometer className="size-3" />
              {candidate.mannerScore}°
            </Badge>
          </div>
        </div>
        <ScoreRing score={candidate.matchScore} />
      </div>

      {/* 공통 종목 */}
      <div className="mt-4 space-y-2">
        {candidate.commonSports.map((sport) => (
          <div key={sport.sportId} className="flex items-center justify-between rounded-2xl bg-ink-50 px-3 py-2.5 text-sm">
            <span className="font-semibold">
              {sportEmoji(sport.name)} {sport.name}
            </span>
            <span className="text-ink-500">
              나 <b className="text-ink-800">{sport.myLevel ? SKILL_LABEL[sport.myLevel] : '-'}</b>
              <span className="mx-1.5 text-ink-300">·</span>
              상대 <b className="text-ink-800">{SKILL_LABEL[sport.theirLevel]}</b>
            </span>
          </div>
        ))}
      </div>

      {/* 공통 종목 수에 따라 카드 높이가 달라져도, 아래 영역은 항상 카드 바닥에 붙여 버튼 위치를 맞춘다 */}
      <div className="mt-auto pt-3">
        <p className="flex items-center gap-1.5 text-sm text-ink-500">
          <CalendarClock className="size-4 text-ink-400" />
          운동 가능 시간 {formatMinutes(candidate.overlapMinutesPerWeek)}
          {candidate.overlapMinutesPerWeek > 0 && ' 겹쳐요'}
        </p>

        {/* 점수 내역 */}
        <div className="mt-4 grid grid-cols-4 gap-2">
          {SCORE_PARTS.map(({ key, label, max }) => (
            <div key={key}>
              <div className="h-1.5 overflow-hidden rounded-full bg-ink-100">
                <div
                  className="h-full rounded-full bg-linear-to-r from-brand-300 to-brand-500"
                  style={{ width: `${(candidate.scoreDetail[key] / max) * 100}%` }}
                />
              </div>
              <p className="mt-1 text-[11px] text-ink-400">
                {label} <span className="font-semibold text-ink-600">{candidate.scoreDetail[key]}</span>/{max}
              </p>
            </div>
          ))}
        </div>

        <div className="mt-5 flex gap-2">
          <Button className="flex-1" variant={requested ? 'secondary' : 'primary'} disabled={requested} onClick={onRequest}>
            {requested ? (
              <>
                <Check className="size-4" /> 요청 보냄
              </>
            ) : (
              <>
                <Send className="size-4" /> 같이 운동하기
              </>
            )}
          </Button>
          <SafetyMenu user={{ id: candidate.userId, nickname: candidate.nickname }} />
        </div>
      </div>
    </Card>
  )
}

function RequestModal({
  candidate,
  onClose,
  onSent,
}: {
  candidate: MatchCandidate | null
  onClose: () => void
  onSent: (userId: number) => void
}) {
  const toast = useToast()
  const sendRequest = useSendMatchRequest()
  const [sportId, setSportId] = useState<number | null>(null)
  const [message, setMessage] = useState('')

  const selectedSportId = sportId ?? candidate?.commonSports[0]?.sportId ?? null

  const close = () => {
    setSportId(null)
    setMessage('')
    onClose()
  }

  const submit = async () => {
    if (!candidate || !selectedSportId) return
    try {
      await sendRequest.mutateAsync({ receiverId: candidate.userId, sportId: selectedSportId, message: message || undefined })
      toast(`${candidate.nickname}님에게 매칭 요청을 보냈어요`)
      onSent(candidate.userId)
      close()
    } catch (e) {
      // 이미 요청했거나 매칭된 상대면 카드도 '요청 보냄'으로 바꿔 둔다
      if (e instanceof ApiError && ['DUPLICATE_MATCH_REQUEST', 'ALREADY_MATCHED'].includes(e.code)) {
        onSent(candidate.userId)
      }
      toast(errorMessage(e), 'error')
    }
  }

  return (
    <Modal open={candidate !== null} onClose={close} title="매칭 요청 보내기">
      {candidate && (
        <div className="space-y-5">
          <div className="flex items-center gap-3 rounded-2xl bg-ink-50 p-3">
            <Avatar id={candidate.userId} name={candidate.nickname} imageUrl={candidate.profileImageUrl} size="sm" />
            <div>
              <p className="font-bold">{candidate.nickname}</p>
              <p className="text-xs text-ink-500">매칭 점수 {candidate.matchScore}점 · {candidate.approximateDistanceKm}km 이내</p>
            </div>
          </div>
          <Field label="어떤 운동을 같이 할까요?">
            <div className="flex flex-wrap gap-2">
              {candidate.commonSports.map((sport) => (
                <Chip key={sport.sportId} active={selectedSportId === sport.sportId} onClick={() => setSportId(sport.sportId)}>
                  {sportEmoji(sport.name)} {sport.name}
                </Chip>
              ))}
            </div>
          </Field>
          <Field label="인사 메시지" hint={`${message.length}/300`}>
            <Textarea
              rows={3}
              maxLength={300}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              placeholder="안녕하세요! 평일 저녁에 같이 운동하실래요?"
            />
          </Field>
          <Button className="w-full" size="lg" loading={sendRequest.isPending} onClick={submit}>
            <Send className="size-4" /> 요청 보내기
          </Button>
        </div>
      )}
    </Modal>
  )
}
