import { expect, test, type Page } from '@playwright/test'

test('centers completion icons and restores them on scroll and reload without the wiki', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 640 })
  // The opaque artwork sits in the upper-right of this transparent PNG.
  const png = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAYAAACNMs+9AAAAF0lEQVR4nGNgoBRU6zr/B+EBUMgwnAEAbQkLfVWFzeMAAAAASUVORK5CYII=',
    'base64',
  )
  let wikiUnavailable = false
  await page.route('https://zh.minecraft.wiki/images/*.png', (route) =>
    wikiUnavailable
      ? route.abort()
      : route.fulfill({
          contentType: 'image/png',
          headers: { 'access-control-allow-origin': '*', 'cache-control': 'no-store' },
          body: png,
        }),
  )
  await page.goto('/')
  const editor = page.getByRole('textbox', { name: '命令输入' })
  const list = page.getByRole('listbox', { name: '命令补全' })
  await expect(page.getByText('欢迎使用CHelper')).toBeVisible()
  await editor.fill('give @s ')

  const firstIcon = list.locator('canvas').first()
  const index = await firstIcon.evaluate((icon) =>
    icon.closest('[data-index]')!.getAttribute('data-index'),
  )
  const selector = `[data-index="${index}"] canvas`
  const icon = list.locator(selector)
  const pixel = () =>
    icon.evaluate((canvas: HTMLCanvasElement) => [
      ...canvas.getContext('2d')!.getImageData(28, 28, 1, 1).data,
    ])
  await expect.poll(pixel).toEqual([123, 45, 67, 255])
  const rowAlignment = await icon.evaluate((canvas: HTMLCanvasElement) => {
    const card = canvas.closest('[data-slot="card"]')!
    const centerY = (element: Element) => {
      const { top, height } = element.getBoundingClientRect()
      return top + height / 2
    }
    return [centerY(canvas) - centerY(card), centerY(canvas.nextElementSibling!) - centerY(card)]
  })
  expect(rowAlignment).toEqual([0, 0])

  await list.evaluate((element) => element.scrollTo(0, 6000))
  await expect(icon).toHaveCount(0)
  wikiUnavailable = true
  const restoredPixel = await list.evaluate((element, iconSelector) => {
    return new Promise<number[]>((resolve) => {
      const observer = new MutationObserver(() => {
        const canvas = element.querySelector<HTMLCanvasElement>(iconSelector)
        if (!canvas) return
        observer.disconnect()
        requestAnimationFrame(() => {
          resolve([...canvas.getContext('2d')!.getImageData(28, 28, 1, 1).data])
        })
      })
      observer.observe(element, { childList: true, subtree: true })
      element.scrollTo(0, 0)
    })
  }, selector)
  expect(restoredPixel).toEqual([123, 45, 67, 255])

  await page.reload()
  await expect(page.getByText('欢迎使用CHelper')).toBeVisible()
  await editor.fill('give @s ')
  await expect.poll(pixel).toEqual([123, 45, 67, 255])
})

