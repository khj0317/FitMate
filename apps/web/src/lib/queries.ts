import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type {
  ChatRoom,
  GatheringDetail,
  GatheringInput,
  GatheringSummary,
  MannerRating,
  MannerSummary,
  MannerTag,
  NotificationPage,
  PendingReview,
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
  gatherings: (sportId: number | null, radiusKm: number | null) => ['gatherings', 'nearby', sportId, radiusKm] as const,
  myGatherings: ['gatherings', 'mine'] as const,
  gathering: (id: number) => ['gatherings', 'detail', id] as const,
  pendingReviews: ['manner', 'pending'] as const,
  manner: (userId: number) => ['manner', 'summary', userId] as const,
  notifications: ['notifications'] as const,
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

export function useNearbyGatherings(sportId: number | null, radiusKm: number | null, enabled: boolean) {
  return useQuery({
    queryKey: keys.gatherings(sportId, radiusKm),
    queryFn: () => {
      const params = new URLSearchParams()
      if (sportId) params.set('sportId', String(sportId))
      if (radiusKm) params.set('radiusKm', String(radiusKm))
      return api.get<GatheringSummary[]>(`/api/gatherings?${params}`)
    },
    enabled,
  })
}

export function useMyGatherings() {
  return useQuery({ queryKey: keys.myGatherings, queryFn: () => api.get<GatheringSummary[]>('/api/gatherings/mine') })
}

export function useGathering(id: number) {
  return useQuery({
    queryKey: keys.gathering(id),
    queryFn: () => api.get<GatheringDetail>(`/api/gatherings/${id}`),
    retry: false,
  })
}

export function usePendingReviews() {
  return useQuery({ queryKey: keys.pendingReviews, queryFn: () => api.get<PendingReview[]>('/api/manner/pending') })
}

export function useMannerSummary(userId: number | undefined) {
  return useQuery({
    queryKey: keys.manner(userId ?? 0),
    queryFn: () => api.get<MannerSummary>(`/api/users/${userId}/manner`),
    enabled: !!userId,
  })
}

/** 최근 알림 30개와 안 읽은 수. 새 알림은 WebSocket으로 오면 다시 불러온다 */
export function useNotifications() {
  return useQuery({ queryKey: keys.notifications, queryFn: () => api.get<NotificationPage>('/api/notifications') })
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

export function useCreateGathering() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (body: GatheringInput) => api.post<GatheringDetail>('/api/gatherings', body),
    onSuccess: () => invalidate(['gatherings'], ['chatRooms']),
  })
}

/** 참여 / 나가기 / 취소. 인원·채팅방 목록이 바뀌므로 관련 목록을 모두 새로 고친다 */
export function useGatheringAction() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'join' | 'leave' | 'cancel' }) => {
      if (action === 'join') return api.post<{ gatheringId: number; chatRoomId: number }>(`/api/gatherings/${id}/participants`)
      if (action === 'leave') return api.delete<undefined>(`/api/gatherings/${id}/participants/me`)
      return api.delete<undefined>(`/api/gatherings/${id}`)
    },
    onSuccess: () => invalidate(['gatherings'], ['chatRooms']),
  })
}

export function useSubmitReview() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (body: {
      targetId: number
      gatheringId: number | null
      matchRequestId: number | null
      rating: MannerRating
      tags: MannerTag[]
    }) => api.post<undefined>('/api/manner/reviews', body),
    onSuccess: () => invalidate(['manner']),
  })
}

export function useReadNotifications() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (id: number | 'all') =>
      api.post<undefined>(id === 'all' ? '/api/notifications/read-all' : `/api/notifications/${id}/read`),
    onSuccess: () => invalidate([...keys.notifications]),
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
