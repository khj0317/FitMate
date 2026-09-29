import clsx from 'clsx'
import { ArrowLeft, CornerDownRight, MessageCircle, MoreHorizontal, Pencil, Send, Trash2, X } from 'lucide-react'
import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { Avatar } from '../components/Avatar'
import { Modal } from '../components/Modal'
import { CategoryBadges, LikeButton, PostAuthorLine, PostImages } from '../components/PostCard'
import { PostComposer } from '../components/PostComposer'
import { SafetyMenu } from '../components/SafetyMenu'
import { Button, Card, EmptyState, PageLoader, Spinner } from '../components/ui'
import { errorMessage } from '../lib/api'
import { timeAgo } from '../lib/format'
import { useAddComment, useComments, useDeleteComment, useDeletePost, usePost } from '../lib/queries'
import type { Post, PostComment } from '../lib/types'
import { useToast } from '../providers/ToastProvider'

export function PostDetailPage() {
  const { postId } = useParams()
  const id = Number(postId)
  const navigate = useNavigate()
  const { data: post, isLoading, error } = usePost(id)
  const [replyTo, setReplyTo] = useState<PostComment | null>(null)

  if (isLoading) return <PageLoader />
  if (error || !post) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-10">
        <Card>
          <EmptyState
            emoji="🤔"
            title="글을 찾을 수 없어요"
            description={error ? errorMessage(error) : '지워졌거나 볼 수 없는 글이에요.'}
            action={<Link to="/community" className="font-bold text-brand-600">커뮤니티로</Link>}
          />
        </Card>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-6 md:px-8 md:py-10">
      <button
        onClick={() => navigate(-1)}
        className="mb-4 inline-flex cursor-pointer items-center gap-1 text-sm font-semibold text-ink-500 hover:text-ink-800"
      >
        <ArrowLeft className="size-4" /> 뒤로
      </button>

      <Card className="p-6">
        <div className="flex items-start gap-2">
          <div className="min-w-0 flex-1">
            <PostAuthorLine post={post} />
          </div>
          {post.mine ? (
            <PostOwnerMenu post={post} />
          ) : (
            <SafetyMenu
              user={{ id: post.author.userId, nickname: post.author.nickname }}
              onBlocked={() => navigate('/community')}
              placement="down"
              className="[&>button]:size-9 [&>button]:ring-0"
            />
          )}
        </div>
        <div className="mt-4">
          <CategoryBadges post={post} />
        </div>
        <p className="mt-3 text-[16px] leading-relaxed break-words whitespace-pre-wrap text-ink-900">{post.content}</p>
        <PostImages post={post} className="mt-4" />
        <div className="mt-5 flex items-center gap-2 border-t border-ink-100 pt-4">
          <LikeButton post={post} size="lg" />
          <span className="inline-flex items-center gap-1.5 px-2 text-sm font-semibold text-ink-500">
            <MessageCircle className="size-4" /> 댓글 {post.commentCount}
          </span>
        </div>
      </Card>

      <Comments post={post} replyTo={replyTo} onReply={setReplyTo} />
    </div>
  )
}

function PostOwnerMenu({ post }: { post: Post }) {
  const navigate = useNavigate()
  const toast = useToast()
  const remove = useDeletePost()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onPointer = (event: PointerEvent) => !ref.current?.contains(event.target as Node) && setOpen(false)
    document.addEventListener('pointerdown', onPointer)
    return () => document.removeEventListener('pointerdown', onPointer)
  }, [open])

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((value) => !value)}
        className="flex size-9 cursor-pointer items-center justify-center rounded-full text-ink-400 hover:bg-ink-100 hover:text-ink-700"
        aria-label="글 메뉴"
        aria-expanded={open}
      >
        <MoreHorizontal className="size-5" />
      </button>
      {open && (
        <div className="absolute top-10 right-0 z-20 w-36 animate-pop overflow-hidden rounded-2xl bg-white py-1 shadow-lift ring-1 ring-ink-100">
          <button
            onClick={() => {
              setOpen(false)
              setEditing(true)
            }}
            className="flex w-full cursor-pointer items-center gap-2 px-4 py-2.5 text-sm font-medium hover:bg-ink-50"
          >
            <Pencil className="size-4" /> 고치기
          </button>
          <button
            onClick={() => {
              setOpen(false)
              setConfirming(true)
            }}
            className="flex w-full cursor-pointer items-center gap-2 px-4 py-2.5 text-sm font-medium text-red-600 hover:bg-red-50"
          >
            <Trash2 className="size-4" /> 지우기
          </button>
        </div>
      )}
      {editing && <PostComposer open editing={post} onClose={() => setEditing(false)} />}
      <Modal open={confirming} onClose={() => setConfirming(false)} title="글 지우기">
        <p className="text-[15px] leading-relaxed text-ink-600">글을 지우면 댓글과 사진도 함께 지워지고 되돌릴 수 없어요.</p>
        <div className="mt-6 flex gap-2">
          <Button variant="secondary" className="flex-1" onClick={() => setConfirming(false)}>
            돌아가기
          </Button>
          <Button
            variant="danger"
            className="flex-1"
            loading={remove.isPending}
            onClick={() =>
              remove.mutate(post.id, {
                onSuccess: () => {
                  toast('글을 지웠어요')
                  navigate('/community', { replace: true })
                },
                onError: (e) => toast(errorMessage(e), 'error'),
              })
            }
          >
            지우기
          </Button>
        </div>
      </Modal>
    </div>
  )
}

