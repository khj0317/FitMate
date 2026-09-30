import type { ApiErrorBody, Tokens } from './types'

// REST 요청은 항상 같은 주소(/api)로 보낸다. 개발 중에는 Vite 프록시, 배포에서는 vercel.json의 rewrite가
// 백엔드로 전달한다. 같은 사이트로 보여야 리프레시 토큰 쿠키가 서드파티 쿠키로 막히지 않는다(Safari 등).

/** WebSocket은 프록시를 거치지 않고 백엔드에 바로 연결한다 (배포: VITE_API_URL, 개발: Vite 프록시) */
export const SOCKET_BASE = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? ''

/** 이 헤더를 보내면 서버가 리프레시 토큰을 HttpOnly 쿠키로 주고받는다 */
const AUTH_MODE_HEADER = { 'X-Auth-Mode': 'cookie' }

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

// ---------- 토큰 ----------

/**
 * 액세스 토큰(30분)은 메모리에만 둔다. 리프레시 토큰(14일)은 JavaScript가 읽을 수 없는 HttpOnly 쿠키라
 * 화면에 악성 스크립트가 끼어들어도(XSS) 훔쳐 갈 수 없다. 새로고침하면 쿠키로 액세스 토큰을 다시 받는다.
 */
let accessToken: string | null = null
const sessionListeners = new Set<(loggedIn: boolean) => void>()

export function getAccessToken() {
  return accessToken
}

/** 로그인하면 액세스 토큰을 넣고, 로그아웃하면 null */
export function setAccessToken(next: string | null) {
  accessToken = next
  sessionListeners.forEach((listener) => listener(next !== null))
}

/** 로그인/로그아웃(토큰 만료로 인한 강제 로그아웃 포함)을 구독한다. */
export function onSessionChange(listener: (loggedIn: boolean) => void) {
  sessionListeners.add(listener)
  return () => {
    sessionListeners.delete(listener)
  }
}

// ---------- 느린 응답 감지 (무료 서버가 잠들었다 깨어나는 중) ----------

/** 이 시간 안에 응답이 없으면 "서버를 깨우는 중" 안내를 띄운다 */
const SLOW_REQUEST_MS = 4000
let slowRequests = 0
const slowListeners = new Set<() => void>()

/** useSyncExternalStore용: 지금 늦어지는 요청이 있는지 */
export const slowRequestStore = {
  subscribe(listener: () => void) {
    slowListeners.add(listener)
    return () => {
      slowListeners.delete(listener)
    }
  },
  isSlow: () => slowRequests > 0,
}

function changeSlowRequests(delta: number) {
  slowRequests = Math.max(0, slowRequests + delta)
  slowListeners.forEach((listener) => listener())
}

/** fetch가 오래 걸리면 "서버를 깨우는 중" 안내에 반영한다 */
async function trackedFetch(input: string, init: RequestInit, trackSlow = true): Promise<Response> {
  let slow = false
  const slowTimer = trackSlow
    ? setTimeout(() => {
        slow = true
        changeSlowRequests(1)
      }, SLOW_REQUEST_MS)
    : undefined
  try {
    return await fetch(input, init)
  } finally {
    clearTimeout(slowTimer)
    if (slow) changeSlowRequests(-1)
  }
}

// ---------- 토큰 재발급 ----------

let refreshing: Promise<boolean> | null = null

function refreshOnce(): Promise<Response> {
  return trackedFetch('/api/auth/refresh', { method: 'POST', headers: AUTH_MODE_HEADER })
}

/**
 * 쿠키의 리프레시 토큰으로 액세스 토큰을 새로 받는다. 앱을 처음 열 때(로그인 상태 복원)와 401을 받았을 때 쓴다.
 * - 여러 요청이 동시에 401을 받아도 재발급은 한 번만 한다 (리프레시 토큰은 1회용이라 두 번째 요청은 실패)
 * - 탭 두 개가 동시에 재발급하면 한쪽은 이미 교체된 쿠키를 보내 실패한다. 그 사이 다른 탭의 응답으로
 *   브라우저 쿠키가 새것으로 바뀌므로 잠시 뒤 한 번 더 시도한다
 */
export function refreshTokens(): Promise<boolean> {
  if (!refreshing) {
    refreshing = (async () => {
      try {
        let res = await refreshOnce()
        if (res.status === 401) {
          await new Promise((resolve) => setTimeout(resolve, 400))
          res = await refreshOnce()
        }
        if (!res.ok) return false
        const next = (await res.json()) as Tokens
        setAccessToken(next.accessToken)
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
  const token = getAccessToken()
  const isForm = body instanceof FormData // 파일 업로드는 브라우저가 boundary를 포함한 Content-Type을 직접 붙인다
  const res = await trackedFetch(
    path,
    {
      method,
      headers: {
        ...AUTH_MODE_HEADER,
        ...(body !== undefined && !isForm && { 'Content-Type': 'application/json' }),
        ...(token && { Authorization: `Bearer ${token}` }),
      },
      body: isForm ? body : body !== undefined ? JSON.stringify(body) : undefined,
    },
    // 사진 업로드는 원래 몇 초 걸릴 수 있으므로 서버가 깨어나는 중인지 판단하는 데서 뺀다
    !isForm,
  )

  if (res.status === 401 && retry && !path.startsWith('/api/auth/')) {
    if (await refreshTokens()) return request<T>(method, path, body, false)
    setAccessToken(null) // 재발급도 실패하면 로그아웃
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
  /** 여러 파트(JSON + 파일)를 한 번에 보내는 multipart 요청 */
  form: <T>(path: string, form: FormData) => request<T>('POST', path, form),
  upload: <T>(path: string, file: Blob, filename = 'photo.jpg') => {
    const form = new FormData()
    form.append('file', file, filename)
    return request<T>('POST', path, form)
  },
}

export function errorMessage(error: unknown) {
  if (error instanceof ApiError) return error.message
  return '네트워크 오류가 발생했어요. 잠시 후 다시 시도해 주세요.'
}
