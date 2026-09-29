import clsx from 'clsx'
import { AlarmClock, Bell, BellRing, ShieldAlert, CheckCheck, CornerDownRight, HeartHandshake, Inbox, MessageCircle, MessageSquareText, Star, UserPlus, XCircle, type LucideIcon } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { timeAgo } from '../lib/format'
import { useNotifications, useReadNotifications } from '../lib/queries'
import type { AppNotification, NotificationType } from '../lib/types'
import { Spinner } from './ui'

const TYPE_ICON: Record<NotificationType, { icon: LucideIcon; tone: string }> = {
  MATCH_REQUEST_RECEIVED: { icon: Inbox, tone: 'bg-brand-50 text-brand-600' },
  MATCH_REQUEST_ACCEPTED: { icon: MessageCircle, tone: 'bg-emerald-50 text-emerald-600' },
  GATHERING_JOINED: { icon: UserPlus, tone: 'bg-sky-50 text-sky-600' },
  GATHERING_CANCELED: { icon: XCircle, tone: 'bg-red-50 text-red-500' },
  GATHERING_REMINDER: { icon: AlarmClock, tone: 'bg-violet-50 text-violet-600' },
  MANNER_REVIEW_RECEIVED: { icon: HeartHandshake, tone: 'bg-amber-50 text-amber-600' },
  REVIEW_REQUESTED: { icon: Star, tone: 'bg-amber-50 text-amber-600' },
  POST_COMMENTED: { icon: MessageSquareText, tone: 'bg-teal-50 text-teal-600' },
  COMMENT_REPLIED: { icon: CornerDownRight, tone: 'bg-teal-50 text-teal-600' },
  ADMIN_WARNING: { icon: ShieldAlert, tone: 'bg-red-50 text-red-600' },
  REPORT_RESOLVED: { icon: BellRing, tone: 'bg-ink-100 text-ink-600' },
}

/**
 * 종 아이콘 + 안 읽은 수. 누르면 최근 알림 목록이 열리고, 알림을 누르면 읽음 처리 후 해당 화면으로 이동한다.
 * align: 사이드바에서는 오른쪽으로, 모바일 상단 바에서는 왼쪽으로 펼친다
 */
export function NotificationBell({ align = 'left', className }: { align?: 'left' | 'right'; className?: string }) {
  const navigate = useNavigate()
  const { data, isLoading } = useNotifications()
  const read = useReadNotifications()
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const unread = data?.unreadCount ?? 0

  useEffect(() => {
    if (!open) return
    const onPointer = (event: PointerEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && setOpen(false)
    document.addEventListener('pointerdown', onPointer)
    window.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      window.removeEventListener('keydown', onKey)
    }
  }, [open])

  const openNotification = (notification: AppNotification) => {
    if (!notification.read) read.mutate(notification.id)
    setOpen(false)
    if (notification.link) navigate(notification.link)
  }

  return (
    <div ref={containerRef} className={clsx('relative', className)}>
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-label={unread ? `알림 ${unread}개 안 읽음` : '알림'}
        aria-expanded={open}
        className={clsx(
          'relative flex size-10 cursor-pointer items-center justify-center rounded-full transition-colors',
          open ? 'bg-ink-100 text-ink-900' : 'text-ink-500 hover:bg-ink-100 hover:text-ink-800',
        )}
      >
        <Bell className="size-5" />
        {unread > 0 && (
          <span className="absolute top-1 right-1 flex h-4.5 min-w-4.5 items-center justify-center rounded-full bg-brand-500 px-1 text-[10px] font-bold text-white ring-2 ring-white">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div
          className={clsx(
            'absolute top-12 z-40 flex max-h-[min(70dvh,32rem)] w-[min(22rem,calc(100vw-2rem))] animate-pop flex-col overflow-hidden rounded-3xl bg-white shadow-lift ring-1 ring-ink-100',
            align === 'left' ? 'right-0' : 'left-0',
          )}
        >
          <div className="flex items-center justify-between border-b border-ink-100 px-5 py-3.5">
            <p className="font-bold">알림</p>
            {unread > 0 && (
              <button
                onClick={() => read.mutate('all')}
                className="flex cursor-pointer items-center gap-1 text-xs font-semibold text-ink-500 hover:text-brand-600"
              >
                <CheckCheck className="size-3.5" /> 모두 읽음
              </button>
            )}
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">
            {isLoading ? (
              <div className="flex justify-center py-10"><Spinner /></div>
            ) : !data?.items.length ? (
              <div className="px-6 py-12 text-center">
                <p className="text-3xl">🔔</p>
                <p className="mt-2 text-sm font-semibold text-ink-700">아직 알림이 없어요</p>
                <p className="mt-1 text-xs text-ink-400">매칭 요청, 모임 소식, 댓글, 매너 칭찬이 여기에 모여요</p>
              </div>
            ) : (
              <ul className="p-2">
                {data.items.map((notification) => {
                  const { icon: Icon, tone } = TYPE_ICON[notification.type] ?? TYPE_ICON.MATCH_REQUEST_RECEIVED
                  return (
                    <li key={notification.id}>
                      <button
                        onClick={() => openNotification(notification)}
                        className={clsx(
                          'flex w-full cursor-pointer items-start gap-3 rounded-2xl p-3 text-left transition-colors hover:bg-ink-50',
                          !notification.read && 'bg-brand-50/50',
                        )}
                      >
                        <span className={clsx('flex size-9 shrink-0 items-center justify-center rounded-full', tone)}>
                          <Icon className="size-4" />
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="flex items-center gap-2">
                            <span className={clsx('flex-1 truncate text-sm', notification.read ? 'font-medium text-ink-600' : 'font-bold text-ink-900')}>
                              {notification.title}
                            </span>
                            {!notification.read && <span className="size-2 shrink-0 rounded-full bg-brand-500" aria-label="안 읽음" />}
                          </span>
                          {notification.body && <span className="mt-0.5 line-clamp-2 block text-xs text-ink-500">{notification.body}</span>}
                          <span className="mt-1 block text-[11px] text-ink-400">{timeAgo(notification.createdAt)}</span>
                        </span>
                      </button>
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
