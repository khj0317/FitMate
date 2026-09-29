import { ArrowLeft, CalendarDays, Crown, ExternalLink, MapPin, MessageCircle, UsersRound } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { Avatar } from '../components/Avatar'
import { MannerReviewModal } from '../components/MannerReviewModal'
import { Modal } from '../components/Modal'
import { Badge, Button, Card, EmptyState, PageLoader } from '../components/ui'
import { errorMessage } from '../lib/api'
import { sportEmoji } from '../lib/format'
import { dDay, gatheringTime, seatsLeft, STATUS_LABEL, STATUS_TONE } from '../lib/gathering'
import { useGathering, useGatheringAction, usePendingReviews } from '../lib/queries'
import type { PendingReview } from '../lib/types'
import { useToast } from '../providers/ToastProvider'

export function GatheringDetailPage() {
  const { gatheringId } = useParams()
  const id = Number(gatheringId)
  const navigate = useNavigate()
  const toast = useToast()
  const { data: detail, isLoading, error } = useGathering(id)
  const { data: pending } = usePendingReviews()
  const action = useGatheringAction()
  const [confirm, setConfirm] = useState<'leave' | 'cancel' | null>(null)
  const [reviewTarget, setReviewTarget] = useState<PendingReview | null>(null)
  const [now] = useState(Date.now)

  if (isLoading) return <PageLoader />
  if (error || !detail) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-10">
        <Card>
          <EmptyState
            emoji="🤔"
            title="모임을 찾을 수 없어요"
            description={error ? errorMessage(error) : '취소되었거나 볼 수 없는 모임이에요.'}
            action={<Link to="/gatherings" className="font-bold text-brand-600">모임 목록으로</Link>}
          />
        </Card>
      </div>
    )
  }

  const { summary } = detail
  const left = seatsLeft(summary)
  const started = new Date(summary.startsAt).getTime() <= now
  const canJoin = !summary.joined && summary.status === 'RECRUITING' && !started
  const reviewable = new Map(
    (pending ?? []).filter((item) => item.gatheringId === id).map((item) => [item.targetId, item]),
  )

  const run = (kind: 'join' | 'leave' | 'cancel') =>
    action.mutate(
      { id, action: kind },
      {
        onSuccess: (result) => {
          setConfirm(null)
          if (kind === 'join') {
            toast('모임에 참여했어요! 단체 채팅방에서 인사해 보세요')
            const roomId = result && 'chatRoomId' in result ? result.chatRoomId : null
            if (roomId) navigate(`/chats/${roomId}`)
          } else if (kind === 'leave') {
            toast('모임에서 나왔어요')
          } else {
            toast('모임을 취소했어요. 참여자들에게 알림을 보냈어요')
            navigate('/gatherings')
          }
        },
        onError: (e) => {
          setConfirm(null)
          toast(errorMessage(e), 'error')
        },
      },
    )

  return (
    <div className="mx-auto max-w-3xl px-4 py-6 md:px-8 md:py-10">
      <button
        onClick={() => navigate(-1)}
        className="mb-4 inline-flex cursor-pointer items-center gap-1 text-sm font-semibold text-ink-500 hover:text-ink-800"
      >
        <ArrowLeft className="size-4" /> 뒤로
      </button>

      <Card className="overflow-hidden">
        <div className="bg-linear-to-br from-brand-50 via-white to-amber-50 p-6">
          <div className="flex items-start gap-4">
            <div className="flex size-16 shrink-0 items-center justify-center rounded-3xl bg-white text-4xl shadow-card">
              {sportEmoji(summary.sportCode)}
            </div>
            <div className="min-w-0 flex-1">
              <div className="mb-1.5 flex flex-wrap items-center gap-1.5">
                <Badge tone={STATUS_TONE[summary.status]}>{STATUS_LABEL[summary.status]}</Badge>
                <Badge>{summary.sportName}</Badge>
                {summary.joined && <Badge tone="green">참여 중</Badge>}
                {!started && <span className="text-xs font-bold text-brand-600">{dDay(summary.startsAt)}</span>}
              </div>
              <h1 className="text-xl font-extrabold leading-snug tracking-tight md:text-2xl">{summary.title}</h1>
            </div>
          </div>

          <div className="mt-6 grid gap-3 sm:grid-cols-2">
            <InfoRow icon={<CalendarDays className="size-4" />} label="언제">
              {gatheringTime(summary.startsAt)}
            </InfoRow>
            <InfoRow icon={<MapPin className="size-4" />} label="어디서">
              <a
                href={`https://map.kakao.com/link/map/${encodeURIComponent(summary.placeName)},${detail.latitude},${detail.longitude}`}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1 hover:text-brand-600 hover:underline"
              >
                {summary.placeName}
                <ExternalLink className="size-3.5 shrink-0" />
              </a>
              {summary.distanceKm !== null && <span className="text-ink-400"> · {summary.distanceKm}km</span>}
            </InfoRow>
          </div>
        </div>

        {detail.description && (
          <div className="border-t border-ink-100 p-6">
            <p className="text-[15px] leading-relaxed whitespace-pre-wrap text-ink-700">{detail.description}</p>
          </div>
        )}

        <div className="border-t border-ink-100 p-6">
          <div className="mb-4 flex items-center justify-between">
            <p className="flex items-center gap-1.5 font-bold">
              <UsersRound className="size-4" /> 참여자 {summary.currentCount}/{summary.capacity}
            </p>
            {summary.status === 'RECRUITING' && !started && (
              <span className="text-sm font-semibold text-brand-600">{left}자리 남음</span>
            )}
          </div>
          <ul className="space-y-2">
            {detail.participants.map((participant) => {
              const review = reviewable.get(participant.userId)
              return (
                <li key={participant.userId} className="flex items-center gap-3 rounded-2xl p-2 hover:bg-ink-50">
                  <Link to={`/users/${participant.userId}`} className="shrink-0">
                    <Avatar id={participant.userId} name={participant.nickname} imageUrl={participant.profileImageUrl} size="sm" />
                  </Link>
                  <div className="min-w-0 flex-1">
                    <p className="flex items-center gap-1.5 truncate font-semibold">
                      <Link to={`/users/${participant.userId}`} className="truncate hover:underline">{participant.nickname}</Link>
                      {participant.host && <Crown className="size-4 shrink-0 fill-amber-300 text-amber-500" aria-label="모임장" />}
                    </p>
                    <p className="text-xs text-ink-500">매너 온도 {participant.mannerScore}°</p>
                  </div>
                  {review && (
                    <Button size="sm" variant="secondary" onClick={() => setReviewTarget(review)}>
                      평가하기
                    </Button>
                  )}
                </li>
              )
            })}
          </ul>
        </div>

        <div className="flex flex-wrap gap-2 border-t border-ink-100 p-6">
          {detail.chatRoomId && (
            <Button className="flex-1" onClick={() => navigate(`/chats/${detail.chatRoomId}`)}>
              <MessageCircle className="size-4" /> 단체 채팅방
            </Button>
          )}
          {canJoin && (
            <Button className="flex-1" size="lg" loading={action.isPending} onClick={() => run('join')}>
              참여하기
            </Button>
          )}
          {!summary.joined && !canJoin && (
            <Button className="flex-1" size="lg" disabled>
              {started ? '이미 시작된 모임이에요' : '모집이 마감됐어요'}
            </Button>
          )}
          {summary.joined && !detail.isHost && !started && (
            <Button variant="secondary" onClick={() => setConfirm('leave')}>
              참여 취소
            </Button>
          )}
          {detail.isHost && !started && (
            <Button variant="danger" onClick={() => setConfirm('cancel')}>
              모임 취소
            </Button>
          )}
        </div>
      </Card>

      <Modal open={confirm !== null} onClose={() => setConfirm(null)} title={confirm === 'cancel' ? '모임 취소' : '참여 취소'}>
        <p className="text-[15px] leading-relaxed text-ink-600">
          {confirm === 'cancel'
            ? '모임을 취소하면 참여자 모두에게 알림이 가고, 되돌릴 수 없어요.'
            : '참여를 취소하면 단체 채팅방에서도 나가게 돼요. 자리가 남아 있으면 다시 참여할 수 있어요.'}
        </p>
        <div className="mt-6 flex gap-2">
          <Button variant="secondary" className="flex-1" onClick={() => setConfirm(null)}>
            돌아가기
          </Button>
          <Button
            variant={confirm === 'cancel' ? 'danger' : 'primary'}
            className="flex-1"
            loading={action.isPending}
            onClick={() => confirm && run(confirm)}
          >
            {confirm === 'cancel' ? '모임 취소하기' : '참여 취소하기'}
          </Button>
        </div>
      </Modal>

      <MannerReviewModal target={reviewTarget} onClose={() => setReviewTarget(null)} />
    </div>
  )
}

function InfoRow({ icon, label, children }: { icon: ReactNode; label: string; children: ReactNode }) {
  return (
    <div className="flex items-start gap-3 rounded-2xl bg-white/80 p-3 ring-1 ring-ink-100">
      <div className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-600">{icon}</div>
      <div className="min-w-0">
        <p className="text-xs font-medium text-ink-400">{label}</p>
        <p className="text-sm font-semibold text-ink-800">{children}</p>
      </div>
    </div>
  )
}
