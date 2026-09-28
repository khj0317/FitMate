import type { ApiErrorBody, Tokens } from './types'

/** 배포 시 VITE_API_URL=https://api.example.com, 개발 중에는 Vite 프록시를 쓰므로 비워 둔다. */
export const API_BASE = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? ''

const STORAGE_KEY = 'fitmate.tokens'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: { field: string; reason: string }[]

  constructor(body: ApiErrorBody) {
    // 입력값 오류면 첫 번째 필드의 이유를 보여주는 게 더 친절하다
    super(body.errors?.[0]?.reason ?? body.message)
    this.status = body.status
    this.code = body.code
    this.fieldErrors = body.errors ?? []
  }
}

// ---------- 토큰 저장 ----------

type StoredTokens = Pick<Tokens, 'accessToken' | 'refreshToken'>

function readTokens(): StoredTokens | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as StoredTokens) : null
  } catch {
    return null
  }
}

let tokens: StoredTokens | null = readTokens()
const sessionListeners = new Set<(loggedIn: boolean) => void>()

export function getAccessToken() {
  return tokens?.accessToken ?? null
}

export function getRefreshToken() {
  return tokens?.refreshToken ?? null
}

export function setTokens(next: StoredTokens | null) {
  tokens = next
  try {
    if (next) localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // 저장소를 쓸 수 없는 환경(시크릿 모드 등)에서는 메모리에만 유지
  }
  sessionListeners.forEach((listener) => listener(next !== null))
}

/** 로그인/로그아웃(토큰 만료로 인한 강제 로그아웃 포함)을 구독한다. */
export function onSessionChange(listener: (loggedIn: boolean) => void) {
  sessionListeners.add(listener)
  return () => {
    sessionListeners.delete(listener)
  }
}

// ---------- 토큰 재발급 ----------

let refreshing: Promise<boolean> | null = null

/**
 * 여러 요청이 동시에 401을 받아도 재발급은 한 번만 한다.
 * 서버의 리프레시 토큰은 1회용(Rotation)이라 두 번 요청하면 두 번째가 실패하기 때문이다.
 */
export function refreshTokens(): Promise<boolean> {
  if (!refreshing) {
    refreshing = (async () => {
      const refreshToken = getRefreshToken()
      if (!refreshToken) return false
      try {
        const res = await fetch(`${API_BASE}/api/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        })
        if (!res.ok) return false
        const next = (await res.json()) as Tokens
        setTokens({ accessToken: next.accessToken, refreshToken: next.refreshToken })
        return true
      } catch {
        return false
      }
    })().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

// ---------- 요청 ----------

async function request<T>(method: string, path: string, body?: unknown, retry = true): Promise<T> {
  const accessToken = getAccessToken()
  const isForm = body instanceof FormData // 파일 업로드는 브라우저가 boundary를 포함한 Content-Type을 직접 붙인다
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers: {
      ...(body !== undefined && !isForm && { 'Content-Type': 'application/json' }),
      ...(accessToken && { Authorization: `Bearer ${accessToken}` }),
    },
    body: isForm ? body : body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (res.status === 401 && retry && !path.startsWith('/api/auth/')) {
    if (await refreshTokens()) return request<T>(method, path, body, false)
    setTokens(null) // 재발급도 실패하면 로그아웃
  }

  if (!res.ok) {
    const error = (await res.json().catch(() => null)) as ApiErrorBody | null
    throw new ApiError(error ?? { status: res.status, code: 'NETWORK_ERROR', message: '요청을 처리하지 못했어요.' })
  }
  // 204뿐 아니라 202처럼 본문 없이 성공하는 응답도 있으므로 상태 코드가 아니라 본문으로 판단한다
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body: unknown) => request<T>('PATCH', path, body),
  delete: <T>(path: string) => request<T>('DELETE', path),
  upload: <T>(path: string, file: Blob, filename = 'photo.jpg') => {
    const form = new FormData()
    form.append('file', file, filename)
    return request<T>('POST', path, form)
  },
}

/** 서버가 준 파일 경로(/files/...)를 실제 주소로 바꾼다. 외부 저장소(S3) URL은 그대로 둔다 */
export function fileUrl(url: string | null | undefined) {
  if (!url) return null
  return url.startsWith('/') ? `${API_BASE}${url}` : url
}

export function errorMessage(error: unknown) {
  if (error instanceof ApiError) return error.message
  return '네트워크 오류가 발생했어요. 잠시 후 다시 시도해 주세요.'
}
