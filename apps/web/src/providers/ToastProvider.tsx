import { CheckCircle2, CircleAlert } from 'lucide-react'
import { useCallback, useState, type ReactNode } from 'react'
import { ToastContext, type ToastKind } from './toastContext'

interface Toast {
  id: number
  kind: ToastKind
  message: string
}

let nextId = 1

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const show = useCallback((message: string, kind: ToastKind = 'success') => {
    const id = nextId++
    setToasts((current) => [...current, { id, kind, message }])
    setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), 3200)
  }, [])

  return (
    <ToastContext value={show}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 bottom-24 z-50 flex flex-col items-center gap-2 px-4 md:bottom-8"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className="flex animate-fade-up items-center gap-2 rounded-2xl bg-ink-900/95 px-4 py-3 text-sm font-medium text-white shadow-lift backdrop-blur"
          >
            {toast.kind === 'success' ? (
              <CheckCircle2 className="size-4 text-emerald-400" />
            ) : (
              <CircleAlert className="size-4 text-brand-400" />
            )}
            {toast.message}
          </div>
        ))}
      </div>
    </ToastContext>
  )
}
