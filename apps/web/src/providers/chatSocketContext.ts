import { createContext, useContext } from 'react'
import type { ChatMessage } from '../lib/types'

export interface ChatSocketValue {
  connected: boolean
  /** 모든 내 채팅방의 새 메시지를 구독한다. 반환값은 구독 해제 함수 */
  onMessage: (listener: (message: ChatMessage) => void) => () => void
  send: (roomId: number, content: string) => Promise<void>
  /** 연결된 상태에서 토픽을 구독한다. 연결이 바뀌면(재연결) 다시 불러야 하므로 connected를 의존성에 넣어 쓴다 */
  subscribe: (destination: string, handler: (body: string) => void) => () => void
}

export const ChatSocketContext = createContext<ChatSocketValue | null>(null)

export function useChatSocket() {
  const context = useContext(ChatSocketContext)
  if (!context) throw new Error('ChatSocketProvider가 필요합니다')
  return context
}
