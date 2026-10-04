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

class ResizeObserverStub {
  observe(): void {}

  unobserve(): void {}

  disconnect(): void {}
}

describe('App 内核加载失败提示', () => {
  it('初始加载失败时显示错误提示与重试按钮，不再停留在加载中', async () => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub)
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.get('.text-structure').text()).toBe('CHelper加载失败')
    const errorBox = wrapper.get('.text-error-reason')
    expect(errorBox.text()).toContain('CHelper 内核加载失败')
    expect(errorBox.text()).toContain('模拟内核加载失败')
    expect(errorBox.get('.retry-button').text()).toBe('重试')

    // 点击重试后加载再次失败，错误提示继续显示
    await wrapper.get('.retry-button').trigger('click')
    await flushPromises()
    expect(wrapper.get('.text-error-reason').text()).toContain('模拟内核加载失败')

    wrapper.unmount()
    vi.unstubAllGlobals()
  })
})
