// 백엔드 DTO와 1:1로 맞춘 타입

export type SkillLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'
export type Gender = 'MALE' | 'FEMALE'
export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'
export type MatchRequestStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELED'

export interface Tokens {
  accessToken: string
  /** 웹은 HttpOnly 쿠키로 받으므로 null */
  refreshToken: string | null
  tokenType: string
  expiresIn: number
}

export interface Sport {
  id: number
  code: string
  name: string
}

export interface UserSport {
  sportId: number
  code: string
  name: string
  skillLevel: SkillLevel
}

export interface AvailableTime {
  dayOfWeek: DayOfWeek
  startTime: string // HH:mm
  endTime: string
}

export interface MyProfile {
  id: number
  loginId: string
  email: string | null
  nickname: string
  bio: string | null
  profileImageUrl: string | null
  gender: Gender | null
  birthDate: string | null // YYYY-MM-DD
  location: { latitude: number; longitude: number; areaName: string } | null
  searchRadiusKm: number
  mannerScore: number
  sports: UserSport[]
  availableTimes: AvailableTime[]
  /** GUEST: 로그인 화면의 "체험하기"로 만든 1회용 계정 (사진·이메일 변경 불가, 24시간 뒤 삭제) */
  role: 'USER' | 'ADMIN' | 'GUEST'
}

export interface LocationInput {
  latitude: number
  longitude: number
  areaName: string
}

export interface LocationSuggestion extends LocationInput {
  name: string
  address: string
}

export interface SignupInput {
  loginId: string
  password: string
  passwordConfirm: string
  nickname: string
  email: string
  birthDate: string
  gender: Gender
  location: LocationInput
  /** 이메일 인증으로 받은 토큰 */
  emailVerificationToken: string | null
}

export interface ProfileInput {
  nickname: string
  email: string
  bio: string
  gender: Gender
  birthDate: string
  searchRadiusKm: number
  location: LocationInput
  sports: { sportId: number; skillLevel: SkillLevel }[]
  availableTimes: AvailableTime[]
  /** 이메일을 바꿀 때만 필요 */
  emailVerificationToken?: string | null
}

export interface MatchCandidate {
  userId: number
  nickname: string
  profileImageUrl: string | null
  gender: Gender | null
  ageGroup: string | null
  activityAreaName: string | null
  mannerScore: number
  approximateDistanceKm: number
  overlapMinutesPerWeek: number
  matchScore: number
  scoreDetail: { distance: number; skill: number; time: number; manner: number }
  commonSports: { sportId: number; name: string; myLevel: SkillLevel | null; theirLevel: SkillLevel }[]
}

export interface MatchRequest {
  id: number
  status: MatchRequestStatus
  sportId: number
  sportName: string
  message: string | null
  counterpart: { userId: number; nickname: string; profileImageUrl: string | null; mannerScore: number }
  createdAt: string
  respondedAt: string | null
  chatRoomId: number | null
}

export interface ChatMessage {
  id: number
  roomId: number
  senderId: number | null
  senderNickname: string | null
  /** SYSTEM: 단체방 입장·퇴장 안내 (보낸 사람 없음) */
  type: 'TEXT' | 'IMAGE' | 'SYSTEM'
  content: string | null
  imageUrl: string | null
  imageWidth: number | null
  imageHeight: number | null
  createdAt: string
}

export interface ChatRoom {
  roomId: number
  type: 'DIRECT' | 'GATHERING'
  counterpart: { userId: number; nickname: string; profileImageUrl: string | null } | null
  lastMessage: { id: number; content: string; createdAt: string } | null
  unreadCount: number
  /** 상대가 탈퇴했거나 차단 관계면 false */
  canSend: boolean
  /** 모임 단체방 이름 (1:1 방은 null) */
  title: string | null
  gatheringId: number | null
  memberCount: number
}

export type ReportReason = 'SPAM' | 'ABUSE' | 'SEXUAL' | 'FAKE_PROFILE' | 'NO_SHOW' | 'OTHER'

export interface BlockedUser {
  userId: number
  nickname: string
  profileImageUrl: string | null
  blockedAt: string
}

export interface MessagePage {
  messages: ChatMessage[]
  nextCursor: number | null
  /** 상대가 읽은 마지막 메시지 ID (null이면 아직 하나도 안 읽음) */
  otherLastReadMessageId: number | null
  /** 나를 뺀 멤버들의 읽음 위치 (단체방의 "안 읽은 사람 수" 계산용) */
  readCursors: { userId: number; lastReadMessageId: number | null }[]
}

export interface ReadEvent {
  roomId: number
  userId: number
  lastReadMessageId: number
}

export interface Presence {
  userId: number
  online: boolean
  lastSeenAt: string | null
}

// ---------- 모임 ----------

export type GatheringStatus = 'RECRUITING' | 'CLOSED' | 'COMPLETED' | 'CANCELED'

