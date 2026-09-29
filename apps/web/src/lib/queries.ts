import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type {
  AdminAction,
  AdminReportPage,
  AdminStats,
  ChatRoom,
  FeedPage,
  NotificationCategory,
  Post,
  PostCategory,
  PostComment,
  PublicProfile,
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
  notificationSettings: ['notificationSettings'] as const,
  feed: (filter: FeedFilter) => ['posts', 'feed', filter] as const,
  post: (id: number) => ['posts', 'detail', id] as const,
  comments: (postId: number) => ['posts', 'comments', postId] as const,
  profile: (userId: number) => ['profile', userId] as const,
  adminStats: ['admin', 'stats'] as const,
  adminReports: (pending: boolean) => ['admin', 'reports', pending] as const,
}

export interface FeedFilter {
  scope: 'NEARBY' | 'ALL'
  category: PostCategory | null
  sportId: number | null
  authorId: number | null
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

export function useNotificationSettings() {
  return useQuery({
    queryKey: keys.notificationSettings,
    queryFn: () => api.get<{ muted: NotificationCategory[] }>('/api/notifications/settings'),
  })
}

export function usePublicProfile(userId: number) {
  return useQuery({
    queryKey: keys.profile(userId),
    queryFn: () => api.get<PublicProfile>(`/api/users/${userId}`),
    retry: false,
  })
}

/** 커서 기반 무한 스크롤 피드 */
export function useFeed(filter: FeedFilter, enabled = true) {
  return useInfiniteQuery({
    queryKey: keys.feed(filter),
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ scope: filter.scope })
      if (filter.category) params.set('category', filter.category)
      if (filter.sportId) params.set('sportId', String(filter.sportId))
      if (filter.authorId) params.set('authorId', String(filter.authorId))
      if (pageParam) params.set('cursor', String(pageParam))
      return api.get<FeedPage>(`/api/posts?${params}`)
    },
    initialPageParam: null as number | null,
    getNextPageParam: (last) => last.nextCursor,
    enabled,
  })
}

export function usePost(id: number) {
  return useQuery({ queryKey: keys.post(id), queryFn: () => api.get<Post>(`/api/posts/${id}`), retry: false })
}

export function useComments(postId: number, enabled = true) {
  return useQuery({
    queryKey: keys.comments(postId),
    queryFn: () => api.get<PostComment[]>(`/api/posts/${postId}/comments`),
    enabled,
  })
}

export function useAdminStats(enabled: boolean) {
  return useQuery({ queryKey: keys.adminStats, queryFn: () => api.get<AdminStats>('/api/admin/stats'), enabled })
}

export function useAdminReports(pending: boolean, enabled: boolean) {
  return useInfiniteQuery({
    queryKey: keys.adminReports(pending),
    queryFn: ({ pageParam }) =>
      api.get<AdminReportPage>(`/api/admin/reports?pending=${pending}${pageParam ? `&cursor=${pageParam}` : ''}`),
    initialPageParam: null as number | null,
    getNextPageParam: (last) => last.nextCursor,
    enabled,
  })
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

export function useSaveNotificationSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (muted: NotificationCategory[]) =>
      api.put<{ muted: NotificationCategory[] }>('/api/notifications/settings', { muted }),
    onSuccess: (settings) => queryClient.setQueryData(keys.notificationSettings, settings),
  })
}

export function useCreatePost() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ post, images }: { post: { category: PostCategory; sportId: number | null; content: string }; images: Blob[] }) => {
      const form = new FormData()
      form.append('post', new Blob([JSON.stringify(post)], { type: 'application/json' }))
      images.forEach((image, index) => form.append('images', image, `photo${index}.jpg`))
      return api.form<Post>('/api/posts', form)
    },
    onSuccess: () => invalidate(['posts']),
  })
}

export function useUpdatePost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: { id: number; category: PostCategory; sportId: number | null; content: string }) =>
      api.put<Post>(`/api/posts/${id}`, body),
    onSuccess: (post) => {
      queryClient.setQueryData(keys.post(post.id), post)
      return queryClient.invalidateQueries({ queryKey: ['posts', 'feed'] })
    },
  })
}

export function useDeletePost() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (id: number) => api.delete<undefined>(`/api/posts/${id}`),
    onSuccess: () => invalidate(['posts', 'feed']),
  })
}

/**
 * 좋아요는 누르자마자 화면에 반영하고(낙관적 업데이트), 서버가 돌려준 최종 수로 맞춘다.
 * 피드 여러 곳과 상세 화면에 같은 글이 있을 수 있어서 캐시 전체에서 그 글을 찾아 고친다
 */
export function useToggleLike() {
  const queryClient = useQueryClient()
  const patch = (id: number, change: (post: Post) => Post) => {
    queryClient.setQueriesData<Post>({ queryKey: ['posts', 'detail', id] }, (post) => (post ? change(post) : post))
    queryClient.setQueriesData<{ pages: FeedPage[]; pageParams: unknown[] }>({ queryKey: ['posts', 'feed'] }, (data) =>
      data
        ? { ...data, pages: data.pages.map((page) => ({ ...page, items: page.items.map((p) => (p.id === id ? change(p) : p)) })) }
        : data,
    )
  }
  return useMutation({
    mutationFn: ({ id, like }: { id: number; like: boolean }) =>
      like
        ? api.put<{ liked: boolean; likeCount: number }>(`/api/posts/${id}/like`, {})
        : api.delete<{ liked: boolean; likeCount: number }>(`/api/posts/${id}/like`),
    onMutate: ({ id, like }) =>
      patch(id, (post) => ({ ...post, liked: like, likeCount: Math.max(0, post.likeCount + (like ? 1 : -1)) })),
    onSuccess: (result, { id }) => patch(id, (post) => ({ ...post, liked: result.liked, likeCount: result.likeCount })),
    onError: (_error, { id, like }) =>
      patch(id, (post) => ({ ...post, liked: !like, likeCount: Math.max(0, post.likeCount + (like ? -1 : 1)) })),
  })
}

export function useAddComment(postId: number) {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (body: { content: string; parentId: number | null }) =>
      api.post<PostComment>(`/api/posts/${postId}/comments`, body),
    onSuccess: () => invalidate([...keys.comments(postId)], [...keys.post(postId)], ['posts', 'feed']),
  })
}

export function useDeleteComment(postId: number) {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (commentId: number) => api.delete<undefined>(`/api/comments/${commentId}`),
    onSuccess: () => invalidate([...keys.comments(postId)], [...keys.post(postId)], ['posts', 'feed']),
  })
}

export function useResolveReport() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, action, note }: { id: number; action: AdminAction; note: string }) =>
      api.post<{ handledReports: number }>(`/api/admin/reports/${id}/resolve`, { action, note }),
    onSuccess: () => invalidate(['admin']),
  })
}

export function useUnsuspend() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (userId: number) => api.post<undefined>(`/api/admin/users/${userId}/unsuspend`),
    onSuccess: () => invalidate(['admin']),
  })
}

export function useHidePost() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (postId: number) => api.post<undefined>(`/api/admin/posts/${postId}/hide`),
    onSuccess: () => invalidate(['posts'], ['admin']),
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
