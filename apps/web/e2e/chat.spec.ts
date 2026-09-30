import { expect, test } from '@playwright/test'
import { loginAs } from './helpers'

test('1:1 채팅 메시지가 새로고침 없이 상대에게 도착하고, 상대가 읽으면 "1"이 사라진다', async ({ browser }) => {
  // demo01과 demo02는 데모 데이터에서 이미 매칭되어 채팅방이 있다
  const sender = await loginAs(browser, 'demo01')
  const receiver = await loginAs(browser, 'demo02')

  await sender.goto('/chats')
  await sender.locator('a[href^="/chats/"]', { hasText: '초록고래' }).first().click()
  await expect(sender).toHaveURL(/\/chats\/\d+/)

  // 받는 사람은 채팅 목록 화면에 있다가 메시지를 받는다
  await receiver.goto('/chats')
  await expect(receiver.getByText('실시간 연결됨')).toBeVisible()

  const text = `E2E 실시간 메시지 ${Date.now()}`
  await sender.getByPlaceholder('메시지를 입력하세요').fill(text)
  await sender.getByPlaceholder('메시지를 입력하세요').press('Enter')

  // 보낸 쪽: 상대가 아직 안 읽어서 "1"
  const sentRow = sender.locator('div.items-end', { hasText: text }).last()
  await expect(sentRow.getByLabel('1명 안 읽음')).toBeVisible()

  // 받는 쪽: 목록의 마지막 메시지가 바로 바뀌고, 방을 열면 메시지가 보인다
  const room = receiver.locator('a[href^="/chats/"]', { hasText: '달리는판다' }).first()
  await expect(room).toContainText(text)
  await room.click()
  await expect(receiver.getByText(text)).toBeVisible()

  // 받는 쪽이 읽었으므로 보낸 쪽의 "1"이 새로고침 없이 사라진다
  await expect(sentRow.getByLabel('1명 안 읽음')).toHaveCount(0)
})
