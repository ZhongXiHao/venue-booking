import { test, expect } from '@playwright/test'

// 模拟 API 仅验证前端交互，不作为后端或 Kubernetes 实验成功的证据。
const report = {
  id: '12',
  triggerType: 'MANUAL',
  rangeStart: '2026-11-01',
  rangeEnd: '2026-11-14',
  status: 'PENDING',
  jobName: 'report-12',
  attempt: 0,
  errorMessage: null,
  createdAt: '2026-11-15T08:30:00Z',
  startedAt: null,
  finishedAt: null,
}

test('mock API: submit, poll, display results, reload the report deep link', async ({
  page,
}, testInfo) => {
  let reads = 0
  await page.route('**/api/reports**', async (route) => {
    const request = route.request()
    const isDetail = new URL(request.url()).pathname === '/api/reports/12'
    if (request.method() === 'POST') {
      await route.fulfill({
        status: 202,
        json: { code: 'SUCCESS', message: '已创建', data: report },
      })
    } else if (isDetail) {
      reads++
      await route.fulfill({
        json: {
          code: 'SUCCESS',
          message: 'ok',
          data: {
            ...report,
            status: reads === 1 ? 'RUNNING' : 'SUCCESS',
            attempt: 1,
            results:
              reads === 1
                ? []
                : [
                    {
                      venueId: '1',
                      venueName: '第一会议室',
                      reservationCount: 6,
                      totalMinutes: 540,
                    },
                    { venueId: '5', venueName: '旧礼堂', reservationCount: 0, totalMinutes: 0 },
                  ],
          },
        },
      })
    } else {
      await route.fulfill({
        json: {
          code: 'SUCCESS',
          message: 'ok',
          data: { items: [], total: 0, page: 1, pageSize: 10 },
        },
      })
    }
  })
  await page.goto('/reports')
  await page.getByLabel('起始日期').fill('2026-11-01')
  await page.getByLabel('结束日期').fill('2026-11-14')
  const submission = page.waitForRequest(
    (request) => request.method() === 'POST' && new URL(request.url()).pathname === '/api/reports',
  )
  await page.getByRole('button', { name: '生成报告', exact: true }).click()
  expect((await submission).postDataJSON()).toEqual({
    rangeStart: '2026-11-01',
    rangeEnd: '2026-11-14',
  })
  await expect(page).toHaveURL(/\/reports\/12$/)
  await expect(page.locator('.status')).toHaveText('处理中')
  await expect(page.locator('.status')).toHaveText('成功')
  await expect(page.getByRole('row', { name: /第一会议室/ })).toContainText('540')
  await expect(page.getByRole('row', { name: /旧礼堂/ })).toContainText('0.00')
  await page.screenshot({ path: testInfo.outputPath('report-success-mock.png'), fullPage: true })
  await page.reload()
  await expect(page.getByRole('heading', { name: '各场地统计结果' })).toBeVisible()
})

test('mock API: failure reason remains readable on a mobile viewport', async ({
  page,
}, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.route('**/api/reports/12', (route) =>
    route.fulfill({
      json: {
        code: 'SUCCESS',
        message: 'ok',
        data: { ...report, status: 'FAILED', errorMessage: '测试任务无法连接数据库', results: [] },
      },
    }),
  )
  await page.goto('/reports/12')
  await expect(page.locator('.status')).toHaveText('失败')
  await expect(page.getByRole('alert')).toContainText('测试任务无法连接数据库')
  await expect(page.getByRole('heading', { name: '各场地统计结果' })).toHaveCount(0)
  await page.screenshot({
    path: testInfo.outputPath('report-failure-mobile-mock.png'),
    fullPage: true,
  })
})