test('audio suggestions support pause, resume, and seeking without applying the command', async ({
  page,
}) => {
  await page.addInitScript(() => {
    Object.defineProperty(window, 'Audio', {
      value: function Audio() {
        const player = document.createElement('audio')
        Object.defineProperty(player, 'duration', { get: () => 30 })
        const context = window as Window & { testAudio: HTMLAudioElement }
        context.testAudio = player
        return player
      },
    })
    HTMLMediaElement.prototype.play = async () => {}
    HTMLMediaElement.prototype.pause = () => {}
    HTMLMediaElement.prototype.load = () => {}
  })
  await page.goto('/')
  await expect(page.getByText('欢迎使用CHelper')).toBeVisible()
  const editor = page.getByRole('textbox', { name: '命令输入' })
  await editor.click()
  await page.keyboard.insertText('playsound random.levelu')

  const play = page.getByRole('button', { name: '播放试听' }).first()
  const pause = page.getByRole('button', { name: '暂停试听' })
  const buttonStyle = (element: Element) => {
    const { backgroundColor, borderColor, borderRadius, width } = getComputedStyle(element)
    return { backgroundColor, borderColor, borderRadius, width }
  }
  expect(await play.evaluate(buttonStyle)).toEqual(
    await page.getByRole('button', { name: '复制', exact: true }).evaluate(buttonStyle),
  )
  await play.click()
  await expect(pause).toBeVisible()
  await expect(pause.locator('svg.lucide-pause')).toBeVisible()
  await expect(editor).toHaveText('playsound random.levelu')
  await expect(editor).toBeFocused()

  const progress = page.getByRole('slider', { name: '播放进度' })
  const timeline = page.locator('[data-slot="audio-timeline"]')
  const readPosition = async () => Number(await progress.getAttribute('aria-valuenow'))
  for (const slot of ['slider-track', 'slider-range']) {
    const radius = await timeline
      .locator(`[data-slot="${slot}"]`)
      .evaluate((element) => Number.parseFloat(getComputedStyle(element).borderTopLeftRadius))
    expect(radius).toBeGreaterThan(0)
  }
  const duration = timeline.getByText('0:30', { exact: true })
  await expect(duration).toBeVisible()
  await page.evaluate(() => {
    const { testAudio } = window as Window & { testAudio: HTMLAudioElement }
    testAudio.currentTime = 6
    testAudio.dispatchEvent(new Event('timeupdate'))
  })
  await expect(progress).toHaveAttribute('aria-valuenow', '6')
  await expect(timeline.getByText('0:06', { exact: true })).toBeVisible()
  await expect(progress).toHaveAttribute('aria-valuetext', '0:06 / 0:30')
  await pause.click()
  await expect(play.locator('svg.lucide-play')).toBeVisible()

  const track = (await timeline.locator('[data-slot="slider-track"]').boundingBox())!
  const y = track.y + track.height / 2
  await page.mouse.move(track.x + track.width * 0.2, y)
  await page.mouse.down()
  await page.mouse.move(track.x + track.width * 0.75, y, { steps: 5 })
  await page.mouse.up()
  await expect.poll(readPosition).toBeGreaterThan(18)
  await expect(timeline.getByText('0:06', { exact: true })).toHaveCount(0)
  await expect(duration).toBeVisible()

  const draggedTime = await readPosition()
  await progress.press('ArrowLeft')
  const selectedTime = await readPosition()
  expect(selectedTime).toBeLessThan(draggedTime)
  expect(
    await page.evaluate(
      () => (window as Window & { testAudio: HTMLAudioElement }).testAudio.currentTime,
    ),
  ).toBeCloseTo(selectedTime)

  await play.press('Enter')
  await expect(pause).toBeVisible()
  expect(await readPosition()).toBeCloseTo(selectedTime)
  await expect(editor).toHaveText('playsound random.levelu')
  await page
    .getByRole('option')
    .filter({ hasText: /^random\.levelup/ })
    .first()
    .click()
  await expect
    .poll(async () => (await editor.textContent())?.trimEnd())
    .toBe('playsound random.levelup')
  await expect(progress).toHaveCount(0)
})

