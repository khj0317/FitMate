import { PenLine } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { PostCard } from '../components/PostCard'
import { PostComposer } from '../components/PostComposer'
import { Button, Card, Chip, EmptyState, PageHeader, PageLoader, Segmented, Spinner } from '../components/ui'
import { errorMessage } from '../lib/api'
import { POST_CATEGORIES } from '../lib/community'
import { useFeed, useMe, type FeedFilter } from '../lib/queries'
import type { PostCategory } from '../lib/types'

export function CommunityPage() {
  const navigate = useNavigate()
  const { data: me, isLoading: meLoading } = useMe()
  const [scope, setScope] = useState<FeedFilter['scope'] | null>(null)
  const [category, setCategory] = useState<PostCategory | null>(null)
  const [writing, setWriting] = useState(false)

  // 활동 지역이 없으면 동네 글을 볼 수 없으므로 전체 글로 시작한다
  const effectiveScope = scope ?? (me?.location ? 'NEARBY' : 'ALL')
  const feed = useFeed({ scope: effectiveScope, category, sportId: null, authorId: null }, !!me)
  const posts = feed.data?.pages.flatMap((page) => page.items) ?? []

  // 목록 끝이 보이면 다음 페이지를 불러온다
  const sentinel = useRef<HTMLDivElement>(null)
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = feed
  useEffect(() => {
    const el = sentinel.current
    if (!el || !hasNextPage) return
    const observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting && !isFetchingNextPage) void fetchNextPage()
    }, { rootMargin: '400px' })
    observer.observe(el)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])

  if (meLoading || !me) return <PageLoader />

  return (
    <div className="mx-auto max-w-2xl px-4 py-6 md:px-8 md:py-10">
      <PageHeader
        title="커뮤니티"
        description={
          effectiveScope === 'NEARBY' && me.location
            ? `${me.location.areaName} 반경 ${me.searchRadiusKm}km 운동 이야기`
            : '운동 인증, 질문, 후기를 나눠요'
        }
        action={
          <Button onClick={() => setWriting(true)}>
            <PenLine className="size-4" /> 글쓰기
          </Button>
        }
      />

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <Segmented
          options={[
            { value: 'NEARBY', label: '우리 동네' },
            { value: 'ALL', label: '전체' },
          ]}
          value={effectiveScope}
          onChange={(value) => {
            if (value === 'NEARBY' && !me.location) {
              navigate('/profile')
              return
            }
            setScope(value)
          }}
        />
      </div>
      <div className="-mx-4 mb-6 flex gap-2 overflow-x-auto px-4 pb-1 scrollbar-thin md:mx-0 md:px-0">
        <Chip active={category === null} onClick={() => setCategory(null)}>✨ 전체</Chip>
        {POST_CATEGORIES.map((info) => (
          <Chip key={info.value} active={category === info.value} onClick={() => setCategory(info.value)}>
            {info.emoji} {info.label}
          </Chip>
        ))}
      </div>

      {feed.isLoading ? (
        <PageLoader />
      ) : feed.error ? (
        <Card>
          <EmptyState emoji="😵" title="글을 불러오지 못했어요" description={errorMessage(feed.error)} />
        </Card>
      ) : posts.length === 0 ? (
        <Card>
          <EmptyState
            emoji="🌱"
            title={effectiveScope === 'NEARBY' ? '아직 우리 동네 글이 없어요' : '아직 글이 없어요'}
            description="오늘 한 운동을 인증하거나 궁금한 걸 물어보세요. 첫 글의 주인공이 되어 보세요!"
            action={<Button onClick={() => setWriting(true)}>첫 글 쓰기</Button>}
          />
        </Card>
      ) : (
        <div className="space-y-4">
          {posts.map((post) => (
            <PostCard key={post.id} post={post} />
          ))}
          <div ref={sentinel} className="flex justify-center py-4">
            {isFetchingNextPage && <Spinner />}
            {!hasNextPage && posts.length > 5 && <p className="text-sm text-ink-400">모든 글을 봤어요 👀</p>}
          </div>
        </div>
      )}

      {/* 열 때마다 새로 마운트해서 이전 입력이 남지 않게 한다 */}
      {writing && (
        <PostComposer open onClose={() => setWriting(false)} onCreated={(post) => navigate(`/community/${post.id}`)} />
      )}
    </div>
  )
}
