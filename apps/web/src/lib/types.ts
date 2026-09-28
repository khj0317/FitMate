// 백엔드 DTO와 1:1로 맞춘 타입

export type SkillLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'
export type Gender = 'MALE' | 'FEMALE'
export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'
export type MatchRequestStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELED'

export interface Tokens {
  accessToken: string
  refreshToken: string
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
  email?: string
  birthDate: string
  gender: Gender
  location: LocationInput
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
  content: string
  createdAt: string
}

export interface ChatRoom {
  roomId: number
  type: 'DIRECT' | 'GATHERING'
  counterpart: { userId: number; nickname: string; profileImageUrl: string | null } | null
  lastMessage: { id: number; content: string; createdAt: string } | null
  unreadCount: number
}

export interface MessagePage {
  messages: ChatMessage[]
  nextCursor: number | null
}

export interface ApiErrorBody {
  status: number
  code: string
  message: string
  errors?: { field: string; reason: string }[]
}
