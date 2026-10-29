import { test, expect } from '@playwright/test'

// 部署冒烟：断言能区分 Vue 应用与旧静态页（frontend/public/index.html 已迁出）。
// 旧页面只有场地列表表格，没有 #app，也没有导航链接。
test('renders the Vue app shell', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('#app')).toBeAttached()
  await expect(page.locator('h1')).toHaveText('场地预约系统')

  const nav = page.locator('nav')
  await expect(nav.getByRole('link', { name: '场地列表' })).toBeAttached()
  await expect(nav.getByRole('link', { name: '预约记录' })).toBeAttached()
  await expect(nav.getByRole('link', { name: '创建预约' })).toBeAttached()
  await expect(nav.getByRole('link', { name: '创建场地' })).toBeAttached()
})

// 深链直达：SPA 回退（Vite dev 或 nginx try_files）必须让深链直接打开而不 404
test('loads deep links without server 404', async ({ page }) => {
  await page.goto('/reservations/new')
  await expect(page.locator('#app')).toBeAttached()
  await expect(page.locator('nav').getByRole('link', { name: '创建预约' })).toBeAttached()
  await expect(page).toHaveURL(/\/reservations\/new$/)
})