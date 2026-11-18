import { expect, test, type Page } from '@playwright/test'

test.use({ viewport: { width: 320, height: 600 }, hasTouch: true })

async function resizeVisualViewport(page: Page, height: number, offsetTop: number, scale = 1) {
  await page.evaluate(
    ({ height, offsetTop, scale }) => {
      const viewport = window.visualViewport!
      Object.defineProperties(viewport, {
        height: { configurable: true, value: height },
        offsetTop: { configurable: true, value: offsetTop },
        scale: { configurable: true, value: scale },
      })
      viewport.dispatchEvent(new Event('resize'))
    },
    { height, offsetTop, scale },
  )
}

test('keeps the footer in view during visual-only keyboard resizes', async ({ page }) => {
  await page.goto('/')
  const editor = page.getByRole('textbox', { name: '命令输入' })
  const footer = page.getByRole('contentinfo')
  const banner = page.getByRole('banner')
  const footerBottom = () => footer.evaluate((element) => element.getBoundingClientRect().bottom)
  const bannerTop = () => banner.evaluate((element) => element.getBoundingClientRect().top)

  await editor.focus()
  await expect.poll(footerBottom).toBe(592)

  await resizeVisualViewport(page, 420, 40)
  await expect.poll(footerBottom).toBe(452)
  await expect.poll(bannerTop).toBe(48)

  await resizeVisualViewport(page, 340, 0)
  await expect.poll(footerBottom).toBe(332)
  await expect.poll(bannerTop).toBe(8)

  await resizeVisualViewport(page, 600, 0)
  await expect.poll(footerBottom).toBe(592)
  await expect(editor).toBeFocused()

  await resizeVisualViewport(page, 300, 80, 2)
  await expect.poll(footerBottom).toBe(592)
  await expect.poll(bannerTop).toBe(8)
})

test('scrolls completions without scrolling the document in a short viewport', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 280 })
  await page.goto('/')
  const editor = page.getByRole('textbox', { name: '命令输入' })
  const completions = page.getByRole('listbox', { name: '命令补全' })
  const footer = page.getByRole('contentinfo')

  await completions.hover()
  await page.mouse.wheel(0, 500)
  await expect.poll(() => completions.evaluate((element) => element.scrollTop)).toBeGreaterThan(0)
  await expect(footer).toBeInViewport({ ratio: 1 })

  // A wrapped command error competes with the footer for space.
  await editor.fill('give @e[')
  await expect(page.getByRole('alert')).toBeVisible()
  await expect
    .poll(() =>
      page.evaluate(() => {
        window.scrollTo(0, 100)
        const { clientHeight, scrollHeight } = document.documentElement
        return { overflow: scrollHeight - clientHeight, scroll: window.scrollY }
      }),
    )
    .toEqual({ overflow: 0, scroll: 0 })
  await expect(footer).toBeInViewport({ ratio: 1 })
})