function Comments({ post, replyTo, onReply }: {
  post: Post
  replyTo: PostComment | null
  onReply: (comment: PostComment | null) => void
}) {
  const toast = useToast()
  const { data: comments, isLoading } = useComments(post.id)
  const add = useAddComment(post.id)
  const remove = useDeleteComment(post.id)
  const [text, setText] = useState('')
  const inputRef = useRef<HTMLTextAreaElement>(null)

  const startReply = (comment: PostComment) => {
    onReply(comment)
    requestAnimationFrame(() => inputRef.current?.focus())
  }

  const submit = () => {
    const content = text.trim()
    if (!content || add.isPending) return
    add.mutate({ content, parentId: replyTo?.id ?? null }, {
      onSuccess: () => {
        setText('')
        onReply(null)
      },
      onError: (e) => toast(errorMessage(e), 'error'),
    })
  }

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      submit()
    }
  }

  const deleteComment = (comment: PostComment) =>
    remove.mutate(comment.id, { onError: (e) => toast(errorMessage(e), 'error') })

  return (
    <Card className="mt-4 p-6">
      <h2 className="mb-4 font-bold">댓글 {post.commentCount}</h2>
      {isLoading ? (
        <div className="flex justify-center py-6"><Spinner /></div>
      ) : !comments?.length ? (
        <p className="py-6 text-center text-sm text-ink-400">첫 댓글을 남겨 보세요 💬</p>
      ) : (
        <ul className="space-y-5">
          {comments.map((comment) => (
            <li key={comment.id}>
              <CommentRow comment={comment} onReply={() => startReply(comment)} onDelete={() => deleteComment(comment)} />
              {comment.replies.length > 0 && (
                <ul className="mt-3 space-y-3 pl-6">
                  {comment.replies.map((reply) => (
                    <li key={reply.id} className="flex gap-1.5">
                      <CornerDownRight className="mt-2 size-4 shrink-0 text-ink-300" />
                      <div className="min-w-0 flex-1">
                        <CommentRow comment={reply} onDelete={() => deleteComment(reply)} />
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}

      <div className="mt-6 border-t border-ink-100 pt-4">
        {replyTo && (
          <div className="mb-2 flex items-center justify-between rounded-xl bg-ink-50 px-3 py-2 text-xs text-ink-600">
            <span className="truncate">
              <b>{replyTo.author?.nickname ?? '탈퇴한 회원'}</b>님에게 답글 남기는 중
            </span>
            <button onClick={() => onReply(null)} className="cursor-pointer p-0.5 text-ink-400 hover:text-ink-700" aria-label="답글 취소">
              <X className="size-4" />
            </button>
          </div>
        )}
        <div className="flex items-end gap-2 rounded-3xl bg-ink-50 p-1.5 pl-4 ring-1 ring-ink-200 focus-within:ring-2 focus-within:ring-brand-400">
          <textarea
            ref={inputRef}
            rows={1}
            value={text}
            maxLength={1000}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder={replyTo ? '답글을 입력하세요' : '댓글을 입력하세요'}
            aria-label="댓글"
            className="max-h-32 min-h-9 flex-1 resize-none bg-transparent py-2 text-[15px] outline-none [field-sizing:content] placeholder:text-ink-400"
          />
          <button
            onClick={submit}
            disabled={!text.trim() || add.isPending}
            className="flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-full bg-brand-500 text-white transition hover:bg-brand-600 disabled:cursor-default disabled:bg-ink-200"
            aria-label="댓글 보내기"
          >
            <Send className="size-4" />
          </button>
        </div>
      </div>
    </Card>
  )
}

function CommentRow({ comment, onReply, onDelete }: { comment: PostComment; onReply?: () => void; onDelete: () => void }) {
  if (comment.deleted) {
    return <p className="py-1 text-sm text-ink-400">삭제된 댓글이에요</p>
  }
  const name = comment.author?.nickname ?? '탈퇴한 회원'
  return (
    <div className="flex gap-2.5">
      {comment.author ? (
        <Link to={`/users/${comment.author.userId}`} className="shrink-0">
          <Avatar id={comment.author.userId} name={name} imageUrl={comment.author.profileImageUrl} size="sm" className="size-8! text-xs!" />
        </Link>
      ) : (
        <Avatar id={0} name="?" size="sm" className="size-8! text-xs!" />
      )}
      <div className="min-w-0 flex-1">
        <p className="text-sm">
          {comment.author ? (
            <Link to={`/users/${comment.author.userId}`} className="font-bold hover:underline">{name}</Link>
          ) : (
            <span className="font-bold text-ink-400">{name}</span>
          )}
          <span className="ml-2 text-xs text-ink-400">{timeAgo(comment.createdAt)}</span>
        </p>
        <p className="mt-0.5 text-[15px] leading-relaxed break-words whitespace-pre-wrap text-ink-800">{comment.content}</p>
        <div className={clsx('mt-1 flex gap-3 text-xs font-semibold text-ink-400', !onReply && !comment.mine && 'hidden')}>
          {onReply && (
            <button onClick={onReply} className="cursor-pointer hover:text-ink-700">답글 달기</button>
          )}
          {comment.mine && (
            <button onClick={onDelete} className="cursor-pointer hover:text-red-600">삭제</button>
          )}
        </div>
      </div>
    </div>
  )
}