test('the single-column layout uses consistent gaps on wide and narrow screens', async ({
  page,
}) => {
  await page.goto('/')
  const moreButton = page.getByRole('button', { name: '更多' })
  const editor = page.locator('.editor-shell')
  const copyButton = page.getByRole('button', { name: '复制' })
  const banner = page.getByRole('banner')
  const main = page.getByRole('main')
  const list = page.getByRole('listbox', { name: '命令补全' })
  const infoCards = banner.locator('[data-slot="card"]')
  const optionCards = list.locator('[data-slot="card"]')
  const scrollbar = main.locator('[data-slot="scroll-area-scrollbar"]')
  await expect(list).toBeVisible()
  await expect(infoCards.first()).toHaveCSS('padding-left', '12px')
  await expect(optionCards.first()).toHaveCSS('padding-left', '12px')
  await expect(moreButton).toHaveAttribute('data-variant', 'outline')
  await expect(copyButton).toHaveAttribute('data-variant', 'outline')
  await expect(moreButton).toHaveAttribute('data-size', 'icon')
  await expect(copyButton).toHaveAttribute('data-size', 'icon')
  for (const button of [moreButton, copyButton]) {
    const radius = await button.evaluate((element) =>
      Number.parseFloat(getComputedStyle(element).borderTopLeftRadius),
    )
    expect(radius).toBeGreaterThanOrEqual(19)
  }

  for (const width of [1280, 320]) {
    await page.setViewportSize({ width, height: 600 })
    await editor.locator('.editor').focus()
    await expect(scrollbar).toBeVisible()
    const [
      header,
      suggestions,
      footer,
      listViewport,
      row,
      track,
      welcome,
      params,
      divider,
      nextRow,
    ] = await Promise.all([
      banner.boundingBox(),
      list.boundingBox(),
      page.getByRole('contentinfo').boundingBox(),
      main.boundingBox(),
      optionCards.first().boundingBox(),
      scrollbar.boundingBox(),
      infoCards.first().boundingBox(),
      infoCards.nth(1).boundingBox(),
      banner.locator('[data-slot="separator"]').boundingBox(),
      optionCards.nth(1).boundingBox(),
    ])
    if (
      !header ||
      !suggestions ||
      !footer ||
      !listViewport ||
      !row ||
      !track ||
      !welcome ||
      !params ||
      !divider ||
      !nextRow
    ) {
      throw new Error('Expected the page sections and their contents to be visible')
    }
    const gap = 8
    for (const edge of [header.x, suggestions.x, footer.x, welcome.x, params.x, divider.x, row.x]) {
      expect(edge).toBeCloseTo(gap, 0)
    }
    expect(divider.width).toBeCloseTo(header.width, 0)
    expect(listViewport.width).toBeCloseTo(width - 2 * gap, 0)
    expect(suggestions.width).toBeCloseTo(listViewport.width, 0)
    expect(row.y).toBeCloseTo(suggestions.y, 0)
    for (const [location, spacing] of Object.entries({
      'top margin': welcome.y,
      'between header cards': params.y - (welcome.y + welcome.height),
      'above the divider': divider.y - (params.y + params.height),
      'below the divider': suggestions.y - (header.y + header.height),
      'between completion cards': nextRow.y - (row.y + row.height),
      'above the footer': footer.y - (listViewport.y + listViewport.height),
      'bottom margin': 600 - (footer.y + footer.height),
      'before the scrollbar': track.x - (row.x + row.width),
    })) {
      expect(spacing, location).toBeCloseTo(gap, 0)
    }

    const [more, input, copy] = await Promise.all([
      moreButton.boundingBox(),
      editor.boundingBox(),
      copyButton.boundingBox(),
    ])
    if (!more || !input || !copy) {
      throw new Error('Expected all three footer controls to be visible')
    }

    expect(more.y).toBeCloseTo(input.y, 0)
    expect(copy.y).toBeCloseTo(input.y, 0)
    expect(more.height).toBeCloseTo(input.height, 0)
    expect(copy.height).toBeCloseTo(input.height, 0)
    expect(more.width).toBeCloseTo(more.height, 0)
    expect(copy.width).toBeCloseTo(copy.height, 0)
    const leftGap = input.x - (more.x + more.width)
    const rightGap = copy.x - (input.x + input.width)
    expect(leftGap).toBeCloseTo(gap, 0)
    expect(rightGap).toBeCloseTo(gap, 0)
    expect(more.x).toBeCloseTo(gap, 0)
    expect(copy.x + copy.width).toBeCloseTo(width - gap, 0)
  }
})

test('the more menu supports keyboard navigation into and out of a submenu', async ({ page }) => {
  await page.goto('/')
  const trigger = page.getByRole('button', { name: '更多' })
  const branch = page.getByRole('menuitem', { name: '分支' })
  const submenu = page.getByRole('menu', { name: '分支' })

  await trigger.press('Enter')
  await expect(branch).toBeFocused()
  await page.keyboard.press('ArrowRight')
  await expect(submenu.getByRole('menuitemradio').first()).toBeFocused()

  await page.keyboard.press('ArrowLeft')
  await expect(submenu).toBeHidden()
  await expect(branch).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('menu')).toHaveCount(0)
  await expect(trigger).toBeFocused()
})

