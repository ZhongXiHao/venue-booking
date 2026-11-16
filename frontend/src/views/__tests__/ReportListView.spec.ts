import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ReportListView from '../ReportListView.vue'

function json(data: unknown, status = 200, code = 'SUCCESS') {
  return new Response(JSON.stringify({ code, message: code, data }), { status })
}
const emptyPage = { items: [], total: 0, page: 1, pageSize: 10 }
afterEach(() => vi.unstubAllGlobals())
async function setup() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/reports', component: ReportListView },
      { path: '/reports/:id', name: 'report-detail', component: { template: '<div />' } },
    ],
  })
  await router.push('/reports')
  const wrapper = mount(ReportListView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('report submission', () => {
  it('uses the contract body and navigates to the accepted report', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockImplementation((_url, init) =>
        Promise.resolve(
          init?.method === 'POST' ? json({ id: '12', status: 'PENDING' }, 202) : json(emptyPage),
        ),
      )
    vi.stubGlobal('fetch', fetchMock)
    const { wrapper, router } = await setup()
    await wrapper.get('#range-start').setValue('2026-11-01')
    await wrapper.get('#range-end').setValue('2026-11-14')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')
    expect(post?.[0]).toBe('/api/reports')
    expect(JSON.parse(String(post?.[1]?.body))).toEqual({
      rangeStart: '2026-11-01',
      rangeEnd: '2026-11-14',
    })
    expect(router.currentRoute.value.path).toBe('/reports/12')
    wrapper.unmount()
  })

  it('shows backend field errors and refreshes saved failures after a Job creation error', async () => {
    let attempts = 0
    const fetchMock = vi.fn<typeof fetch>().mockImplementation((_url, init) => {
      if (init?.method !== 'POST') return Promise.resolve(json(emptyPage))
      attempts++
      return Promise.resolve(
        attempts === 1
          ? json(
              { fieldErrors: [{ field: 'rangeEnd', message: '测试日期校验错误' }] },
              400,
              'VALIDATION_ERROR',
            )
          : json(null, 503, 'REPORT_JOB_CREATE_FAILED'),
      )
    })
    vi.stubGlobal('fetch', fetchMock)
    const { wrapper } = await setup()
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('测试日期校验错误')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('失败记录已保存')
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method !== 'POST')).toHaveLength(2)
    wrapper.unmount()
  })
})
