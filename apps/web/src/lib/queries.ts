import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type {
  ChatRoom,
  LocationSuggestion,
  MatchCandidate,
  MatchRequest,
  MatchRequestStatus,
  MessagePage,
  MyProfile,
  Presence,
  ProfileInput,
  Sport,
} from './types'

export const keys = {
  me: ['me'] as const,
  sports: ['sports'] as const,
  recommendations: (sportId: number | null, radiusKm: number | null) => ['recommendations', sportId, radiusKm] as const,
  requests: (box: 'received' | 'sent', status: MatchRequestStatus) => ['matchRequests', box, status] as const,
  chatRooms: ['chatRooms'] as const,
  messages: (roomId: number) => ['messages', roomId] as const,
  presence: (userIds: number[]) => ['presence', ...userIds] as const,
}

export function useMe(enabled = true) {
  return useQuery({ queryKey: keys.me, queryFn: () => api.get<MyProfile>('/api/users/me'), enabled })
}

export function useSports() {
  return useQuery({ queryKey: keys.sports, queryFn: () => api.get<Sport[]>('/api/sports'), staleTime: Infinity })
}

export function useRecommendations(sportId: number | null, radiusKm: number | null, enabled: boolean) {
  return useQuery({
    queryKey: keys.recommendations(sportId, radiusKm),
    queryFn: () => {
      const params = new URLSearchParams({ limit: '30' })
      if (sportId) params.set('sportId', String(sportId))
      if (radiusKm) params.set('radiusKm', String(radiusKm))
      return api.get<MatchCandidate[]>(`/api/matching/recommendations?${params}`)
    },
    enabled,
  })
}

export function useMatchRequests(box: 'received' | 'sent', status: MatchRequestStatus) {
  return useQuery({
    queryKey: keys.requests(box, status),
    queryFn: () => api.get<MatchRequest[]>(`/api/match-requests/${box}?status=${status}`),
  })
}

/** 접속 상태는 자주 바뀌므로 20초마다 다시 확인한다 */
export function usePresence(userIds: number[]) {
  const ids = [...new Set(userIds)].sort((a, b) => a - b)
  return useQuery({
    queryKey: keys.presence(ids),
    queryFn: async () => {
      const list = await api.get<Presence[]>(`/api/users/presence?userIds=${ids.join(',')}`)
      return new Map(list.map((presence) => [presence.userId, presence]))
    },
    enabled: ids.length > 0,
    refetchInterval: 20_000,
    staleTime: 10_000,
  })
}

export function useChatRooms(enabled = true) {
  return useQuery({ queryKey: keys.chatRooms, queryFn: () => api.get<ChatRoom[]>('/api/chat-rooms'), enabled })
}

export function fetchMessages(roomId: number, cursor?: number | null) {
  const params = new URLSearchParams({ size: '30' })
  if (cursor) params.set('cursor', String(cursor))
  return api.get<MessagePage>(`/api/chat-rooms/${roomId}/messages?${params}`)
}

// ---------- 변경 ----------

function useInvalidate() {
  const queryClient = useQueryClient()
  return (...queryKeys: readonly unknown[][]) =>
    Promise.all(queryKeys.map((queryKey) => queryClient.invalidateQueries({ queryKey })))
}

export function useSendMatchRequest() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (body: { receiverId: number; sportId: number; message?: string }) =>
      api.post<{ matchRequestId: number }>('/api/match-requests', body),
    onSuccess: () => invalidate(['matchRequests']),
  })
}

export function useHandleMatchRequest() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'accept' | 'reject' | 'cancel' }) =>
      api.post<{ matchRequestId: number; chatRoomId: number } | undefined>(`/api/match-requests/${id}/${action}`),
    onSuccess: () => invalidate(['matchRequests'], ['chatRooms']),
  })
}

/** 프로필 화면의 "전체 저장": 모든 항목을 한 번의 요청(한 트랜잭션)으로 저장한다 */
export function useSaveProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: ProfileInput) => api.put<MyProfile>('/api/users/me', body),
    onSuccess: (profile) => {
      queryClient.setQueryData(keys.me, profile)
      return queryClient.invalidateQueries({ queryKey: ['recommendations'] })
    },
  })
}

export function searchLocations(query: string) {
  return api.get<LocationSuggestion[]>(`/api/locations/search?query=${encodeURIComponent(query)}`)
}
