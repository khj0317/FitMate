import { Client, type StompSubscription } from '@stomp/stompjs'
import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { API_BASE, api, getAccessToken, refreshTokens } from '../lib/api'
import { keys, useChatRooms } from '../lib/queries'
import type { ChatMessage } from '../lib/types'
import { useAuth } from './AuthProvider'
import { useToast } from './ToastProvider'

interface ChatSocketValue {
  connected: boolean
  /** 모든 내 채팅방의 새 메시지를 구독한다. 반환값은 구독 해제 함수 */
  onMessage: (listener: (message: ChatMessage) => void) => () => void
  send: (roomId: number, content: string) => Promise<void>
  /** 연결된 상태에서 토픽을 구독한다. 연결이 바뀌면(재연결) 다시 불러야 하므로 connected를 의존성에 넣어 쓴다 */
  subscribe: (destination: string, handler: (body: string) => void) => () => void
}

const ChatSocketContext = createContext<ChatSocketValue | null>(null)

function socketUrl() {
  const base = API_BASE || window.location.origin
  return base.replace(/^http/, 'ws') + '/ws'
}

/**
 * 로그인하면 WebSocket(STOMP)에 연결하고 내 모든 채팅방 토픽을 구독한다.
 * 새 메시지가 오면 채팅방 목록(안 읽은 수)을 갱신하고, 화면별 리스너에게 전달한다.
 */
export function ChatSocketProvider({ children }: { children: ReactNode }) {
  const { loggedIn } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const { data: rooms } = useChatRooms(loggedIn)

  const clientRef = useRef<Client | null>(null)
  const listenersRef = useRef(new Set<(message: ChatMessage) => void>())
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (!loggedIn) return
    const client = new Client({
      brokerURL: socketUrl(),
      reconnectDelay: 3000,
      // 재연결 때마다 최신 토큰을 쓰고, 만료됐으면 먼저 재발급한다
      beforeConnect: async () => {
        if (!getAccessToken()) await refreshTokens()
        client.connectHeaders = { Authorization: `Bearer ${getAccessToken()}` }
      },
      onConnect: () => {
        setConnected(true)
        client.subscribe('/user/queue/errors', (frame) => toast(JSON.parse(frame.body).message, 'error'))
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: () => {
        // 토큰 만료로 CONNECT가 거절되면 재발급 후 자동 재연결
        void refreshTokens()
      },
    })
    clientRef.current = client
    client.activate()
    return () => {
      void client.deactivate()
      clientRef.current = null
      setConnected(false)
    }
  }, [loggedIn, toast])

  // 채팅방 목록이 바뀌면(새 매칭 등) 구독도 다시 맞춘다
  const roomIds = rooms?.map((room) => room.roomId).join(',') ?? ''
  useEffect(() => {
    const client = clientRef.current
    if (!connected || !client || !roomIds) return
    const subscriptions: StompSubscription[] = roomIds.split(',').map((roomId) =>
      client.subscribe(`/topic/chat-rooms/${roomId}`, (frame) => {
        const message = JSON.parse(frame.body) as ChatMessage
        listenersRef.current.forEach((listener) => listener(message))
        void queryClient.invalidateQueries({ queryKey: keys.chatRooms })
      }),
    )
    return () => subscriptions.forEach((subscription) => subscription.unsubscribe())
  }, [connected, roomIds, queryClient])

  const onMessage = useCallback((listener: (message: ChatMessage) => void) => {
    listenersRef.current.add(listener)
    return () => {
      listenersRef.current.delete(listener)
    }
  }, [])

  const send = useCallback(async (roomId: number, content: string) => {
    const client = clientRef.current
    if (client?.connected) {
      client.publish({
        destination: `/app/chat-rooms/${roomId}/messages`,
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ content }),
      })
    } else {
      // 연결이 끊겨 있으면 REST로 보낸다. 서버가 WebSocket 구독자에게도 전달해 준다
      await api.post(`/api/chat-rooms/${roomId}/messages`, { content })
    }
  }, [])

  const subscribe = useCallback((destination: string, handler: (body: string) => void) => {
    const client = clientRef.current
    if (!client?.connected) return () => {}
    const subscription = client.subscribe(destination, (frame) => handler(frame.body))
    return () => subscription.unsubscribe()
  }, [])

  return <ChatSocketContext value={{ connected, onMessage, send, subscribe }}>{children}</ChatSocketContext>
}

export function useChatSocket() {
  const context = useContext(ChatSocketContext)
  if (!context) throw new Error('ChatSocketProvider가 필요합니다')
  return context
}
