import { afterEach, describe, expect, it, vi, type Mock } from 'vitest'
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'

import ReservationListView from '../ReservationListView.vue'

const reservation = {
  id: '101',
  venueId: '1',
  venueName: '第一会议室',
  bookerName: '学习小组',
  reservationDate: '2099-01-15',
  startTime: '14:00:00',
  endTime: '15:00:00',
  purpose: '课程讨论',
  status: 'ACTIVE',
}

function json(data: unknown, status = 200) {
  return new Response(
    JSON.stringify({ code: 'SUCCESS', message: 'ok', data }),
    { status },
  )
}

function page(items: unknown[]) {
  return json({ items, total: items.length, page: 1, pageSize: 10 })
}

afterEach(() => {
  vi.unstubAllGlobals()
})

async function mountList(fetchMock: Mock<typeof fetch>) {
  vi.stubGlobal('fetch', fetchMock)
  const wrapper = mount(ReservationListView, {
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
  await flushPromises()
  return wrapper
}

function buttonByText(wrapper: Awaited<ReturnType<typeof mountList>>, text: string) {
  const button = wrapper.findAll('button').find((b) => b.text() === text)
  if (!button) throw new Error(`button not found: ${text}`)
  return button
}

describe('ReservationListView', () => {
  it('sends the date filter as reservationDate', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(() => Promise.resolve(page([])))
    const wrapper = await mountList(fetchMock)

    await wrapper.get('#filter-date').setValue('2099-01-15')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const calls = fetchMock.mock.calls
    const url = String(calls[calls.length - 1]?.[0])
    expect(url).toContain('reservationDate=2099-01-15')
    expect(url).not.toMatch(/[?&]date=/)
  })

  it('asks for confirmation in the page before cancelling', async () => {
    const confirmSpy = vi.fn<typeof confirm>()
    vi.stubGlobal('confirm', confirmSpy)

    const fetchMock = vi.fn<typeof fetch>().mockImplementation((_url, init) => {
      if (init?.method === 'POST') {
        return Promise.resolve(json({ ...reservation, status: 'CANCELLED' }))
      }
      return Promise.resolve(page([reservation]))
    })
    const wrapper = await mountList(fetchMock)

    await buttonByText(wrapper, '取消预约').trigger('click')

    // 只进入确认状态：没有原生弹窗，也没有发出取消请求
    expect(wrapper.text()).toContain('确定取消？')
    expect(confirmSpy).not.toHaveBeenCalled()
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)

    await buttonByText(wrapper, '确认取消').trigger('click')
    await flushPromises()

    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')
    expect(post?.[0]).toBe('/api/reservations/101/cancel')
    expect(wrapper.text()).toContain('预约 101 已取消')
  })

  it('goes back without cancelling', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(() => Promise.resolve(page([reservation])))
    const wrapper = await mountList(fetchMock)

    await buttonByText(wrapper, '取消预约').trigger('click')
    await buttonByText(wrapper, '返回').trigger('click')

    expect(wrapper.text()).not.toContain('确定取消？')
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
  })
})
