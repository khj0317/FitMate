import { defineConfig, devices } from '@playwright/test'

/**
 * 브라우저로 핵심 흐름(가입·실시간 채팅·커뮤니티)을 끝까지 확인하는 E2E 테스트.
 * 백엔드(데모 데이터 켜짐)와 Mailpit이 떠 있어야 한다. 로컬: npm run dev 후 npm run test:e2e
 * 이미 떠 있는 웹 주소를 쓰려면 E2E_BASE_URL=http://localhost:5175 처럼 지정한다.
 */
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:5173'

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // 같은 데모 계정을 쓰는 테스트끼리 섞이지 않게 하나씩 실행한다
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL,
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : { command: 'npm run dev', url: baseURL, reuseExistingServer: true, timeout: 60_000 },
})
