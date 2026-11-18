import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

// 内核加载失败是 App 自己处理的 UI 状态，mock 掉真实的 wasm 加载
vi.mock('@/core/CPackManager', () => ({
  DEFAULT_BRANCH: 'release-experiment',
  ALL_BRANCH: ['release-experiment'],
  ALL_BRANCH_CHINESE: ['正式版-实验性玩法-1.21.132.1'],
  getCore: vi.fn(async () => {
    throw new Error('模拟内核加载失败')
  }),
}))

import App from './App.vue'

describe('App 内核加载失败提示', () => {
  it('初始加载失败时显示错误提示与重试按钮，不再停留在加载中', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.text()).toContain('CHelper加载失败')
    const errorBox = wrapper.get('[role="alert"]')
    expect(errorBox.text()).toContain('CHelper 内核加载失败')
    expect(errorBox.text()).toContain('模拟内核加载失败')
    expect(errorBox.get('button').text()).toBe('重试')

    // 点击重试后加载再次失败，错误提示继续显示
    await errorBox.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('模拟内核加载失败')

    wrapper.unmount()
  })
})
