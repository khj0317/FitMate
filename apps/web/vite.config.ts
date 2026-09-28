import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, type ProxyOptions } from 'vite'

// 개발 중에는 /api, /ws 요청을 백엔드로 프록시해서 CORS 설정 없이 같은 주소처럼 쓴다.
// 백엔드 포트가 다르면 BACKEND_URL=http://localhost:8082 npm run dev 처럼 바꿀 수 있다.
const backend = process.env.BACKEND_URL ?? 'http://localhost:8081'

// 브라우저 입장에서는 같은 출처 요청이므로 Origin 헤더를 지워 백엔드 CORS 검사를 피한다.
// (지우지 않으면 개발 서버 포트가 CORS 허용 목록에 없을 때 403이 난다)
const stripOrigin: ProxyOptions['configure'] = (proxy) => {
  proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
  proxy.on('proxyReqWs', (proxyReq) => proxyReq.removeHeader('origin'))
}

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: backend, configure: stripOrigin },
      '/ws': { target: backend.replace(/^http/, 'ws'), ws: true, configure: stripOrigin },
    },
  },
})
