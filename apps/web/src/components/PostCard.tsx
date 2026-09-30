import clsx from 'clsx'
import { Heart, MessageCircle } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { CATEGORY_INFO } from '../lib/community'
import { sportEmoji, timeAgo } from '../lib/format'
import { useToggleLike } from '../lib/queries'
import type { Post } from '../lib/types'
import { Avatar } from './Avatar'
import { PhotoViewer } from './PhotoViewer'

/** 글쓴이 · 동네 · 시간 한 줄 */
export function PostAuthorLine({ post }: { post: Post }) {
  return (
    <div className="flex items-center gap-2.5">
      <Link to={`/users/${post.author.userId}`} className="shrink-0" onClick={(e) => e.stopPropagation()}>
        <Avatar id={post.author.userId} name={post.author.nickname} imageUrl={post.author.profileImageUrl} size="sm" />
      </Link>
      <div className="min-w-0 flex-1">
        <Link
          to={`/users/${post.author.userId}`}
          onClick={(e) => e.stopPropagation()}
          className="block truncate text-sm font-bold hover:underline"
        >
          {post.author.nickname}
        </Link>
        <p className="truncate text-xs text-ink-400">
          {post.areaName && `${post.areaName} · `}
          {timeAgo(post.createdAt)}
          {post.edited && ' · 수정됨'}
        </p>
      </div>
    </div>
  )
}

export function CategoryBadges({ post }: { post: Post }) {
  const info = CATEGORY_INFO[post.category]
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <span className="inline-flex items-center gap-1 rounded-lg bg-brand-50 px-2 py-0.5 text-xs font-semibold text-brand-700">
        {info.emoji} {info.label}
      </span>
      {post.sportName && (
        <span className="inline-flex items-center gap-1 rounded-lg bg-ink-100 px-2 py-0.5 text-xs font-semibold text-ink-600">
          {sportEmoji(post.sportCode ?? post.sportName)} {post.sportName}
        </span>
      )}
    </div>
  )
}

/** 사진 1~4장을 격자로. 누르면 크게 본다 */
export function PostImages({ post, className }: { post: Post; className?: string }) {
  const [viewer, setViewer] = useState<string | null>(null)
  const images = post.images
  if (images.length === 0) return null
  return (
    <>
      <div
        className={clsx(
          'grid gap-1 overflow-hidden rounded-2xl',
          images.length === 1 ? 'grid-cols-1' : 'grid-cols-2',
          className,
        )}
      >
        {images.map((image, index) => (
          <button
            key={image.url}
            type="button"
            onClick={(e) => {
              e.preventDefault()
              e.stopPropagation()
              setViewer(image.url)
            }}
            className={clsx(
              'block cursor-zoom-in overflow-hidden bg-ink-100',
              images.length === 3 && index === 0 && 'row-span-2',
            )}
            aria-label="사진 크게 보기"
          >
            <img
              src={image.url}
              alt=""
              loading="lazy"
              className={clsx(
                'h-full w-full object-cover',
                images.length === 1 ? 'max-h-96' : 'aspect-square',
                images.length === 3 && index === 0 && 'aspect-auto',
              )}
            />
          </button>
        ))}
      </div>
      {viewer && <PhotoViewer url={viewer} onClose={() => setViewer(null)} />}
    </>
  )
}

export function LikeButton({ post, size = 'md' }: { post: Post; size?: 'md' | 'lg' }) {
  const toggle = useToggleLike()
  return (
    <button
      type="button"
      onClick={(e) => {
        e.preventDefault()
        e.stopPropagation()
        toggle.mutate({ id: post.id, like: !post.liked })
      }}
      aria-pressed={post.liked}
      aria-label={post.liked ? '좋아요 취소' : '좋아요'}
      className={clsx(
        'inline-flex cursor-pointer items-center gap-1.5 rounded-full font-semibold transition-colors',
        size === 'lg' ? 'px-4 py-2 text-sm ring-1' : 'px-2 py-1 text-sm',
        post.liked
          ? 'text-rose-500 ring-rose-200 hover:bg-rose-50'
          : 'text-ink-500 ring-ink-200 hover:bg-ink-50 hover:text-ink-800',
      )}
    >
      <Heart className={clsx('size-4 transition-transform', post.liked && 'scale-110 fill-rose-500')} />
      {post.likeCount}
    </button>
  )
}

/** 피드의 글 카드. 본문은 5줄까지만 보여주고 누르면 상세로 간다 */
export function PostCard({ post }: { post: Post }) {
  return (
    <Link
      to={`/community/${post.id}`}
      className="block rounded-3xl bg-white p-5 shadow-card ring-1 ring-ink-100 transition-shadow hover:shadow-lift"
    >
      <PostAuthorLine post={post} />
      <div className="mt-3">
        <CategoryBadges post={post} />
      </div>
      <p className="mt-2.5 line-clamp-5 text-[15px] leading-relaxed whitespace-pre-wrap text-ink-800">{post.content}</p>
      <PostImages post={post} className="mt-3" />
      <div className="-ml-2 mt-3 flex items-center gap-1">
        <LikeButton post={post} />
        <span className="inline-flex items-center gap-1.5 px-2 py-1 text-sm font-semibold text-ink-500">
          <MessageCircle className="size-4" /> {post.commentCount}
        </span>
      </div>
    </Link>
  )
}
