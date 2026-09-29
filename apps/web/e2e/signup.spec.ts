import { expect, test } from '@playwright/test'
import { readVerificationCode, uniqueId } from './helpers'

test('이메일 인증을 마쳐야 가입할 수 있고, 가입하면 프로필 설정 화면으로 간다', async ({ page, request }) => {
  const loginId = uniqueId('e2e')
  const email = `${loginId}@example.com`

  await page.goto('/signup')
  await page.getByPlaceholder('fitmate_runner').fill(loginId)
  await page.getByPlaceholder('비밀번호', { exact: true }).fill('password123')
  await page.getByPlaceholder('비밀번호를 한 번 더 입력').fill('password123')
  await page.getByPlaceholder('운동하는_곰').fill(`테스터_${loginId.slice(-5)}`)
  await page.getByPlaceholder('예) 19980520').fill('19980520')
  await page.getByRole('button', { name: '남성' }).click()
  await page.getByPlaceholder('동 이름이나 역 이름으로 검색 (예: 성수, 강남역)').fill('성수')
  await page.getByRole('option').first().click()

  // 이메일을 인증하지 않으면 가입 버튼을 눌러도 안내가 뜬다
  await page.getByRole('textbox', { name: '이메일' }).fill(email)
  await page.getByRole('button', { name: '가입하고 시작하기' }).click()
  await expect(page.getByText('이메일 인증을 완료해 주세요')).toBeVisible()

  await page.getByRole('button', { name: '인증번호 받기' }).click()
  const code = await readVerificationCode(request, email)
  await page.getByLabel('인증 코드').fill(code)
  await page.getByRole('button', { name: '확인', exact: true }).click()
  await expect(page.getByText('인증 완료')).toBeVisible()

  await page.getByRole('button', { name: '가입하고 시작하기' }).click()
  await expect(page).toHaveURL(/\/profile\?welcome=1/)
  await expect(page.getByRole('textbox', { name: '이메일' })).toHaveValue(email)
})

test('없는 계정이나 틀린 비밀번호로는 로그인할 수 없다', async ({ page }) => {
  await page.goto('/login')
  // 데모 계정으로 틀리면 5번째에 잠기므로(로그인 시도 제한) 없는 아이디로 확인한다. 응답 메시지는 같다
  await page.getByPlaceholder('아이디').fill('no_such_user_e2e')
  await page.getByPlaceholder('비밀번호').fill('wrong-password-1')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByText('아이디 또는 비밀번호가 올바르지 않습니다')).toBeVisible()
  await expect(page).toHaveURL(/\/login/)
})
