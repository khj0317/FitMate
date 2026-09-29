import { ArrowLeft, MapPin, Thermometer } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router'
import { Avatar } from '../components/Avatar'
import { MannerPraise } from '../components/MannerPraise'
import { PostCard } from '../components/PostCard'
import { SafetyMenu } from '../components/SafetyMenu'
import { Badge, Button, Card, EmptyState, PageLoader } from '../components/ui'
import { errorMessage } from '../lib/api'
import { GENDER_LABEL, SKILL_LABEL, sportEmoji } from '../lib/format'
import { useFeed, useMe, usePublicProfile } from '../lib/queries'

/** 다른 사람의 공개 프로필: 매너 온도, 받은 칭찬, 운동 종목, 쓴 글 */
export function UserProfilePage() {
  const { userId } = useParams()
  const id = Number(userId)
  const navigate = useNavigate()
  const { data: me } = useMe()
  const { data: profile, isLoading, error } = usePublicProfile(id)
  const posts = useFeed({ scope: 'ALL', category: null, sportId: null, authorId: id }, !!profile)

  if (isLoading) return <PageLoader />
  if (error || !profile) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-10">
        <Card>
          <EmptyState
            emoji="🤔"
            title="사용자를 찾을 수 없어요"
            description={error ? errorMessage(error) : undefined}
            action={<Button variant="secondary" onClick={() => navigate(-1)}>돌아가기</Button>}
          />
        </Card>
      </div>
    )
  }

  const isMe = me?.id === profile.id
  const facts = [profile.ageGroup, profile.gender && GENDER_LABEL[profile.gender]].filter(Boolean).join(' · ')
  const items = posts.data?.pages.flatMap((page) => page.items) ?? []

  return (
    <div className="mx-auto max-w-2xl px-4 py-6 md:px-8 md:py-10">
      <button
        onClick={() => navigate(-1)}
        className="mb-4 inline-flex cursor-pointer items-center gap-1 text-sm font-semibold text-ink-500 hover:text-ink-800"
      >
        <ArrowLeft className="size-4" /> 뒤로
      </button>

      <Card className="mb-6 p-6">
        <div className="flex items-start gap-4">
          <Avatar id={profile.id} name={profile.nickname} imageUrl={profile.profileImageUrl} size="lg" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-xl font-extrabold">{profile.nickname}</p>
            {facts && <p className="text-sm text-ink-500">{facts}</p>}
            {profile.activityAreaName && (
              <p className="mt-0.5 flex items-center gap-1 text-sm text-ink-500">
                <MapPin className="size-3.5" /> {profile.activityAreaName}
              </p>
            )}
          </div>
          <div className="text-center">
            <div className="flex items-center gap-1 text-2xl font-extrabold text-brand-600">
              <Thermometer className="size-5" />
              {profile.mannerScore}°
            </div>
            <p className="text-xs font-medium text-ink-400">매너 온도</p>
          </div>
        </div>
        {profile.bio && <p className="mt-4 text-[15px] leading-relaxed whitespace-pre-wrap text-ink-700">{profile.bio}</p>}
        {profile.sports.length > 0 && (
          <div className="mt-4 flex flex-wrap gap-2">
            {profile.sports.map((sport) => (
              <Badge key={sport.sportId}>
                {sportEmoji(sport.code)} {sport.name} · {SKILL_LABEL[sport.skillLevel]}
              </Badge>
            ))}
          </div>
        )}
        <div className="mt-5 flex justify-end gap-2">
          {isMe ? (
            <Link to="/profile" className="text-sm font-bold text-brand-600">내 프로필 고치기 →</Link>
          ) : (
            <SafetyMenu user={{ id: profile.id, nickname: profile.nickname }} onBlocked={() => navigate(-1)} placement="up" />
          )}
        </div>
      </Card>

      <MannerPraise userId={profile.id} />

      <h2 className="mb-3 font-bold">{isMe ? '내가 쓴 글' : `${profile.nickname}님이 쓴 글`}</h2>
      {posts.isLoading ? (
        <PageLoader />
      ) : items.length === 0 ? (
        <Card>
          <p className="py-10 text-center text-sm text-ink-400">아직 쓴 글이 없어요</p>
        </Card>
      ) : (
        <div className="space-y-4">
          {items.map((post) => (
            <PostCard key={post.id} post={post} />
          ))}
          {posts.hasNextPage && (
            <div className="flex justify-center">
              <Button variant="secondary" loading={posts.isFetchingNextPage} onClick={() => void posts.fetchNextPage()}>
                더 보기
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
