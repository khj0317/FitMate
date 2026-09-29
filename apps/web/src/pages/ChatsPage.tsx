import { useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { ArrowLeft, ArrowUp, ChevronRight, ChevronUp, ImagePlus, Smile, UsersRound, X } from 'lucide-react'
import { useCallback, useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { Avatar } from '../components/Avatar'
import { EmojiPicker, isBigEmoji } from '../components/EmojiPicker'
import { SafetyMenu } from '../components/SafetyMenu'
import { Button, EmptyState, Spinner } from '../components/ui'
import { api, errorMessage, fileUrl } from '../lib/api'
import { compressImage, ImageError } from '../lib/image'
import { clockTime, dayLabel, isSameDay, presenceLabel, timeAgo } from '../lib/format'
import { fetchMessages, keys, useChatRooms, useMe, usePresence } from '../lib/queries'
import type { ChatMessage, ChatRoom, Presence, ReadEvent } from '../lib/types'
import { useChatSocket } from '../providers/ChatSocketProvider'
import { useToast } from '../providers/ToastProvider'

export function ChatsPage() {
  const { roomId } = useParams()
  const selectedId = roomId ? Number(roomId) : null
  const { data: rooms, isLoading } = useChatRooms()
  const { data: presence } = usePresence(
    (rooms ?? []).flatMap((r) => (r.counterpart ? [r.counterpart.userId] : [])),
  )
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
            <EmptyState emoji="💬" title="아직 대화가 없어요" description="매칭 요청이 수락되거나 모임에 참여하면 여기에서 대화할 수 있어요." />
          ) : (
            rooms.map((r) => (
              <RoomItem
                key={r.roomId}
                room={r}
                active={r.roomId === selectedId}
                online={!!r.counterpart && !!presence?.get(r.counterpart.userId)?.online}
              />
            ))
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
          <ChatRoomView
            key={selectedId}
            room={room}
            presence={room.counterpart ? presence?.get(room.counterpart.userId) : undefined}
          />
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

function roomName(room: ChatRoom) {
  if (room.type === 'GATHERING') return room.title ?? '모임 채팅'
  return room.counterpart?.nickname ?? '탈퇴한 회원'
}

/** 단체방은 사람 사진 대신 모임 아이콘을 보여준다 */
function RoomAvatar({ room, size = 'md', online }: { room: ChatRoom; size?: 'sm' | 'md'; online?: boolean }) {
  if (room.type === 'GATHERING') {
    return (
      <div
        className={clsx(
          'flex shrink-0 items-center justify-center rounded-full bg-linear-to-br from-brand-400 to-rose-500 text-white',
          size === 'sm' ? 'size-9' : 'size-12',
        )}
      >
        <UsersRound className={size === 'sm' ? 'size-4' : 'size-5'} />
      </div>
    )
  }
  return (
    <Avatar
      id={room.counterpart?.userId ?? room.roomId}
      name={roomName(room)}
      imageUrl={room.counterpart?.profileImageUrl}
      size={size}
      online={online}
    />
  )
}

function RoomItem({ room, active, online }: { room: ChatRoom; active: boolean; online: boolean }) {
  const name = roomName(room)
  return (
    <Link
      to={`/chats/${room.roomId}`}
      className={clsx(
        'flex items-center gap-3 rounded-2xl px-3 py-3 transition-colors',
        active ? 'bg-brand-50' : 'hover:bg-ink-50',
      )}
    >
      <RoomAvatar room={room} online={online} />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="flex min-w-0 flex-1 items-center gap-1.5 font-bold">
            <span className="truncate">{name}</span>
            {room.type === 'GATHERING' && <span className="shrink-0 text-sm font-medium text-ink-400">{room.memberCount}</span>}
          </p>
          {room.lastMessage && <span className="shrink-0 text-xs text-ink-400">{timeAgo(room.lastMessage.createdAt)}</span>}
        </div>
        <div className="mt-0.5 flex items-center gap-2">
          <p className={clsx('flex-1 truncate text-sm', room.unreadCount ? 'font-semibold text-ink-800' : 'text-ink-500')}>
            {room.lastMessage?.content ?? (room.type === 'GATHERING' ? '모임 채팅방이 열렸어요 🙌' : '매칭됐어요! 먼저 인사를 건네 보세요 👋')}
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

function ChatRoomView({ room, presence }: { room: ChatRoom; presence: Presence | undefined }) {
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const { data: me } = useMe()
  const { connected, onMessage, send, subscribe } = useChatSocket()

  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [nextCursor, setNextCursor] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [text, setText] = useState('')
  /**
   * 나를 뺀 멤버별 읽은 위치. 메시지마다 "아직 안 읽은 사람 수"를 계산해서 카카오톡처럼 숫자로 표시한다.
   * 읽음 위치는 앞으로만 움직이므로 늦게 도착한 이벤트가 되돌리지 않게 max로 합친다
   */
  const [readCursors, setReadCursors] = useState<Map<number, number>>(new Map())
  const advanceRead = useCallback((cursors: { userId: number; lastReadMessageId: number | null }[]) => {
    setReadCursors((current) => {
      const next = new Map(current)
      for (const { userId, lastReadMessageId } of cursors) {
        next.set(userId, Math.max(next.get(userId) ?? 0, lastReadMessageId ?? 0))
      }
      return next
    })
  }, [])

  const scrollRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const stickToBottom = useRef(true)
  const preserveFromBottom = useRef<number | null>(null)
  const lastReadId = useRef(0)

  const name = roomName(room)
  const group = room.type === 'GATHERING'

  // 처음 열면 최근 메시지를 불러온다 (서버는 최신순이므로 뒤집어서 표시)
  useEffect(() => {
    let cancelled = false
    fetchMessages(room.roomId)
      .then((page) => {
        if (cancelled) return
        setMessages(page.messages.slice().reverse())
        setNextCursor(page.nextCursor)
        advanceRead(page.readCursors)
      })
      .catch((e) => toast(errorMessage(e), 'error'))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [room.roomId, toast, advanceRead])

  // 상대가 읽으면 실시간으로 "1"을 지운다. 재연결되면(connected 변화) 다시 구독하고, 끊긴 사이 놓친 읽음도 다시 받아온다
  useEffect(() => {
    if (!connected) return
    fetchMessages(room.roomId)
      .then((page) => advanceRead(page.readCursors))
      .catch(() => undefined)
    return subscribe(`/topic/chat-rooms/${room.roomId}/reads`, (body) => {
      const event = JSON.parse(body) as ReadEvent
      if (event.userId !== me?.id) advanceRead([event])
    })
  }, [connected, subscribe, room.roomId, me?.id, advanceRead])

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

  const [emojiOpen, setEmojiOpen] = useState(false)
  const closeEmoji = useCallback(() => setEmojiOpen(false), [])

  const fileRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(0)
  const [viewer, setViewer] = useState<string | null>(null)

  /** 사진은 브라우저에서 줄인 뒤 올린다. 한 번에 최대 5장, 한 장씩 순서대로 보낸다 */
  const sendPhotos = async (files: File[]) => {
    const images = files.filter((file) => file.type.startsWith('image/')).slice(0, 5)
    if (images.length === 0) return
    stickToBottom.current = true
    for (const file of images) {
      setUploading((count) => count + 1)
      try {
        const photo = await compressImage(file, 1600)
        const message = await api.upload<ChatMessage>(`/api/chat-rooms/${room.roomId}/images`, photo)
        // WebSocket으로도 같은 메시지가 오므로 ID로 중복을 막는다
        setMessages((current) => (current.some((m) => m.id === message.id) ? current : [...current, message]))
      } catch (e) {
        toast(e instanceof ImageError ? e.message : errorMessage(e), 'error')
      } finally {
        setUploading((count) => count - 1)
      }
    }
  }

  /** 커서가 있던 자리에 이모티콘을 넣고, 커서를 그 뒤로 옮긴다 */
  const insertEmoji = (emoji: string) => {
    const input = inputRef.current
    const start = input?.selectionStart ?? text.length
    const end = input?.selectionEnd ?? text.length
    setText((current) => current.slice(0, start) + emoji + current.slice(end))
    requestAnimationFrame(() => {
      input?.focus()
      input?.setSelectionRange(start + emoji.length, start + emoji.length)
    })
  }

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
        <RoomAvatar room={room} size="sm" online={presence?.online} />
        {group ? (
          <div className="min-w-0 flex-1">
            <p className="truncate font-bold">{name}</p>
            <p className="text-xs text-ink-500">
              참여자 {room.memberCount}명
              {!connected && <span className="text-amber-600"> · 내 연결 재시도 중</span>}
            </p>
          </div>
        ) : (
        <div className="min-w-0 flex-1">
          <p className="truncate font-bold">{name}</p>
          <p className="flex items-center gap-1.5 text-xs text-ink-500">
            <span className={clsx('size-1.5 rounded-full', presence?.online ? 'bg-emerald-500' : 'bg-ink-300')} />
            <span className={clsx(presence?.online && 'font-medium text-emerald-600')}>
              {presenceLabel(presence) ?? '접속 기록 없음'}
            </span>
            {!connected && <span className="text-amber-600">· 내 연결 재시도 중</span>}
          </p>
        </div>
        )}
        {group && room.gatheringId && (
          <Link
            to={`/gatherings/${room.gatheringId}`}
            className="flex shrink-0 items-center gap-0.5 rounded-full px-3 py-1.5 text-sm font-semibold text-ink-600 ring-1 ring-ink-200 hover:bg-ink-50"
          >
            모임 정보 <ChevronRight className="size-4" />
          </Link>
        )}
        {!group && room.counterpart && (
          <SafetyMenu
            user={{ id: room.counterpart.userId, nickname: room.counterpart.nickname }}
            onBlocked={() => navigate('/chats')}
            placement="down"
            className="[&>button]:size-9 [&>button]:ring-0"
          />
        )}
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
              group ? (
                <EmptyState emoji="🙌" title="모임 채팅방이에요" description="참여자들과 준비물, 만날 위치를 이야기해 보세요." />
              ) : (
                <EmptyState emoji="🤝" title={`${name}님과 매칭됐어요!`} description="운동 종목, 시간, 장소를 이야기해 보세요." />
              )
            )}
            <MessageList
              messages={messages}
              myId={me?.id ?? -1}
              group={group}
              onOpenImage={setViewer}
              readCursors={readCursors}
            />
            {uploading > 0 && (
              <div className="mt-3 flex justify-end">
                <div className="flex h-40 w-60 animate-pulse flex-col items-center justify-center gap-2 rounded-2xl bg-ink-100 text-sm text-ink-400">
                  <Spinner />
                  사진 {uploading > 1 ? `${uploading}장 ` : ''}보내는 중
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {viewer && <PhotoViewer url={viewer} onClose={() => setViewer(null)} />}

      {!room.canSend ? (
        <div className="border-t border-ink-100 bg-ink-50 p-4 pb-safe text-center text-sm text-ink-500 md:pb-4">
          {group || room.counterpart ? '메시지를 보낼 수 없는 채팅방이에요' : '대화 상대가 탈퇴해서 메시지를 보낼 수 없어요'}
        </div>
      ) : (
      <div className="relative border-t border-ink-100 bg-white p-3 pb-safe md:pb-3">
        {emojiOpen && <EmojiPicker onSelect={insertEmoji} onClose={closeEmoji} />}
        <div className="flex items-end gap-1 rounded-3xl bg-ink-50 p-1.5 ring-1 ring-ink-200 focus-within:ring-2 focus-within:ring-brand-400">
          <button
            type="button"
            onClick={() => setEmojiOpen((open) => !open)}
            aria-label="이모티콘"
            aria-expanded={emojiOpen}
            className={clsx(
              'flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-full transition-colors',
              emojiOpen ? 'bg-brand-100 text-brand-600' : 'text-ink-400 hover:bg-ink-100 hover:text-ink-700',
            )}
          >
            <Smile className="size-5" />
          </button>
          <button
            type="button"
            onClick={() => fileRef.current?.click()}
            aria-label="사진 보내기"
            title="사진 보내기 (붙여넣기도 가능)"
            className="flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-full text-ink-400 transition-colors hover:bg-ink-100 hover:text-ink-700"
          >
            <ImagePlus className="size-5" />
          </button>
          <input
            ref={fileRef}
            type="file"
            accept="image/*"
            multiple
            hidden
            onChange={(e) => {
              void sendPhotos([...(e.target.files ?? [])])
              e.target.value = '' // 같은 사진을 다시 고를 수 있게
            }}
          />
          <textarea
            ref={inputRef}
            rows={1}
            value={text}
            maxLength={1000}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={onKeyDown}
            onPaste={(e) => {
              // 캡처·복사한 사진을 붙여넣으면 바로 보낸다
              const files = [...e.clipboardData.files].filter((file) => file.type.startsWith('image/'))
              if (files.length > 0) {
                e.preventDefault()
                void sendPhotos(files)
              }
            }}
            placeholder="메시지를 입력하세요"
            className="max-h-32 min-h-9 flex-1 resize-none bg-transparent py-2 text-[15px] outline-none [field-sizing:content] placeholder:text-ink-400"
          />
          <button
            onClick={() => {
              setEmojiOpen(false)
              void submit()
            }}
            disabled={!text.trim()}
            className="flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-full bg-brand-500 text-white transition hover:bg-brand-600 disabled:cursor-default disabled:bg-ink-200"
            aria-label="보내기"
          >
            <ArrowUp className="size-5" />
          </button>
        </div>
      </div>
      )}
    </div>
  )
}

/** 보낸 사람을 빼고, 이 메시지를 아직 읽지 않은 멤버 수 */
function unreadCount(message: ChatMessage, readCursors: Map<number, number>) {
  let count = 0
  for (const [userId, lastRead] of readCursors) {
    if (userId !== message.senderId && lastRead < message.id) count++
  }
  return count
}

/** 같은 사람이 3분 안에 연달아 보낸 메시지는 하나의 묶음으로 보여준다. */
function MessageList({
  messages,
  myId,
  group,
  onOpenImage,
  readCursors,
}: {
  messages: ChatMessage[]
  myId: number
  group: boolean
  onOpenImage: (url: string) => void
  readCursors: Map<number, number>
}) {
  return (
    <div className="flex flex-col">
      {messages.map((message, index) => {
        const prev = messages[index - 1]
        const next = messages[index + 1]
        const mine = message.senderId === myId
        // 카카오톡처럼 아직 안 읽은 사람 수를 표시한다 (1:1 방에서는 내 메시지에만 1이 붙는다)
        const unread = unreadCount(message, readCursors)
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
            {group && !mine && !groupedWithPrev && (
              <p className="mt-3 mb-1 ml-10 text-xs font-semibold text-ink-500">{message.senderNickname ?? '알 수 없음'}</p>
            )}
            <div
              className={clsx(
                'flex items-end gap-2',
                mine ? 'justify-end' : 'justify-start',
                groupedWithPrev || (group && !mine) ? 'mt-1' : 'mt-3',
              )}
            >
              {!mine && (
                <div className="w-8 shrink-0">
                  {!groupedWithNext && (
                    <Avatar id={message.senderId ?? 0} name={message.senderNickname ?? '?'} size="sm" className="size-8! text-xs!" />
                  )}
                </div>
              )}
              {mine && (unread > 0 || !groupedWithNext) && (
                <div className="flex shrink-0 flex-col items-end">
                  {unread > 0 && (
                    <span className="mb-0.5 text-[11px] leading-none font-bold text-brand-500" aria-label={`${unread}명 안 읽음`}>
                      {unread}
                    </span>
                  )}
                  {!groupedWithNext && <Time iso={message.createdAt} />}
                </div>
              )}
              {message.type === 'IMAGE' && message.imageUrl ? (
                <PhotoBubble message={message} onOpen={onOpenImage} />
              ) : isBigEmoji(message.content ?? '') ? (
                <div className="animate-pop px-1 text-5xl leading-tight" role="img" aria-label={message.content ?? ''}>
                  {message.content}
                </div>
              ) : (
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
              )}
              {!mine && (unread > 0 || !groupedWithNext) && (
                <div className="flex shrink-0 flex-col items-start">
                  {unread > 0 && (
                    <span className="mb-0.5 text-[11px] leading-none font-bold text-brand-500" aria-label={`${unread}명 안 읽음`}>
                      {unread}
                    </span>
                  )}
                  {!groupedWithNext && <Time iso={message.createdAt} />}
                </div>
              )}
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

/** 사진 크기를 미리 알고 있으므로 비율대로 자리를 잡아 두어, 사진이 늦게 떠도 스크롤이 튀지 않는다 */
function PhotoBubble({ message, onOpen }: { message: ChatMessage; onOpen: (url: string) => void }) {
  const url = fileUrl(message.imageUrl)!
  const ratio = message.imageWidth && message.imageHeight ? message.imageWidth / message.imageHeight : 4 / 3
  return (
    <button
      type="button"
      onClick={() => onOpen(url)}
      className="block w-60 max-w-[70%] animate-pop cursor-zoom-in overflow-hidden rounded-2xl bg-ink-100 ring-1 ring-ink-100"
      aria-label="사진 크게 보기"
    >
      <img
        src={url}
        alt="보낸 사진"
        loading="lazy"
        style={{ aspectRatio: Math.max(ratio, 0.6) }}
        className="block w-full object-cover"
      />
    </button>
  )
}

function PhotoViewer({ url, onClose }: { url: string; onClose: () => void }) {
  useEffect(() => {
    const onKey = (event: globalThis.KeyboardEvent) => event.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="fixed inset-0 z-50 flex animate-pop items-center justify-center bg-ink-900/90 p-4" onClick={onClose}>
      <button
        onClick={onClose}
        className="absolute top-4 right-4 cursor-pointer rounded-full bg-white/10 p-2 text-white hover:bg-white/20"
        aria-label="닫기"
      >
        <X className="size-6" />
      </button>
      <img src={url} alt="사진 원본" className="max-h-full max-w-full rounded-lg object-contain" onClick={(e) => e.stopPropagation()} />
    </div>
  )
}
