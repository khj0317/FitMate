import clsx from 'clsx'
import { ArrowRight, Ban, CheckCircle2, ShieldCheck, TriangleAlert } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { Avatar } from '../components/Avatar'
import { Modal } from '../components/Modal'
import { Badge, Button, Card, EmptyState, PageHeader, PageLoader, Segmented, Textarea } from '../components/ui'
import { errorMessage } from '../lib/api'
import { REPORT_REASON_LABEL, timeAgo } from '../lib/format'
import { useAdminReports, useAdminStats, useMe, useResolveReport, useUnsuspend } from '../lib/queries'
import type { AdminAction, AdminReport } from '../lib/types'
import { useToast } from '../providers/toastContext'

const ACTIONS: { value: AdminAction; label: string; description: string; variant: 'secondary' | 'danger' }[] = [
  { value: 'DISMISS', label: '문제 없음', description: '위반이 아니라고 판단해요. 신고자에게 결과를 알려요.', variant: 'secondary' },
  { value: 'WARN', label: '경고', description: '신고당한 사람에게 경고 알림을 보내요. 이용은 계속할 수 있어요.', variant: 'secondary' },
  { value: 'SUSPEND_7D', label: '7일 정지', description: '7일 동안 로그인할 수 없고, 모든 기기에서 로그아웃돼요.', variant: 'danger' },
  { value: 'SUSPEND_PERMANENT', label: '영구 정지', description: '다시 로그인할 수 없어요. 정지 해제로 되돌릴 수 있어요.', variant: 'danger' },
]
const ACTION_LABEL = Object.fromEntries(ACTIONS.map((a) => [a.value, a.label])) as Record<AdminAction, string>

/** 영구 정지는 9999년까지 정지로 저장된다 */
function suspensionLabel(until: string | null) {
  if (!until || new Date(until).getTime() <= Date.now()) return null
  if (new Date(until).getFullYear() >= 2100) return '영구 정지'
  return `${new Date(until).toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' })}까지 정지`
}

export function AdminPage() {
  const { data: me, isLoading } = useMe()
  const isAdmin = me?.role === 'ADMIN'
  const [tab, setTab] = useState<'pending' | 'done'>('pending')
  const { data: stats } = useAdminStats(isAdmin)
  const reports = useAdminReports(tab === 'pending', isAdmin)
  const [target, setTarget] = useState<AdminReport | null>(null)

  if (isLoading) return <PageLoader />
  if (!isAdmin) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-10">
        <Card>
          <EmptyState emoji="🔒" title="관리자만 볼 수 있는 화면이에요" />
        </Card>
      </div>
    )
  }

  const items = reports.data?.pages.flatMap((page) => page.items) ?? []
  const cards = stats
    ? [
        { label: '전체 회원', value: stats.totalUsers, sub: `최근 7일 +${stats.newUsers7d}` },
        { label: '매칭 성사', value: stats.acceptedMatches },
        { label: '다가오는 모임', value: stats.upcomingGatherings },
        { label: '최근 7일 글', value: stats.posts7d },
        { label: '최근 7일 메시지', value: stats.messages7d },
        { label: '검토 대기 신고', value: stats.pendingReports, alert: stats.pendingReports > 0 },
        { label: '정지된 계정', value: stats.suspendedUsers },
      ]
    : []

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 md:px-8 md:py-10">
      <PageHeader
        title="관리자"
        description={
          <span className="inline-flex items-center gap-1">
            <ShieldCheck className="size-4 text-brand-500" /> 운영 현황과 신고 처리
          </span>
        }
      />

      <div className="mb-8 grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
        {cards.map((card) => (
          <Card key={card.label} className={clsx('p-4', card.alert && 'ring-2 ring-brand-300')}>
            <p className="text-xs font-medium text-ink-500">{card.label}</p>
            <p className={clsx('mt-1 text-2xl font-extrabold', card.alert ? 'text-brand-600' : 'text-ink-900')}>
              {card.value.toLocaleString()}
            </p>
            {card.sub && <p className="mt-0.5 text-xs text-emerald-600">{card.sub}</p>}
          </Card>
        ))}
      </div>

      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-lg font-bold">신고</h2>
        <Segmented
          options={[
            { value: 'pending', label: `검토 대기${stats?.pendingReports ? ` ${stats.pendingReports}` : ''}` },
            { value: 'done', label: '처리 완료' },
          ]}
          value={tab}
          onChange={setTab}
        />
      </div>

      {reports.isLoading ? (
        <PageLoader />
      ) : items.length === 0 ? (
        <Card>
          <EmptyState
            emoji={tab === 'pending' ? '✨' : '🗂️'}
            title={tab === 'pending' ? '검토할 신고가 없어요' : '처리한 신고가 없어요'}
          />
        </Card>
      ) : (
        <div className="space-y-3">
          {items.map((report) => (
            <ReportCard key={report.id} report={report} onHandle={() => setTarget(report)} />
          ))}
          {reports.hasNextPage && (
            <div className="flex justify-center pt-2">
              <Button variant="secondary" loading={reports.isFetchingNextPage} onClick={() => void reports.fetchNextPage()}>
                더 보기
              </Button>
            </div>
          )}
        </div>
      )}

      {target && <ResolveModal report={target} onClose={() => setTarget(null)} />}
    </div>
  )
}

