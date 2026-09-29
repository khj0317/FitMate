import clsx from 'clsx'
import { Check, MessageCircle, Thermometer, X } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { Avatar } from '../components/Avatar'
import { Badge, Button, Card, Chip, EmptyState, PageHeader, PageLoader } from '../components/ui'
import { errorMessage } from '../lib/api'
import { sportEmoji, timeAgo } from '../lib/format'
import { useHandleMatchRequest, useMatchRequests } from '../lib/queries'
import type { MatchRequest, MatchRequestStatus } from '../lib/types'
import { useToast } from '../providers/ToastProvider'

type Box = 'received' | 'sent'

const STATUS_OPTIONS: { value: MatchRequestStatus; label: string }[] = [
  { value: 'PENDING', label: '대기 중' },
  { value: 'ACCEPTED', label: '매칭 완료' },
  { value: 'REJECTED', label: '거절됨' },
  { value: 'CANCELED', label: '취소됨' },
]

const EMPTY_TEXT: Record<Box, Record<MatchRequestStatus, string>> = {
  received: {
    PENDING: '아직 받은 요청이 없어요',
    ACCEPTED: '수락한 요청이 없어요',
    REJECTED: '거절한 요청이 없어요',
    CANCELED: '상대가 취소한 요청이 없어요',
  },
  sent: {
    PENDING: '대기 중인 요청이 없어요',
    ACCEPTED: '수락된 요청이 없어요',
    REJECTED: '거절된 요청이 없어요',
    CANCELED: '취소한 요청이 없어요',
  },
}

export function RequestsPage() {
  const [box, setBox] = useState<Box>('received')
  const [status, setStatus] = useState<MatchRequestStatus>('PENDING')
  const { data: requests, isLoading } = useMatchRequests(box, status)
  const { data: pendingReceived } = useMatchRequests('received', 'PENDING')

  return (
    <div className="mx-auto max-w-3xl px-4 py-6 md:px-8 md:py-10">
      <PageHeader title="매칭 요청" description="같이 운동하자는 요청을 확인하고 답해 주세요" />

      <div className="mb-4 grid grid-cols-2 rounded-2xl bg-ink-100 p-1">
        {(['received', 'sent'] as const).map((value) => (
          <button
            key={value}
            onClick={() => setBox(value)}
            className={clsx(
              'flex cursor-pointer items-center justify-center gap-2 rounded-xl py-2.5 text-[15px] font-bold transition-all',
              box === value ? 'bg-white text-ink-900 shadow-sm' : 'text-ink-500',
            )}
          >
            {value === 'received' ? '받은 요청' : '보낸 요청'}
            {value === 'received' && !!pendingReceived?.length && (
              <span className="rounded-full bg-brand-500 px-1.5 text-[11px] text-white">{pendingReceived.length}</span>
            )}
          </button>
        ))}
      </div>

      <div className="-mx-4 mb-6 flex gap-2 overflow-x-auto px-4 md:mx-0 md:px-0">
        {STATUS_OPTIONS.map((option) => (
          <Chip key={option.value} active={status === option.value} onClick={() => setStatus(option.value)}>
            {option.label}
          </Chip>
        ))}
      </div>

      {isLoading ? (
        <PageLoader />
      ) : !requests?.length ? (
        <Card>
          <EmptyState
            emoji={box === 'received' ? '📭' : '📤'}
            title={EMPTY_TEXT[box][status]}
            description={status === 'PENDING' ? '운동 메이트 탭에서 마음에 드는 사람에게 먼저 요청해 보세요.' : undefined}
          />
        </Card>
      ) : (
        <div className="space-y-3">
          {requests.map((request) => (
            <RequestCard key={request.id} request={request} box={box} />
          ))}
        </div>
      )}
    </div>
  )
}

function RequestCard({ request, box }: { request: MatchRequest; box: Box }) {
  const navigate = useNavigate()
  const toast = useToast()
  const handle = useHandleMatchRequest()
  const { counterpart } = request

  const run = async (action: 'accept' | 'reject' | 'cancel') => {
    try {
      const result = await handle.mutateAsync({ id: request.id, action })
      if (action === 'accept' && result) {
        toast(`${counterpart.nickname}님과 매칭됐어요! 채팅을 시작해 보세요`)
        navigate(`/chats/${result.chatRoomId}`)
      } else {
        toast(action === 'reject' ? '요청을 거절했어요' : '요청을 취소했어요')
      }
    } catch (e) {
      toast(errorMessage(e), 'error')
    }
  }

  return (
    <Card className="animate-fade-up p-5">
      <div className="flex items-start gap-3">
        <Link to={`/users/${counterpart.userId}`} className="shrink-0" aria-label={`${counterpart.nickname} 프로필 보기`}>
          <Avatar id={counterpart.userId} name={counterpart.nickname} imageUrl={counterpart.profileImageUrl} />
        </Link>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <Link to={`/users/${counterpart.userId}`} className="font-bold hover:underline">{counterpart.nickname}</Link>
            <Badge tone="brand">
              <Thermometer className="size-3" />
              {counterpart.mannerScore}°
            </Badge>
            <span className="ml-auto text-xs text-ink-400">{timeAgo(request.createdAt)}</span>
          </div>
          <p className="mt-1 text-sm text-ink-500">
            {sportEmoji(request.sportName)} {request.sportName} 같이 하기
          </p>
          {request.message && (
            <p className="mt-3 rounded-2xl rounded-tl-md bg-ink-50 px-4 py-3 text-[15px] leading-relaxed text-ink-800">
              {request.message}
            </p>
          )}
        </div>
      </div>

      {request.status === 'PENDING' && box === 'received' && (
        <div className="mt-4 grid grid-cols-2 gap-2">
          <Button variant="secondary" onClick={() => run('reject')} disabled={handle.isPending}>
            <X className="size-4" /> 거절
          </Button>
          <Button onClick={() => run('accept')} loading={handle.isPending && handle.variables?.action === 'accept'}>
            <Check className="size-4" /> 수락하기
          </Button>
        </div>
      )}
      {request.status === 'PENDING' && box === 'sent' && (
        <div className="mt-4 flex items-center justify-between gap-3">
          <span className="text-sm text-ink-400">상대방의 답을 기다리고 있어요</span>
          <Button variant="ghost" size="sm" onClick={() => run('cancel')} loading={handle.isPending}>
            요청 취소
          </Button>
        </div>
      )}
      {request.status === 'ACCEPTED' && request.chatRoomId && (
        <Button variant="secondary" className="mt-4 w-full" onClick={() => navigate(`/chats/${request.chatRoomId}`)}>
          <MessageCircle className="size-4" /> 채팅하기
        </Button>
      )}
    </Card>
  )
}
