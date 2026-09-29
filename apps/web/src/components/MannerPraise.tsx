import { HeartHandshake } from 'lucide-react'
import { MANNER_TAG_INFO } from '../lib/gathering'
import { useMannerSummary } from '../lib/queries'
import { Card } from './ui'

/** 받은 매너 칭찬 태그. 아쉬운 태그는 서버가 내려주지 않는다 */
export function MannerPraise({ userId }: { userId: number }) {
  const { data } = useMannerSummary(userId)
  if (!data) return null

  return (
    <Card className="mb-6 p-6">
      <div className="mb-4 flex items-center gap-2">
        <HeartHandshake className="size-5 text-brand-500" />
        <h2 className="font-bold">받은 매너 칭찬</h2>
        <span className="text-sm text-ink-400">평가 {data.reviewCount}개</span>
      </div>
      {data.tags.length === 0 ? (
        <p className="rounded-2xl bg-ink-50 px-4 py-5 text-center text-sm text-ink-500">
          아직 받은 칭찬이 없어요. 모임이나 매칭으로 함께 운동하면 메이트들이 칭찬을 남길 수 있어요 🙌
        </p>
      ) : (
        <ul className="grid gap-2 sm:grid-cols-2">
          {data.tags.map(({ tag, count }) => {
            const info = MANNER_TAG_INFO[tag]
            return (
              <li key={tag} className="flex items-center gap-3 rounded-2xl bg-ink-50 px-4 py-3">
                <span className="text-xl">{info?.emoji ?? '👍'}</span>
                <span className="flex-1 text-sm font-medium text-ink-700">{info?.label ?? tag}</span>
                <span className="text-sm font-bold text-brand-600">{count}</span>
              </li>
            )
          })}
        </ul>
      )}
    </Card>
  )
}
