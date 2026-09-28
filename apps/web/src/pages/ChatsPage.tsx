import { useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { ArrowLeft, ArrowUp, ChevronUp } from 'lucide-react'
import { useCallback, useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { Avatar } from '../components/Avatar'
import { Button, EmptyState, Spinner } from '../components/ui'
import { api, errorMessage } from '../lib/api'
import { clockTime, dayLabel, isSameDay, timeAgo } from '../lib/format'
import { fetchMessages, keys, useChatRooms, useMe } from '../lib/queries'
import type { ChatMessage, ChatRoom } from '../lib/types'
import { useChatSocket } from '../providers/ChatSocketProvider'
import { useToast } from '../providers/ToastProvider'

export function ChatsPage() {
  const { roomId } = useParams()
  const selectedId = roomId ? Number(roomId) : null
  const { data: rooms, isLoading } = useChatRooms()
  const room = rooms?.find((r) => r.roomId === selectedId) ?? null

  return (
    <div className="md:grid md:h-dvh md:grid-cols-[340px_1fr] md:gap-6 md:p-6">
      {/* 채팅방 목록: 모바일에서는 방을 열면 숨긴다 */}
      <section
        className={clsx(
          'flex flex-col md:min-h-0 md:rounded-3xl md:bg-white md:shadow-card md:ring-1 md:ring-ink-100',
          selectedId && 'hidden md:flex',
        )}
      >
        <div className="px-5 pt-6 pb-3 md:pt-5">
          <h1 className="text-2xl font-extrabold tracking-tight">채팅</h1>
        </div>
        <div className="px-2 pb-4 scrollbar-thin md:min-h-0 md:flex-1 md:overflow-y-auto">
          {isLoading ? (
            <div className="flex justify-center py-10"><Spinner /></div>
          ) : !rooms?.length ? (
            <EmptyState emoji="💬" title="아직 대화가 없어요" description="매칭 요청이 수락되면 여기에서 대화할 수 있어요." />
          ) : (
            rooms.map((r) => <RoomItem key={r.roomId} room={r} active={r.roomId === selectedId} />)
          )}
        </div>
      </section>

      {/* 대화 */}
      <section
        className={clsx(
          'min-h-0 flex-col bg-white md:flex md:overflow-hidden md:rounded-3xl md:shadow-card md:ring-1 md:ring-ink-100',
          selectedId ? 'flex h-dvh md:h-auto' : 'hidden',
        )}
      >
        {selectedId && room ? (
          <ChatRoomView key={selectedId} room={room} />
        ) : selectedId && !isLoading ? (
          <EmptyState emoji="🤔" title="채팅방을 찾을 수 없어요" action={<Link to="/chats" className="font-bold text-brand-600">목록으로</Link>} />
        ) : (
          <div className="hidden h-full items-center justify-center md:flex">
            <EmptyState emoji="👈" title="대화를 선택하세요" description="왼쪽 목록에서 채팅방을 고르면 대화가 열려요." />
          </div>
        )}
      </section>
    </div>
  )
}

function RoomItem({ room, active }: { room: ChatRoom; active: boolean }) {
  const name = room.counterpart?.nickname ?? `채팅방 ${room.roomId}`
  return (
    <Link
      to={`/chats/${room.roomId}`}
      className={clsx(
        'flex items-center gap-3 rounded-2xl px-3 py-3 transition-colors',
        active ? 'bg-brand-50' : 'hover:bg-ink-50',
      )}
    >
      <Avatar id={room.counterpart?.userId ?? room.roomId} name={name} imageUrl={room.counterpart?.profileImageUrl} />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="flex-1 truncate font-bold">{name}</p>
          {room.lastMessage && <span className="shrink-0 text-xs text-ink-400">{timeAgo(room.lastMessage.createdAt)}</span>}
        </div>
        <div className="mt-0.5 flex items-center gap-2">
          <p className={clsx('flex-1 truncate text-sm', room.unreadCount ? 'font-semibold text-ink-800' : 'text-ink-500')}>
            {room.lastMessage?.content ?? '매칭됐어요! 먼저 인사를 건네 보세요 👋'}
          </p>
          {room.unreadCount > 0 && (
            <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-brand-500 px-1.5 text-[11px] font-bold text-white">
              {room.unreadCount}
            </span>
          )}
        </div>
      </div>
    </Link>
  )
}

function ChatRoomView({ room }: { room: ChatRoom }) {
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const { data: me } = useMe()
  const { connected, onMessage, send } = useChatSocket()

  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [nextCursor, setNextCursor] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [text, setText] = useState('')

  const scrollRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const stickToBottom = useRef(true)
  const preserveFromBottom = useRef<number | null>(null)
  const lastReadId = useRef(0)

  const name = room.counterpart?.nickname ?? `채팅방 ${room.roomId}`

  // 처음 열면 최근 메시지를 불러온다 (서버는 최신순이므로 뒤집어서 표시)
  useEffect(() => {
    let cancelled = false
    fetchMessages(room.roomId)
      .then((page) => {
        if (cancelled) return
        setMessages(page.messages.slice().reverse())
        setNextCursor(page.nextCursor)
      })
      .catch((e) => toast(errorMessage(e), 'error'))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [room.roomId, toast])

  // 실시간 메시지 수신
  useEffect(
    () =>
      onMessage((message) => {
        if (message.roomId !== room.roomId) return
        setMessages((current) => (current.some((m) => m.id === message.id) ? current : [...current, message]))
      }),
    [onMessage, room.roomId],
  )

  // 마지막 메시지까지 읽음 처리
  const latestId = messages.at(-1)?.id ?? 0
  useEffect(() => {
    if (!latestId || latestId <= lastReadId.current) return
    lastReadId.current = latestId
    api
      .post(`/api/chat-rooms/${room.roomId}/read`, { lastMessageId: latestId })
      .then(() => queryClient.invalidateQueries({ queryKey: keys.chatRooms }))
      .catch(() => undefined)
  }, [latestId, room.roomId, queryClient])

  // 스크롤: 새 메시지는 아래로, 이전 메시지를 불러왔을 때는 보던 위치 유지
  useLayoutEffect(() => {
    const el = scrollRef.current
    if (!el) return
    if (preserveFromBottom.current !== null) {
      el.scrollTop = el.scrollHeight - preserveFromBottom.current
      preserveFromBottom.current = null
    } else if (stickToBottom.current) {
      el.scrollTop = el.scrollHeight
    }
  }, [messages])

  const onScroll = () => {
    const el = scrollRef.current
    if (el) stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 120
  }

  const loadOlder = useCallback(async () => {
    if (!nextCursor || loadingOlder) return
    setLoadingOlder(true)
    try {
      const page = await fetchMessages(room.roomId, nextCursor)
      const el = scrollRef.current
      preserveFromBottom.current = el ? el.scrollHeight - el.scrollTop : null
      setMessages((current) => [...page.messages.slice().reverse(), ...current])
      setNextCursor(page.nextCursor)
    } catch (e) {
      toast(errorMessage(e), 'error')
    } finally {
      setLoadingOlder(false)
    }
  }, [nextCursor, loadingOlder, room.roomId, toast])

  const submit = async () => {
    const content = text.trim()
    if (!content) return
    setText('')
    stickToBottom.current = true
    try {
      await send(room.roomId, content)
    } catch (e) {
      setText(content)
      toast(errorMessage(e), 'error')
    }
    inputRef.current?.focus()
  }

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    // 한글 조합 중 Enter는 글자 확정용이라 전송하지 않는다 (안 그러면 마지막 글자가 한 번 더 전송됨)
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      void submit()
    }
  }

  return (
    <div className="flex h-full min-h-0 flex-col">
      <header className="flex items-center gap-3 border-b border-ink-100 px-4 py-3">
        <button onClick={() => navigate('/chats')} className="-ml-1 cursor-pointer rounded-full p-1.5 hover:bg-ink-100 md:hidden" aria-label="뒤로">
          <ArrowLeft className="size-5" />
        </button>
        <Avatar id={room.counterpart?.userId ?? room.roomId} name={name} imageUrl={room.counterpart?.profileImageUrl} size="sm" />
        <div className="min-w-0 flex-1">
          <p className="truncate font-bold">{name}</p>
          <p className="flex items-center gap-1.5 text-xs text-ink-500">
            <span className={clsx('size-1.5 rounded-full', connected ? 'bg-emerald-500' : 'bg-amber-400')} />
            {connected ? '실시간 대화 중' : '연결 중... (메시지는 계속 보낼 수 있어요)'}
          </p>
        </div>
      </header>

      <div ref={scrollRef} onScroll={onScroll} className="min-h-0 flex-1 overflow-y-auto bg-ink-50/60 px-4 py-4 scrollbar-thin">
        {loading ? (
          <div className="flex h-full items-center justify-center"><Spinner /></div>
        ) : (
          <>
            {nextCursor && (
              <div className="mb-4 flex justify-center">
                <Button variant="secondary" size="sm" onClick={loadOlder} loading={loadingOlder}>
                  <ChevronUp className="size-4" /> 이전 대화 보기
                </Button>
              </div>
            )}
            {messages.length === 0 && (
              <EmptyState emoji="🤝" title={`${name}님과 매칭됐어요!`} description="운동 종목, 시간, 장소를 이야기해 보세요." />
            )}
            <MessageList messages={messages} myId={me?.id ?? -1} counterpartId={room.counterpart?.userId ?? 0} />
          </>
        )}
      </div>

      <div className="border-t border-ink-100 bg-white p-3 pb-safe md:pb-3">
        <div className="flex items-end gap-2 rounded-3xl bg-ink-50 p-1.5 pl-4 ring-1 ring-ink-200 focus-within:ring-2 focus-within:ring-brand-400">
          <textarea
            ref={inputRef}
            rows={1}
            value={text}
            maxLength={1000}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="메시지를 입력하세요"
            className="max-h-32 min-h-9 flex-1 resize-none bg-transparent py-2 text-[15px] outline-none [field-sizing:content] placeholder:text-ink-400"
          />
          <button
            onClick={() => void submit()}
            disabled={!text.trim()}
            className="flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-full bg-brand-500 text-white transition hover:bg-brand-600 disabled:cursor-default disabled:bg-ink-200"
            aria-label="보내기"
          >
            <ArrowUp className="size-5" />
          </button>
        </div>
      </div>
    </div>
  )
}

/** 같은 사람이 3분 안에 연달아 보낸 메시지는 하나의 묶음으로 보여준다. */
function MessageList({ messages, myId, counterpartId }: { messages: ChatMessage[]; myId: number; counterpartId: number }) {
  return (
    <div className="flex flex-col">
      {messages.map((message, index) => {
        const prev = messages[index - 1]
        const next = messages[index + 1]
        const mine = message.senderId === myId
        const newDay = !prev || !isSameDay(prev.createdAt, message.createdAt)
        const groupedWithPrev = !newDay && prev?.senderId === message.senderId && withinMinutes(prev.createdAt, message.createdAt, 3)
        const groupedWithNext =
          !!next && next.senderId === message.senderId && isSameDay(message.createdAt, next.createdAt) && withinMinutes(message.createdAt, next.createdAt, 3)

        return (
          <div key={message.id}>
            {newDay && (
              <div className="my-4 flex justify-center">
                <span className="rounded-full bg-ink-200/60 px-3 py-1 text-xs font-medium text-ink-500">{dayLabel(message.createdAt)}</span>
              </div>
            )}
            <div className={clsx('flex items-end gap-2', mine ? 'justify-end' : 'justify-start', groupedWithPrev ? 'mt-1' : 'mt-3')}>
              {!mine && (
                <div className="w-8 shrink-0">
                  {!groupedWithNext && <Avatar id={counterpartId} name={message.senderNickname ?? '?'} size="sm" className="size-8! text-xs!" />}
                </div>
              )}
              {mine && !groupedWithNext && <Time iso={message.createdAt} />}
              <div
                className={clsx(
                  'max-w-[75%] animate-pop px-4 py-2.5 text-[15px] leading-relaxed break-words whitespace-pre-wrap',
                  mine
                    ? 'rounded-3xl rounded-br-lg bg-brand-500 text-white'
                    : 'rounded-3xl rounded-bl-lg bg-white text-ink-900 shadow-sm ring-1 ring-ink-100',
                )}
              >
                {message.content}
              </div>
              {!mine && !groupedWithNext && <Time iso={message.createdAt} />}
            </div>
          </div>
        )
      })}
    </div>
  )
}

function Time({ iso }: { iso: string }) {
  return <span className="mb-1 shrink-0 text-[11px] text-ink-400">{clockTime(iso)}</span>
}

function withinMinutes(a: string, b: string, minutes: number) {
  return Math.abs(new Date(b).getTime() - new Date(a).getTime()) < minutes * 60_000
}
