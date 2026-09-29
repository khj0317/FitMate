import { useMutation, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Ban, Flag, MoreHorizontal } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { api, errorMessage } from '../lib/api'
import type { ReportReason } from '../lib/types'
import { useToast } from '../providers/ToastProvider'
import { Modal } from './Modal'
import { Button, Chip, Field, Textarea } from './ui'

const REASONS: { value: ReportReason; label: string }[] = [
  { value: 'NO_SHOW', label: '약속 불이행 (노쇼)' },
  { value: 'ABUSE', label: '욕설 · 비하' },
  { value: 'SEXUAL', label: '성희롱 · 불쾌한 행동' },
  { value: 'SPAM', label: '스팸 · 광고' },
  { value: 'FAKE_PROFILE', label: '허위 프로필' },
  { value: 'OTHER', label: '기타' },
]

/** 사람을 차단·신고하는 ⋯ 메뉴. 차단하면 추천·요청·채팅 목록이 모두 바뀌므로 관련 데이터를 새로 받는다 */
export function SafetyMenu({
  user,
  onBlocked,
  className,
  placement = 'up',
}: {
  user: { id: number; nickname: string }
  onBlocked?: () => void
  className?: string
  /** 메뉴가 열리는 방향. 화면 위쪽(채팅방 헤더)에서는 아래로 연다 */
  placement?: 'up' | 'down'
}) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [dialog, setDialog] = useState<'report' | 'block' | null>(null)
  const [reason, setReason] = useState<ReportReason | null>(null)
  const [detail, setDetail] = useState('')
  const [alsoBlock, setAlsoBlock] = useState(true)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onClick = (event: MouseEvent) => !ref.current?.contains(event.target as Node) && setOpen(false)
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [open])

  const refresh = () =>
    Promise.all(
      ['recommendations', 'chatRooms', 'matchRequests', 'blocks'].map((key) =>
        queryClient.invalidateQueries({ queryKey: [key] }),
      ),
    )

  const close = () => {
    setDialog(null)
    setReason(null)
    setDetail('')
    setAlsoBlock(true)
  }

  const block = useMutation({
    mutationFn: () => api.put(`/api/users/${user.id}/block`, {}),
    onSuccess: async () => {
      toast(`${user.nickname}님을 차단했어요`)
      close()
      await refresh()
      onBlocked?.()
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  const report = useMutation({
    mutationFn: () => api.post(`/api/users/${user.id}/report`, { reason, detail, block: alsoBlock }),
    onSuccess: async () => {
      toast(alsoBlock ? '신고하고 차단했어요. 운영자가 확인할게요' : '신고했어요. 운영자가 확인할게요')
      close()
      if (alsoBlock) {
        await refresh()
        onBlocked?.()
      }
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  return (
    <div ref={ref} className={clsx('relative', className)}>
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-label="더보기"
        aria-expanded={open}
        className="flex size-11 cursor-pointer items-center justify-center rounded-xl text-ink-400 ring-1 ring-ink-200 transition hover:bg-ink-50 hover:text-ink-700"
      >
        <MoreHorizontal className="size-5" />
      </button>
      {open && (
        <div
          className={clsx(
            'absolute right-0 z-20 w-40 animate-pop overflow-hidden rounded-2xl bg-white py-1 shadow-lift ring-1 ring-ink-200',
            placement === 'up' ? 'bottom-full mb-2' : 'top-full mt-2',
          )}
        >
          <button
            type="button"
            onClick={() => {
              setOpen(false)
              setDialog('report')
            }}
            className="flex w-full cursor-pointer items-center gap-2 px-4 py-2.5 text-sm font-medium text-ink-700 hover:bg-ink-50"
          >
            <Flag className="size-4" /> 신고하기
          </button>
          <button
            type="button"
            onClick={() => {
              setOpen(false)
              setDialog('block')
            }}
            className="flex w-full cursor-pointer items-center gap-2 px-4 py-2.5 text-sm font-medium text-red-600 hover:bg-red-50"
          >
            <Ban className="size-4" /> 차단하기
          </button>
        </div>
      )}

      <Modal open={dialog === 'block'} onClose={close} title={`${user.nickname}님을 차단할까요?`}>
        <ul className="space-y-2 rounded-2xl bg-ink-50 p-4 text-sm leading-relaxed text-ink-600">
          <li>• 서로 추천에 보이지 않아요</li>
          <li>• 매칭 요청을 주고받을 수 없고, 대기 중인 요청은 취소돼요</li>
          <li>• 채팅을 주고받을 수 없고, 내 채팅 목록에서 대화가 숨겨져요</li>
          <li>• 상대방에게 차단 사실을 알리지 않아요</li>
        </ul>
        <p className="mt-3 text-xs text-ink-400">내 프로필 &gt; 차단한 사용자에서 언제든 해제할 수 있어요.</p>
        <div className="mt-5 grid grid-cols-2 gap-2">
          <Button variant="secondary" onClick={close}>취소</Button>
          <Button variant="danger" onClick={() => block.mutate()} loading={block.isPending}>
            <Ban className="size-4" /> 차단하기
          </Button>
        </div>
      </Modal>

      <Modal open={dialog === 'report'} onClose={close} title={`${user.nickname}님 신고하기`}>
        <div className="space-y-5">
          <Field label="어떤 문제가 있었나요?">
            <div className="flex flex-wrap gap-2">
              {REASONS.map((option) => (
                <Chip key={option.value} active={reason === option.value} onClick={() => setReason(option.value)}>
                  {option.label}
                </Chip>
              ))}
            </div>
          </Field>
          <Field label="자세한 내용 (선택)" hint={`${detail.length}/500`}>
            <Textarea
              rows={3}
              maxLength={500}
              value={detail}
              onChange={(e) => setDetail(e.target.value)}
              placeholder="운영자가 확인하는 데 도움이 돼요"
            />
          </Field>
          <label className="flex cursor-pointer items-center gap-2 text-sm font-medium text-ink-700">
            <input
              type="checkbox"
              checked={alsoBlock}
              onChange={(e) => setAlsoBlock(e.target.checked)}
              className="size-4 accent-brand-500"
            />
            이 사람을 차단도 할게요
          </label>
          <Button className="w-full" size="lg" onClick={() => report.mutate()} loading={report.isPending} disabled={!reason}>
            <Flag className="size-4" /> 신고하기
          </Button>
        </div>
      </Modal>
    </div>
  )
}
