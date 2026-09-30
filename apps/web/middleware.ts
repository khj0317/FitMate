import { ipAddress, next } from '@vercel/functions'

/**
 * Vercel 라우팅 미들웨어 (배포에서만 실행, 로컬 Vite 개발 서버에서는 실행되지 않음).
 * /api 요청은 vercel.json의 rewrite로 API 서버에 전달되는데, 그러면 API가 보는 접속 주소가 Vercel 서버라
 * 모든 사용자가 한 IP로 묶인다. 그래서 사용자 IP를 헤더에 담고, 누구나 위조할 수 있는 헤더와 구별되도록
 * API와 나눠 가진 비밀값(PROXY_SECRET)을 함께 보낸다. API는 비밀값이 맞을 때만 이 IP를 믿는다.
 *
 * IP별 요청 제한이 걸린 경로에만 실행한다. 미들웨어를 거치는 요청은 본문이 4MB로 제한되므로
 * 사진 업로드 같은 요청은 거치지 않게 한다.
 */
export const config = {
  matcher: ['/api/auth/:path*', '/api/locations/:path*'],
}

export default function middleware(request: Request) {
  const secret = process.env.PROXY_SECRET
  if (!secret) return next()

  const headers = new Headers(request.headers)
  // 사용자가 직접 보낸 같은 이름의 헤더는 덮어쓴다
  headers.set('x-fitmate-proxy-secret', secret)
  headers.set('x-fitmate-client-ip', ipAddress(request) ?? '')
  return next({ request: { headers } })
}