function ReportCard({ report, onHandle }: { report: AdminReport; onHandle: () => void }) {
  const toast = useToast()
  const unsuspend = useUnsuspend()
  const reported = report.reported
  const suspension = suspensionLabel(reported?.suspendedUntil ?? null)
  const pending = report.status === 'PENDING'

  return (
    <Card className="p-5">
      <div className="flex flex-wrap items-start gap-4">
        {reported ? (
          <Link to={`/users/${reported.userId}`} className="flex min-w-0 flex-1 items-center gap-3">
            <Avatar id={reported.userId} name={reported.nickname} imageUrl={reported.profileImageUrl} size="sm" />
            <div className="min-w-0">
              <p className="truncate font-bold hover:underline">{reported.nickname}</p>
              <p className="text-xs text-ink-500">
                매너 {reported.mannerScore}° · 누적 신고 {reported.totalReports}건
              </p>
            </div>
          </Link>
        ) : (
          <p className="flex-1 font-bold text-ink-400">탈퇴한 회원</p>
        )}
        <div className="flex flex-wrap items-center gap-1.5">
          <Badge tone="red">{REPORT_REASON_LABEL[report.reason]}</Badge>
          {suspension && <Badge tone="red"><Ban className="size-3" /> {suspension}</Badge>}
          {!pending && report.action && (
            <Badge tone={report.action === 'DISMISS' ? 'neutral' : 'brand'}>{ACTION_LABEL[report.action]}</Badge>
          )}
        </div>
      </div>

      {report.detail && (
        <p className="mt-3 rounded-2xl bg-ink-50 px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap text-ink-700">
          {report.detail}
        </p>
      )}
      {report.adminNote && <p className="mt-2 text-xs text-ink-500">메모: {report.adminNote}</p>}

      <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-ink-400">
        <span>신고자 {report.reporter?.nickname ?? '탈퇴한 회원'}</span>
        <span>· {timeAgo(report.createdAt)}</span>
        {report.resolvedAt && <span>· 처리 {timeAgo(report.resolvedAt)}</span>}
        <span className="flex-1" />
        {suspension && reported && (
          <Button
            size="sm"
            variant="secondary"
            loading={unsuspend.isPending}
            onClick={() =>
              unsuspend.mutate(reported.userId, {
                onSuccess: () => toast(`${reported.nickname}님의 정지를 풀었어요`),
                onError: (e) => toast(errorMessage(e), 'error'),
              })
            }
          >
            정지 해제
          </Button>
        )}
        {pending && (
          <Button size="sm" onClick={onHandle}>
            처리하기 <ArrowRight className="size-4" />
          </Button>
        )}
      </div>
    </Card>
  )
}

function ResolveModal({ report, onClose }: { report: AdminReport; onClose: () => void }) {
  const toast = useToast()
  const resolve = useResolveReport()
  const [action, setAction] = useState<AdminAction | null>(null)
  const [note, setNote] = useState('')
  const name = report.reported?.nickname ?? '탈퇴한 회원'

  const submit = () => {
    if (!action) return
    resolve.mutate({ id: report.id, action, note }, {
      onSuccess: (result) => {
        toast(result.handledReports > 1
          ? `${name}님에 대한 신고 ${result.handledReports}건을 함께 처리했어요`
          : '신고를 처리했어요')
        onClose()
      },
      onError: (e) => toast(errorMessage(e), 'error'),
    })
  }

  return (
    <Modal open onClose={onClose} title={`${name}님 신고 처리`}>
      <div className="space-y-4">
        <p className="text-sm text-ink-500">같은 사람에 대한 다른 검토 대기 신고도 함께 처리돼요.</p>
        <div className="space-y-2">
          {ACTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              onClick={() => setAction(option.value)}
              className={clsx(
                'flex w-full cursor-pointer items-start gap-3 rounded-2xl p-3 text-left transition-all',
                action === option.value
                  ? option.variant === 'danger' ? 'bg-red-50 ring-2 ring-red-300' : 'bg-brand-50 ring-2 ring-brand-300'
                  : 'ring-1 ring-ink-200 hover:ring-ink-300',
              )}
            >
              {option.variant === 'danger'
                ? <Ban className="mt-0.5 size-5 shrink-0 text-red-500" />
                : option.value === 'WARN'
                  ? <TriangleAlert className="mt-0.5 size-5 shrink-0 text-amber-500" />
                  : <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-emerald-500" />}
              <span>
                <span className="block font-semibold">{option.label}</span>
                <span className="block text-xs text-ink-500">{option.description}</span>
              </span>
            </button>
          ))}
        </div>
        <Textarea
          value={note}
          onChange={(e) => setNote(e.target.value)}
          maxLength={500}
          rows={2}
          placeholder="처리 메모 (선택, 관리자만 볼 수 있어요)"
          aria-label="처리 메모"
        />
        <Button
          className="w-full"
          size="lg"
          variant={action === 'SUSPEND_7D' || action === 'SUSPEND_PERMANENT' ? 'danger' : 'primary'}
          disabled={!action}
          loading={resolve.isPending}
          onClick={submit}
        >
          {action ? `${ACTION_LABEL[action]}로 처리하기` : '처리 방법을 골라 주세요'}
        </Button>
      </div>
    </Modal>
  )
}
