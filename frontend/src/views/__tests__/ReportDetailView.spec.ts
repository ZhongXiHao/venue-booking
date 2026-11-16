import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ReportDetailView from '../ReportDetailView.vue'
import type { ReportDetail } from '../../types/report'

const base: ReportDetail = {
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
  results: [],
}
function json(data: unknown, status = 200, code = 'SUCCESS') {
  return new Response(JSON.stringify({ code, message: code, data }), { status })
}
let wrapper: VueWrapper | undefined
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  vi.useRealTimers()
  vi.unstubAllGlobals()
})
async function setup() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/reports/:id', component: ReportDetailView },
      { path: '/reports', component: { template: '<div />' } },
    ],
  })
  await router.push('/reports/12')
  wrapper = mount(ReportDetailView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('report detail polling', () => {
  it('polls every two seconds and stops on success, preserving zero-result venues', async () => {
    vi.useFakeTimers()
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json(base))
      .mockResolvedValueOnce(json({ ...base, status: 'RUNNING', attempt: 1 }))
      .mockResolvedValueOnce(
        json({
          ...base,
          status: 'SUCCESS',
          results: [
            { venueId: '1', venueName: '第一会议室', reservationCount: 6, totalMinutes: 540 },
            { venueId: '5', venueName: '旧礼堂', reservationCount: 0, totalMinutes: 0 },
          ],
        }),
      )
    vi.stubGlobal('fetch', fetchMock)
    const view = await setup()
    expect(view.text()).toContain('处理中')
    await vi.advanceTimersByTimeAsync(1999)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()
    expect(view.text()).toContain('成功')
    expect(view.text()).toContain('旧礼堂')
    expect(view.text()).toContain('540')
    expect(view.text()).toContain('9.00')
    expect(view.text()).toContain('16:30:00')
    await vi.advanceTimersByTimeAsync(10000)
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('stops on failure, shows its reason, and hides any stale results', async () => {
    vi.useFakeTimers()
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      json({
        ...base,
        status: 'FAILED',
        errorMessage: '数据库连接失败',
        results: [{ venueId: '1', venueName: '不应展示', reservationCount: 1, totalMinutes: 30 }],
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const view = await setup()
    expect(view.text()).toContain('数据库连接失败')
    expect(view.text()).not.toContain('不应展示')
    await vi.advanceTimersByTimeAsync(10000)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('recovers from a network error without marking the job failed', async () => {
    vi.useFakeTimers()
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json(base))
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValueOnce(json({ ...base, status: 'SUCCESS' }))
    vi.stubGlobal('fetch', fetchMock)
    const view = await setup()
    await vi.advanceTimersByTimeAsync(2000)
    expect(view.text()).toContain('处理中')
    expect(view.text()).toContain('无法连接服务')
    await vi.advanceTimersByTimeAsync(2000)
    expect(view.text()).toContain('成功')
    expect(view.text()).not.toContain('无法连接服务')
  })

  it('stops on 404', async () => {
    vi.useFakeTimers()
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(json(null, 404, 'REPORT_NOT_FOUND'))
    vi.stubGlobal('fetch', fetchMock)
    await setup()
    await vi.advanceTimersByTimeAsync(10000)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('aborts an in-flight query on unmount and does not restart polling', async () => {
    vi.useFakeTimers()
    let finish: (value: Response) => void = () => {}
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const view = await setup()
    const signal = fetchMock.mock.calls[0]?.[1]?.signal
    view.unmount()
    wrapper = undefined
    expect(signal?.aborted).toBe(true)
    finish(json(base))
    await flushPromises()
    await vi.advanceTimersByTimeAsync(10000)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
