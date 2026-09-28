import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type {
  AvailableTime,
  ChatRoom,
  Gender,
  MatchCandidate,
  MatchRequest,
  MatchRequestStatus,
  MessagePage,
  MyProfile,
  SkillLevel,
  Sport,
} from './types'

export const keys = {
  me: ['me'] as const,
  sports: ['sports'] as const,
  recommendations: (sportId: number | null, radiusKm: number | null) => ['recommendations', sportId, radiusKm] as const,
  requests: (box: 'received' | 'sent', status: MatchRequestStatus) => ['matchRequests', box, status] as const,
  chatRooms: ['chatRooms'] as const,
  messages: (roomId: number) => ['messages', roomId] as const,
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

export function useUpdateProfile() {
  const queryClient = useQueryClient()
  const onSuccess = (profile: MyProfile) => {
    queryClient.setQueryData(keys.me, profile)
    return queryClient.invalidateQueries({ queryKey: ['recommendations'] })
  }
  return {
    basic: useMutation({
      mutationFn: (body: {
        nickname?: string
        bio?: string
        gender?: Gender
        birthYear?: number
        searchRadiusKm?: number
      }) => api.patch<MyProfile>('/api/users/me', body),
      onSuccess,
    }),
    location: useMutation({
      mutationFn: (body: { latitude: number; longitude: number; areaName: string }) =>
        api.put<MyProfile>('/api/users/me/location', body),
      onSuccess,
    }),
    sports: useMutation({
      mutationFn: (sports: { sportId: number; skillLevel: SkillLevel }[]) =>
        api.put<MyProfile>('/api/users/me/sports', { sports }),
      onSuccess,
    }),
    times: useMutation({
      mutationFn: (availableTimes: AvailableTime[]) =>
        api.put<MyProfile>('/api/users/me/available-times', { availableTimes }),
      onSuccess,
    }),
  }
}
