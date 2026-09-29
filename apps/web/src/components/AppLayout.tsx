import clsx from 'clsx'
import { Compass, Inbox, LogOut, MessageCircle, Newspaper, ShieldCheck, UserRound, UsersRound, type LucideIcon } from 'lucide-react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { useChatRooms, useMatchRequests, useMe } from '../lib/queries'
import { useAuth } from '../providers/AuthProvider'
import { useChatSocket } from '../providers/ChatSocketProvider'
import { Avatar } from './Avatar'
import { Logo } from './Logo'
import { NotificationBell } from './NotificationBell'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  badge?: number
  /** 모바일 하단 탭에 보일지 (아니면 상단 바 아이콘으로) */
  mobileTab?: boolean
}

function useNavItems(): NavItem[] {
  const { data: rooms } = useChatRooms()
  const { data: received } = useMatchRequests('received', 'PENDING')
  const unread = rooms?.reduce((sum, room) => sum + room.unreadCount, 0) ?? 0
  const { data: me } = useMe()
  const admin: NavItem[] = me?.role === 'ADMIN' ? [{ to: '/admin', label: '관리자', icon: ShieldCheck }] : []
  return [
    { to: '/', label: '운동 메이트', icon: Compass, mobileTab: true },
    { to: '/gatherings', label: '모임', icon: UsersRound, mobileTab: true },
    { to: '/community', label: '커뮤니티', icon: Newspaper, mobileTab: true },
    { to: '/requests', label: '매칭 요청', icon: Inbox, badge: received?.length ?? 0 },
    { to: '/chats', label: '채팅', icon: MessageCircle, badge: unread, mobileTab: true },
    { to: '/profile', label: '내 프로필', icon: UserRound, mobileTab: true },
    ...admin,
  ]
}

function CountBadge({ count, className }: { count?: number; className?: string }) {
  if (!count) return null
  return (
    <span
      className={clsx(
        'flex h-5 min-w-5 items-center justify-center rounded-full bg-brand-500 px-1.5 text-[11px] font-bold text-white',
        className,
      )}
    >
      {count > 99 ? '99+' : count}
    </span>
  )
}

export function AppLayout() {
  const items = useNavItems()
  const { data: me } = useMe()
  const { logout } = useAuth()
  const { connected } = useChatSocket()
  const location = useLocation()
  // 채팅방 안에서는 입력창이 가려지지 않도록 모바일 하단 탭바를 숨긴다
  const inChatRoom = /^\/chats\/\d+/.test(location.pathname)

  return (
    <div className="min-h-dvh md:flex">
      {/* 데스크톱 사이드바 */}
      <aside className="sticky top-0 z-30 hidden h-dvh w-64 shrink-0 flex-col border-r border-ink-100 bg-white px-4 py-6 md:flex">
        <div className="flex items-center justify-between">
          <Logo className="px-3" />
          <NotificationBell align="right" />
        </div>
        <nav className="mt-10 flex flex-col gap-1">
          {items.map(({ to, label, icon: Icon, badge }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                clsx(
                  'flex items-center gap-3 rounded-2xl px-3 py-3 text-[15px] font-semibold transition-colors',
                  isActive ? 'bg-brand-50 text-brand-700' : 'text-ink-600 hover:bg-ink-50 hover:text-ink-900',
                )
              }
            >
              <Icon className="size-5" />
              <span className="flex-1">{label}</span>
              <CountBadge count={badge} />
            </NavLink>
          ))}
        </nav>

        {me && (
          <div className="mt-auto rounded-2xl bg-ink-50 p-3">
            <div className="flex items-center gap-3">
              <Avatar id={me.id} name={me.nickname} imageUrl={me.profileImageUrl} size="sm" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-bold">{me.nickname}</p>
                <p className="flex items-center gap-1.5 text-xs text-ink-500">
                  <span className={clsx('size-1.5 rounded-full', connected ? 'bg-emerald-500' : 'bg-ink-300')} />
                  {connected ? '실시간 연결됨' : '연결 중...'}
                </p>
              </div>
              <button
                onClick={() => void logout()}
                className="cursor-pointer rounded-lg p-2 text-ink-400 hover:bg-white hover:text-ink-700"
                aria-label="로그아웃"
                title="로그아웃"
              >
                <LogOut className="size-4" />
              </button>
            </div>
          </div>
        )}
      </aside>

      {/* 모바일 상단 바 */}
      {!inChatRoom && (
        <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-ink-100 bg-white/85 px-4 backdrop-blur md:hidden">
          <Logo />
          <div className="-mr-2 flex items-center">
            {items
              .filter((item) => !item.mobileTab)
              .map(({ to, label, icon: Icon, badge }) => (
                <NavLink
                  key={to}
                  to={to}
                  aria-label={badge ? `${label} ${badge}개` : label}
                  className={({ isActive }) =>
                    clsx(
                      'relative flex size-10 items-center justify-center rounded-full transition-colors',
                      isActive ? 'bg-brand-50 text-brand-600' : 'text-ink-500 hover:bg-ink-100',
                    )
                  }
                >
                  <Icon className="size-5" />
                  <CountBadge count={badge} className="absolute top-0.5 right-0.5 h-4.5! min-w-4.5! px-1! text-[10px]! ring-2 ring-white" />
                </NavLink>
              ))}
            <NotificationBell />
          </div>
        </header>
      )}

      <main className={clsx('min-w-0 flex-1', !inChatRoom && 'pb-24 md:pb-0')}>
        <Outlet />
      </main>

      {/* 모바일 하단 탭바 */}
      {!inChatRoom && (
        <nav className="fixed inset-x-0 bottom-0 z-20 grid grid-cols-5 border-t border-ink-100 bg-white/95 pt-2 backdrop-blur pb-safe md:hidden">
          {items.filter((item) => item.mobileTab).map(({ to, label, icon: Icon, badge }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                clsx(
                  'relative flex flex-col items-center gap-1 text-[11px] font-semibold',
                  isActive ? 'text-brand-600' : 'text-ink-400',
                )
              }
            >
              <Icon className="size-6" />
              {label}
              <CountBadge count={badge} className="absolute -top-1 left-1/2 ml-2" />
            </NavLink>
          ))}
        </nav>
      )}
    </div>
  )
}
