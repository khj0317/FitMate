import { X } from 'lucide-react'
import { useEffect, type MouseEvent } from 'react'
import { createPortal } from 'react-dom'

/**
 * 사진 크게 보기. body에 portal로 그리고, 클릭이 부모(예: 글 카드 링크)로 전달되지 않게 막는다.
 * portal이어도 React 이벤트는 컴포넌트 트리를 따라 부모로 올라가기 때문이다.
 */
export function PhotoViewer({ url, onClose }: { url: string; onClose: () => void }) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const close = (event: MouseEvent) => {
    event.preventDefault()
    event.stopPropagation()
    onClose()
  }

  return createPortal(
    <div className="fixed inset-0 z-50 flex animate-pop items-center justify-center bg-ink-900/90 p-4" onClick={close}>
      <button
        onClick={close}
        className="absolute top-4 right-4 cursor-pointer rounded-full bg-white/10 p-2 text-white hover:bg-white/20"
        aria-label="닫기"
      >
        <X className="size-6" />
      </button>
      <img
        src={url}
        alt="사진 원본"
        className="max-h-full max-w-full rounded-lg object-contain"
        onClick={(e) => {
          e.preventDefault()
          e.stopPropagation()
        }}
      />
    </div>,
    document.body,
  )
}
