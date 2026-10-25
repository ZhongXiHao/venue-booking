import { describe, it, expect } from 'vitest'

import { mount, RouterLinkStub } from '@vue/test-utils'
import App from '../App.vue'

describe('App', () => {
  it('renders the title and the main navigation', () => {
    const wrapper = mount(App, {
      global: {
        stubs: { RouterLink: RouterLinkStub, RouterView: true },
      },
    })

    expect(wrapper.find('h1').text()).toBe('场地预约系统')

    const links = wrapper
      .findAllComponents(RouterLinkStub)
      .map((link) => link.props('to'))

    expect(links).toEqual([
      '/venues',
      '/reservations',
      '/reservations/new',
      '/venues/new',
    ])
  })
})