test('choosing a branch in the submenu loads and marks the selected pack on a narrow screen', async ({
  page,
}) => {
  await page.setViewportSize({ width: 320, height: 600 })
  await page.goto('/')
  await expect(page.getByText('欢迎使用CHelper')).toBeVisible()
  const trigger = page.getByRole('button', { name: '更多' })
  const branch = page.getByRole('menuitem', { name: '分支' })
  const submenu = page.getByRole('menu', { name: '分支' })
  const option = submenu.getByRole('menuitemradio', { name: /^测试版-原版-/ })

  await trigger.click()
  await branch.click()
  await expect(option).toBeVisible()
  const bounds = await submenu.boundingBox()
  expect(bounds!.x).toBeGreaterThanOrEqual(0)
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(320)
  const pack = page.waitForResponse(
    (response) => response.url().includes('beta-vanilla-') && response.url().includes('.cpack'),
  )
  await option.click()

  expect((await pack).ok()).toBe(true)
  await expect(submenu).toBeHidden()
  await expect(trigger).toBeFocused()
  await trigger.click()
  await branch.click()
  await expect(option).toHaveAttribute('aria-checked', 'true')
})

async function openThemeMenu(page: Page) {
  await page.getByRole('button', { name: '更多' }).click()
  await page.getByRole('menuitem', { name: '主题' }).click()
  return page.getByRole('menu', { name: '主题' })
}

test('the theme follows the system by default and remembers a manual selection', async ({
  page,
}) => {
  await page.emulateMedia({ colorScheme: 'dark' })
  await page.goto('/')
  const html = page.locator('html')
  await expect(html).toHaveClass(/\bdark\b/)
  await expect(page.locator('body')).toHaveCSS('background-color', 'oklch(0.145 0 0)')
  await expect(page.locator('[data-slot="scroll-area-thumb"]')).toHaveCSS(
    'background-color',
    'oklch(1 0 0 / 0.1)',
  )

  let menu = await openThemeMenu(page)
  await expect(menu.getByRole('menuitemradio', { name: '自动' })).toHaveAttribute(
    'aria-checked',
    'true',
  )
  await menu.getByRole('menuitemradio', { name: '浅色' }).click()
  await expect(html).toHaveClass(/\blight\b/)
  await page.reload()
  await expect(html).toHaveClass(/\blight\b/)

  await page.emulateMedia({ colorScheme: 'light' })
  menu = await openThemeMenu(page)
  await menu.getByRole('menuitemradio', { name: '深色' }).click()
  await expect(html).toHaveClass(/\bdark\b/)
  menu = await openThemeMenu(page)
  await menu.getByRole('menuitemradio', { name: '自动' }).click()
  await expect(html).toHaveClass(/\blight\b/)

  await expect(page.getByText('欢迎使用CHelper')).toBeVisible()
  const editor = page.getByRole('textbox', { name: '命令输入' })
  const highlightedCommand = page.locator('.editor-highlight-content span').first()
  await editor.fill('say hello')
  await editor.press('Control+A')
  await expect(highlightedCommand).toHaveCSS('color', 'rgb(159, 32, 167)')

  await page.emulateMedia({ colorScheme: 'dark' })
  await expect(html).toHaveClass(/\bdark\b/)
  await expect(highlightedCommand).toHaveCSS('color', 'rgb(197, 134, 192)')
  await expect(editor).toHaveCSS('caret-color', 'oklch(0.985 0 0)')
  await expect(editor).toBeFocused()
  expect(await page.evaluate(() => window.getSelection()?.toString())).toBe('say hello')
})

test('keyboard navigation can apply a completion from the list', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('欢迎使用CHelper')).toBeVisible()
  const editor = page.locator('.editor')
  await editor.fill('s')
  const list = page.getByRole('listbox', { name: '命令补全' })
  const options = list.getByRole('option')

  await expect(options.nth(1)).toBeVisible()
  await list.press('Home')
  await expect(options.first()).toBeFocused()
  await page.keyboard.press('ArrowDown')
  await expect(options.nth(1)).toBeFocused()

  await editor.fill('sa')
  await list.press('Home')
  await page.keyboard.press('Enter')
  await expect(editor).toHaveText(/^say\s*$/)
  await expect(editor).toBeFocused()
})

