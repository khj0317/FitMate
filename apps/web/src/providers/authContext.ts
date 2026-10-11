import { createContext, useContext } from 'react'
import type { SignupInput } from '../lib/types'

export interface AuthContextValue {
  /** 새로고침 직후 쿠키로 로그인 상태를 확인하는 동안은 false */
  ready: boolean
  loggedIn: boolean
  /** 로그인 직후 이동할 경로. 회원가입이면 프로필 설정 화면으로 보낸다 */
  afterLoginPath: string
  login: (loginId: string, password: string) => Promise<void>
  /** 가입 없이 둘러볼 1회용 체험 계정을 만들고 로그인한다 */
  startGuest: () => Promise<void>
  signup: (input: SignupInput) => Promise<void>
  /** 체험 계정이 삭제 확인에서 취소하면 false */
  logout: () => Promise<boolean>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('AuthProvider가 필요합니다')
  return context
}
