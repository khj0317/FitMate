import { expect, test } from '@playwright/test'
import { loginAs } from './helpers'

test('글을 쓰면 다른 사람이 댓글·좋아요를 남길 수 있고, 글쓴이에게 실시간 알림이 간다', async ({ browser }) => {
  const author = await loginAs(browser, 'demo01')
  const reader = await loginAs(browser, 'demo03')
  const content = `E2E 운동 인증 ${Date.now()}`

  await author.goto('/community')
  await author.getByRole('button', { name: '글쓰기' }).click()
  await author.getByLabel('내용').fill(content)
  await author.getByRole('button', { name: '올리기' }).click()
  await expect(author).toHaveURL(/\/community\/\d+/)
  await expect(author.getByText(content)).toBeVisible()
  const postUrl = new URL(author.url()).pathname

  // 다른 사용자: 피드에서 글을 찾고 좋아요·댓글
  await reader.goto('/community')
  await reader.getByRole('button', { name: '전체', exact: true }).click()
  await reader.locator('a[href="' + postUrl + '"]').click()
  await reader.getByRole('button', { name: '좋아요' }).click()
  await expect(reader.getByRole('button', { name: '좋아요 취소' })).toContainText('1')

  const comment = `E2E 댓글 ${Date.now()}`
  await reader.getByLabel('댓글', { exact: true }).fill(comment)
  await reader.getByRole('button', { name: '댓글 보내기' }).click()
  await expect(reader.getByText(comment)).toBeVisible()

  // 글쓴이: 새로고침 없이 알림 토스트가 뜨고, 알림 목록에서 눌러 글로 이동한다
  await expect(author.getByText('데모_03님이 내 글에 댓글을 남겼어요').first()).toBeVisible()
  await author.getByRole('button', { name: /^알림/ }).first().click()
  await author.getByRole('button', { name: /데모_03님이 내 글에 댓글을 남겼어요/ }).first().click()
  await expect(author.getByText(comment)).toBeVisible()
})
