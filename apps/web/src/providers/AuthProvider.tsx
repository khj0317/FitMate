import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'
import { AuthContext } from './authContext'
import { api, onSessionChange, refreshTokens, setAccessToken } from '../lib/api'
import { keys } from '../lib/queries'
import type { MyProfile, SignupInput, Tokens } from '../lib/types'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<'checking' | 'in' | 'out'>('checking')
  const [afterLoginPath, setAfterLoginPath] = useState('/')

  // 앱을 열면 리프레시 토큰 쿠키로 로그인 상태를 복원한다 (액세스 토큰은 메모리에만 있어서 새로고침하면 사라짐)
  useEffect(() => {
    let cancelled = false
    void refreshTokens().then((ok) => {
      if (!cancelled) setStatus(ok ? 'in' : 'out')
    })
    return () => {
      cancelled = true
    }
  }, [])

  // 로그인·로그아웃, 토큰 재발급 실패로 인한 로그아웃을 반영하고 캐시도 비운다
  useEffect(
    () =>
      onSessionChange((next) => {
        setStatus(next ? 'in' : 'out')
        if (!next) {
          queryClient.clear()
          setAfterLoginPath('/')
        }
      }),
    [queryClient],
  )

  const login = async (loginId: string, password: string) => {
    const tokens = await api.post<Tokens>('/api/auth/login', { loginId, password })
    setAccessToken(tokens.accessToken) // 리프레시 토큰은 서버가 HttpOnly 쿠키로 저장했다
  }

  const startGuest = async () => {
    const tokens = await api.post<Tokens>('/api/auth/guest')
    setAccessToken(tokens.accessToken)
  }

  const signup = async (input: SignupInput) => {
    await api.post('/api/auth/signup', input)
    // 로그인되는 순간 GuestOnly가 리다이렉트하므로, 그 전에 목적지를 정해 둔다
    setAfterLoginPath('/profile?welcome=1')
    await login(input.loginId, input.password)
  }

  const logout = async () => {
    // 체험 계정은 로그아웃하면 서버가 계정과 체험하며 남긴 데이터를 모두 지우므로 먼저 확인한다
    const guest = queryClient.getQueryData<MyProfile>(keys.me)?.role === 'GUEST'
    if (guest && !window.confirm('로그아웃하면 체험 계정과 체험하며 남긴 글·채팅·요청이 모두 삭제돼요. 로그아웃할까요?')) {
      return false
    }
    // 서버가 리프레시 토큰을 폐기하고 쿠키를 지운다
    await api.post('/api/auth/logout').catch(() => undefined)
    setAccessToken(null)
    return true
  }

  return (
    <AuthContext value={{ ready: status !== 'checking', loggedIn: status === 'in', afterLoginPath, login, startGuest, signup, logout }}>
      {children}
    </AuthContext>
  )
}
