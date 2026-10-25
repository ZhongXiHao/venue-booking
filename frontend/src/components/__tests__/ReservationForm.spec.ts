import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'

import ReservationForm from '../ReservationForm.vue'
import type { Venue } from '../../api/venues'

const venue: Venue = {
  id: '1',
  name: '第一会议室',
  location: '行政楼 3 楼 301',
  capacity: 20,
  openTime: '08:00:00',
  closeTime: '22:00:00',
  status: 'OPEN',
}

function mountForm() {
  return mount(ReservationForm, {
    props: { venue },
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
}

async function fill(
  wrapper: ReturnType<typeof mountForm>,
  startTime: string,
  endTime: string,
) {
  await wrapper.get('#bookerName').setValue('学习小组')
  await wrapper.get('#reservationDate').setValue('2099-01-15')
  await wrapper.get('#startTime').setValue(startTime)
  await wrapper.get('#endTime').setValue(endTime)
  await wrapper.get('#purpose').setValue('课程讨论')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('ReservationForm', () => {
  it('uses a 30-minute step for the time inputs', () => {
    const wrapper = mountForm()

    expect(wrapper.get('#startTime').attributes('step')).toBe('1800')
    expect(wrapper.get('#endTime').attributes('step')).toBe('1800')
  })

  it('rejects times that are not on a 30-minute mark without calling the API', async () => {
    const fetchMock = vi.fn<typeof fetch>()
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mountForm()
    await fill(wrapper, '14:15', '15:00')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('[role="alert"]').text()).toContain('30 分钟整点')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('shows API fieldErrors under the matching inputs', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockResolvedValue(
        new Response(
          JSON.stringify({
            code: 'VALIDATION_ERROR',
            message: '请求参数校验失败',
            data: {
              fieldErrors: [
                { field: 'purpose', message: '使用目的不能为空' },
                { field: 'venueId', message: '场地不能为空' },
              ],
            },
          }),
          { status: 400 },
        ),
      ),
    )

    const wrapper = mountForm()
    await fill(wrapper, '14:00', '15:00')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('#error-purpose').text()).toBe('使用目的不能为空')
    expect(wrapper.get('#purpose').attributes('aria-invalid')).toBe('true')
    // 没有对应输入框的字段并入总提示
    expect(wrapper.get('[role="alert"]').text()).toContain('venueId：场地不能为空')
  })
})