export interface GatheringSummary {
  id: number
  title: string
  sportId: number
  sportCode: string
  sportName: string
  placeName: string
  startsAt: string
  capacity: number
  currentCount: number
  status: GatheringStatus
  host: { userId: number; nickname: string; profileImageUrl: string | null; mannerScore: number }
  distanceKm: number | null
  joined: boolean
  latitude: number
  longitude: number
}

export interface GatheringParticipant {
  userId: number
  nickname: string
  profileImageUrl: string | null
  mannerScore: number
  host: boolean
}

export interface GatheringDetail {
  summary: GatheringSummary
  description: string | null
  latitude: number
  longitude: number
  participants: GatheringParticipant[]
  isHost: boolean
  chatRoomId: number | null
}

export interface GatheringInput {
  sportId: number
  title: string
  description: string
  placeName: string
  location: { latitude: number; longitude: number }
  startsAt: string
  capacity: number
}

// ---------- 매너 평가 ----------

export type MannerRating = 'GOOD' | 'NORMAL' | 'BAD'
export type MannerTag = 'PUNCTUAL' | 'KIND' | 'SKILLED' | 'TEACHES' | 'FUN' | 'LATE' | 'NO_SHOW' | 'RUDE'

export interface PendingReview {
  targetId: number
  nickname: string
  profileImageUrl: string | null
  gatheringId: number | null
  matchRequestId: number | null
  /** 모임 제목 또는 1:1 매칭 종목 */
  context: string
  happenedAt: string
}

export interface MannerSummary {
  mannerScore: number
  reviewCount: number
  tags: { tag: MannerTag; count: number }[]
}

// ---------- 알림 ----------

export type NotificationType =
  | 'MATCH_REQUEST_RECEIVED'
  | 'MATCH_REQUEST_ACCEPTED'
  | 'GATHERING_JOINED'
  | 'GATHERING_CANCELED'
  | 'GATHERING_REMINDER'
  | 'MANNER_REVIEW_RECEIVED'
  | 'REVIEW_REQUESTED'
  | 'POST_COMMENTED'
  | 'COMMENT_REPLIED'
  | 'ADMIN_WARNING'
  | 'REPORT_RESOLVED'

export type NotificationCategory = 'MATCH' | 'GATHERING' | 'MANNER' | 'COMMUNITY'

export interface AppNotification {
  id: number
  type: NotificationType
  title: string
  body: string | null
  link: string | null
  read: boolean
  createdAt: string
}

export interface NotificationPage {
  items: AppNotification[]
  nextCursor: number | null
  unreadCount: number
}

// ---------- 커뮤니티 ----------

export type PostCategory = 'CERTIFY' | 'QUESTION' | 'REVIEW' | 'FREE'

export interface Author {
  userId: number
  nickname: string
  profileImageUrl: string | null
  mannerScore: number
}

export interface Post {
  id: number
  category: PostCategory
  sportId: number | null
  sportCode: string | null
  sportName: string | null
  content: string
  images: { url: string; width: number; height: number }[]
  author: Author
  areaName: string | null
  likeCount: number
  commentCount: number
  liked: boolean
  mine: boolean
  createdAt: string
  edited: boolean
}

export interface FeedPage {
  items: Post[]
  nextCursor: number | null
}

export interface PostComment {
  id: number
  parentId: number | null
  /** null이면 탈퇴한 회원 */
  author: Author | null
  content: string | null
  deleted: boolean
  mine: boolean
  createdAt: string
  replies: PostComment[]
}

export interface PublicProfile {
  id: number
  nickname: string
  bio: string | null
  profileImageUrl: string | null
  gender: Gender | null
  ageGroup: string | null
  activityAreaName: string | null
  mannerScore: number
  sports: UserSport[]
  availableTimes: AvailableTime[]
}

// ---------- 관리자 ----------

export interface AdminStats {
  totalUsers: number
  newUsers7d: number
  acceptedMatches: number
  upcomingGatherings: number
  posts7d: number
  messages7d: number
  pendingReports: number
  suspendedUsers: number
}

export type AdminAction = 'DISMISS' | 'WARN' | 'SUSPEND_7D' | 'SUSPEND_PERMANENT'

export interface AdminReport {
  id: number
  reason: ReportReason
  detail: string | null
  status: 'PENDING' | 'RESOLVED' | 'DISMISSED'
  action: AdminAction | null
  adminNote: string | null
  createdAt: string
  resolvedAt: string | null
  /** null이면 탈퇴한 회원 */
  reporter: { userId: number; nickname: string } | null
  reported: {
    userId: number
    nickname: string
    profileImageUrl: string | null
    mannerScore: number
    suspendedUntil: string | null
    totalReports: number
  } | null
}

export interface AdminReportPage {
  items: AdminReport[]
  nextCursor: number | null
}

export interface ApiErrorBody {
  status: number
  code: string
  message: string
  errors?: { field: string; reason: string }[]
}
