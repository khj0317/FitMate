import { useQueryClient } from '@tanstack/react-query'
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, getAccessToken, getRefreshToken, onSessionChange, setTokens } from '../lib/api'
import type { SignupInput, Tokens } from '../lib/types'

interface AuthContextValue {
  loggedIn: boolean
  /** 로그인 직후 이동할 경로. 회원가입이면 프로필 설정 화면으로 보낸다 */
  afterLoginPath: string
  login: (loginId: string, password: string) => Promise<void>
  signup: (input: SignupInput) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [loggedIn, setLoggedIn] = useState(() => getAccessToken() !== null)
  const [afterLoginPath, setAfterLoginPath] = useState('/')

  // 토큰 재발급 실패 등으로 로그아웃되면 캐시도 비운다
  useEffect(
    () =>
      onSessionChange((next) => {
        setLoggedIn(next)
        if (!next) {
          queryClient.clear()
          setAfterLoginPath('/')
        }
      }),
    [queryClient],
  )

  const login = async (loginId: string, password: string) => {
    const tokens = await api.post<Tokens>('/api/auth/login', { loginId, password })
    setTokens({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken })
  }

  const signup = async (input: SignupInput) => {
    await api.post('/api/auth/signup', input)
    // 로그인되는 순간 GuestOnly가 리다이렉트하므로, 그 전에 목적지를 정해 둔다
    setAfterLoginPath('/profile?welcome=1')
    await login(input.loginId, input.password)
  }

  const logout = async () => {
    const refreshToken = getRefreshToken()
    setTokens(null)
    if (refreshToken) await api.post('/api/auth/logout', { refreshToken }).catch(() => undefined)
  }

  return <AuthContext value={{ loggedIn, afterLoginPath, login, signup, logout }}>{children}</AuthContext>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('AuthProvider가 필요합니다')
  return context
}