test('the styled scrollbar spans every completion and supports on-demand dragging', async ({
  page,
}) => {
  await page.setViewportSize({ width: 800, height: 500 })
  await page.goto('/')
  await expect(page.getByText('欢迎使用CHelper')).toBeVisible()
  type InstrumentedWindow = Window & { completionReads: number[] }
  await page.evaluate(async () => {
    const path = '/src/core/libCHelperWeb.js'
    const { CommandContext } = await import(/* @vite-ignore */ path)
    const reads: number[] = []
    ;(window as InstrumentedWindow).completionReads = reads
    const original = CommandContext.prototype.getSuggestion
    CommandContext.prototype.getSuggestion = function (cursor: number, index: number) {
      if (this.getCommand() === '') {
        reads.push(index)
      }
      return original.call(this, cursor, index)
    }
    CommandContext.prototype.getAllSuggestions = () => {
      throw new Error('The completion list must read suggestions by index')
    }
  })

  const editor = page.getByRole('textbox', { name: '命令输入' })
  await editor.fill('sa')
  await editor.fill('')
  const list = page.getByRole('listbox', { name: '命令补全' })
  const fadeSizes = () =>
    list.evaluate((element) => {
      const style = getComputedStyle(element)
      return ['--scroll-fade-t', '--scroll-fade-b'].map((edge) =>
        Number.parseFloat(style.getPropertyValue(edge)),
      )
    })
  const first = list.locator('[aria-posinset="1"]')
  await expect(first).toBeVisible()
  await expect.poll(fadeSizes).toEqual([0, 16])
  const total = Number(await first.getAttribute('aria-setsize'))
  const initialReads = await page.evaluate(() => (window as InstrumentedWindow).completionReads)
  expect(total).toBeGreaterThan(50)
  expect(initialReads).toContain(0)
  expect(initialReads.length).toBeLessThan(total / 2)
  expect(await list.evaluate((element) => element.scrollHeight)).toBeGreaterThan(total * 50)

  const scrollbar = page.locator('[data-slot="scroll-area-scrollbar"]')
  const thumb = scrollbar.locator('[data-slot="scroll-area-thumb"]')
  await expect(thumb).toBeVisible()
  await expect(list).toHaveCSS('scrollbar-width', 'none')
  const [trackBounds, thumbBounds] = await Promise.all([
    scrollbar.boundingBox(),
    thumb.boundingBox(),
  ])
  if (!trackBounds || !thumbBounds) {
    throw new Error('Expected the Scroll Area track and thumb to be visible')
  }

  const last = list.locator(`[aria-posinset="${total}"]`)
  await expect(last).toHaveCount(0)
  const thumbX = thumbBounds.x + thumbBounds.width / 2
  await page.mouse.move(thumbX, thumbBounds.y + thumbBounds.height / 2)
  await page.mouse.down()
  await page.mouse.move(thumbX, trackBounds.y + trackBounds.height)
  await page.mouse.up()
  await expect(last).toBeInViewport()
  await expect.poll(fadeSizes).toEqual([16, 0])
  const readsAfterJump = await page.evaluate(() => (window as InstrumentedWindow).completionReads)
  expect(readsAfterJump).toContain(total - 1)
  expect(readsAfterJump.length).toBeLessThan(total / 2)

  await list.evaluate((element) => {
    element.scrollTop = element.scrollHeight / 2
  })
  await expect.poll(fadeSizes).toEqual([16, 16])

  await list.press('Home')
  await expect(first).toBeFocused()
  await page.keyboard.press('End')
  await expect(last).toBeFocused()

  await editor.fill('sa')
  await expect(list.getByRole('option').first()).toHaveAttribute('aria-posinset', '1')
  expect(await list.evaluate((element) => element.scrollTop)).toBe(0)
  await expect(scrollbar).toBeHidden()
  await expect.poll(fadeSizes).toEqual([0, 0])
})

test('clipboard failures appear as a dismissible notification', async ({ page }) => {
  await page.addInitScript(() => {
    navigator.clipboard.writeText = async () => {
      throw new Error('测试剪贴板不可用')
    }
  })
  await page.goto('/')

  await page.getByRole('button', { name: '复制' }).click()
  const message = page.getByText('复制失败', { exact: true })
  await expect(message).toBeVisible()
  await expect(page.getByText('Error: 测试剪贴板不可用', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '关闭复制提示' }).click()
  await expect(message).toBeHidden()
})

test('copy writes the command to the clipboard', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write'])
  await page.goto('/')
  await expect(page.getByText('欢迎使用CHelper')).toBeVisible()
  await page.getByRole('textbox', { name: '命令输入' }).fill('say hello')

  await page.getByRole('button', { name: '复制' }).click()

  await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe('say hello')
})
