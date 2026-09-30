import { expect, test } from '@playwright/test'

test('체험 계정으로 가입 없이 바로 둘러볼 수 있고, 준비된 채팅과 받은 요청이 있다', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: '체험 계정으로 둘러보기' }).click()

  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByText('체험 계정으로 둘러보는 중이에요')).toBeVisible()

  // 데모 사용자와 이야기 중인 채팅방 (사진 보내기는 체험 계정에서 숨김)
  await page.goto('/chats')
  await page.getByText('초록고래').first().click()
  await expect(page.getByText('이번 주 수요일 저녁 7시에 성수역에서 같이 운동할래요?')).toBeVisible()
  await expect(page.getByRole('button', { name: '사진 보내기' })).toHaveCount(0)

  // 받은 매칭 요청을 수락하면 채팅방이 하나 더 생긴다
  await page.goto('/requests')
  await expect(page.getByText('주말 아침 헬스 같이 하실래요?')).toBeVisible()
  await page.getByRole('button', { name: '수락하기' }).click()
  await expect(page).toHaveURL(/\/chats\/\d+/)
})
