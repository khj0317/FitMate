import { X } from 'lucide-react'
import { useEffect, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

/**
 * 모달은 document.body에 portal로 그린다.
 * 부모 중에 transform이 걸린 요소(예: 마우스를 올리면 떠오르는 카드)가 있으면 position: fixed가
 * 화면이 아니라 그 요소를 기준으로 잡혀 모달이 카드 안에 갇혀 잘리기 때문이다.
 */
export function Modal({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    // 모달이 열려 있는 동안 뒤 화면이 스크롤되지 않게 한다
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      window.removeEventListener('keydown', onKey)
      document.body.style.overflow = previousOverflow
    }
  }, [open, onClose])

  if (!open) return null
  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-ink-900/40 backdrop-blur-sm sm:items-center sm:p-4"
      onClick={(event) => {
        event.stopPropagation() // portal이어도 React 이벤트는 부모 컴포넌트로 전파되므로 막는다
        onClose()
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(event) => event.stopPropagation()}
        className="flex max-h-[90dvh] w-full max-w-md animate-pop flex-col rounded-t-3xl bg-white shadow-lift sm:rounded-3xl"
      >
        <div className="flex shrink-0 items-center justify-between px-6 pt-6 pb-5">
          <h2 className="text-lg font-bold">{title}</h2>
          <button onClick={onClose} className="cursor-pointer rounded-full p-1.5 text-ink-400 hover:bg-ink-100 hover:text-ink-700" aria-label="닫기">
            <X className="size-5" />
          </button>
        </div>
        {/* 내용이 화면보다 길면 모달 안에서 스크롤 */}
        <div className="min-h-0 overflow-y-auto px-6 pb-safe sm:pb-6">{children}</div>
      </div>
    </div>,
    document.body,
  )
}
