import { expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

export const DEMO_PASSWORD = 'password123'
const MAILPIT_URL = process.env.MAILPIT_URL ?? 'http://localhost:8025'

/** 새 브라우저(쿠키·저장소가 분리된 컨텍스트)로 로그인한다. 두 사용자를 동시에 띄울 때 쓴다 */
export async function loginAs(browser: Browser, loginId: string, password = DEMO_PASSWORD): Promise<Page> {
  const context = await browser.newContext()
  const page = await context.newPage()
  await page.goto('/login')
  await page.getByPlaceholder('아이디').fill(loginId)
  await page.getByPlaceholder('비밀번호').fill(password)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).not.toHaveURL(/\/login/)
  return page
}

/** Mailpit(개발용 메일 서버)에 도착한 인증 메일에서 6자리 코드를 꺼낸다 */
export async function readVerificationCode(request: APIRequestContext, email: string): Promise<string> {
  let code: string | undefined
  await expect
    .poll(
      async () => {
        const response = await request.get(`${MAILPIT_URL}/api/v1/search`, { params: { query: `to:"${email}"` } })
        const body = (await response.json()) as { messages: { Snippet: string }[] }
        code = body.messages[0]?.Snippet.match(/\b(\d{6})\b/)?.[1]
        return code
      },
      { message: `${email}로 인증 메일이 와야 한다`, timeout: 15_000 },
    )
    .toBeTruthy()
  return code!
}

export function uniqueId(prefix: string) {
  return `${prefix}${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`
}
