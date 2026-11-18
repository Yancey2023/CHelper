import { expect, test, type Page } from '@playwright/test'

async function editorText(page: Page): Promise<string> {
  return page.locator('.editor').evaluate((editor) => editor.textContent ?? '')
}

async function setEditorSelection(page: Page, start: number, end: number): Promise<void> {
  await page.locator('.editor').evaluate(
    (editor, offsets) => {
      const findPosition = (offset: number): { node: Node; offset: number } => {
        let remaining = offset
        const walker = document.createTreeWalker(editor, NodeFilter.SHOW_TEXT)
        let lastText: Text | null = null
        let node = walker.nextNode() as Text | null
        while (node) {
          lastText = node
          if (remaining <= node.length) {
            return { node, offset: remaining }
          }
          remaining -= node.length
          node = walker.nextNode() as Text | null
        }
        return { node: lastText ?? editor, offset: lastText?.length ?? 0 }
      }

      const selection = window.getSelection()
      const range = document.createRange()
      const startPosition = findPosition(offsets.start)
      const endPosition = findPosition(offsets.end)
      range.setStart(startPosition.node, startPosition.offset)
      range.setEnd(endPosition.node, endPosition.offset)
      selection?.removeAllRanges()
      selection?.addRange(range)
      editor.dispatchEvent(new Event('selectionchange', { bubbles: true }))
    },
    { start, end },
  )
}

async function typeHelloWorld(page: Page): Promise<void> {
  const editor = page.locator('.editor')
  await editor.click()
  await page.keyboard.type('hello world')
  await expect.poll(() => editorText(page)).toBe('hello world')
}

async function highlightEditor(page: Page): Promise<void> {
  const editor = page.locator('.editor')
  await expect.poll(() => hasSyntaxHighlight(page)).toBe(true)
  await expect(page.locator('.editor-highlight')).toHaveText(await editorText(page))
}

async function hasSyntaxHighlight(page: Page): Promise<boolean> {
  return page
    .locator('.editor-highlight-content > span[style*="color"]')
    .count()
    .then((count) => count > 0)
}

test('keeps committed syntax visible around native IME composition', async ({ page, context }) => {
  await page.goto('/')
  await expect(page.getByText('欢迎使用CHelper')).toBeVisible()
  const editor = page.getByRole('textbox', { name: '命令输入' })
  const highlight = page.locator('.editor-highlight')
  const command = highlight.getByText('give', { exact: true })
  const target = highlight.getByText('s', { exact: true })
  const preedit = highlight.locator('.editor-highlight-preedit')
  await editor.fill('give @s')
  await expect(command).toHaveCSS('color', 'rgb(159, 32, 167)')
  await expect(target).toHaveCSS('color', 'rgb(15, 160, 200)')
  await setEditorSelection(page, 5, 5)

  const ime = await context.newCDPSession(page)
  await ime.send('Input.imeSetComposition', { text: 'ni', selectionStart: 2, selectionEnd: 2 })
  await expect(editor).toHaveText('give ni@s')
  await expect(highlight).toBeVisible()
  await expect(preedit).toHaveText('ni')
  await expect(preedit).toHaveCSS('color', 'rgba(0, 0, 0, 0)')
  await expect(preedit).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)')
  await expect(command).toHaveCSS('color', 'rgb(159, 32, 167)')
  await expect(target).toHaveCSS('color', 'rgb(15, 160, 200)')

  await page.emulateMedia({ colorScheme: 'dark' })
  await expect(command).toHaveCSS('color', 'rgb(197, 134, 192)')
  await ime.send('Input.imeSetComposition', { text: 'nin', selectionStart: 3, selectionEnd: 3 })
  await expect(editor).toHaveText('give nin@s')
  await expect(preedit).toHaveText('nin')
  await ime.send('Input.insertText', { text: '您' })
  await expect(editor).toHaveText('give 您@s')
  await expect(preedit).toHaveCount(0)
})

