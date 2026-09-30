import { createContext, useContext } from 'react'

export type ToastKind = 'success' | 'error'

export const ToastContext = createContext<((message: string, kind?: ToastKind) => void) | null>(null)

/** 화면 아래에 잠깐 떴다 사라지는 안내를 띄운다 (ToastProvider 안에서만) */
export function useToast() {
  const context = useContext(ToastContext)
  if (!context) throw new Error('ToastProvider가 필요합니다')
  return context
}
