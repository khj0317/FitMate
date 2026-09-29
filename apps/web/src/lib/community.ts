import type { PostCategory } from './types'

export const POST_CATEGORIES: { value: PostCategory; label: string; emoji: string; placeholder: string }[] = [
  { value: 'CERTIFY', label: '운동 인증', emoji: '🔥', placeholder: '오늘 한 운동을 자랑해 보세요! 사진을 함께 올리면 더 좋아요' },
  { value: 'QUESTION', label: '질문', emoji: '🙋', placeholder: '운동 방법, 장비, 동네 운동 장소 등 궁금한 걸 물어보세요' },
  { value: 'REVIEW', label: '후기·정보', emoji: '📝', placeholder: '다녀온 운동 장소나 써 본 장비의 후기를 나눠 주세요' },
  { value: 'FREE', label: '자유', emoji: '💬', placeholder: '운동 이야기라면 무엇이든 좋아요' },
]

export const CATEGORY_INFO = Object.fromEntries(POST_CATEGORIES.map((info) => [info.value, info])) as Record<
  PostCategory,
  (typeof POST_CATEGORIES)[number]
>
