import clsx from 'clsx'
import { ImagePlus, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { errorMessage } from '../lib/api'
import { POST_CATEGORIES } from '../lib/community'
import { sportEmoji } from '../lib/format'
import { compressImage, ImageError } from '../lib/image'
import { useCreatePost, useIsGuest, useSports, useUpdatePost } from '../lib/queries'
import type { Post, PostCategory } from '../lib/types'
import { useToast } from '../providers/toastContext'
import { Modal } from './Modal'
import { Button, Chip, Textarea } from './ui'

const MAX_IMAGES = 4
const MAX_LENGTH = 3000

interface Photo {
  blob: Blob
  preview: string
}

/**
 * 글쓰기 / 고치기. 고칠 때는 사진을 바꿀 수 없고 분류·종목·내용만 고친다.
 * 사진은 올리기 전에 브라우저에서 줄여서 보낸다.
 */
export function PostComposer({ open, onClose, editing, onCreated }: {
  open: boolean
  onClose: () => void
  editing?: Post
  onCreated?: (post: Post) => void
}) {
  const toast = useToast()
  const { data: sports } = useSports()
  const create = useCreatePost()
  const update = useUpdatePost()
  const fileRef = useRef<HTMLInputElement>(null)
  const guest = useIsGuest()

  const [category, setCategory] = useState<PostCategory>(editing?.category ?? 'CERTIFY')
  const [sportId, setSportId] = useState<number | null>(editing?.sportId ?? null)
  const [content, setContent] = useState(editing?.content ?? '')
  const [photos, setPhotos] = useState<Photo[]>([])
  const [compressing, setCompressing] = useState(false)

  // 미리보기용 URL은 창을 닫을 때 모두 해제한다
  const previews = useRef(new Set<string>())
  useEffect(() => {
    const created = previews.current
    return () => created.forEach((url) => URL.revokeObjectURL(url))
  }, [])

  const addPhotos = async (files: File[]) => {
    const room = MAX_IMAGES - photos.length
    if (files.length > room) toast(`사진은 ${MAX_IMAGES}장까지 올릴 수 있어요`, 'error')
    setCompressing(true)
    try {
      const added: Photo[] = []
      for (const file of files.slice(0, room)) {
        const blob = await compressImage(file, 1600)
        const preview = URL.createObjectURL(blob)
        previews.current.add(preview)
        added.push({ blob, preview })
      }
      setPhotos((current) => [...current, ...added])
    } catch (e) {
      toast(e instanceof ImageError ? e.message : '사진을 읽지 못했어요', 'error')
    } finally {
      setCompressing(false)
    }
  }

  const removePhoto = (index: number) =>
    setPhotos((current) => {
      URL.revokeObjectURL(current[index].preview)
      previews.current.delete(current[index].preview)
      return current.filter((_, i) => i !== index)
    })

  const submit = () => {
    const body = { category, sportId, content: content.trim() }
    if (!body.content) {
      toast('내용을 입력해 주세요', 'error')
      return
    }
    if (editing) {
      update.mutate({ id: editing.id, ...body }, {
        onSuccess: () => {
          toast('글을 고쳤어요')
          onClose()
        },
        onError: (e) => toast(errorMessage(e), 'error'),
      })
      return
    }
    create.mutate({ post: body, images: photos.map((photo) => photo.blob) }, {
      onSuccess: (post) => {
        toast('글을 올렸어요')
        onClose()
        onCreated?.(post)
      },
      onError: (e) => toast(errorMessage(e), 'error'),
    })
  }

  const placeholder = POST_CATEGORIES.find((info) => info.value === category)?.placeholder

  return (
    <Modal open={open} onClose={onClose} title={editing ? '글 고치기' : '글쓰기'}>
      <div className="space-y-5">
        <div>
          <p className="mb-1.5 text-sm font-semibold text-ink-700">어떤 글인가요?</p>
          <div className="grid grid-cols-2 gap-2">
            {POST_CATEGORIES.map((info) => (
              <button
                key={info.value}
                type="button"
                onClick={() => setCategory(info.value)}
                className={clsx(
                  'flex cursor-pointer items-center gap-2 rounded-2xl px-3 py-2.5 text-sm font-semibold transition-all',
                  category === info.value
                    ? 'bg-brand-50 text-brand-700 ring-2 ring-brand-400'
                    : 'bg-white text-ink-600 ring-1 ring-ink-200 hover:ring-ink-300',
                )}
              >
                <span className="text-lg">{info.emoji}</span> {info.label}
              </button>
            ))}
          </div>
        </div>

        <div>
          <p className="mb-1.5 text-sm font-semibold text-ink-700">
            종목 <span className="font-normal text-ink-400">(선택)</span>
          </p>
          <div className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1 scrollbar-thin">
            <Chip active={sportId === null} onClick={() => setSportId(null)}>선택 안 함</Chip>
            {sports?.map((sport) => (
              <Chip key={sport.id} active={sportId === sport.id} onClick={() => setSportId(sport.id)}>
                {sportEmoji(sport.code)} {sport.name}
              </Chip>
            ))}
          </div>
        </div>

        <div>
          <Textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            maxLength={MAX_LENGTH}
            rows={6}
            placeholder={placeholder}
            aria-label="내용"
          />
          <p className="mt-1 text-right text-xs text-ink-400">{content.length} / {MAX_LENGTH}</p>
        </div>

        {!editing && guest && <p className="text-xs text-ink-400">체험 계정은 사진 없이 글만 올릴 수 있어요</p>}
        {!editing && !guest && (
          <div>
            <div className="flex flex-wrap gap-2">
              {photos.map((photo, index) => (
                <div key={photo.preview} className="relative size-20 overflow-hidden rounded-2xl ring-1 ring-ink-100">
                  <img src={photo.preview} alt="" className="size-full object-cover" />
                  <button
                    type="button"
                    onClick={() => removePhoto(index)}
                    className="absolute top-1 right-1 flex size-6 cursor-pointer items-center justify-center rounded-full bg-ink-900/70 text-white"
                    aria-label="사진 빼기"
                  >
                    <X className="size-3.5" />
                  </button>
                </div>
              ))}
              {photos.length < MAX_IMAGES && (
                <button
                  type="button"
                  onClick={() => fileRef.current?.click()}
                  disabled={compressing}
                  className="flex size-20 cursor-pointer flex-col items-center justify-center gap-1 rounded-2xl text-xs font-semibold text-ink-500 ring-1 ring-ink-200 ring-dashed hover:bg-ink-50 disabled:opacity-50"
                >
                  <ImagePlus className="size-5" />
                  {photos.length}/{MAX_IMAGES}
                </button>
              )}
            </div>
            <input
              ref={fileRef}
              type="file"
              accept="image/*"
              multiple
              hidden
              onChange={(e) => {
                void addPhotos([...(e.target.files ?? [])])
                e.target.value = ''
              }}
            />
          </div>
        )}

        <Button className="w-full" size="lg" loading={create.isPending || update.isPending || compressing} onClick={submit}>
          {editing ? '고치기' : '올리기'}
        </Button>
      </div>
    </Modal>
  )
}
