import { expect, test } from '@playwright/test'

test('loads every bundled resource pack with the generated Wasm core', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto('/')

  const results = await page.evaluate(async () => {
    const modulePath = '/src/core/CPackManager.ts'
    const { ALL_BRANCH, getCore } = await import(/* @vite-ignore */ modulePath)
    const results = []
    for (const branch of ALL_BRANCH) {
      const core = await getCore(branch)
      try {
        const context = core.createContext('say hello')
        try {
          results.push({ branch, command: context.getCommand(), errors: context.getErrorReasons() })
        } finally {
          context.release()
        }
      } finally {
        core.release()
      }
    }
    return results
  })

  expect(results).toHaveLength(6)
  for (const result of results) {
    expect(result.command, result.branch).toBe('say hello')
    expect(result.errors, result.branch).toEqual([])
  }
  expect(errors).toEqual([])
})
