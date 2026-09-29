import clsx from 'clsx'
import { useState } from 'react'
import { errorMessage } from '../lib/api'
import { MANNER_TAGS } from '../lib/gathering'
import { useSubmitReview } from '../lib/queries'
import type { MannerRating, MannerTag, PendingReview } from '../lib/types'
import { useToast } from '../providers/ToastProvider'
import { Avatar } from './Avatar'
import { Modal } from './Modal'
import { Button } from './ui'

const RATINGS: { value: MannerRating; label: string; emoji: string }[] = [
  { value: 'GOOD', label: '좋았어요', emoji: '😍' },
  { value: 'NORMAL', label: '보통이에요', emoji: '🙂' },
  { value: 'BAD', label: '별로였어요', emoji: '😞' },
]

/** 함께 운동한 상대 평가. 좋았어요엔 칭찬 태그, 별로였어요엔 아쉬운 태그를 고를 수 있다 */
export function MannerReviewModal({ target, onClose }: { target: PendingReview | null; onClose: () => void }) {
  const toast = useToast()
  const submit = useSubmitReview()
  const [rating, setRating] = useState<MannerRating | null>(null)
  const [tags, setTags] = useState<Set<MannerTag>>(new Set())

  const close = () => {
    setRating(null)
    setTags(new Set())
    onClose()
  }

  const chooseRating = (value: MannerRating) => {
    setRating(value)
    setTags(new Set()) // 평가를 바꾸면 맞지 않는 태그가 남지 않게 비운다
  }

  const toggleTag = (tag: MannerTag) =>
    setTags((current) => {
      const next = new Set(current)
      if (next.has(tag)) next.delete(tag)
      else next.add(tag)
      return next
    })

  const visibleTags = MANNER_TAGS.filter((info) =>
    rating === 'GOOD' ? info.positive : rating === 'BAD' ? !info.positive : false,
  )

  const onSubmit = () => {
    if (!target || !rating) return
    submit.mutate(
      {
        targetId: target.targetId,
        gatheringId: target.gatheringId,
        matchRequestId: target.matchRequestId,
        rating,
        tags: [...tags],
      },
      {
        onSuccess: () => {
          toast(`${target.nickname}님을 평가했어요. 고마워요!`)
          close()
        },
        onError: (e) => toast(errorMessage(e), 'error'),
      },
    )
  }

  return (
    <Modal open={!!target} onClose={close} title="매너 평가">
      {target && (
        <div className="space-y-6">
          <div className="flex items-center gap-3 rounded-2xl bg-ink-50 p-3">
            <Avatar id={target.targetId} name={target.nickname} imageUrl={target.profileImageUrl} size="sm" />
            <div className="min-w-0">
              <p className="truncate font-bold">{target.nickname}</p>
              <p className="truncate text-xs text-ink-500">{target.context} · 함께 운동했어요</p>
            </div>
          </div>

          <div>
            <p className="mb-3 text-sm font-semibold text-ink-700">함께한 운동은 어땠나요?</p>
            <div className="grid grid-cols-3 gap-2">
              {RATINGS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => chooseRating(option.value)}
                  className={clsx(
                    'flex cursor-pointer flex-col items-center gap-1 rounded-2xl py-4 text-sm font-semibold transition-all',
                    rating === option.value
                      ? 'bg-brand-50 text-brand-700 ring-2 ring-brand-400'
                      : 'bg-white text-ink-600 ring-1 ring-ink-200 hover:ring-ink-300',
                  )}
                >
                  <span className="text-3xl">{option.emoji}</span>
                  {option.label}
                </button>
              ))}
            </div>
          </div>

          {visibleTags.length > 0 && (
            <div className="animate-fade-up">
              <p className="mb-3 text-sm font-semibold text-ink-700">
                {rating === 'GOOD' ? '어떤 점이 좋았나요?' : '어떤 점이 아쉬웠나요?'}{' '}
                <span className="font-normal text-ink-400">(여러 개 선택)</span>
              </p>
              <div className="flex flex-wrap gap-2">
                {visibleTags.map((info) => (
                  <button
                    key={info.tag}
                    type="button"
                    onClick={() => toggleTag(info.tag)}
                    aria-pressed={tags.has(info.tag)}
                    className={clsx(
                      'cursor-pointer rounded-full px-3.5 py-2 text-sm font-medium transition-colors',
                      tags.has(info.tag) ? 'bg-ink-900 text-white' : 'bg-white text-ink-700 ring-1 ring-ink-200 hover:ring-ink-300',
                    )}
                  >
                    {info.emoji} {info.label}
                  </button>
                ))}
              </div>
              {rating === 'BAD' && (
                <p className="mt-3 text-xs text-ink-400">아쉬운 평가는 상대에게 공개되지 않고 매너 온도에만 반영돼요.</p>
              )}
            </div>
          )}

          <Button className="w-full" size="lg" disabled={!rating} loading={submit.isPending} onClick={onSubmit}>
            평가 보내기
          </Button>
        </div>
      )}
    </Modal>
  )
}
