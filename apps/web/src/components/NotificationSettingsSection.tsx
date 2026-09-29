import clsx from 'clsx'
import { errorMessage } from '../lib/api'
import { useNotificationSettings, useSaveNotificationSettings } from '../lib/queries'
import type { NotificationCategory } from '../lib/types'
import { useToast } from '../providers/ToastProvider'
import { Card } from './ui'

const CATEGORIES: { value: NotificationCategory; label: string; description: string }[] = [
  { value: 'MATCH', label: '매칭', description: '매칭 요청을 받거나 내 요청이 수락됐을 때' },
  { value: 'GATHERING', label: '모임', description: '참여자가 생기거나 모임이 취소됐을 때, 시작 1시간 전' },
  { value: 'MANNER', label: '매너 평가', description: '칭찬을 받았을 때, 끝난 모임 평가 요청' },
  { value: 'COMMUNITY', label: '커뮤니티', description: '내 글에 댓글, 내 댓글에 답글이 달렸을 때' },
]

/** 알림 종류별 켜기·끄기. 누르는 즉시 저장한다 */
export function NotificationSettingsSection() {
  const toast = useToast()
  const { data } = useNotificationSettings()
  const save = useSaveNotificationSettings()
  const muted = new Set(data?.muted ?? [])

  const toggle = (category: NotificationCategory) => {
    const next = new Set(muted)
    if (next.has(category)) next.delete(category)
    else next.add(category)
    save.mutate([...next], { onError: (e) => toast(errorMessage(e), 'error') })
  }

  return (
    <Card className="p-6">
      <h2 className="text-lg font-bold">알림 설정</h2>
      <p className="mt-0.5 text-sm text-ink-500">끈 알림은 알림 목록에도 쌓이지 않아요</p>
      <ul className="mt-3 divide-y divide-ink-100">
        {CATEGORIES.map((category) => {
          const on = !muted.has(category.value)
          return (
            <li key={category.value} className="flex items-center gap-4 py-3">
              <div className="min-w-0 flex-1">
                <p className="font-semibold">{category.label}</p>
                <p className="text-xs text-ink-500">{category.description}</p>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={on}
                aria-label={`${category.label} 알림`}
                disabled={!data || save.isPending}
                onClick={() => toggle(category.value)}
                className={clsx(
                  'relative h-7 w-12 shrink-0 cursor-pointer rounded-full transition-colors disabled:cursor-wait',
                  on ? 'bg-brand-500' : 'bg-ink-200',
                )}
              >
                <span
                  className={clsx(
                    'absolute top-1 left-1 size-5 rounded-full bg-white shadow transition-transform',
                    on && 'translate-x-5',
                  )}
                />
              </button>
            </li>
          )
        })}
      </ul>
    </Card>
  )
}
