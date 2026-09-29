// FitMate 부하 테스트: 로그인한 사용자들이 동시에 주요 화면을 둘러보는 상황
// 실행 (로컬 서버 + 데모 데이터 필요):
//   docker run --rm -i -e BASE_URL=http://host.docker.internal:8081 grafana/k6 run - < scripts/k6/browse.js
// 쉬는 시간 없이 최대 처리량을 재려면 -e MODE=stress (100명이 30초 동안 계속 요청)
import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081'
const USERS = 30 // 데모 계정 demo01 ~ demo30
const STRESS = __ENV.MODE === 'stress'

export const options = {
  scenarios: STRESS
    ? { stress: { executor: 'constant-vus', vus: 100, duration: '30s' } }
    : {
        browse: {
          executor: 'ramping-vus',
          startVUs: 0,
          stages: [
            { duration: '20s', target: 50 }, // 50명까지 늘리고
            { duration: '40s', target: 50 }, // 40초 유지
            { duration: '10s', target: 0 },
          ],
        },
      },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{screen:recommendations}': ['p(95)<500'],
    'http_req_duration{screen:feed}': ['p(95)<300'],
    'http_req_duration{screen:gatherings}': ['p(95)<300'],
    'http_req_duration{screen:chatRooms}': ['p(95)<300'],
  },
}

/** 테스트 전에 데모 계정 30개로 로그인해서 토큰을 나눠 쓴다 */
export function setup() {
  const tokens = []
  for (let i = 1; i <= USERS; i++) {
    const loginId = `demo${String(i).padStart(2, '0')}`
    const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ loginId, password: 'password123' }), {
      headers: { 'Content-Type': 'application/json' },
    })
    if (res.status === 200) tokens.push(res.json('accessToken'))
  }
  if (tokens.length === 0) throw new Error('로그인 실패: 서버와 데모 데이터를 확인하세요')
  return { tokens }
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length]
  const params = (screen) => ({ headers: { Authorization: `Bearer ${token}` }, tags: { screen } })

  const screens = [
    ['recommendations', '/api/matching/recommendations?limit=30'],
    ['feed', '/api/posts?scope=ALL'],
    ['gatherings', '/api/gatherings?radiusKm=20'],
    ['chatRooms', '/api/chat-rooms'],
    ['notifications', '/api/notifications'],
  ]
  for (const [screen, path] of screens) {
    const res = http.get(`${BASE_URL}${path}`, params(screen))
    check(res, { [`${screen} 200`]: (r) => r.status === 200 })
  }
  if (!STRESS) sleep(1) // 사람이 화면을 보는 시간
}