test.describe('contenteditable selection behavior', () => {
  test('Ctrl+A followed immediately by Backspace clears the editor', async ({ page }) => {
    await page.goto('/')
    await typeHelloWorld(page)

    await page.keyboard.press('Control+A')
    await page.keyboard.press('Backspace')

    await expect.poll(() => editorText(page)).toBe('')
  })

  test('rapid Ctrl+A and Backspace clears a multi-token command', async ({ page }) => {
    for (let attempt = 0; attempt < 10; attempt++) {
      await page.goto('/')
      const editor = page.locator('.editor')
      await editor.click()
      await page.keyboard.type('give @s 命令')
      await expect.poll(() => editorText(page)).toBe('give @s 命令')

      await page.keyboard.press('Control+A')
      await page.keyboard.press('Backspace')

      await expect.poll(() => editorText(page)).toBe('')
    }
  })

  test('Ctrl+A held with Backspace still clears a multi-token command', async ({ page }) => {
    await page.goto('/')
    const editor = page.locator('.editor')
    await editor.click()
    await page.keyboard.type('give @s 命令')
    await expect.poll(() => editorText(page)).toBe('give @s 命令')

    await page.keyboard.down('Control')
    await page.keyboard.press('a')
    await page.keyboard.press('Backspace')
    await page.keyboard.up('Control')

    await expect.poll(() => editorText(page)).toBe('')
  })

  test('Ctrl+A replaces the whole text with new input', async ({ page }) => {
    await page.goto('/')
    await typeHelloWorld(page)

    await page.keyboard.press('Control+A')
    await page.keyboard.type('abc')

    await expect.poll(() => editorText(page)).toBe('abc')
  })

  test('Ctrl+V paste is syntax highlighted', async ({ page, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write'])
    await page.goto('/')
    const editor = page.locator('.editor')
    await editor.click()
    await page.evaluate(() => navigator.clipboard.writeText('hello world'))

    await page.keyboard.press('Control+V')

    await expect.poll(() => editorText(page)).toBe('hello world')
    await expect.poll(() => hasSyntaxHighlight(page)).toBe(true)
  })

  test('Ctrl+X followed by Ctrl+V reapplies syntax highlighting', async ({ page, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write'])
    await page.goto('/')
    await typeHelloWorld(page)

    await page.keyboard.press('Control+A')
    await page.keyboard.press('Control+X')
    await expect.poll(() => editorText(page)).toBe('')

    await page.keyboard.press('Control+V')

    await expect.poll(() => editorText(page)).toBe('hello world')
    await expect.poll(() => hasSyntaxHighlight(page)).toBe(true)
  })

  test('Backspace deletes a partial selection across highlighted text', async ({ page }) => {
    await page.goto('/')
    await typeHelloWorld(page)
    await highlightEditor(page)

    await setEditorSelection(page, 6, 11)
    await page.keyboard.press('Backspace')

    await expect.poll(() => editorText(page)).toBe('hello ')
  })

  test('uses an opaque platform selection background above the highlight layer', async ({
    page,
  }) => {
    await page.goto('/')
    await typeHelloWorld(page)
    await highlightEditor(page)
    await setEditorSelection(page, 0, 5)

    const selectionStyle = await page.locator('.editor').evaluate((editor) => {
      const style = getComputedStyle(editor, '::selection')
      return {
        backgroundColor: style.backgroundColor,
        color: style.color,
      }
    })

    expect(selectionStyle.backgroundColor).not.toBe('rgba(0, 0, 0, 0)')
    expect(selectionStyle.backgroundColor).not.toBe('transparent')
    expect(selectionStyle.color).not.toBe('rgba(0, 0, 0, 0)')
  })

  test('Shift+Arrow selection works with both Backspace and Delete', async ({ page }) => {
    await page.goto('/')
    await typeHelloWorld(page)
    await page.keyboard.press('End')
    for (let index = 0; index < 5; index++) {
      await page.keyboard.press('Shift+ArrowLeft')
    }
    await page.keyboard.press('Backspace')
    await expect.poll(() => editorText(page)).toBe('hello ')

    await page.keyboard.press('Control+A')
    await page.keyboard.type('hello world')
    await page.keyboard.press('Home')
    for (let index = 0; index < 5; index++) {
      await page.keyboard.press('Shift+ArrowRight')
    }
    await page.keyboard.press('Delete')
    await expect.poll(() => editorText(page)).toBe(' world')
  })

  test('keeps Ctrl+A selection after syntax highlighting', async ({ page }) => {
    await page.goto('/')
    await typeHelloWorld(page)
    await highlightEditor(page)

    await page.keyboard.press('Control+A')
    await expect
      .poll(() => page.evaluate(() => window.getSelection()?.toString() ?? ''))
      .toBe('hello world')
    await page.keyboard.press('Backspace')

    await expect.poll(() => editorText(page)).toBe('')
  })

  test('preserves native single-caret editing and Undo/Redo', async ({ page }) => {
    await page.goto('/')
    const editor = page.locator('.editor')
    await editor.click()
    await page.keyboard.type('hello')
    await page.keyboard.press('Control+Z')
    await expect.poll(() => editorText(page)).not.toBe('hello')
    await page.keyboard.press('Control+Y')
    await expect.poll(() => editorText(page)).toBe('hello')

    await setEditorSelection(page, 5, 5)
    await page.keyboard.type('!')
    await expect.poll(() => editorText(page)).toBe('hello!')
    await page.keyboard.press('Backspace')
    await expect.poll(() => editorText(page)).toBe('hello')
    await page.keyboard.press('Delete')
    await expect.poll(() => editorText(page)).toBe('hello')
  })

  test('does not steal focus or delete editor text when another control owns focus', async ({
    page,
  }) => {
    await page.goto('/')
    await typeHelloWorld(page)
    await setEditorSelection(page, 6, 11)
    await page.evaluate(() => {
      const button = document.createElement('button')
      button.id = 'focus-target'
      button.textContent = 'focus target'
      document.body.append(button)
      button.focus()
    })

    await page.keyboard.press('Backspace')

    await expect.poll(() => editorText(page)).toBe('hello world')
    await expect(page.locator('#focus-target')).toBeFocused()
  })
})

test.describe('mobile-compatible input behavior', () => {
  test.use({
    viewport: { width: 390, height: 844 },
    hasTouch: true,
  })

  test('supports touch focus, input without keydown, and full-range deletion', async ({ page }) => {
    await page.goto('/')
    const editor = page.locator('.editor')
    const box = await editor.boundingBox()
    expect(box).not.toBeNull()
    await page.touchscreen.tap((box?.x ?? 0) + 10, (box?.y ?? 0) + 10)

    await page.keyboard.insertText('give @s 命令')
    await expect.poll(() => editorText(page)).toBe('give @s 命令')
    await expect.poll(() => hasSyntaxHighlight(page)).toBe(true)

    await setEditorSelection(page, 0, 'give @s 命令'.length)
    await editor.evaluate(() => document.execCommand('delete'))

    await expect.poll(() => editorText(page)).toBe('')
  })
})
