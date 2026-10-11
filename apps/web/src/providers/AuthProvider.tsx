import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'
import { AuthContext } from './authContext'
import { useToast } from './toastContext'
import { api, getAccessToken, onAccountGone, onSessionChange, refreshTokens, setAccessToken } from '../lib/api'
import { keys } from '../lib/queries'
import type { MyProfile, SignupInput, Tokens } from '../lib/types'

/** 한 탭에서 로그아웃하면 다른 탭에 알리는 localStorage 키 (값이 바뀔 때 storage 이벤트가 다른 탭에만 간다) */
const LOGOUT_SIGNAL_KEY = 'fitmate:logout'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const toast = useToast()
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

  // 계정이 지워져서 로그아웃될 때: 체험 계정이면 무슨 일인지 알려 준다
  useEffect(
    () =>
      onAccountGone(() => {
        if (queryClient.getQueryData<MyProfile>(keys.me)?.role === 'GUEST') {
          toast('체험 계정이 삭제되어 로그아웃됐어요', 'error')
        }
      }),
    [queryClient, toast],
  )

  // 다른 탭에서 로그아웃하면 이 탭도 로그아웃한다 (리프레시 토큰 쿠키는 이미 지워졌고, 체험 계정이면 계정도 없다)
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key !== LOGOUT_SIGNAL_KEY || getAccessToken() === null) return
      if (queryClient.getQueryData<MyProfile>(keys.me)?.role === 'GUEST') {
        toast('다른 창에서 로그아웃해서 체험 계정이 삭제됐어요', 'error')
      }
      setAccessToken(null)
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [queryClient, toast])

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
    // 서버가 리프레시 토큰을 폐기하고 쿠키를 지운다 (체험 계정은 계정도 지운다)
    try {
      await api.post('/api/auth/logout')
    } catch {
      // 체험 계정은 실패하면 데이터가 지워지지 않은 채 남으므로 로그아웃하지 않고 다시 시도하게 한다
      if (guest) {
        toast('로그아웃하지 못했어요. 잠시 후 다시 시도해 주세요', 'error')
        return false
      }
    }
    try {
      localStorage.setItem(LOGOUT_SIGNAL_KEY, String(Date.now()))
    } catch {
      // 저장소를 못 쓰는 환경이면 다른 탭에는 알리지 못한다 (다음 요청에서 401로 로그아웃됨)
    }
    setAccessToken(null)
    return true
  }

  return (
    <AuthContext value={{ ready: status !== 'checking', loggedIn: status === 'in', afterLoginPath, login, startGuest, signup, logout }}>
      {children}
    </AuthContext>
  )
}
