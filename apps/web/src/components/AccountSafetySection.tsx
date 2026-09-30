import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, UserX } from 'lucide-react'
import { useState } from 'react'
import { api, errorMessage, setAccessToken } from '../lib/api'
import { timeAgo } from '../lib/format'
import type { BlockedUser } from '../lib/types'
import { useToast } from '../providers/toastContext'
import { Avatar } from './Avatar'
import { Modal } from './Modal'
import { Button, Card, Field, Input } from './ui'

/** 내 프로필 아래쪽: 차단한 사용자 관리 + 회원 탈퇴 */
export function AccountSafetySection() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const { data: blocks } = useQuery({
    queryKey: ['blocks'],
    queryFn: () => api.get<BlockedUser[]>('/api/users/me/blocks'),
  })

  const unblock = useMutation({
    mutationFn: (userId: number) => api.delete(`/api/users/${userId}/block`),
    onSuccess: () => {
      toast('차단을 해제했어요')
      return Promise.all(
        ['blocks', 'recommendations', 'chatRooms'].map((key) => queryClient.invalidateQueries({ queryKey: [key] })),
      )
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  return (
    <Card className="p-6">
      <h2 className="text-lg font-bold">차단한 사용자</h2>
      <p className="mt-0.5 text-sm text-ink-500">차단한 사람은 추천·요청·채팅에서 보이지 않아요</p>
      <div className="mt-5 space-y-2">
        {!blocks?.length ? (
          <p className="rounded-2xl bg-ink-50 px-4 py-5 text-center text-sm text-ink-400">차단한 사용자가 없어요</p>
        ) : (
          blocks.map((user) => (
            <div key={user.userId} className="flex items-center gap-3 rounded-2xl bg-ink-50 px-3 py-2.5">
              <Avatar id={user.userId} name={user.nickname} imageUrl={user.profileImageUrl} size="sm" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold">{user.nickname}</p>
                <p className="text-xs text-ink-400">{timeAgo(user.blockedAt)} 차단</p>
              </div>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => unblock.mutate(user.userId)}
                loading={unblock.isPending && unblock.variables === user.userId}
              >
                해제
              </Button>
            </div>
          ))
        )}
      </div>
      <WithdrawButton />
    </Card>
  )
}

function WithdrawButton() {
  const toast = useToast()
  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const withdraw = useMutation({
    mutationFn: () => api.post('/api/users/me/withdrawal', { password }),
    onSuccess: () => {
      setAccessToken(null) // 서버에서 모든 로그인이 이미 끊겼으므로 화면도 로그아웃
      toast('탈퇴했어요. 그동안 함께해 주셔서 고마워요')
    },
    onError: (e) => setError(errorMessage(e)),
  })

  const close = () => {
    setOpen(false)
    setPassword('')
    setAgreed(false)
    setError(null)
  }

  return (
    <>
      <div className="mt-6 border-t border-ink-100 pt-4 text-right">
        <button type="button" onClick={() => setOpen(true)} className="cursor-pointer text-sm text-ink-400 underline-offset-2 hover:text-red-500 hover:underline">
          회원 탈퇴
        </button>
      </div>
      <Modal open={open} onClose={close} title="정말 탈퇴할까요?">
        <div className="space-y-4">
          <div className="flex gap-3 rounded-2xl bg-red-50 p-4 text-sm leading-relaxed text-red-700">
            <AlertTriangle className="mt-0.5 size-5 shrink-0" />
            <div>
              프로필, 사진, 활동 지역, 운동 정보, 매칭 요청이 <b>모두 삭제되고 되돌릴 수 없어요.</b>
              <br />
              상대방 채팅방의 대화는 남지만 &quot;탈퇴한 회원&quot;으로 표시돼요.
            </div>
          </div>
          <Field label="비밀번호 확인" error={error ?? undefined}>
            <Input
              type="password"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value)
                setError(null)
              }}
              placeholder="현재 비밀번호"
              autoComplete="current-password"
            />
          </Field>
          <label className="flex cursor-pointer items-center gap-2 text-sm font-medium text-ink-700">
            <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} className="size-4 accent-red-500" />
            위 내용을 확인했고 탈퇴할게요
          </label>
          <div className="grid grid-cols-2 gap-2">
            <Button variant="secondary" onClick={close}>취소</Button>
            <Button variant="danger" onClick={() => withdraw.mutate()} loading={withdraw.isPending} disabled={!agreed || !password}>
              <UserX className="size-4" /> 탈퇴하기
            </Button>
          </div>
        </div>
      </Modal>
    </>
  )
}
