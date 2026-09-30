import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Camera, LoaderCircle } from 'lucide-react'
import { useRef } from 'react'
import { api, errorMessage } from '../lib/api'
import { compressImage, ImageError } from '../lib/image'
import { keys } from '../lib/queries'
import type { MyProfile } from '../lib/types'
import { useToast } from '../providers/toastContext'
import { Avatar } from './Avatar'

/** 프로필 사진은 고르는 즉시 저장한다 (서버가 가운데를 정사각형으로 잘라 512px로 저장). */
export function ProfilePhoto({ me, nickname }: { me: MyProfile; nickname: string }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const inputRef = useRef<HTMLInputElement>(null)

  const onSaved = (profile: MyProfile) => queryClient.setQueryData(keys.me, profile)
  const upload = useMutation({
    mutationFn: async (file: File) =>
      api.upload<MyProfile>('/api/users/me/profile-image', await compressImage(file, 1024)),
    onSuccess: (profile) => {
      onSaved(profile)
      toast('프로필 사진을 바꿨어요')
    },
    onError: (e) => toast(e instanceof ImageError ? e.message : errorMessage(e), 'error'),
  })
  const remove = useMutation({
    mutationFn: () => api.delete<MyProfile>('/api/users/me/profile-image'),
    onSuccess: (profile) => {
      onSaved(profile)
      toast('프로필 사진을 지웠어요')
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })
  const busy = upload.isPending || remove.isPending

  return (
    <div className="flex flex-col items-center gap-1.5">
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={busy}
        className="group relative cursor-pointer rounded-full focus-visible:outline-2"
        aria-label="프로필 사진 바꾸기"
      >
        <Avatar id={me.id} name={nickname} imageUrl={me.profileImageUrl} size="lg" />
        <span className="absolute inset-0 flex items-center justify-center rounded-full bg-ink-900/0 text-white opacity-0 transition group-hover:bg-ink-900/40 group-hover:opacity-100">
          <Camera className="size-5" />
        </span>
        {busy ? (
          <span className="absolute inset-0 flex items-center justify-center rounded-full bg-ink-900/50 text-white">
            <LoaderCircle className="size-5 animate-spin" />
          </span>
        ) : (
          <span className="absolute -right-0.5 -bottom-0.5 flex size-6 items-center justify-center rounded-full bg-brand-500 text-white ring-2 ring-white">
            <Camera className="size-3.5" />
          </span>
        )}
      </button>
      {me.profileImageUrl && (
        <button
          type="button"
          onClick={() => remove.mutate()}
          disabled={busy}
          className="cursor-pointer text-xs font-medium text-ink-400 hover:text-red-500 disabled:opacity-50"
        >
          사진 삭제
        </button>
      )}
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        hidden
        onChange={(e) => {
          const file = e.target.files?.[0]
          if (file) upload.mutate(file)
          e.target.value = ''
        }}
      />
    </div>
  )
}
