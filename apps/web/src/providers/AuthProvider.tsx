import { useQueryClient } from '@tanstack/react-query'
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, getAccessToken, getRefreshToken, onSessionChange, setTokens } from '../lib/api'
import type { Tokens } from '../lib/types'

interface AuthContextValue {
  loggedIn: boolean
  login: (email: string, password: string) => Promise<void>
  signup: (email: string, password: string, nickname: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [loggedIn, setLoggedIn] = useState(() => getAccessToken() !== null)

  // 토큰 재발급 실패 등으로 로그아웃되면 캐시도 비운다
  useEffect(
    () =>
      onSessionChange((next) => {
        setLoggedIn(next)
        if (!next) queryClient.clear()
      }),
    [queryClient],
  )

  const login = async (email: string, password: string) => {
    const tokens = await api.post<Tokens>('/api/auth/login', { email, password })
    setTokens({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken })
  }

  const signup = async (email: string, password: string, nickname: string) => {
    await api.post('/api/auth/signup', { email, password, nickname })
    await login(email, password)
  }

  const logout = async () => {
    const refreshToken = getRefreshToken()
    setTokens(null)
    if (refreshToken) await api.post('/api/auth/logout', { refreshToken }).catch(() => undefined)
  }

  return <AuthContext value={{ loggedIn, login, signup, logout }}>{children}</AuthContext>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('AuthProvider가 필요합니다')
  return context
}
