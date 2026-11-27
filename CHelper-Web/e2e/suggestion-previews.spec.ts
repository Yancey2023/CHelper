import { expect, test, type Locator } from '@playwright/test'

test.describe.configure({ mode: 'parallel' })

const png = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAEUlEQVR4nGOo1nX+D8IMMAYAQbYHqeIjnQ0AAAAASUVORK5CYII=',
  'base64',
)
const gif = Buffer.from('R0lGODlhAQABAPAAAP///wAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw==', 'base64')

const viewports = [
  { width: 320, height: 280 },
  { width: 320, height: 568 },
  { width: 390, height: 844 },
  { width: 844, height: 390 },
  { width: 768, height: 1024 },
  { width: 1440, height: 580 },
  { width: 1920, height: 1080 },
]

async function expectPreviewInCard(
  preview: Locator,
  card: Locator,
  text: Locator,
  placement: 'inline' | 'below' = 'inline',
) {
  await expect(preview).toBeVisible()
  await expect
    .poll(async () => {
      const [previewBox, cardBox, textBox] = await Promise.all([
        preview.boundingBox(),
        card.boundingBox(),
        text.boundingBox(),
      ])
      if (!previewBox || !cardBox || !textBox) return false
      const { x, y, width, height } = previewBox
      return (
        x >= cardBox.x &&
        x + width <= cardBox.x + cardBox.width &&
        y >= cardBox.y &&
        y + height <= cardBox.y + cardBox.height &&
        (placement === 'below'
          ? y >= textBox.y + textBox.height
          : Math.abs(y + height / 2 - cardBox.y - cardBox.height / 2) <= 1 &&
            (x >= textBox.x + textBox.width || x + width <= textBox.x))
      )
    })
    .toBe(true)
  await preview.scrollIntoViewIfNeeded()
  await expect(preview).toBeInViewport({ ratio: 1 })
}

for (const viewport of viewports) {
  test(`keeps completion previews visible and aligned at ${viewport.width}×${viewport.height}`, async ({
    page,
  }) => {
    await page.setViewportSize(viewport)
    await page.addInitScript(() => {
      Object.defineProperty(HTMLMediaElement.prototype, 'duration', { get: () => 8.9 })
      HTMLMediaElement.prototype.play = async () => {}
      HTMLMediaElement.prototype.pause = () => {}
      HTMLMediaElement.prototype.load = () => {}
    })
    await page.route('https://zh.minecraft.wiki/images/*.png', (route) =>
      route.fulfill({
        contentType: 'image/png',
        headers: { 'access-control-allow-origin': '*' },
        body: png,
      }),
    )
    await page.route('https://zh.minecraft.wiki/images/*.gif', (route) =>
      route.fulfill({
        contentType: 'image/gif',
        headers: { 'access-control-allow-origin': '*' },
        body: gif,
      }),
    )
    await page.goto('/')
    await expect(page.getByText('欢迎使用CHelper')).toBeVisible()
    const editor = page.getByRole('textbox', { name: '命令输入' })
    const list = page.getByRole('listbox', { name: '命令补全' })

    await editor.fill('playsound ')
    for (const title of ['ambient.basalt_deltas.additions', 'ambient.basalt_deltas.loop']) {
      const option = list.getByRole('option').filter({ hasText: title })
      const card = option.locator('[data-slot="card"]')
      const row = option.locator('..')
      const text = option.getByText(title, { exact: true })
      const play = row.getByRole('button', { name: '播放试听' })
      await expectPreviewInCard(play, card, text)
      await play.click()
      const timeline = row.locator('[data-slot="audio-timeline"]')
      const progress = timeline.getByRole('slider', { name: '播放进度' })
      const placement = viewport.width <= 576 && viewport.height >= 384 ? 'below' : 'inline'
      await expectPreviewInCard(timeline, card, text.locator('..'), placement)
      await expect(progress).toBeEnabled()
      await expect(timeline.getByText('0:00', { exact: true })).toBeInViewport({ ratio: 1 })
      await expect(timeline.getByText('0:08', { exact: true })).toBeInViewport({ ratio: 1 })
      const pause = row.getByRole('button', { name: '暂停试听' })
      await expect(pause).toBeInViewport({ ratio: 1 })
      await pause.click()
      await expect(play).toBeVisible()
    }
    await expect(editor).toHaveText('playsound ')

    for (const { command, title, selector } of [
      { command: 'give @s acacia_chest_boat', title: 'acacia_chest_boat', selector: 'canvas' },
      { command: 'give @s command_block', title: 'command_block', selector: 'canvas' },
      { command: 'setblock ~ ~ ~ minecraft:dirt', title: 'minecraft:dirt', selector: 'canvas' },
      { command: 'effect @s speed', title: 'speed', selector: 'canvas' },
      {
        command: 'camera @s set minecraft:free ease 1 in_quad',
        title: 'in_quad',
        selector: '[aria-label="缓动曲线"]',
      },
      { command: 'give @s blue_dye', title: 'blue_dye', selector: '[aria-label="染料色样"]' },
    ]) {
      await editor.fill(command)
      const text = list.getByText(title, { exact: true })
      const card = text.locator('xpath=ancestor::*[@data-slot="card"]').first()
      const preview = card.locator(selector)
      await expectPreviewInCard(preview, card, text)
      if (selector === 'canvas') {
        await expect
          .poll(() =>
            preview.evaluate(
              (canvas: HTMLCanvasElement) =>
                canvas.getContext('2d')!.getImageData(28, 28, 1, 1).data[3],
            ),
          )
          .toBe(255)
      }
    }
  })
}
